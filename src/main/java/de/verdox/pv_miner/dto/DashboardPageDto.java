package de.verdox.pv_miner.dto;

import java.util.List;

/**
 * Shared DTOs for the dashboard initialization and chart endpoints.
 */
public final class DashboardPageDto {
    private DashboardPageDto() {
    }

    public record DashboardInitDto(
            String siteName,
            List<MinerDashboardItemDTO> miners,
            List<PoolDto> pools,
            List<ChartDatapointDto> chartData
    ) {
    }

    public record PoolDto(String id, String url, String worker, String status) {
    }

    public record ChartDatapointDto(long timestamp, double powerKw, double allocatedKw) {
    }

    public record SeriesPointDto(long timestamp, double value) {
    }

    public record LivePowerSeriesDto(
            List<SeriesPointDto> pvPower,
            List<SeriesPointDto> gridImport,
            List<SeriesPointDto> gridExport,
            List<SeriesPointDto> consumption,
            List<SeriesPointDto> minerConsumption
    ) {
    }

    public record ControllerChartPointDto(
            long timestamp,
            double targetPowerWatts,
            double allocatedPowerWatts,
            String activeMode,
            String event
    ) {
    }

    public record ControllerChartDto(
            String clusterName,
            boolean running,
            List<ControllerChartPointDto> points
    ) {
    }

    public record DashboardChartsDto(
            LivePowerSeriesDto live,
            List<SeriesPointDto> pvHistory,
            ControllerChartDto controller,
            List<String> clusterNames
    ) {
    }
}
