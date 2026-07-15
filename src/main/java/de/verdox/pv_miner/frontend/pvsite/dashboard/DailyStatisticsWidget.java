package de.verdox.pv_miner.frontend.pvsite.dashboard;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import de.verdox.pv_miner.frontend.FrontendColor;
import de.verdox.pv_miner.dto.DailyFinancialStatsDto;

public class DailyStatisticsWidget extends VerticalLayout {

    private final DailyStatisticRow exportedToday = new DailyStatisticRow("pv_site.card_data.grid.exported", FrontendColor.TEXT_VALUE_GRAY);
    private final DailyStatisticRow revenueExportToday = new DailyStatisticRow("pv_site.card_data.grid.revenue_export", FrontendColor.TEXT_VALUE_GRAY);
    private final DailyStatisticRow importToday = new DailyStatisticRow("pv_site.card_data.grid.imported", FrontendColor.TEXT_VALUE_GRAY);
    private final DailyStatisticRow costImportToday = new DailyStatisticRow("pv_site.card_data.grid.cost_import", FrontendColor.TEXT_VALUE_GRAY);
    private final DailyStatisticRow loadHomeTotalToday = new DailyStatisticRow("pv_site.card_data.home.used", FrontendColor.TEXT_VALUE_GRAY);
    private final DailyStatisticRow avoidedEnergyCost = new DailyStatisticRow("pv_site.card_data.home.avoided_import_cost", FrontendColor.TEXT_VALUE_GRAY);
    private final DailyStatisticRow loadMinerTotalToday = new DailyStatisticRow("pv_site.card_data.mining.consumption", FrontendColor.TEXT_VALUE_GRAY);
    private final DailyStatisticRow minerNotExported = new DailyStatisticRow("pv_site.card_data.mining.lost_export_revenue", FrontendColor.TEXT_VALUE_GRAY);

    public DailyStatisticsWidget() {
        getStyle().set("background-color", FrontendColor.CARD_BACKGROUND_COLOR)
                .set("border-radius", "4px")
                .set("padding", "15px");

        add(exportedToday, revenueExportToday, importToday, costImportToday,
                loadHomeTotalToday, avoidedEnergyCost, loadMinerTotalToday, minerNotExported);
    }

    public void update(UI ui, DailyFinancialStatsDto dto) {
        ui.access(() -> {
            exportedToday.setValue(dto.exportedToday());
            revenueExportToday.setValue(dto.revenueExportToday());
            importToday.setValue(dto.importToday());
            costImportToday.setValue(dto.costImportToday());
            loadHomeTotalToday.setValue(dto.loadHomeTotalToday());
            avoidedEnergyCost.setValue(dto.avoidedEnergyCost());
            loadMinerTotalToday.setValue(dto.loadMinerTotalToday());
            minerNotExported.setValue(dto.minerNotExported());
        });
    }
}
