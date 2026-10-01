package de.verdox.pv_miner.core.miner.twentyoneenergy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.core.miner.braiins.MinerController;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;
import de.verdox.pv_miner.core.service.DevFeeService;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

/** Safe-by-default adapter: monitoring works immediately; all writes require an explicitly verified map. */
public final class TwentyOneEnergyController implements MinerController {
    private final ObjectMapper json;
    private final boolean controlEnabled;
    private final TwentyOneEnergyPowerMapper powerMapper;

    public TwentyOneEnergyController(ObjectMapper json, boolean controlEnabled, TwentyOneEnergyPowerMapper powerMapper) {
        this.json = json;
        this.controlEnabled = controlEnabled;
        this.powerMapper = powerMapper;
    }

    private TwentyOneEnergyClient client(MinerDetails details) { return new TwentyOneEnergyClient(details, json); }
    private boolean mayWrite() { return controlEnabled && powerMapper != null; }

    /**
     * Explicit commissioning action. It only learns the device's requested-watt table and restores
     * the prior level; it never enables the heater, changes a pool, or grants control permission.
     */
    public CalibratedPowerMap calibratePowerMap(MinerDetails details, boolean heatAndLoadRiskAcknowledged) {
        if (!heatAndLoadRiskAcknowledged) {
            throw new IllegalArgumentException("Calibration requires explicit acknowledgement of heat and electrical load");
        }
        TwentyOneEnergyClient client = client(details);
        if (!client.status().operational()) throw new TwentyOneEnergyApiException("21energy heater is not operational");
        int originalLevel = client.powerLevel();
        if (originalLevel < 0 || originalLevel > 4) throw new TwentyOneEnergyApiException("21energy heater returned an invalid power level");

        Map<Integer, Long> wattsByLevel = new LinkedHashMap<>();
        try {
            for (int level = 0; level <= 4; level++) {
                client.setPowerLevel(level);
                if (client.powerLevel() != level) throw new TwentyOneEnergyApiException("21energy power-level read-back failed");
                long watts = client.powerTargetWatts();
                if (watts < 0) throw new TwentyOneEnergyApiException("21energy watt read-back failed");
                wattsByLevel.put(level, watts);
            }
            // Validates completeness and monotonicity before anything can persist or use the map.
            new TwentyOneEnergyPowerMapper(wattsByLevel);
            return new CalibratedPowerMap(Map.copyOf(wattsByLevel));
        } finally {
            // Restoring the configuration is mandatory even if one probe/read-back fails.
            client.setPowerLevel(originalLevel);
            if (client.powerLevel() != originalLevel) {
                throw new TwentyOneEnergyApiException("Could not restore 21energy power level after calibration");
            }
        }
    }

    public record CalibratedPowerMap(Map<Integer, Long> wattsByLevel) { }

    @Override public boolean startMining(MinerDetails details) { return setEnabled(details, true); }
    @Override public boolean stopMining(MinerDetails details) { return setEnabled(details, false); }
    @Override public boolean pauseMining(MinerDetails details) { return setEnabled(details, false); }
    @Override public boolean resumeMining(MinerDetails details) { return setEnabled(details, true); }

    private boolean setEnabled(MinerDetails details, boolean enabled) {
        if (!mayWrite()) return false;
        TwentyOneEnergyClient client = client(details);
        client.setEnabled(enabled);
        // The API does not expose a documented enabled boolean. A successful health read is the only safe generic read-back.
        return client.status().operational();
    }

    @Override public boolean setPowerTarget(MinerDetails details, long watts) {
        if (!mayWrite()) return false;
        OptionalInt level = powerMapper.levelForBudget(watts);
        if (level.isEmpty()) return setEnabled(details, false);
        TwentyOneEnergyClient client = client(details);
        client.setPowerLevel(level.getAsInt());
        return client.powerLevel() == level.getAsInt() && client.powerTargetWatts() == powerMapper.wattsForLevel(level.getAsInt());
    }

    @Override public boolean incrementPowerTarget(MinerDetails details, long watts) {
        long current = client(details).powerTargetWatts();
        return current >= 0 && setPowerTarget(details, Math.addExact(current, watts));
    }
    @Override public boolean decrementPowerTarget(MinerDetails details, long watts) {
        long current = client(details).powerTargetWatts();
        return current >= 0 && setPowerTarget(details, Math.max(0, current - watts));
    }
    @Override public boolean setPoolTarget(MinerDetails details, String stratumUrl, String userName) { return false; }

    @Override public MinerStats queryStats(String minerName, MinerDetails details) {
        TwentyOneEnergyClient client = client(details);
        TwentyOneEnergyDtos.StatusResponse health = client.status();
        TwentyOneEnergyDtos.SystemStatusResponse system = client.system();
        JsonNode summary = client.summary();
        long target = client.powerTargetWatts();
        long actual = firstLong(summary, "/forge/currentPowerConsumptionW", "/miningDevices/powerConsumptionW");
        double temperature = firstDouble(summary, "/miningDevices/maxChipTemperature");
        // Hashrate units are not verified by the official contract. Never invent a TH/s conversion.
        return new MinerStats(new MinerStats.MinerIdentity(system.productId(), "", system.model()), minerName,
                health.operational() ? MinerStats.MinerStatus.PAUSED : MinerStats.MinerStatus.ERROR,
                Math.max(target, 0), minPower(), defaultPower(), maxPower(), Math.max(actual, 0), 0D, temperature,
                List.<Pools>of(), List.of());
    }

    private long minPower() { return powerMapper == null ? 0 : powerMapper.levels().entrySet().stream().filter(entry -> entry.getKey() > 0).mapToLong(java.util.Map.Entry::getValue).min().orElse(0); }
    private long defaultPower() { return powerMapper == null ? 0 : powerMapper.wattsForLevel(2); }
    private long maxPower() { return powerMapper == null ? 0 : powerMapper.levels().values().stream().mapToLong(Long::longValue).max().orElse(0); }
    private static long firstLong(JsonNode root, String... pointers) { for (String pointer : pointers) { JsonNode n = root.at(pointer); if (n.isNumber()) return n.asLong(); } return 0; }
    private static double firstDouble(JsonNode root, String... pointers) { for (String pointer : pointers) { JsonNode n = root.at(pointer); if (n.isNumber()) return n.asDouble(); } return 0; }

    @Override public MinerStats getLastData(MinerDetails details) { return null; }
    @Override public boolean checkIfCustomCredentialsWork(MinerDetails details) { return client(details).status().operational() && client(details).system().productId() != null; }
    @Override public boolean checkIfStandardCredentialsWork(MinerDetails details) { return checkIfCustomCredentialsWork(details); }
    @Override public boolean verifyProxyRouting(MinerDetails details, String proxyIP) { return false; }
    @Override public void enforceProxyRouting(MinerDetails details, String proxyIP, String proxyPort) { }
    @Override public boolean verifyDevFeeNative(MinerDetails details, List<DevFeeService.FeeTarget> targets) { return false; }
    @Override public void enforceDevFeeNative(MinerDetails details, List<DevFeeService.FeeTarget> targets) { }
}
