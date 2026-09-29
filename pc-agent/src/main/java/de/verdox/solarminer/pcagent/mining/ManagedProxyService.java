package de.verdox.solarminer.pcagent.mining;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Starts the embedded Stratum proxy in a separate Spring context for standalone distributions. */
@Service
public class ManagedProxyService {
    private static final Logger LOGGER = Logger.getLogger(ManagedProxyService.class.getName());
    private final boolean standalone;
    private volatile ConfigurableApplicationContext proxyContext;
    private volatile String status = "external";
    private volatile String detail = "";
    private volatile long lastAttempt;

    @Autowired
    public ManagedProxyService(@Value("${solarminer.agent.standalone:false}") boolean standalone) {
        this.standalone = standalone;
        if (standalone) status = "starting";
    }

    /** Keeps the prior constructor available for unit-level proxy configuration fixtures. */
    public ManagedProxyService(boolean standalone, String ignoredProxyJar) {
        this(standalone);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { startIfNeeded(); }

    @Scheduled(fixedDelay = 15_000)
    public void keepRunning() { startIfNeeded(); }

    private synchronized void startIfNeeded() {
        if (!standalone || running() || System.currentTimeMillis() - lastAttempt < 30_000) return;
        lastAttempt = System.currentTimeMillis();
        try {
            Class<?> proxyApplication = Class.forName(
                    "de.verdox.solarminer.solarminerstratumproxy.SolarminerStratumProxyApplication");
            proxyContext = new SpringApplicationBuilder(proxyApplication)
                    .logStartupInfo(false)
                    .run(
                    "--server.address=127.0.0.1", "--server.port=8090",
                    "--proxy.bind-address=127.0.0.1", "--proxy.fee.required=true",
                    "--proxy.coins.bitcoin.port=3333", "--proxy.coins.monero.port=3335",
                    "--proxy.coins.pearl.port=3334",
                    "--proxy.discovery.enabled=false",
                    "--proxy.pearl.enabled=true", "--solarminer.fee.referral=solarminer");
            status = proxyContext.isActive() ? "running" : "failed";
            detail = proxyContext.isActive() ? "" : "Local Stratum service did not remain active";
        } catch (Exception e) {
            status = "failed";
            detail = "Local mining service could not be started: " + e.getMessage();
            LOGGER.log(Level.WARNING, detail, e);
        }
    }

    public boolean running() { return proxyContext != null && proxyContext.isActive(); }
    public String status() { return running() ? "running" : status; }
    public String detail() { return detail; }

    @PreDestroy
    public synchronized void stop() {
        ConfigurableApplicationContext current = proxyContext;
        proxyContext = null;
        if (current != null && current.isActive()) current.close();
    }
}
