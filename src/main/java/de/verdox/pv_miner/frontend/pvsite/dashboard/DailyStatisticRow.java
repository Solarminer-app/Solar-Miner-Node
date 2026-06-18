package de.verdox.pv_miner.frontend.pvsite.dashboard;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;

@CssImport("./themes/solarminer/dashboard.css")
public class DailyStatisticRow extends HorizontalLayout {
    private final TranslatableSpan name = new TranslatableSpan("");
    private final Span valueSpan = new Span("");

    public DailyStatisticRow(String translationTitle, String color) {
        this.name.setText(translationTitle);
        setWidthFull();
        setJustifyContentMode(JustifyContentMode.BETWEEN);
        name.getStyle().set("color", color).set("font-size", "13px");

        valueSpan.getStyle().set("font-size", "13px");
        add(name, valueSpan);
    }

    public void setValue(String value) {
        this.valueSpan.setText(value);
    }
}
