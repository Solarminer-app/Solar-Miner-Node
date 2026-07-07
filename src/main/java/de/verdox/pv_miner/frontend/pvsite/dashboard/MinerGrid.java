package de.verdox.pv_miner.frontend.pvsite.dashboard;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.pvsite.dashboard.dto.MinerDashboardItemDTO;

public class MinerGrid extends Grid<MinerDashboardItemDTO> {

    public MinerGrid() {
        addColumn(MinerDashboardItemDTO::name).setHeader(new TranslatableSpan("dashboard.grid.miner.name")).setAutoWidth(true);
        addColumn(MinerDashboardItemDTO::ip).setHeader(new TranslatableSpan("dashboard.grid.miner.ip")).setAutoWidth(true);
        addColumn(new ComponentRenderer<>(miner -> new StatusBadge(miner.status()))).setHeader(new TranslatableSpan("dashboard.grid.miner.status")).setAutoWidth(true);
        addColumn(MinerDashboardItemDTO::hashrate).setHeader(new TranslatableSpan("dashboard.grid.miner.hashrate"));
        addColumn(MinerDashboardItemDTO::power).setHeader(new TranslatableSpan("dashboard.grid.miner.power"));

        addColumn(new ComponentRenderer<>(miner -> createLockBadge(miner.stateLockRemainingSeconds()))).setHeader(new TranslatableSpan("dashboard.grid.miner.lock.state")).setAutoWidth(true);
        addColumn(new ComponentRenderer<>(miner -> createLockBadge(miner.powerLockRemainingSeconds()))).setHeader(new TranslatableSpan("dashboard.grid.miner.lock.power")).setAutoWidth(true);

        addColumn(MinerDashboardItemDTO::temp).setHeader(new TranslatableSpan("dashboard.grid.miner.temp"));
        addColumn(MinerDashboardItemDTO::pool).setHeader(new TranslatableSpan("dashboard.grid.miner.pool"));

        addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        getStyle().set("background-color", "transparent").set("border", "none");

        setAllRowsVisible(true);
    }

    private HorizontalLayout createLockBadge(long remainingSeconds) {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setSpacing(false);
        layout.getStyle().set("gap", "6px");

        if (remainingSeconds <= 0) {
            Icon icon = VaadinIcon.CHECK_CIRCLE.create();
            icon.setSize("14px");
            icon.setColor("#2ecc71");

            Span text = new TranslatableSpan("dashboard.grid.miner.lock.ready");
            text.getStyle().set("color", "#2ecc71").set("font-size", "13px");

            layout.add(icon, text);
        } else {
            Icon icon = VaadinIcon.CLOCK.create();
            icon.setSize("14px");
            icon.setColor("#e74c3c");

            String timeStr = remainingSeconds > 60 ? (remainingSeconds / 60) + "m " + (remainingSeconds % 60) + "s" : remainingSeconds + "s";

            Span text = new Span(timeStr);
            text.getStyle().set("color", "#e74c3c").set("font-size", "13px").set("font-weight", "bold");

            layout.add(icon, text);
        }
        return layout;
    }
}
