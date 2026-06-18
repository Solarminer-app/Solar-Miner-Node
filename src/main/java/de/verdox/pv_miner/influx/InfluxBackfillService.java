package de.verdox.pv_miner.influx;

import com.influxdb.client.BucketsApi;
import com.influxdb.client.domain.Bucket;
import de.verdox.pv_miner.miner.MinerStatisticsAccumulator;
import de.verdox.pv_miner.pvsite.PVStatisticsAccumulator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

@Service
public class InfluxBackfillService {
    private static final Logger LOGGER = Logger.getLogger(InfluxBackfillService.class.getSimpleName());

    private final InfluxService influxService;

    @Value("${influxdb.bucket}")
    private String rawBucket;

    public InfluxBackfillService(InfluxService influxService) {
        this.influxService = influxService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkForRequiredBackfills() {
        CompletableFuture.runAsync(() -> {
            LOGGER.info("Checking if InfluxDB-Backfills are needed...");

            ensureDownsampleBucketExists();

            // 1. BACKFILL FÜR PV-ANLAGEN
            String pvTargetMeasurement = new PVStatisticsAccumulator().getDownsampledMeasurementName();
            if (influxService.isMeasurementEmpty(influxService.getDownSampledInfluxBucket(), pvTargetMeasurement)) {
                LOGGER.info("Downsampling-Cache for pv site is empty. Starting daily-chunked backfill with Miner-Fallback...");
                executeDailyChunkedPvBackfill("pv_site_data", pvTargetMeasurement);
                LOGGER.info("Backfill done for pv sites.");
            }

            // 2. BACKFILL FÜR MINER
            String minerTargetMeasurement = new MinerStatisticsAccumulator().getDownsampledMeasurementName();
            if (influxService.isMeasurementEmpty(influxService.getDownSampledInfluxBucket(), minerTargetMeasurement)) {
                LOGGER.info("Downsampling-Cache for miner data is empty. Starting daily-chunked backfill...");
                executeDailyChunkedMinerBackfill("miner_data", minerTargetMeasurement);
                LOGGER.info("Backfill done for miner data.");
            }

            LOGGER.info("InfluxDB-Backfill-Check done.");
        }).exceptionally(ex -> {
            LOGGER.severe("Error while doing the backfill check: " + ex.getMessage());
            ex.printStackTrace();
            return null;
        });
    }

    private void executeDailyChunkedPvBackfill(String sourceMeasurement, String targetMeasurement) {
        LocalDate currentDay = LocalDate.of(2023, 1, 1);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        while (currentDay.isBefore(today) || currentDay.isEqual(today)) {
            String startRange = currentDay.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
            String stopRange = currentDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1).toString();

            LOGGER.fine(String.format(" -> Berechne PV für Tag %s...", currentDay));

            String pvBackfillScript = String.format(
                    """
                            pv_all = from(bucket: "%s")
                              |> range(start: %s, stop: %s)
                              |> filter(fn: (r) => r["_measurement"] == "%s")
                              |> filter(fn: (r) => r["_field"] == "PVPowerInKw" or r["_field"] == "GridConsumptionPower" or r["_field"] == "FeedInPowerInKw" or r["_field"] == "LoadsPowerInKw" or r["_field"] == "MinerPowerInKw")
                            
                            // 1. Hole dynamisch die "entity" UUID der PV-Anlage von diesem Tag
                            pv_entity_arr = pv_all
                              |> keep(columns: ["entity"])
                              |> limit(n: 1)
                              |> findColumn(fn: (key) => true, column: "entity")
                            
                            pv_entity = if length(arr: pv_entity_arr) > 0 then pv_entity_arr[0] else "unknown"
                            
                            // 2. Zähle, ob heute Miner-Daten in den PV-Daten existieren
                            miner_pv_count = pv_all
                              |> filter(fn: (r) => r["_field"] == "MinerPowerInKw")
                              |> count()
                              |> findColumn(fn: (key) => true, column: "_value")
                            
                            has_miner_in_pv = if length(arr: miner_pv_count) > 0 and miner_pv_count[0] > 0 then true else false
                            
                            // 3. PV-Daten ganz normal aggregieren
                            pv_aggregated = pv_all
                              |> group(columns: ["_measurement", "_field", "entity"])
                              |> aggregateWindow(every: 1d, fn: (column, tables=<-) => tables |> integral(unit: 1h), createEmpty: false)
                            
                            // 4. Fallback: Integral PRO MINER bilden und dann ALLE Miner addieren
                            miner_fallback = from(bucket: "%s")
                              |> range(start: %s, stop: %s)
                              |> filter(fn: (r) => r["_measurement"] == "miner_data")
                              |> filter(fn: (r) => r["_field"] == "powerUsageWatts")
                              |> filter(fn: (r) => not has_miner_in_pv)
                              |> group(columns: ["_measurement", "_field", "entity"]) // Erst streng nach einzelnem Miner trennen!
                              |> aggregateWindow(every: 1d, fn: (column, tables=<-) => tables |> integral(unit: 1h), createEmpty: false) // Wh pro Miner
                              |> group(columns: ["_time"]) // Jetzt Gruppierung aufheben und nur nach Tag zusammenfassen
                              |> sum(column: "_value") // Den Tagesverbrauch ALLER Miner addieren
                              |> map(fn: (r) => ({ r with _value: r._value / 1000.0, _field: "MinerPowerInKw", _measurement: "%s", entity: pv_entity })) // Auf PV mappen
                            
                            // 5. Bereits fertig berechnete PV-Daten und Miner-Summen zusammenführen
                            union(tables: [pv_aggregated, miner_fallback])
                              |> group(columns: ["_measurement", "_field", "entity"]) // Saubere Tags für den Bucket-Write
                              |> set(key: "_measurement", value: "%s")
                              |> to(bucket: "%s")""",
                    rawBucket, startRange, stopRange, sourceMeasurement,
                    rawBucket, startRange, stopRange, sourceMeasurement, targetMeasurement, influxService.getDownSampledInfluxBucket()
            );

            influxService.executeRawFluxQuery(pvBackfillScript);
            currentDay = currentDay.plusDays(1);
        }
    }

    private void executeDailyChunkedMinerBackfill(String sourceMeasurement, String targetMeasurement) {
        LocalDate currentDay = LocalDate.of(2023, 1, 1);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        while (currentDay.isBefore(today) || currentDay.isEqual(today)) {
            String startRange = currentDay.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
            String stopRange = currentDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1).toString();

            LOGGER.fine(String.format(" -> Berechne Miner für Tag %s...", currentDay));

            String minerBackfillScript = String.format(
                    """
                            from(bucket: "%s")
                              |> range(start: %s, stop: %s)
                              |> filter(fn: (r) => r["_measurement"] == "%s")
                              |> filter(fn: (r) => r["_field"] == "powerUsageWatts")
                              |> group(columns: ["_measurement", "_field", "entity"])
                              |> aggregateWindow(every: 1d, fn: (column, tables=<-) => tables |> integral(unit: 1h), createEmpty: false)
                              |> map(fn: (r) => ({ r with _value: r._value / 1000.0 }))
                              |> set(key: "_measurement", value: "%s")
                              |> to(bucket: "%s")""",
                    rawBucket, startRange, stopRange, sourceMeasurement, targetMeasurement, influxService.getDownSampledInfluxBucket()
            );

            influxService.executeRawFluxQuery(minerBackfillScript);
            currentDay = currentDay.plusDays(1);
        }
    }

    private void ensureDownsampleBucketExists() {
        try {
            var client = influxService.getInfluxDBClient();
            BucketsApi bucketsApi = client.getBucketsApi();

            Bucket bucket = bucketsApi.findBucketByName(influxService.getDownSampledInfluxBucket());
            if (bucket == null) {
                LOGGER.info("Bucket '" + influxService.getDownSampledInfluxBucket() + "' will be created...");

                String orgId = client.getOrganizationsApi().findOrganizations().stream()
                        .filter(o -> o.getName().equals(influxService.getInfluxOrg()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Organisation '" + influxService.getInfluxOrg() + "' not found!"))
                        .getId();

                bucketsApi.createBucket(influxService.getDownSampledInfluxBucket(), orgId);
                LOGGER.info("Bucket '" + influxService.getDownSampledInfluxBucket() + "' created!");

                Thread.sleep(1000);
            }
        } catch (Exception e) {
            LOGGER.severe("Could not create bucket '" + influxService.getDownSampledInfluxBucket() + "': " + e.getMessage());
        }
    }
}