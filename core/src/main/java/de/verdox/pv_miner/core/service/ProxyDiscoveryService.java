package de.verdox.pv_miner.core.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class ProxyDiscoveryService {
    private static final Logger LOGGER = Logger.getLogger(ProxyDiscoveryService.class.getName());

    @Value("${PROXY_API_URL:http://localhost:8090}/api/network/ip")
    private String proxyApiUrl;

    @Getter
    private String currentProxyIp = "127.0.0.1";
    private final RestTemplate restTemplate;

    public ProxyDiscoveryService() {
        this.restTemplate = new RestTemplate();
    }


    @EventListener(ApplicationReadyEvent.class)
    public void fetchProxyIpOnStartup() {
        LOGGER.info("Fetching proxy ip from proxy api url: " + proxyApiUrl);
        boolean ipFound = false;
        int attempts = 0;
        int maxAttempts = 30;

        while (!ipFound && attempts < maxAttempts) {
            try {
                attempts++;
                String responseIp = restTemplate.getForObject(proxyApiUrl, String.class);

                if (responseIp != null && !responseIp.isBlank()) {
                    this.currentProxyIp = responseIp.trim();
                    ipFound = true;
                    LOGGER.info("Proxy ip found: " + this.currentProxyIp);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Proxy not found yet (Try " + attempts + "/" + maxAttempts + "). Waiting 2 seconds...");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (!ipFound) {
            LOGGER.log(Level.SEVERE, "Could not find proxy ip: " + currentProxyIp);
        }
    }

}
