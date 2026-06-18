package de.verdox.pv_miner.frontend;

import com.vaadin.flow.component.UI;
import de.verdox.pv_miner.SpringContextHelper;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@EnableScheduling
@Service
public class FrontendService {
    private static final Logger LOGGER = Logger.getLogger(FrontendService.class.getSimpleName());

    private static final int UI_UPDATE_INTERVAL = 1;
    private static final TimeUnit UI_UPDATE_INTERVAL_TIMEUNIT = TimeUnit.SECONDS;

    private final UpdateMap updateMap = new UpdateMap();
    private final AtomicBoolean processing = new AtomicBoolean(false);

    public static void scheduleUpdate(Optional<UI> optionalUI, Consumer<UI> update) {
        optionalUI.ifPresent(ui -> {
            SpringContextHelper.getBean(FrontendService.class).scheduleUpdate(ui, update);
        });
    }

    public static void updateNow(Optional<UI> optionalUI, Consumer<UI> update) {
        optionalUI.ifPresent(ui -> {
            ui.access(() -> {
                update.accept(ui);
                ui.push();
            });
        });
    }

    /**
     * Fügt ein UI-Update zur Warteschlange hinzu
     */
    public void scheduleUpdate(UI ui, Consumer<UI> update) {
        if (ui.isClosing()) {
            throw new IllegalArgumentException("The provided UI is not available anymore");
        }
        updateMap.add(ui, update);
    }

    @Scheduled(fixedRate = UI_UPDATE_INTERVAL, timeUnit = TimeUnit.SECONDS)
    public void processUpdates() {
        if (processing.compareAndSet(false, true)) {
            try {
                updateMap.processUpdates();
            } finally {
                processing.set(false);
            }
        }
    }

    private static class UpdateMap {
        private final Map<UI, Queue<Consumer<UI>>> updateCache = new ConcurrentReferenceHashMap<>(1024, ConcurrentReferenceHashMap.ReferenceType.WEAK);

        public void add(UI ui, Consumer<UI> update) {
            updateCache.computeIfAbsent(ui, ui1 -> new ConcurrentLinkedQueue<>()).add(update);
        }

        public void processUpdates() {
            updateCache.entrySet().stream()
                    .filter(entry -> {
                        UI ui = entry.getKey();
                        return ui.isAttached() && !ui.isClosing();
                    })
                    .collect(Collectors.groupingBy(entry -> entry.getKey().getSession()))
                    .values()
                    .parallelStream()
                    .flatMap(List::stream)
                    .forEach(entry -> processUI(entry.getKey(), entry.getValue()));
        }

        private void processUI(UI ui, Queue<Consumer<UI>> updateQueue) {
            if (updateQueue.isEmpty()) {
                return;
            }

            try {
                ui.access(() -> {
                    while (!updateQueue.isEmpty()) {
                        Consumer<UI> task = updateQueue.poll();
                        if (task != null) {
                            task.accept(ui);
                        }
                    }
                    ui.push();
                }).get(UI_UPDATE_INTERVAL, UI_UPDATE_INTERVAL_TIMEUNIT);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                LOGGER.log(Level.SEVERE, "Could not update ui in time.");
            }
        }
    }
}
