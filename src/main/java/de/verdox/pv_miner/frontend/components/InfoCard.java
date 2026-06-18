package de.verdox.pv_miner.frontend.components;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH3;

@CssImport("./styles/pv_cards.css")
public class InfoCard extends VerticalLayout {
    private final TranslatableH3 title;
    private final Icon icon;

    public InfoCard(String translationKey, Icon icon, String cssCardClass) {
        this.icon = icon;

        setHeight("200px");
        setMaxHeight("250px");
        setMaxWidth("250px");
        setPadding(true);
        setSpacing(false);
        setAlignItems(Alignment.CENTER);
        addClassName("card");
        if(!cssCardClass.isBlank()) addClassName(cssCardClass);

        this.title = new TranslatableH3(translationKey);

        icon.setSize("40px");
        add(icon, title);
    }

    public H3 getTitle() {
        return title;
    }

    public Icon getIcon() {
        return icon;
    }
}
