package de.verdox.pv_miner.frontend.setup;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH3;
import de.verdox.pv_miner.frontend.setup.pool.MiningPoolStep;

public class SummaryStep extends VerticalLayout implements WizardStep {

    private final EconomicsStep economicsStep;
    private final PVSetupStep pvSetupStep;
    private final MinerSetupStep minerSetupStep;
    private final MiningPoolStep miningPoolStep;

    private final VerticalLayout summaryContainer = new VerticalLayout();

    public SummaryStep(EconomicsStep eco, PVSetupStep pv, MinerSetupStep miner, MiningPoolStep pool) {
        this.economicsStep = eco;
        this.pvSetupStep = pv;
        this.minerSetupStep = miner;
        this.miningPoolStep = pool;

        setAlignItems(Alignment.CENTER);
        summaryContainer.setMaxWidth("600px");
        summaryContainer.getStyle().set("background-color", "var(--lumo-contrast-5pct)")
                .set("padding", "var(--lumo-space-l)")
                .set("border-radius", "8px")
                .set("width", "100%");

        add(new TranslatableH3("setup.summary.header"), summaryContainer);
    }

    @Override
    public void onEnter() {
        summaryContainer.removeAll();

        int pvCount = pvSetupStep.getSelectedValidPVDevices().size();
        String pvStatus = pvCount > 0 ? getTranslation("setup.summary.pv_configured", pvCount) : getTranslation("setup.summary.pv_none");
        summaryContainer.add(createRow(getTranslation("setup.summary.pv"), pvStatus));

        int minerCount = minerSetupStep.getSelectedMiners().size();
        String minerStatus = minerCount > 0 ? getTranslation("setup.summary.miner_found", minerCount) : getTranslation("setup.summary.miner_skipped");
        summaryContainer.add(createRow(getTranslation("setup.summary.miner"), minerStatus));

        var pool = miningPoolStep.getSelectedPoolEntity();
        String poolStatus = pool != null ? getTranslation("setup.summary.pool_configured") : getTranslation("setup.summary.pool_skipped");
        summaryContainer.add(createRow(getTranslation("setup.summary.pool"), poolStatus));

        var eco = economicsStep.getEconomicsData();
        if(eco != null) {
            summaryContainer.add(createRow(getTranslation("setup.summary.price"), eco.electricityPrice() + " " + eco.currency().getCurrencyCode()));
        }
    }

    private Span createRow(String label, String value) {
        Span s = new Span(label + " " + value);
        s.getStyle().set("font-size", "1.1em");
        return s;
    }

    @Override
    public String getTitleTranslationKey() { return "setup.summary.title"; }

    @Override
    public boolean isValid() { return true; }
}