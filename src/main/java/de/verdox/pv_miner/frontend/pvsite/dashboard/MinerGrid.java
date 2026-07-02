package de.verdox.pv_miner.frontend.pvsite.dashboard;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;

public class MinerGrid extends Grid<MinerGrid.MinerItem> {

    public MinerGrid() {
        addColumn(MinerGrid.MinerItem::name).setHeader(new TranslatableSpan("dashboard.grid.miner.name")).setAutoWidth(true);
        addColumn(MinerGrid.MinerItem::ip).setHeader(new TranslatableSpan("dashboard.grid.miner.ip")).setAutoWidth(true);
        addColumn(new ComponentRenderer<>(miner -> new StatusBadge(miner.status()))).setHeader(new TranslatableSpan("dashboard.grid.miner.status")).setAutoWidth(true);
        addColumn(MinerGrid.MinerItem::hashrate).setHeader(new TranslatableSpan("dashboard.grid.miner.hashrate"));
        addColumn(MinerGrid.MinerItem::power).setHeader(new TranslatableSpan("dashboard.grid.miner.power"));
        addColumn(MinerGrid.MinerItem::temp).setHeader(new TranslatableSpan("dashboard.grid.miner.temp"));
        addColumn(MinerGrid.MinerItem::pool).setHeader(new TranslatableSpan("dashboard.grid.miner.pool"));

        addThemeVariants(GridVariant.LUMO_NO_BORDER, GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        getStyle().set("background-color", "transparent").set("border", "none");

        setAllRowsVisible(true);
    }

    public record MinerItem(String name, String ip, String status, String hashrate, String power, String temp, String pool) { }
}
