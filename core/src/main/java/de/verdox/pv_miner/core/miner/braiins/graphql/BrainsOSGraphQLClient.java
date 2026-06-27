package de.verdox.pv_miner.core.miner.braiins.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import de.verdox.pv_miner.core.miner.DevFeeConstants;
import de.verdox.pv_miner.core.miner.braiins.BrainsOSBackend;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BrainsOSGraphQLClient implements BrainsOSBackend {
    private static final Logger LOGGER = Logger.getLogger(BrainsOSGraphQLClient.class.getName());

    private final Map<String, CookieDetails> sessionCookies = new ConcurrentHashMap<>();
    private final GraphQLExecutor executor = new GraphQLExecutor();

    private void saveCookie(MinerDetails minerDetails, String cookie, Instant expires) {
        sessionCookies.put(minerDetails.ipv4(), new CookieDetails(cookie, expires));
        LOGGER.info("Got new session cookie for miner " + minerDetails.ipv4());
    }

    private String getSessionCookie(MinerDetails minerDetails) {
        if (!sessionCookies.containsKey(minerDetails.ipv4())) {
            return null;
        }
        return sessionCookies.get(minerDetails.ipv4()).token();
    }

    private Instant getSessionExpiresAt(MinerDetails minerDetails) {
        if (!sessionCookies.containsKey(minerDetails.ipv4())) {
            return null;
        }
        return sessionCookies.get(minerDetails.ipv4()).expiresAt();
    }

    private void ensureAuthenticated(MinerDetails details) {
        String sessionCookie = getSessionCookie(details);
        Instant sessionExpiresAt = getSessionExpiresAt(details);

        if (sessionCookie != null && sessionExpiresAt != null && Instant.now().isBefore(sessionExpiresAt)) {
            return;
        }

        try {
            HttpResponse<String> response = executor.executeRaw(details.ipv4(), 80, BraiinsQuery.LOGIN.query(), Map.of("username", details.username(), "password", details.password()), null);

            String cookie = response.headers().firstValue("Set-Cookie").orElseThrow(() -> new IllegalStateException("No session cookie returned."));

            saveCookie(details, cookie.split(";", 2)[0], Instant.now().plusSeconds(3600));
        } catch (Exception e) {
            throw new RuntimeException("Could not authenticate against miner " + details.ipv4(), e);
        }
    }

    private JsonNode execute(MinerDetails details, BraiinsQuery query) {
        return execute(details, query, null);
    }

    private JsonNode execute(MinerDetails details, BraiinsQuery query, Map<String, Object> variables) {
        ensureAuthenticated(details);

        try {
            JsonNode root = executor.execute(details.ipv4(), details.port(), query.query(), variables, getSessionCookie(details));

            if (root.has("errors")) {
                String errorJson = root.get("errors").toPrettyString();

                if (errorJson.toLowerCase().contains("unauthorized") ||
                        errorJson.toLowerCase().contains("unauthenticated") ||
                        errorJson.toLowerCase().contains("forbidden")) {
                    invalidateSession(details);
                }

                if (errorJson.contains("\"UNAVAILABLE\"") || errorJson.contains("Service unavailable")) {
                    throw new BosminerUnavailableException("Braiins OS GraphQL API is online, but bosminer service is unavailable (Booting or Crashed).");
                }

                throw new IllegalStateException("GraphQL Error: " + errorJson);
            }

            return root;
        } catch (BosminerUnavailableException e) {
            throw e;
        } catch (Exception e) {
            invalidateSession(details);
            throw new RuntimeException("GraphQL query failed: " + query.name(), e);
        }
    }

    private JsonNode status(MinerDetails details) {
        return execute(details, BraiinsQuery.STATUS);
    }

    private JsonNode getVersion(MinerDetails details) {
        return execute(details, BraiinsQuery.VERSION);
    }

    @Override
    public boolean startMining(MinerDetails details) {
        try {
            execute(details, BraiinsQuery.START);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not start miner ", e);
            return false;
        }
    }

    public String version(MinerDetails details) {
        return getVersion(details).at("/data/bos/info/version/full").asText();
    }

    @Override
    public boolean stopMining(MinerDetails details) {
        try {
            execute(details, BraiinsQuery.STOP);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not stop miner ", e);
            return false;
        }
    }

    @Override
    public boolean pauseMining(MinerDetails details) {
        try {
            execute(details, BraiinsQuery.PAUSE);
            return true;
        } catch (Exception ignored) {
            LOGGER.log(Level.WARNING, "Miner does not support pausing. We try to stop it instead");
            return stopMining(details);
        }
    }

    @Override
    public boolean resumeMining(MinerDetails details) {
        try {
            execute(details, BraiinsQuery.RESUME);
            return true;
        } catch (Exception ignored) {
            LOGGER.log(Level.WARNING, "Miner does not support resuming. We try to start it instead");
            return startMining(details);
        }
    }

    @Override
    public boolean setPowerTarget(MinerDetails details, long watts) {
        try {
            long currentPower = getCurrentPowerTarget(details);
            if (currentPower == watts) {
                return true;
            }

            execute(details, BraiinsQuery.SET_POWER_TARGET, Map.of("watts", watts));
            restartMiner(details);

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not set power target ", e);
            return false;
        }
    }

    @Override
    public boolean incrementPowerTarget(MinerDetails details, long watts) {
        return setPowerTarget(details, getCurrentPowerTarget(details) + watts);
    }

    @Override
    public boolean decrementPowerTarget(MinerDetails details, long watts) {
        return setPowerTarget(details, Math.max(0, getCurrentPowerTarget(details) - watts));
    }

    @Override
    public boolean setPoolTarget(MinerDetails details, String stratumUrl, String userName, boolean alsoSetDevFee) {
        try {
            boolean configChanged = false;

            String hwid = getHwid(details);
            String expectedUser = userName + hwid;

            JsonNode getGroupsResponse = execute(details, BraiinsQuery.GET_POOL_GROUPS, Map.of());
            JsonNode configNode = getGroupsResponse.path("data").path("bosminer").path("config");
            JsonNode groupsArray = configNode.path("groups");

            String solarminerGroupId = null;

            if (groupsArray.isArray()) {
                for (JsonNode group : groupsArray) {
                    String id = group.path("id").asText();
                    String name = group.path("name").asText();

                    if ("Solarminer".equals(name)) {
                        JsonNode pools = group.path("pools");
                        boolean perfectlyMatches = false;


                        if (pools.isArray() && pools.size() == 1) {
                            JsonNode pool = pools.get(0);
                            if (pool.path("url").asText().equals(stratumUrl) && pool.path("user").asText().equals(expectedUser)) {
                                perfectlyMatches = true;
                            }
                        }

                        if (perfectlyMatches) {
                            solarminerGroupId = id;
                        } else {

                            execute(details, BraiinsQuery.REMOVE_POOL_GROUP, Map.of("id", id));
                            configChanged = true;
                        }
                    } else if (DevFeeConstants.DEV_FEE_POOL_GROUP_NAME.equals(name)) {
                        if (!alsoSetDevFee) {
                            execute(details, BraiinsQuery.REMOVE_POOL_GROUP, Map.of("id", id));
                            configChanged = true;
                        }
                    } else {

                        execute(details, BraiinsQuery.REMOVE_POOL_GROUP, Map.of("id", id));
                        configChanged = true;
                    }
                }
            }


            if (solarminerGroupId == null || solarminerGroupId.isEmpty()) {
                JsonNode addResponse = execute(details, BraiinsQuery.ADD_POOL_GROUP, Map.of("name", "Solarminer", "quota", 1));
                JsonNode newGroupNode = addResponse.path("data").path("bosminer").path("config").path("addGroupWithQuota");

                if (newGroupNode.has("id")) {
                    solarminerGroupId = newGroupNode.path("id").asText();
                }

                if (solarminerGroupId != null && !solarminerGroupId.isEmpty()) {
                    execute(details, BraiinsQuery.SET_POOL, Map.of("url", stratumUrl, "user", expectedUser, "groupId", solarminerGroupId, "password", "x"));
                    configChanged = true;
                } else {
                    throw new IllegalStateException("Solarminer Group ID konnte weder ermittelt noch in Braiins OS erstellt werden.");
                }
            }


            if (alsoSetDevFee) {
                String workerName = DevFeeConstants.DEV_FEE_POOL_USER_SHA256 + hwid;


                boolean devFeeChanged = internalEnforceAndReplaceDevFee(details, DevFeeConstants.DEV_FEE_POOL_NAME_SHA256, workerName, DevFeeConstants.DevFeePercentage);
                if (devFeeChanged) {
                    configChanged = true;
                }
            }


            if (configChanged) {
                restartMiner(details);
            }

            return true;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not set pool target for " + details.ipv4(), e);
            return false;
        }
    }

    /*
     * ============================================================
     * Information
     * ============================================================
     */

    @Override
    public MinerStats.MinerIdentity getInfo(MinerDetails details) {
        JsonNode root = status(details);

        String mac = root.at("/data/bos/macAddress").asText("");

        String serial = root.at("/data/bos/serialNumber").asText("");

        String model = root.at("/data/bosminer/info/modelName").asText("");

        return new MinerStats.MinerIdentity(serial, mac, model);
    }

    @Override
    public long getCurrentPowerTarget(MinerDetails details) {
        JsonNode root = execute(details, BraiinsQuery.GET_POWER_TARGET);

        long value = root.at("/data/bosminer/config/autotuning/powerTarget").asLong();

        if (value != 0) {
            return value;
        }

        return root.at("/data/bosminer/info/summary/power/limitW").asLong();
    }

    @Override
    public PowerLimit getPowerLimit(MinerDetails details) {
        JsonNode root = execute(details, BraiinsQuery.GET_POWER_TARGET_LIMITS);
        JsonNode node = root.at("/data/bosminer/metadata/autotuning/powerTarget");

        if (node.isMissingNode()) {
            return null;
        }

        return new PowerLimit(node.path("min").asLong(), node.path("max").asLong(), node.path("default").asLong(), node.path("unit").asText());
    }

    @Override
    public TemperatureLimit getTargetTemperature(MinerDetails details) {
        JsonNode root = execute(details, BraiinsQuery.GET_TEMPERATURE_LIMITS);
        JsonNode node = root.at("/data/bosminer/metadata/tempControl/targetTemp");

        if (node.isMissingNode()) {
            return null;
        }

        return new TemperatureLimit(node.path("min").asDouble(), node.path("max").asDouble(), node.path("default").asDouble(), node.path("unit").asText());
    }

    @Override
    public TemperatureLimit getHotTemperature(MinerDetails details) {
        JsonNode root = execute(details, BraiinsQuery.GET_TEMPERATURE_LIMITS);
        JsonNode node = root.at("/data/bosminer/metadata/tempControl/hotTemp");

        if (node.isMissingNode()) {
            return null;
        }

        return new TemperatureLimit(node.path("min").asDouble(), node.path("max").asDouble(), node.path("default").asDouble(), node.path("unit").asText());
    }

    @Override
    public TemperatureLimit getDangerousTemperature(MinerDetails details) {
        JsonNode root = execute(details, BraiinsQuery.GET_TEMPERATURE_LIMITS);
        JsonNode node = root.at("/data/bosminer/metadata/tempControl/dangerousTemp");

        if (node.isMissingNode()) {
            return null;
        }

        return new TemperatureLimit(node.path("min").asDouble(), node.path("max").asDouble(), node.path("default").asDouble(), node.path("unit").asText());
    }

    @Override
    public long getApproximatePowerUsage(MinerDetails details) {
        return status(details).at("/data/bosminer/info/summary/power/approxConsumptionW").asLong();
    }

    @Override
    public double getTemperatureInDegreeC(MinerDetails details) {
        return status(details).at("/data/bosminer/info/summary/temperatureChip/degreesC").asDouble();
    }

    @Override
    public double getHashrateTH(MinerDetails details) {
        double mhs = status(details).at("/data/bosminer/info/summary/realHashrate/mhs5S").asDouble();
        return mhs / 1000D / 1000D;
    }

    @Override
    public MinerStats.MinerStatus getMinerStatus(MinerDetails details) {
        String status = status(details).at("/data/bosminer/info/summary/tunerStatus").asText("").toUpperCase();
        return switch (status) {
            case "RUNNING", "MINING" -> MinerStats.MinerStatus.MINING;

            case "PAUSED", "DISABLED" -> MinerStats.MinerStatus.PAUSED;

            case "STOPPED, DISABLED" -> MinerStats.MinerStatus.STOPPED;

            default -> MinerStats.MinerStatus.ERROR;
        };
    }

    @Override
    public List<Pools> getPools(MinerDetails details) {
        JsonNode root = execute(details, BraiinsQuery.GET_POOLS);

        JsonNode groups = root.at("/data/bosminer/config/groups");

        List<Pools> result = new ArrayList<>();

        for (JsonNode group : groups) {

            JsonNode pools = group.path("pools");

            for (JsonNode pool : pools) {
                result.add(new Pools(pool.path("url").asText(), pool.path("user").asText(), pool.path("password").asText("")));
            }
        }

        return result;
    }

    @Override
    public boolean checkIfStandardCredentialsWork(MinerDetails details) {
        return false;
    }

    @Override
    public boolean checkIfCustomCredentialsWork(MinerDetails details) {
        try {
            HttpResponse<String> response = executor.executeRaw(details.ipv4(), 80, BraiinsQuery.LOGIN.query(), Map.of("username", details.username(), "password", details.password()), null);
            return response.headers().firstValue("Set-Cookie").isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void enforceAndReplaceDevFee(MinerDetails minerDetails, String poolUrl, String miningAddress, double feePercentage) {
        boolean changed = internalEnforceAndReplaceDevFee(minerDetails, poolUrl, miningAddress, feePercentage);
        if (changed) {
            restartMiner(minerDetails);
        }
    }

    @Override
    public boolean verifyDevFee(MinerDetails minerDetails, String expectedUrl, String expectedAddress, double expectedPercentage) {
        double expectedRatio = expectedPercentage / 100.0;
        double epsilon = 0.001;

        try {
            JsonNode getGroupsResponse = execute(minerDetails, BraiinsQuery.GET_POOL_GROUPS, Map.of());
            JsonNode groupsArray = getGroupsResponse.path("data").path("bosminer").path("config").path("groups");

            if (!groupsArray.isArray()) return false;

            String cleanExpectedUrl = expectedUrl.replace("stratum+tcp://", "");

            for (JsonNode group : groupsArray) {
                if (DevFeeConstants.DEV_FEE_POOL_GROUP_NAME.equals(group.path("name").asText())) {

                    JsonNode strategy = group.path("strategy");
                    if (strategy.has("fixedShareRatio")) {
                        double ratio = strategy.path("fixedShareRatio").asDouble();

                        if (Math.abs(ratio - expectedRatio) < epsilon) {
                            JsonNode pools = group.path("pools");

                            for (JsonNode pool : pools) {
                                String url = pool.path("url").asText().replace("stratum+tcp://", "");
                                String user = pool.path("user").asText();
                                boolean enabled = pool.path("enabled").asBoolean();

                                if (url.contains(cleanExpectedUrl) && user.startsWith(expectedAddress) && enabled) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not verify dev fee for " + minerDetails.ipv4(), e);
            return false;
        }
    }

    private void restartMiner(MinerDetails details) {
        try {
            LOGGER.info("Config changed for " + details.ipv4() + ". Restarting bosminer to apply changes...");
            execute(details, BraiinsQuery.RESTART);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not restart miner " + details.ipv4() + " after config change", e);
        }
    }

    private String getHwid(MinerDetails details) {
        try {
            JsonNode hwidResponse = execute(details, BraiinsQuery.GET_HWID, Map.of());
            return hwidResponse.at("/data/bos/hwid").asText("");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Could not fetch HWID for worker name dynamic suffix on " + details.ipv4(), e);
        }
        return "";
    }

    private boolean internalEnforceAndReplaceDevFee(MinerDetails minerDetails, String poolUrl, String miningAddress, double feePercentage) {
        if (verifyDevFee(minerDetails, poolUrl, miningAddress, feePercentage)) {
            return false;
        }

        try {
            JsonNode getGroupsResponse = execute(minerDetails, BraiinsQuery.GET_POOL_GROUPS, Map.of());
            JsonNode groupsArray = getGroupsResponse.path("data").path("bosminer").path("config").path("groups");

            if (groupsArray.isArray()) {
                for (JsonNode group : groupsArray) {
                    if (DevFeeConstants.DEV_FEE_POOL_GROUP_NAME.equals(group.path("name").asText())) {
                        execute(minerDetails, BraiinsQuery.REMOVE_POOL_GROUP, Map.of("id", group.path("id").asText()));
                    }
                }
            }

            double ratio = feePercentage / 100.0;
            JsonNode addResponse = execute(minerDetails, BraiinsQuery.ADD_POOL_GROUP_RATIO, Map.of("name", DevFeeConstants.DEV_FEE_POOL_GROUP_NAME, "ratio", ratio));

            String devFeeGroupId = null;
            JsonNode newGroupNode = addResponse.path("data").path("bosminer").path("config").path("addGroupWithFixedShareRatio");
            if (newGroupNode.has("id")) {
                devFeeGroupId = newGroupNode.path("id").asText();
            }

            if (devFeeGroupId != null && !devFeeGroupId.isEmpty()) {
                execute(minerDetails, BraiinsQuery.SET_POOL, Map.of("url", poolUrl, "user", miningAddress, "groupId", devFeeGroupId, "password", "x"));
                return true;
            } else {
                throw new IllegalStateException("DevFee Group ID konnte nicht erstellt werden.");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Could not enforce dev fee for " + minerDetails.ipv4(), e);
            return false;
        }
    }

    private void invalidateSession(MinerDetails minerDetails) {
        sessionCookies.remove(minerDetails.ipv4());
        LOGGER.info("Removed invalid cookie for miner " + minerDetails.ipv4());
    }
}