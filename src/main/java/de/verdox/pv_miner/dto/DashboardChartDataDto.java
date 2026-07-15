package de.verdox.pv_miner.dto;

import java.util.List;

import static de.verdox.pv_miner.dto.DashboardPageDto.LivePowerSeriesDto;
import static de.verdox.pv_miner.dto.DashboardPageDto.SeriesPointDto;

/**
 * Influx-backed chart data after records from the batched queries have been split into UI series.
 */
public record DashboardChartDataDto(
        LivePowerSeriesDto live,
        List<SeriesPointDto> pvHistory
) {
}
