package de.verdox.solarminer.pcagent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/** Pushes local agent state to the browser as miner processes change outside HTTP requests. */
@RestController
@RequestMapping("/api/agent/local/events")
public class AgentEventController {
    private final MiningController mining;
    private final ObjectMapper mapper;
    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicLong revision = new AtomicLong();
    private volatile String lastSnapshot;

    public AgentEventController(MiningController mining, ObjectMapper mapper) {
        this.mining = mining;
        this.mapper = mapper;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(ignored -> subscribers.remove(emitter));
        send(emitter, snapshot());
        return emitter;
    }

    /** Includes process-monitor, driver and downloader transitions without client-side polling. */
    @Scheduled(fixedDelayString = "${solarminer.agent.events.interval-ms:1000}")
    public void publishChanges() {
        if (subscribers.isEmpty()) return;
        String next = snapshot();
        if (!next.equals(lastSnapshot)) {
            lastSnapshot = next;
            subscribers.forEach(emitter -> send(emitter, next));
        }
    }

    private String snapshot() {
        try {
            return mapper.writeValueAsString(mining.overview());
        } catch (Exception failure) {
            return "{\"error\":\"Agent status could not be collected\"}";
        }
    }

    private void send(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().id(Long.toString(revision.incrementAndGet()))
                    .name("overview").data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException disconnected) {
            subscribers.remove(emitter);
            emitter.complete();
        }
    }
}
