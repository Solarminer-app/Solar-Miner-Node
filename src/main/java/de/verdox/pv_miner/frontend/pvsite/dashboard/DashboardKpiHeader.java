package de.verdox.pv_miner.frontend.pvsite.dashboard;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import de.verdox.pv_miner.frontend.FrontendColor;
import de.verdox.pv_miner.frontend.pvsite.dashboard.dto.LiveKpiDto;

public class DashboardKpiHeader extends HorizontalLayout {

    private final KpiCard pvPower = new KpiCard("dashboard.kpi.pv_power", FrontendColor.TEXT_VALUE_YELLOW, VaadinIcon.SUN_O);
    private final KpiCard powerTotal = new KpiCard("dashboard.kpi.total_load", FrontendColor.TEXT_VALUE_WHITE, VaadinIcon.PLUG);
    private final KpiCard liveExportCard = new KpiCard("dashboard.kpi.live_export", FrontendColor.TEXT_VALUE_WHITE, VaadinIcon.EXTERNAL_LINK);
    private final KpiCard liveImportCard = new KpiCard("dashboard.kpi.live_import", FrontendColor.TEXT_VALUE_WHITE, VaadinIcon.INSERT);
    private final KpiCard activeMiners = new KpiCard("dashboard.kpi.active_miners", FrontendColor.TEXT_VALUE_WHITE, VaadinIcon.SERVER);
    private final KpiCard totalHashrate = new KpiCard("dashboard.kpi.total_hashrate", FrontendColor.TEXT_VALUE_WHITE, VaadinIcon.DASHBOARD);
    private final KpiCard minerPower = new KpiCard("dashboard.kpi.mining_load", FrontendColor.TEXT_VALUE_WHITE, VaadinIcon.PLUG);

    public DashboardKpiHeader() {
        addClassName("kpi-grid");
        getStyle().set("flex-wrap", "wrap");
        setWidthFull();
        setSpacing(true);

        add(pvPower, powerTotal, liveExportCard, liveImportCard, activeMiners, totalHashrate, minerPower);

        activeMiners.setValue("0 / 0");
        totalHashrate.setValue("0.0 TH/s");
        powerTotal.setValue("0.0 kW");
        pvPower.setValue("0.0 kW");
        minerPower.setValue("0.0 kW");
    }

    public void update(UI ui, LiveKpiDto dto) {
        ui.access(() -> {
            pvPower.setValue(dto.pvPower());
            powerTotal.setValue(dto.powerTotal());
            liveExportCard.setValue(dto.liveExport());
            liveImportCard.setValue(dto.liveImport());
            activeMiners.setValue(dto.activeMiners());
            totalHashrate.setValue(dto.totalHashrate());
            minerPower.setValue(dto.minerPower());
        });
    }
}
