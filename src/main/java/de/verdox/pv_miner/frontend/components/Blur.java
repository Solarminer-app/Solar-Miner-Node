package de.verdox.pv_miner.frontend.components;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;

@CssImport("./themes/solarminer/blur.css")
public class Blur extends Div {

    private final Div contentWrapper = new Div();
    private final Div overlay = new Div();
    private final Button unlockButton;
    private Component currentContent;

    public Blur() {
        addClassName("blur-wrapper");

        contentWrapper.addClassName("blurred-content");
        overlay.addClassName("blur-overlay");

        unlockButton = new Button("", event -> {applyBlur();});
        unlockButton.addClassName("unlock-button");
        overlay.add(unlockButton);

        add(contentWrapper, overlay);
    }

    public Blur(Component content) {
        this();
        setContent(content);
    }

    public Blur(Component content, String text) {
        this(content);
        setButtonText(text);
    }

    public Blur(Component content, String text, ComponentEventListener<ClickEvent<Button>> clickListener) {
        this();
        setContent(content);
        setButtonText(text);

        unlockButton.addClickListener(clickListener::onComponentEvent);
    }

    public void setContent(Component content) {
        if (currentContent != null) contentWrapper.remove(currentContent);
        if (content != null) {
            contentWrapper.add(content);
            currentContent = content;
            setWidth(content.getElement().getStyle().get("width"));
            setHeight(content.getElement().getStyle().get("height"));
            applyBlur();
        } else {
            unlockContent();
        }
    }

    public void unlockContent() {
        contentWrapper.removeClassName("blurred-content");
        overlay.setVisible(false);
    }

    public void applyBlur() {
        contentWrapper.addClassName("blurred-content");
        overlay.setVisible(true);
    }

    public void setButtonText(String text) {
        unlockButton.setText(text);
    }
}