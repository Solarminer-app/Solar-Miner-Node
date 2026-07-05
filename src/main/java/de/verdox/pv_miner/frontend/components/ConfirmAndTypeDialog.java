package de.verdox.pv_miner.frontend.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import lombok.Getter;

public class ConfirmAndTypeDialog extends Dialog {

    @Getter
    private final Button cancelButton = new Button("Cancel");
    @Getter
    private final Button confirm = new Button("Confirm");
    private final TextField confirmationTextField;
    private final Span text = new Span();

    public ConfirmAndTypeDialog(String textToConfirm) {
        getFooter().add(cancelButton);
        getFooter().add(confirm);
        confirmationTextField = new TextField("Type "+textToConfirm+" and confirm.");
        VerticalLayout verticalLayout = new VerticalLayout();
        verticalLayout.add(text);
        verticalLayout.add(confirmationTextField);
        this.confirm.setEnabled(false);
        confirmationTextField.setValueChangeMode(ValueChangeMode.EAGER);
        confirmationTextField.addValueChangeListener(event -> confirm.setEnabled(event.getValue().equals(textToConfirm)));
        add(verticalLayout);
        setCancellable(false);
        this.confirm.addClickListener(event -> close());
        this.cancelButton.addClickListener(event -> close());
    }

    public void setText(String text) {
        this.text.setText(text);
    }

    public void setCancellable(boolean cancellable) {
        this.cancelButton.setVisible(cancellable);
        this.cancelButton.setEnabled(cancellable);
    }
}
