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

    private List<WizardStep> steps = new ArrayList<>();
    private int currentStepIndex = 0;

    public SetupWizard(@Autowired UserSessionContext sessionContext) {
        getElement().getThemeList().add(Lumo.DARK);

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);

        var repoSyncStep = new RepoSyncStep(this::nextStep);
        var pvSetupStep = new PVSetupStep(this::updateButtonStates, selectedType -> {
            if (selectedType.contains("Modbus")) {
                UI.getCurrent().navigate(ModbusConfigView.class);
            } else if (selectedType.contains("Rest")) {
                UI.getCurrent().navigate(RestPVConfigView.class);
            }
        });
        var pvSettings = new PVSettingsStep(pvSetupStep, this::updateButtonStates);
        var minerSetupStep = new MinerSetupStep(this::updateButtonStates);
        var miningPoolSetupStep = new MiningPoolStep(this::updateButtonStates);

        var variablesStep = new PVSiteVariablesStep(sessionContext, this::updateButtonStates);

        var finalizeStep = new FinalizeSetupStep(() -> {
            var selectedPVDevices = pvSetupStep.getSelectedValidPVDevices();
            var selectedMiners = minerSetupStep.getSelectedMiners();
            var selectedPool = miningPoolSetupStep.getSelectedPoolEntity();

            var variablesData = variablesStep.getVariablesData();

            WizardSaveService saveService = SpringContextHelper.getBean(WizardSaveService.class);
            saveService.saveSetupData(selectedPVDevices, selectedMiners, selectedPool, variablesData);
        }, () -> {
            UI.getCurrent().navigate("");
        });

        this.steps = List.of(
                repoSyncStep,
                pvSetupStep,
                pvSettings,
                minerSetupStep,
                miningPoolSetupStep,
                variablesStep,
                finalizeStep
        );

        stepTitle = new TranslatableH2("");

        contentArea = new Div();
        contentArea.setWidthFull();
        contentArea.setMaxWidth("1000px");
        contentArea.addClassName("wizard-content-area");

        footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setMaxWidth("1000px");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        backButton = new TranslatableButton("btn.previous", e -> previousStep());
        nextButton = new TranslatableButton("btn.next", e -> nextStep());
        nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        footer.add(backButton, nextButton);
        add(stepTitle, contentArea, footer);

        loadStep(0);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        PVSiteRepository pvSiteRepository = SpringContextHelper.getBean(PVSiteRepository.class);
        long currentSitesCount = pvSiteRepository.count();
        int maxAllowedSites = 1;

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
            if (currentStepIndex == steps.size() - 1) {
                //TODO: Finalize handle in lambda?
            } else {
                loadStep(currentStepIndex + 1);
            }
        }
    }

    private void previousStep() {
        loadStep(currentStepIndex - 1);
    }
}