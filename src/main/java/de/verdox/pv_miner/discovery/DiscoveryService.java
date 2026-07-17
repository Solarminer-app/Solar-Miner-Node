package de.verdox.pv_miner.discovery;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.pvsite.SunspecConfigService;
import de.verdox.pv_miner_extensions.inverter.modbustcp.ModbusConfigStorage;
import de.verdox.pv_miner_extensions.inverter.rest.RestConfigStorage;
import de.verdox.solarminer.modbustcp.ModbusConfig;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.modbustcp.TCPModbusClient;
import de.verdox.solarminer.rest.RestConfigCreatorTemplate;
import org.springframework.stereotype.Service;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.logging.Logger;

@Service
public class DiscoveryService {
    private final MinerApiClient minerApiClient;

    public record MinerInfo(String model, String ipAddress, MiningOS os) {
    }

    public DiscoveryService(MinerApiClient minerApiClient) {
        this.minerApiClient = minerApiClient;
    }

    public void discoverMiners(String subnetPrefix, Consumer<MinerInfo> onMinerFound, Runnable onScanComplete) {
        CompletableFuture.runAsync(() -> {
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 1; i <= 254; i++) {
                    final String ip = subnetPrefix + i;
                    executor.submit(() -> {
                        try {
                            var detectedMiner = minerApiClient.identifyMiningOS(ip);

                            if (detectedMiner != null) {
                                onMinerFound.accept(new MinerInfo(detectedMiner.model(), ip, detectedMiner.os()));
                            }
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
            onScanComplete.run();
        });
    }
}