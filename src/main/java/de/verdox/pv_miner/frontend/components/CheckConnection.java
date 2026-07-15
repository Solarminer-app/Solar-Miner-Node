package de.verdox.pv_miner.frontend.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.progressbar.ProgressBarVariant;
import de.verdox.pv_miner.frontend.FrontendService;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CheckConnection extends Div {
    private final Button testConnectionButton = new Button("Verbindung testen");
    private final ProgressBar progressBar = new ProgressBar();

    public CheckConnection() {
        this(() -> CompletableFuture.completedFuture(true), null);
    }

    public CheckConnection(Supplier<CompletableFuture<Boolean>> checkConnection) {
        this(checkConnection, null);
    }

    public CheckConnection(@NotNull Supplier<CompletableFuture<Boolean>> checkConnection, @Nullable Consumer<Boolean> onCheckFinished) {
        testConnectionButton.addClickListener(event -> {
            progressBar.setIndeterminate(true);
            progressBar.addThemeVariants(ProgressBarVariant.LUMO_CONTRAST);

            checkConnection.get().whenComplete((aBoolean, throwable) ->
                    FrontendService.scheduleUpdate(getUI(), ui -> {
                        var worked = aBoolean != null ? aBoolean : false;

                        progressBar.setIndeterminate(false);
                        progressBar.setValue(progressBar.getMax());

                        if (worked) {
                            progressBar.addThemeVariants(ProgressBarVariant.LUMO_SUCCESS);
                        } else {
                            progressBar.addThemeVariants(ProgressBarVariant.LUMO_ERROR);
                        }

                        testConnectionButton.setEnabled(true);
                        if (onCheckFinished != null) {
                            onCheckFinished.accept(worked);
                        }
                    }));
        });
        testConnectionButton.setEnabled(false);
        testConnectionButton.setDisableOnClick(true);

        add(testConnectionButton);
        add(progressBar);
    }

    public void reset() {
        progressBar.addThemeVariants(ProgressBarVariant.LUMO_CONTRAST);
        progressBar.setValue(progressBar.getMin());
    }

    public Button getTestConnectionButton() {
        return testConnectionButton;
    }
}
