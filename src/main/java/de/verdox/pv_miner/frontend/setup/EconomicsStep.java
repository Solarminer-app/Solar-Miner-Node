package de.verdox.pv_miner.frontend.setup;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH3;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.user.UserSessionContext;
import de.verdox.pv_miner.util.currency.CustomCurrency;

import java.time.LocalDate;

public class EconomicsStep extends VerticalLayout implements WizardStep {

    private final UserSessionContext sessionContext;
    private final DatePicker setupDatePicker = new DatePicker();
    private final ComboBox<CustomCurrency> currencyComboBox = new ComboBox<>();
    private final NumberField pvCostField = new NumberField();
    private final NumberField electricityPriceField = new NumberField();
    private final NumberField feedInTariffField = new NumberField();

    public EconomicsStep(UserSessionContext sessionContext, Runnable onValidationChange) {
        this.sessionContext = sessionContext;
        setAlignItems(Alignment.CENTER);

        VerticalLayout container = new VerticalLayout();
        container.setMaxWidth("700px");

        container.add(new TranslatableH3("setup.economics.title"));
        TranslatableSpan subtitle = new TranslatableSpan("setup.economics.subtitle");
        subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)");
        container.add(subtitle);

        setupDatePicker.setLabel(getTranslation("setup.economics.date"));
        currencyComboBox.setLabel(getTranslation("setup.economics.currency"));
        pvCostField.setLabel(getTranslation("setup.economics.cost_pv"));
        electricityPriceField.setLabel(getTranslation("setup.economics.price_electricity"));
        feedInTariffField.setLabel(getTranslation("setup.economics.feed_in_tariff"));

        FormLayout formLayout = new FormLayout();
        formLayout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("500px", 2));

        currencyComboBox.setItems(CustomCurrency.getAvailableCurrencies().stream()
                .filter(c -> c.getCurrencyCode().equals("EUR") || c.getCurrencyCode().equals("USD") || c.getCurrencyCode().equals("CHF"))
                .toList());
        currencyComboBox.setItemLabelGenerator(CustomCurrency::getCurrencyCode);

        CustomCurrency sessionCurr = sessionContext.getCurrency() != null ? sessionContext.getCurrency() : CustomCurrency.getInstance("EUR");
        currencyComboBox.setValue(sessionCurr);
        currencyComboBox.addValueChangeListener(e -> {
            updateCurrencyLabels(e.getValue());
            onValidationChange.run();
        });

        setupDatePicker.setValue(LocalDate.now());
        setupDatePicker.addValueChangeListener(e -> onValidationChange.run());
        electricityPriceField.setRequiredIndicatorVisible(true);
        electricityPriceField.addValueChangeListener(e -> onValidationChange.run());

        formLayout.add(setupDatePicker, currencyComboBox, pvCostField, electricityPriceField, feedInTariffField);
        container.add(formLayout);
        add(container);

        updateCurrencyLabels(currencyComboBox.getValue());
    }

    private void updateCurrencyLabels(CustomCurrency currency) {
        String sym = currency != null ? currency.getSymbol(sessionContext.getLocale()) : "EUR";
        pvCostField.setLabel(getTranslation("setup.economics.dynamic_cost", sym));
        electricityPriceField.setLabel(getTranslation("setup.economics.dynamic_price", sym));
        feedInTariffField.setLabel(getTranslation("setup.economics.dynamic_feed_in", sym));
    }

    @Override
    public String getTitleTranslationKey() { return "setup.step.economics.title"; }

    @Override
    public boolean isValid() {
        return electricityPriceField.getValue() != null && setupDatePicker.getValue() != null && currencyComboBox.getValue() != null;
    }

    public EconomicsData getEconomicsData() {
        return new EconomicsData(setupDatePicker.getValue(), currencyComboBox.getValue(),
                pvCostField.getValue() != null ? pvCostField.getValue() : 0.0,
                electricityPriceField.getValue(),
                feedInTariffField.getValue() != null ? feedInTariffField.getValue() : 0.0);
    }

    public record EconomicsData(LocalDate setupDate, CustomCurrency currency, Double pvCost, Double electricityPrice, Double feedInTariff) {}
}