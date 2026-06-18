package de.verdox.pv_miner.frontend.pvsite.dashboard;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.FrontendColor;

public class KpiCard extends HorizontalLayout {
    private final TranslatableSpan title = new TranslatableSpan("");
    private final Span value = new Span();

    public KpiCard(String translationTitle, String valueColor, VaadinIcon cardIcon) {
        title.setText(translationTitle);
        setPadding(true);
        setSpacing(false);
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.BETWEEN);
        getStyle()
                .set("background-color", FrontendColor.CARD_BACKGROUND_COLOR)
                .set("border-radius", "4px")
                .set("border", "1px solid #222226")
                .setMinWidth("220px")
                .set("flex", "1");

        title.getStyle().set("color", FrontendColor.TEXT_VALUE_GRAY).set("font-size", "12px");
        value.getStyle().set("color", valueColor).set("font-size", "20px").set("font-weight", "bold");

        VerticalLayout textLayout = new VerticalLayout(title, value);
        textLayout.setPadding(false);
        textLayout.setSpacing(false);

        Icon icon = cardIcon.create();
        icon.getStyle().set("color", valueColor);
        icon.setSize("35px");
        add(textLayout, icon);
    }

    public void setValue(String value) {
        this.value.setText(value);
    }
}
