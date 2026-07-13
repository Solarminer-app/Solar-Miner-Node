package de.verdox.pv_miner_extensions.device.modbus;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import de.verdox.solarminer.modbustcp.TCPModbusClient;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class ModbusClientManager {
    private static final Logger LOGGER = Logger.getLogger(ModbusClientManager.class.getName());

    private final Cache<String, TCPModbusClient> clientCache;

    public ModbusClientManager() {
        this.clientCache = Caffeine.newBuilder()
                .expireAfterAccess(5, TimeUnit.MINUTES)
                .removalListener((String key, TCPModbusClient client, RemovalCause cause) -> {
                    try {
                        client.close();
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Could not close modbus connection " + key + ": " + e.getMessage(), e);
                    }
                })
                .build();
    }

    public TCPModbusClient getClient(String ip, int port, int slaveId) {
        String key = buildKey(ip, port, slaveId);

        return clientCache.get(key, k -> {
            try {
                return new TCPModbusClient(ip, port, slaveId);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Could not create modbus connection " + key + ": " + e.getMessage(), e);
                return null;
            }
        });
    }

    private String buildKey(String ip, int port, int slaveId) {
        return ip + ":" + port + ":" + slaveId;
    }

    @PreDestroy
    public void closeAll() {
        clientCache.invalidateAll();
        clientCache.cleanUp();
    }
}
