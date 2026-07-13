package de.verdox.pv_miner.miningcontroller;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.influx.InfluxService;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerDSL;
import de.verdox.pv_miner.miningcontroller.dsl.ControllerValueProvider;
import de.verdox.pv_miner.pvsite.PVSiteEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

public class InfluxValueProvider implements ControllerValueProvider {
    @Override
    public double getValue(String valueId, PVSiteEntity pvSiteEntity, ControllerDSL.ValueAdjustment valueAdjustment) {
        return queryInflux(pvSiteEntity, valueAdjustment, valueId);
    }

    private double queryInflux(PVSiteEntity pvSiteEntity, ControllerDSL.ValueAdjustment valueAdjustment, String field) {
        Instant start = Instant.now().minus(Duration.of(valueAdjustment.timeValue(), valueAdjustment.timeUnit().toChronoUnit()));
        Instant end = Instant.now();
        List<FluxRecord> any = SpringContextHelper.getBean(InfluxService.class).queryDataFromApi(pvSiteEntity, start, end, influxQueryBuilder -> {
            influxQueryBuilder
                    .setAggregation(valueAdjustment.valueFunction(), Duration.of(valueAdjustment.timeValue(), valueAdjustment.timeUnit().toChronoUnit()))
                    .addField(field);
        });
        if (any.isEmpty()) {
            throw new NoSuchElementException("No single data point could be queried from influx");
        }
        return ((Number) any.getFirst().getValue()).doubleValue();
    }
}
