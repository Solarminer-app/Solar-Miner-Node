package de.verdox.pv_miner.core.service;

import de.verdox.pv_miner.core.miner.DevFeeConstants;
import de.verdox.pv_miner.core.miner.MiningOS;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class DevFeeService {
    private static final Logger LOGGER = Logger.getLogger(DevFeeService.class.getName());

    private static final long ENFORCEMENT_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(5);
    private final Map<String, Long> lastCheckTimes = new ConcurrentHashMap<>();

    public void enforceDevFee(MinerStats.MinerIdentity minerIdentity, MinerService minerService, MiningOS miningOS, MinerDetails minerDetails) {
        long currentTime = System.currentTimeMillis();
        Long lastCheckTime = lastCheckTimes.getOrDefault(minerIdentity.macAddress(), 0L);

        if (lastCheckTime != null && (currentTime - lastCheckTime) < ENFORCEMENT_COOLDOWN_MS) {
            return;
        }

        try {
            String DevFeePool = "";
            String DevFeeName = "";

            switch (miningOS) {
                case BRAIINS, LUXOS, VNISH -> {
                    DevFeePool = DevFeeConstants.DEV_FEE_POOL_NAME_SHA256;
                    DevFeeName = DevFeeConstants.DEV_FEE_POOL_USER_SHA256;
                }
            }

            String workerSuffix = sanitizeWorkerName(minerIdentity.minerModel() + " " + minerIdentity.macAddress());
            String workerName = DevFeeName + workerSuffix;

            if (!minerService.isDevFeeSetup(miningOS, minerDetails, DevFeePool, workerName, DevFeeConstants.DevFeePercentage)) {
                LOGGER.info("Enforcing dev fee on " + minerDetails.ipv4() + " [" + miningOS + "] -> " + workerName);
                minerService.setupDevFee(miningOS, minerDetails, DevFeePool, workerName, DevFeeConstants.DevFeePercentage);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to check or enforce dev fee on " + minerDetails.ipv4(), e);
        } finally {
            lastCheckTimes.put(minerIdentity.macAddress(), currentTime);
        }
    }

    public static String sanitizeWorkerName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return DevFeeConstants.DEV_FEE_POOL_USER_SHA256;
        }
        String sanitized = rawName.replace(" ", "_");
        sanitized = sanitized.replace(":", "");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9_\\-]", "");
        return sanitized;
    }
}
