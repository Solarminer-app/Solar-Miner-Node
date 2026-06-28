package de.verdox.pv_miner.frontend.setup;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.Lumo;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.setup.WizardSaveService;
import de.verdox.pv_miner.frontend.user.UserSessionContext;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableButton;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH2;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;
import de.verdox.pv_miner.frontend.PVSiteSelectionView;
import de.verdox.pv_miner.frontend.config.ModbusConfigView;
import de.verdox.pv_miner.frontend.config.RestPVConfigView;
import de.verdox.pv_miner.frontend.setup.pool.MiningPoolStep;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@CssImport("./themes/solarminer/wizard.css")
@Route(value = "wizard")
public class SetupWizard extends VerticalLayout implements BeforeEnterObserver {

    private final TranslatableH2 stepTitle;
    private final Div contentArea;
    private final Button backButton;
    private final Button nextButton;
    private final HorizontalLayout footer;
    private final HorizontalLayout stepperLayout;

    private List<WizardStep> steps = new ArrayList<>();
    private int currentStepIndex = 0;

    public SetupWizard(@Autowired UserSessionContext sessionContext) {
        getElement().getThemeList().add(Lumo.DARK);

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);

        var repoSyncStep = new RepoSyncStep(this::nextStep);
        var economicsStep = new EconomicsStep(sessionContext, this::updateButtonStates);

        var pvSetupStep = new PVSetupStep(this::updateButtonStates, selectedType -> {
            if (selectedType.contains("Modbus")) UI.getCurrent().navigate(ModbusConfigView.class);
            else if (selectedType.contains("Rest")) UI.getCurrent().navigate(RestPVConfigView.class);
        });

        var pvSettings = new PVSettingsStep(pvSetupStep, this::updateButtonStates);
        var pvPanelsStep = new PVPanelsStep(this::updateButtonStates);
        var minerSetupStep = new MinerSetupStep(this::updateButtonStates);
        var miningPoolSetupStep = new MiningPoolStep(this::updateButtonStates);

        var summaryStep = new SummaryStep(economicsStep, pvSetupStep, minerSetupStep, miningPoolSetupStep);

        var finalizeStep = new FinalizeSetupStep(() -> {
            WizardSaveService saveService = SpringContextHelper.getBean(WizardSaveService.class);
            saveService.saveSetupData(
                    pvSetupStep.getSelectedValidPVDevices(),
                    minerSetupStep.getSelectedMiners(),
                    miningPoolSetupStep.getSelectedPoolEntity(),
                    economicsStep.getEconomicsData(),
                    pvPanelsStep.getPanelsList()
            );
        }, () -> UI.getCurrent().navigate(""));

        this.steps = List.of(
                repoSyncStep,
                economicsStep,
                pvSetupStep,
                pvSettings,
                pvPanelsStep,
                minerSetupStep,
                miningPoolSetupStep,
                summaryStep,
                finalizeStep
        );

        stepTitle = new TranslatableH2("");

        stepperLayout = new HorizontalLayout();
        stepperLayout.setWidthFull();
        stepperLayout.setWidthFull();
        stepperLayout.setJustifyContentMode(JustifyContentMode.CENTER);
        stepperLayout.setPadding(false);

        contentArea = new Div();
        contentArea.setWidthFull();
        contentArea.setWidthFull();
        contentArea.addClassName("wizard-content-area");

        contentArea.getStyle().set("flex-grow", "1");
        contentArea.getStyle().set("overflow-y", "auto");
        contentArea.getStyle().set("padding", "20px 0");
        contentArea.getStyle().set("display", "flex");
        contentArea.getStyle().set("flex-direction", "column");

        footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        footer.getStyle().set("flex-shrink", "0");
        footer.getStyle().set("padding-top", "20px");
        footer.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)");

        backButton = new TranslatableButton("btn.previous", e -> previousStep());
        nextButton = new TranslatableButton("btn.next", e -> nextStep());
        nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        footer.add(backButton, nextButton);
        add(stepperLayout, stepTitle, contentArea, footer);

        loadStep(0);
    }

    private void updateStepper(int activeIndex) {
        stepperLayout.removeAll();
        for (int i = 0; i < steps.size(); i++) {
            Div dot = new Div();
            dot.setWidth("12px");
            dot.setHeight("12px");
            dot.getStyle().set("border-radius", "50%");
            dot.getStyle().set("margin", "0 4px");

            if (i < activeIndex) {
                dot.getStyle().set("background-color", "var(--lumo-primary-color)"); // Abgeschlossen
            } else if (i == activeIndex) {
                dot.getStyle().set("background-color", "var(--lumo-primary-color)"); // Aktiv
                dot.getStyle().set("box-shadow", "0 0 0 4px var(--lumo-primary-color-50pct)");
            } else {
                dot.getStyle().set("background-color", "var(--lumo-contrast-20pct)"); // Ausstehend
            }
            stepperLayout.add(dot);
        }
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PVSiteRepository pvSiteRepository = SpringContextHelper.getBean(PVSiteRepository.class);
        long currentSitesCount = pvSiteRepository.count();
        int maxAllowedSites = EntityService.PV_SITE_LIMIT;

        if (currentSitesCount >= maxAllowedSites) {
            showLimitReachedError();
        }
    }

    private void showLimitReachedError() {
        removeAll();

        VerticalLayout errorLayout = new VerticalLayout();
        errorLayout.setSizeFull();
        errorLayout.setAlignItems(Alignment.CENTER);
        errorLayout.setJustifyContentMode(JustifyContentMode.CENTER);

        Icon warningIcon = VaadinIcon.WARNING.create();
        warningIcon.setSize("64px");
        warningIcon.setColor("var(--lumo-warning-color)");

        H2 errorTitle = new TranslatableH2("setup.limit_reached.title");
        Span errorText = new TranslatableSpan("setup.limit_reached.description");
        errorText.getStyle().set("color", "var(--lumo-secondary-text-color)");
        errorText.getStyle().set("text-align", "center");

        Button homeBtn = new TranslatableButton("setup.limit_reached.back", e -> UI.getCurrent().navigate(PVSiteSelectionView.class));
        homeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        homeBtn.getStyle().set("margin-top", "20px");

        errorLayout.add(warningIcon, errorTitle, errorText, homeBtn);
        add(errorLayout);
    }

    private void updateButtonStates() {
        if (currentStepIndex < 0 || currentStepIndex >= steps.size()) return;

        WizardStep currentStep = steps.get(currentStepIndex);
        getUI().ifPresent(ui -> ui.access(() -> {
            nextButton.setEnabled(currentStep.isValid());
        }));
    }

    private void loadStep(int index) {
        if (index < 0 || index >= steps.size()) return;
        currentStepIndex = index;
        WizardStep step = steps.get(index);

        updateStepper(index);

        stepTitle.setText(step.getTitleTranslationKey());
        stepTitle.setTranslationParameters((index + 1), steps.size());

        contentArea.removeAll();
        contentArea.add(step.getComponent());
        contentArea.removeClassName("fade-in");
        contentArea.getElement().executeJs("setTimeout(() => { this.classList.add('fade-in'); }, 10);");

        backButton.setVisible(index > 0);
        nextButton.setText(index == steps.size() - 1 ? "btn.done" : "btn.next");

        updateButtonStates();
        step.onEnter();
    }

    private void nextStep() {
        if (steps.get(currentStepIndex).isValid()) {
            if (currentStepIndex < steps.size() - 1) loadStep(currentStepIndex + 1);
        }
    }

    private void previousStep() { loadStep(currentStepIndex - 1); }
}