package de.verdox.pv_miner.frontend.setup.pool;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.frontend.components.CheckConnection;
import de.verdox.pv_miner_extensions.pools.braiins.BraiinsPoolEntity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class BraiinsSetupProvider implements MiningPoolSetupProvider {

    private Binder<BraiinsPoolEntity> braiinsBinder;

    @Override
    public String getDisplayName() {
        return "Braiins Pool";
    }

    @Override
    public String getMarkdownInstructions() {
        Locale currentLocale = UI.getCurrent() != null ? UI.getCurrent().getLocale() : Locale.ENGLISH;
        String langTag = currentLocale.getLanguage();

        String filePath = "/markdowns/braiins_pool_instructions_" + langTag + ".md";
        InputStream inputStream = getClass().getResourceAsStream(filePath);

        if (inputStream == null) {
            filePath = "/markdowns//braiins_pool_instructions_en.md";
            inputStream = getClass().getResourceAsStream(filePath);
        }

        if (inputStream == null) {
            return "## Braiins Pool Setup\nError: Instructions file not found in resources.";
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            e.printStackTrace();
            return "## Error\nFailed to read the instructions file.";
        }
    }

    @Override
    public void buildForm(FormLayout formLayout, Runnable onFieldsChanged) {
        braiinsBinder = new Binder<>();

        PasswordField tokenField = new PasswordField("Braiins Access Token");
        tokenField.setValueChangeMode(ValueChangeMode.EAGER);
        tokenField.setWidthFull();

        tokenField.addValueChangeListener(e -> onFieldsChanged.run());

        braiinsBinder.forField(tokenField)
                .asRequired()
                .bind(BraiinsPoolEntity::getAuthToken, BraiinsPoolEntity::setAuthToken);

        formLayout.add(tokenField);
    }

    @Override
    public boolean isInputValid() {
        return braiinsBinder != null && braiinsBinder.isValid();
    }

    @Override
    public CheckConnection buildConnectionChecker(Consumer<Boolean> onVerificationStatusChanged) {
        return new CheckConnection(() -> {
            BraiinsPoolEntity entity = new BraiinsPoolEntity();
            if (braiinsBinder != null && braiinsBinder.writeBeanIfValid(entity)) {
                return SpringContextHelper.getBean(EntityQueryService.class)
                        .ping(entity, 10, TimeUnit.SECONDS);
            }
            return java.util.concurrent.CompletableFuture.completedFuture(false);
        }, onVerificationStatusChanged);
    }

    @Override
    public MiningPoolEntity<?> getConfiguredEntity() {
        BraiinsPoolEntity entity = new BraiinsPoolEntity();
        if (braiinsBinder != null) {
            braiinsBinder.writeBeanIfValid(entity);
        }
        return entity;
    }
}