package de.verdox.pv_miner.frontend.setup.pool;

import com.vaadin.flow.component.formlayout.FormLayout;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.frontend.components.CheckConnection;
import java.util.function.Consumer;

public interface MiningPoolSetupProvider {
    String getDisplayName();

    String getMarkdownInstructions();

    void buildForm(FormLayout formLayout, Runnable onFieldsChanged);

    boolean isInputValid();

    CheckConnection buildConnectionChecker(Consumer<Boolean> onVerificationStatusChanged);

    default boolean isImplemented() {
        return true;
    }

    MiningPoolEntity<?> getConfiguredEntity();
}