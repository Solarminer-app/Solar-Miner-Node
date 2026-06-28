package de.verdox.pv_miner.frontend.pvsite.mining;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.setup.WizardSaveService;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableButton;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH2;
import de.verdox.pv_miner.frontend.AppMainLayout;
import de.verdox.pv_miner.frontend.setup.FinalizeSetupStep;
import de.verdox.pv_miner.frontend.setup.MinerSetupStep;
import de.verdox.pv_miner.frontend.setup.WizardStep;

import java.util.List;
import java.util.UUID;

@CssImport("./themes/solarminer/wizard.css")
@Route(value = "site/:siteId/add-miner", layout = AppMainLayout.class)
@PageTitle("Neue Miner verbinden | PV-Miner")
public class AddMinerStandaloneView extends VerticalLayout implements BeforeEnterObserver, LocaleChangeObserver {

    private final PVSiteRepository pvSiteRepository;

    private final TranslatableH2 stepTitle;
    private final Div contentArea;
    private final Button backButton;
    private final Button nextButton;

    private List<WizardStep> steps;
    private int currentStepIndex = 0;
    private PVSiteEntity pvSiteEntity;

    public AddMinerStandaloneView(PVSiteRepository pvSiteRepository) {
        this.pvSiteRepository = pvSiteRepository;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);

        stepTitle = new TranslatableH2("");

        String maxLayoutWidth = "1400px";

        contentArea = new Div();
        contentArea.setWidthFull();
        contentArea.setMaxWidth(maxLayoutWidth);
        contentArea.addClassName("wizard-content-area");

        contentArea.getStyle().set("flex-grow", "1");
        contentArea.getStyle().set("overflow-y", "auto");
        contentArea.getStyle().set("padding", "20px 0");
        contentArea.getStyle().set("display", "flex");
        contentArea.getStyle().set("flex-direction", "column");

        HorizontalLayout footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setMaxWidth(maxLayoutWidth);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        footer.getStyle().set("flex-shrink", "0");
        footer.getStyle().set("padding-top", "20px");
        footer.getStyle().set("border-top", "1px solid var(--lumo-contrast-10pct)");

        backButton = new TranslatableButton("btn.previous", e -> previousStep());
        nextButton = new TranslatableButton("btn.next", e -> nextStep());
        nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        footer.add(backButton, nextButton);
        add(stepTitle, contentArea, footer);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String parameter = event.getRouteParameters().get("siteId").orElseThrow();
        UUID siteUuid = UUID.fromString(parameter);
        this.pvSiteEntity = pvSiteRepository.findById(siteUuid).orElseThrow();

        initWizard();
    }

    private void initWizard() {
        var minerSetupStep = new MinerSetupStep(this::updateButtonStates);
        minerSetupStep.setExistingSite(pvSiteEntity);

        var finalizeStep = new FinalizeSetupStep(() -> {
            var selectedMiners = minerSetupStep.getSelectedMiners();
            WizardSaveService saveService = SpringContextHelper.getBean(WizardSaveService.class);
            saveService.addMinersToExistingSite(pvSiteEntity, selectedMiners);
        }, () -> {
            UI.getCurrent().navigate("site/" + pvSiteEntity.getId() + "/clusters");
        });

        this.steps = List.of(minerSetupStep, finalizeStep);
        loadStep(0);
    }

    private void updateButtonStates() {
        if (steps == null || currentStepIndex < 0 || currentStepIndex >= steps.size()) return;

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
        nextButton.setText(index == steps.size() - 1 ? getTranslation("btn.done") : getTranslation("btn.next"));

        updateButtonStates();

        step.onEnter();
    }

    private void nextStep() {
        if (steps.get(currentStepIndex).isValid()) {
            if (currentStepIndex < steps.size() - 1) {
                loadStep(currentStepIndex + 1);
            }
        }
    }

    private void previousStep() {
        loadStep(currentStepIndex - 1);
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        nextButton.setText(currentStepIndex == steps.size() - 1 ? getTranslation("btn.done") : getTranslation("btn.next"));
    }
}