package de.verdox.pv_miner.frontend.setup;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;

public class FinalizeSetupStep extends VerticalLayout implements WizardStep {

    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private final TranslatableSpan statusText;
    private final Div spinnerContainer;
    private boolean saveComplete = false;

    private final ThrowingRunnable saveTask;
    private final Runnable onFinalizeDoneCallback;

    public FinalizeSetupStep(ThrowingRunnable saveTask, Runnable onFinalizeDoneCallback) {
        this.saveTask = saveTask;
        this.onFinalizeDoneCallback = onFinalizeDoneCallback;

        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setHeight("350px");

        spinnerContainer = new Div();
        spinnerContainer.addClassName("sync-spinner-container");

        Icon syncIcon = VaadinIcon.REFRESH.create();
        syncIcon.addClassName("sync-spinning-icon");
        spinnerContainer.add(syncIcon);

        statusText = new TranslatableSpan("setup.finalize.processing");
        statusText.addClassName("sync-status-text");

        add(spinnerContainer, statusText);
    }

    @Override
    public String getTitleTranslationKey() {
        return "setup.finalize.title";
    }

    @Override
    public void onEnter() {
        UI ui = UI.getCurrent();
        statusText.setText("setup.finalize.saving");

        Thread.startVirtualThread(() -> {
            try {
                saveTask.run();

                ui.access(() -> {
                    saveComplete = true;
                    statusText.setText("setup.finalize.success");

                    spinnerContainer.removeClassName("sync-spinner-container");
                    spinnerContainer.addClassName("sync-success-pop");

                    spinnerContainer.removeAll();
                    Icon checkIcon = VaadinIcon.CHECK_CIRCLE.create();
                    checkIcon.setSize("64px");
                    checkIcon.setColor("var(--lumo-success-color)");
                    spinnerContainer.add(checkIcon);

                    if (onFinalizeDoneCallback != null) {
                        onFinalizeDoneCallback.run();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                ui.access(() -> {
                    statusText.setText("setup.finalize.error");
                    spinnerContainer.removeAll();
                    Icon errorIcon = VaadinIcon.CLOSE_CIRCLE.create();
                    errorIcon.setSize("64px");
                    errorIcon.setColor("var(--lumo-error-color)");
                    spinnerContainer.add(errorIcon);
                });
            }
        });
    }

    @Override
    public boolean isValid() {
        return saveComplete;
    }
}