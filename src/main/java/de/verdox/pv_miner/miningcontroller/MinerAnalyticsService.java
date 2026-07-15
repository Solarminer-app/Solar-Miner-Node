package de.verdox.pv_miner.miningcontroller;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.dto.MinerDetailsPageDto;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.influx.InfluxService;
import de.verdox.pv_miner.influx.InfluxUtil;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerInfluxStrategy;
import de.verdox.pv_miner.miner.data.MinerStats;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class MinerAnalyticsService {
    private static final List<String> HISTORY_FIELDS = List.of(
            MinerInfluxStrategy.POWER_USAGE_WATTS,
            MinerInfluxStrategy.TERA_HASH_PER_SECOND,
            MinerInfluxStrategy.POWER_TARGET_WATTS,
            MinerInfluxStrategy.TEMPERATURE_CHIP_CELSIUS,
            MinerInfluxStrategy.EFFICIENCY_J_TH
    );

    private final EntityQueryService queryService;
    private final InfluxService influxService;

    public MinerAnalyticsService(EntityQueryService queryService, InfluxService influxService) {
        this.queryService = queryService;
        this.influxService = influxService;
    }

    public MinerDetailsPageDto getDetails(MinerEntity<?> miner, int requestedHours) {
        int hours = Math.max(1, Math.min(24 * 30, requestedHours));
        int intervalMinutes = hours <= 24 ? 5 : hours <= 24 * 7 ? 15 : 60;
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofHours(hours));
        MinerStats stats = queryService.getLastResult(miner, MinerStats.DEFAULT);
        if (stats == null) stats = MinerStats.DEFAULT;

        List<MinerDetailsPageDto.MinerHistoryPointDto> history = loadHistory(miner, from, to, intervalMinutes);
        MinerDetailsPageDto.MinerHistorySummaryDto summary = summarize(history, from, to, intervalMinutes);
        double efficiency = stats.terahashPerSecond() > 0
                ? stats.approximatedPowerUsageWatts() / stats.terahashPerSecond()
                : 0;

        return new MinerDetailsPageDto(
                miner.getId(),
                miner.getName(),
                miner.getIP(),
                miner.getOS().name(),
                stats.minerIdentity().minerModel(),
                stats.minerIdentity().minerUID(),
                stats.minerIdentity().macAddress(),
                stats.miningStatus().name(),
                miner.getClusterName(),
                miner.getCurrentMiningPoolTarget(),
                new MinerDetailsPageDto.LiveMinerStatsDto(
                        stats.terahashPerSecond(), stats.approximatedPowerUsageWatts(), stats.powerTargetWatts(),
                        stats.temperatureCelsius(), efficiency
                ),
                new MinerDetailsPageDto.MinerHardwareDto(
                        stats.minPowerTarget(), stats.defaultPowerTarget(), stats.maxPowerTarget(),
                        miner.getMinPowerTarget(), miner.getMaxPowerTarget(), miner.getPowerStepSizeWatts(),
                        miner.getMinRunTimeMinutes(), miner.getMinIdleTimeMinutes(), miner.getPowerChangeLockTimeMinutes()
                ),
                stats.pools().stream()
                        .map(pool -> new MinerDetailsPageDto.MinerPoolDto(pool.poolUrl(), pool.poolUsername()))
                        .toList(),
                stats.workers().stream()
                        .map(worker -> new MinerDetailsPageDto.MinerWorkerDto(
                                worker.workerDisplayName(), worker.currentAlgorithm(), worker.miningStatus().name(),
                                worker.terahashPerSecond(), worker.temperatureCelsius(), worker.approximatedPowerUsageWatts()
                        ))
                        .toList(),
                summary,
                history
        );
    }

    private List<MinerDetailsPageDto.MinerHistoryPointDto> loadHistory(
            MinerEntity<?> miner,
            Instant from,
            Instant to,
            int intervalMinutes
    ) {
        List<FluxRecord> records = influxService.queryDataFromApi(miner, from, to, builder -> {
            HISTORY_FIELDS.forEach(builder::addField);
            builder.setAggregation(InfluxUtil.AggregateOperation.MEAN, Duration.ofMinutes(intervalMinutes));
        });
        Map<Instant, Map<String, Double>> values = new TreeMap<>();
        for (FluxRecord record : records) {
            if (record.getTime() == null || record.getField() == null || !(record.getValue() instanceof Number number)) continue;
            values.computeIfAbsent(record.getTime(), ignored -> new LinkedHashMap<>())
                    .put(record.getField(), number.doubleValue());
        }
        return values.entrySet().stream()
                .map(entry -> new MinerDetailsPageDto.MinerHistoryPointDto(
                        entry.getKey(),
                        entry.getValue().get(MinerInfluxStrategy.TERA_HASH_PER_SECOND),
                        entry.getValue().get(MinerInfluxStrategy.POWER_USAGE_WATTS),
                        entry.getValue().get(MinerInfluxStrategy.POWER_TARGET_WATTS),
                        entry.getValue().get(MinerInfluxStrategy.TEMPERATURE_CHIP_CELSIUS),
                        entry.getValue().get(MinerInfluxStrategy.EFFICIENCY_J_TH)
                ))
                .toList();
    }

    private MinerDetailsPageDto.MinerHistorySummaryDto summarize(
            List<MinerDetailsPageDto.MinerHistoryPointDto> history,
            Instant from,
            Instant to,
            int intervalMinutes
    ) {
        double averageHashrate = average(history.stream().map(MinerDetailsPageDto.MinerHistoryPointDto::hashrateThs).toList());
        double averagePower = average(history.stream().map(MinerDetailsPageDto.MinerHistoryPointDto::powerUsageWatts).toList());
        double averageEfficiency = average(history.stream().map(MinerDetailsPageDto.MinerHistoryPointDto::efficiencyJTh).toList());
        double maximumTemperature = history.stream()
                .map(MinerDetailsPageDto.MinerHistoryPointDto::temperatureCelsius)
                .filter(value -> value != null && Double.isFinite(value))
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0);
        double energyKwh = history.stream()
                .map(MinerDetailsPageDto.MinerHistoryPointDto::powerUsageWatts)
                .filter(value -> value != null && Double.isFinite(value))
                .mapToDouble(Double::doubleValue)
                .sum() / 1000 * intervalMinutes / 60.0;
        return new MinerDetailsPageDto.MinerHistorySummaryDto(
                from, to, history.size(), averageHashrate, averagePower, averageEfficiency,
                maximumTemperature, energyKwh
        );
    }

    private double average(List<Double> values) {
        return values.stream()
                .filter(value -> value != null && Double.isFinite(value))
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0);
    }
}
