package de.verdox.pv_miner.pvsite;

import com.influxdb.query.FluxRecord;
import de.verdox.pv_miner.dailystatistic.DailyStatisticAccumulator;

public class PVStatisticsAccumulator implements DailyStatisticAccumulator<PVStatisticPerDay, PVSiteDataDTO> {

    @Override
    public PVStatisticPerDay createEmptyInstance() {
        return new PVStatisticPerDay();
    }

    @Override
    public void accumulate(PVStatisticPerDay currentCache, PVSiteDataDTO liveData, double timeWindowInHours) {
        double producedDeltaKwh = (liveData.getPVPowerInKw()) * timeWindowInHours;
        double exportDeltaKwh = (liveData.getExportInKw()) * timeWindowInHours;
        double importDeltaKwh = (liveData.getImportInKw()) * timeWindowInHours;
        double consumptionDeltaKwh = liveData.getLoadsPowerInKw() * timeWindowInHours;
        double consumptionMinerDeltaKwh = liveData.getTotalMinerPowerKw() * timeWindowInHours;

        if (consumptionDeltaKwh < 0) {
            consumptionDeltaKwh = 0;
        }

        currentCache.setProductionKwh(currentCache.getProductionKwh() + producedDeltaKwh);
        currentCache.setExportKwh(currentCache.getExportKwh() + exportDeltaKwh);
        currentCache.setImportKwh(currentCache.getImportKwh() + importDeltaKwh);
        currentCache.setConsumptionKwh(currentCache.getConsumptionKwh() + consumptionDeltaKwh);
        currentCache.setConsumptionKwhMining(currentCache.getConsumptionKwhMining() + consumptionMinerDeltaKwh);
    }

    @Override
    public void mapFromFluxRecord(PVStatisticPerDay statisticObject, FluxRecord record) {
        String fieldName = record.getField();
        Double value = (Double) record.getValue();
        if (value == null) return;

        double valueInKwh = value / 3600;


        switch (fieldName) {
            case PVSiteInfluxStrategy.PV_POWER_IN_KW:
                statisticObject.setProductionKwh(valueInKwh);
                break;
            case PVSiteInfluxStrategy.GRID_CONSUMPTION_POWER:
                statisticObject.setExportKwh(valueInKwh);
                break;
            case PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW:
                statisticObject.setImportKwh(valueInKwh);
                break;
            case PVSiteInfluxStrategy.LOADS_POWER_IN_KW:
                statisticObject.setConsumptionKwh(valueInKwh);
                break;
            case PVSiteInfluxStrategy.MINER_POWER_IN_KW:
                statisticObject.setConsumptionKwhMining(valueInKwh);
                break;
        }
    }

    @Override
    public boolean supportsDownsampling() {
        return true;
    }

    @Override
    public String getDownsampledMeasurementName() {
        return "pv_site_daily_summary";
    }

    @Override
    public String generateDownsamplingTaskQuery(String sourceBucket, String targetBucket, String measurement) {
        return String.format(
                "option task = {name: \"Downsample_PV_%s\", every: 1d, offset: 15m}\n\n" + // Offset hinzugefügt
                        "from(bucket: \"%s\")\n" +
                        "  |> range(start: -2d)\n" +
                        "  |> filter(fn: (r) => r[\"_measurement\"] == \"%s\")\n" +
                        "  |> filter(fn: (r) => r[\"_field\"] == \"" + PVSiteInfluxStrategy.PV_POWER_IN_KW + "\" or r[\"_field\"] == \"" + PVSiteInfluxStrategy.GRID_CONSUMPTION_POWER + "\" or r[\"_field\"] == \"" + PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW + "\" or r[\"_field\"] == \"" + PVSiteInfluxStrategy.LOADS_POWER_IN_KW + "\" or r[\"_field\"] == \"" + PVSiteInfluxStrategy.MINER_POWER_IN_KW + "\")\n" +
                        "  |> group(columns: [\"_measurement\", \"_field\", \"entity\"])\n" + // <-- FIX: Gruppierung wie im Backfill!
                        "  |> aggregateWindow(every: 1d, fn: (column, tables=<-) => tables |> integral(unit: 1h), timeSrc: \"_stop\", timeDst: \"_time\", createEmpty: false)\n" +
                        "  |> set(key: \"_measurement\", value: \"%s\")\n" +
                        "  |> to(bucket: \"%s\")",
                measurement, sourceBucket, measurement, getDownsampledMeasurementName(), targetBucket
        );
    }

    @Override
    public void mapFromDownsampledRecord(PVStatisticPerDay statisticObject, FluxRecord record) {

        Double pvPower = extractDoubleFromRecord(record, PVSiteInfluxStrategy.PV_POWER_IN_KW);
        if (pvPower != null) statisticObject.setProductionKwh(pvPower);

        Double gridConsumption = extractDoubleFromRecord(record, PVSiteInfluxStrategy.GRID_CONSUMPTION_POWER);
        if (gridConsumption != null) statisticObject.setExportKwh(gridConsumption);

        Double feedIn = extractDoubleFromRecord(record, PVSiteInfluxStrategy.FEED_IN_POWER_IN_KW);
        if (feedIn != null) statisticObject.setImportKwh(feedIn);

        Double loads = extractDoubleFromRecord(record, PVSiteInfluxStrategy.LOADS_POWER_IN_KW);
        if (loads != null) statisticObject.setConsumptionKwh(loads);

        Double minerPower = extractDoubleFromRecord(record, PVSiteInfluxStrategy.MINER_POWER_IN_KW);
        if (minerPower != null) statisticObject.setConsumptionKwhMining(minerPower);
    }

    private Double extractDoubleFromRecord(FluxRecord record, String key) {
        Object value = record.getValueByKey(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }
}