package de.verdox.pv_miner.frontend.pvsite.mining;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import de.verdox.pv_miner.setup.WizardSaveService;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableH2;
import de.verdox.pv_miner.frontend.AppMainLayout;
import de.verdox.pv_miner.frontend.setup.FinalizeSetupStep;
import de.verdox.pv_miner.frontend.setup.WizardStep;
import de.verdox.pv_miner.frontend.setup.pool.MiningPoolStep;

import java.util.List;
import java.util.UUID;

@CssImport("./themes/solarminer/wizard.css")
@Route(value = "site/:siteId/add-pool", layout = AppMainLayout.class)
public class AddPoolStandaloneView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    private final PVSiteRepository pvSiteRepository;

    private final TranslatableH2 stepTitle;
    private final Div contentArea;
    private final Button backButton;
    private final Button nextButton;

    private List<WizardStep> steps;
    private int currentStepIndex = 0;
    private PVSiteEntity pvSiteEntity;

    public AddPoolStandaloneView(PVSiteRepository pvSiteRepository) {
        this.pvSiteRepository = pvSiteRepository;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(Alignment.CENTER);

        stepTitle = new TranslatableH2("");

        contentArea = new Div();
        contentArea.setWidthFull();
        contentArea.setMaxWidth("1000px");
        contentArea.addClassName("wizard-content-area");

        HorizontalLayout footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setMaxWidth("1000px");
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        backButton = new Button(getTranslation("btn.cancel"), e -> previousStep());
        nextButton = new Button("Weiter ->", e -> nextStep());
        nextButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        footer.add(backButton, nextButton);
        add(stepTitle, contentArea, footer);
    }

    @Override
    public String getPageTitle() {
        return getTranslation("cluster.page.add_pool.title");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String parameter = event.getRouteParameters().get("siteId").orElseThrow();
        UUID siteUuid = UUID.fromString(parameter);
        this.pvSiteEntity = pvSiteRepository.findById(siteUuid).orElseThrow();

        initWizard();
    }

    private void initWizard() {
        var poolSetupStep = new MiningPoolStep(this::updateButtonStates);
        poolSetupStep.setExistingSite(pvSiteEntity);

        var finalizeStep = new FinalizeSetupStep(() -> {
            MiningPoolEntity<?> selectedPool = poolSetupStep.getSelectedPoolEntity();
            WizardSaveService saveService = SpringContextHelper.getBean(WizardSaveService.class);
            saveService.addPoolToExistingSite(pvSiteEntity, selectedPool);

        }, () -> {
            UI.getCurrent().navigate("site/" + pvSiteEntity.getId() + "/clusters");
        });

        this.steps = List.of(poolSetupStep, finalizeStep);
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
}