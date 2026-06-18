package de.verdox.pv_miner.frontend.setup;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.frontend.components.translatable.TranslatableSpan;

public class RepoSyncStep extends VerticalLayout implements WizardStep {

    private final TranslatableSpan statusText;
    private final Div spinnerContainer;
    private boolean syncComplete = false;
    private final Runnable onSyncDoneCallback;

    public RepoSyncStep(Runnable onSyncDoneCallback) {
        this.onSyncDoneCallback = onSyncDoneCallback;

        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);
        setHeight("350px");

        spinnerContainer = new Div();
        spinnerContainer.addClassName("sync-spinner-container");

        Icon syncIcon = VaadinIcon.REFRESH.create();
        syncIcon.addClassName("sync-spinning-icon");
        spinnerContainer.add(syncIcon);

        statusText = new TranslatableSpan("setup.repo.connecting");
        statusText.addClassName("sync-status-text");

        add(spinnerContainer, statusText);
    }

    @Override
    public String getTitleTranslationKey() {
        return "setup.repo.title";
    }

    @Override
    public void onEnter() {
        UI ui = UI.getCurrent();
        statusText.setText("setup.repo.syncing");

        Thread.startVirtualThread(() -> {
            try {
                ConfigFetcherService fetcherService = SpringContextHelper.getBean(ConfigFetcherService.class);
                fetcherService.fetchLatestConfigs();

                ui.access(() -> {
                    syncComplete = true;
                    statusText.setText("setup.repo.success");
                    statusText.setTranslationParameters(fetcherService.getCachedProfiles().size());

                    spinnerContainer.removeClassName("sync-spinner-container");
                    spinnerContainer.addClassName("sync-success-pop");

                    spinnerContainer.removeAll();
                    Icon checkIcon = VaadinIcon.CHECK_CIRCLE.create();
                    checkIcon.setSize("64px");
                    spinnerContainer.add(checkIcon);

                    if (onSyncDoneCallback != null) {
                        onSyncDoneCallback.run();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                ui.access(() -> {
                    statusText.setText("setup.repo.error");
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
        return syncComplete;
    }
}