package de.verdox.pv_miner.frontend.pvsite.dashboard.dto;

import de.verdox.pv_miner.pvsite.PVSiteDataDTO;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.statistic.live.EntityStatistic;

public record DashboardChartDataDto(
        EntityStatistic<PVSiteEntity, PVSiteDataDTO, Double> pvPower,
        EntityStatistic<PVSiteEntity, PVSiteDataDTO, Double> importData,
        EntityStatistic<PVSiteEntity, PVSiteDataDTO, Double> exportData,
        EntityStatistic<PVSiteEntity, PVSiteDataDTO, Double> consumption,
        EntityStatistic<PVSiteEntity, PVSiteDataDTO, Double> minerConsumption,
        EntityStatistic<PVSiteEntity, PVSiteDataDTO, Double> history
) {
}
