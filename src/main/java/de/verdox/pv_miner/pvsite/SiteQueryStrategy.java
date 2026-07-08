package de.verdox.pv_miner.pvsite;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterDataDTO;

import java.util.List;

public class SiteQueryStrategy implements EntityQueryService.Strategy<PVSiteEntity, PVSiteDataDTO> {
    @Override
    public PVSiteDataDTO query(EntityQueryService entityQueryService, PVSiteEntity entity) throws Throwable {

        var batteries = entity.getBatteries();
        var inverters = entity.getInverters();
        var smartMeters = entity.getSmartMeters();

        var batteryData = batteries.stream().map(batteryEntity -> entityQueryService.getLastResult(batteryEntity, BatteryDataDTO.DEFAULT)).toList();
        var inverterData = inverters.stream().map(inverterEntity -> entityQueryService.getLastResult(inverterEntity, InverterDataDTO.DEFAULT)).toList();
        var smartMeterData = smartMeters.stream().map(smartMeterEntity -> entityQueryService.getLastResult(smartMeterEntity, SmartMeterDataDTO.DEFAULT)).toList();
        double miningCumulated = approximateMiningPowerDrawKw(entityQueryService, entity);

        return aggregateSiteData(batteryData, inverterData, smartMeterData, miningCumulated);
    }

    @Override
    public void ping(PVSiteEntity entity) throws Throwable {

    }

    private double approximateMiningPowerDrawKw(EntityQueryService entityQueryService, PVSiteEntity pvSiteType) {
        double cumulated = 0;
        for (MinerEntity<?> miner : pvSiteType.getMiners()) {
            try {
                var stats = entityQueryService.getLastResult(miner, MinerStats.DEFAULT);
                cumulated += stats.approximatedPowerUsageWatts();
            } catch (Throwable e) {}
        }
        return cumulated / 1000d;
    }

    private PVSiteDataDTO aggregateSiteData(
            List<BatteryDataDTO> batteries,
            List<InverterDataDTO> inverters,
            List<SmartMeterDataDTO> smartMeters,
            double currentMinerPowerKw
    ) {
        double totalPvPowerW = inverters.stream()
                .mapToDouble(InverterDataDTO::currentDcPowerW)
                .sum();

        double totalGridPowerW = smartMeters.stream()
                .mapToDouble(SmartMeterDataDTO::totalActivePowerW)
                .sum();

        double totalBatteryPowerW = batteries.stream()
                .mapToDouble(BatteryDataDTO::currentPowerW)
                .sum();

        double averageSoC = batteries.isEmpty() ? 0.0 : batteries.stream()
                .mapToDouble(BatteryDataDTO::stateOfChargePct)
                .average()
                .orElse(0.0);

        return PVSiteDataDTO.builder()
                .pvPower(totalPvPowerW / 1000.0)
                .gridPower(totalGridPowerW / 1000.0)
                .batteryPower(totalBatteryPowerW / 1000.0)
                .batterySoC((float) averageSoC)
                .totalMinerPowerKw(currentMinerPowerKw)
                .build();
    }
}
