package de.verdox.pv_miner.frontend.pvsite.dashboard;

import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH2;

public class DashboardHeader extends HorizontalLayout {
    public DashboardHeader() {
        setWidthFull();
        setJustifyContentMode(JustifyContentMode.BETWEEN);
        setAlignItems(Alignment.CENTER);

        TranslatableH2 title = new TranslatableH2("dashboard.header.title");
        title.getStyle().set("margin", "0");

        HorizontalLayout titleCombo = new HorizontalLayout(title);
        titleCombo.setAlignItems(Alignment.BASELINE);
        add(titleCombo);
    }
}
