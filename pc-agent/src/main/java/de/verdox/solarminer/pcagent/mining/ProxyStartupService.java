package de.verdox.solarminer.pcagent.mining;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Chooses a LAN proxy once on first start and falls back safely to the bundled loopback proxy. */
@Service
public class ProxyStartupService {
    private final ProxyConfigurationService proxy;
    private final ProxyDiscoveryService discovery;

    public ProxyStartupService(ProxyConfigurationService proxy, ProxyDiscoveryService discovery) {
        this.proxy = proxy;
        this.discovery = discovery;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void configureInitialProxy() {
        if (proxy.hasStoredMode()) {
            proxy.activateStoredMode();
            return;
        }
        Thread.ofVirtual().name("proxy-initial-discovery").start(() -> {
            try {
                var candidate = discovery.discover().stream().findFirst();
                if (candidate.isPresent() && proxy.configure(candidate.get().host())) {
                    proxy.setMode("external");
                    return;
                }
            } catch (Exception ignored) {
                // A missing LAN proxy is expected on standalone installations.
            }
            proxy.setMode("local");
        });
    }
}
