package de.verdox.pv_miner.core.miner.braiins.graphql;

import braiins.bos.v1.Miner;
import com.fasterxml.jackson.databind.JsonNode;
import de.verdox.pv_miner.core.miner.braiins.BrainsOSBackend;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import de.verdox.pv_miner.core.miner.dto.Pools;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BrainsOSGraphQLClient implements BrainsOSBackend {
    private final Map<String, CookieDetails> sessionCookies = new ConcurrentHashMap<>();
    private final GraphQLExecutor executor = new GraphQLExecutor();

    private void saveCookie(MinerDetails minerDetails, String cookie, Instant expires) {
        System.out.println("New session cookie "+cookie+" expires "+expires);
        sessionCookies.put(minerDetails.ipv4(), new CookieDetails(cookie, expires));
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

    /*
     * ============================================================
     * Authentication
     * ============================================================
     */

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

    /*
     * ============================================================
     * Helpers
     * ============================================================
     */

    private JsonNode execute(MinerDetails details, BraiinsQuery query) {
        return execute(details, query, null);
    }

    private JsonNode execute(MinerDetails details, BraiinsQuery query, Map<String, Object> variables) {

        ensureAuthenticated(details);

        try {
            JsonNode root = executor.execute(details.ipv4(), 80, query.query(), variables, getSessionCookie(details));

            if (root.has("errors")) {
                throw new IllegalStateException(root.get("errors").toPrettyString());
            }

            return root;
        } catch (Exception e) {
            throw new RuntimeException("GraphQL query failed: " + query.name(), e);
        }
    }

    private JsonNode status(MinerDetails details) {
        return execute(details, BraiinsQuery.STATUS);
    }

    private JsonNode getVersion(MinerDetails details) {
        return execute(details, BraiinsQuery.VERSION);
    }

    /*
     * ============================================================
     * Miner actions
     * ============================================================
     */

    @Override
    public boolean startMining(MinerDetails details) {
        try {
            execute(details, BraiinsQuery.START);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String version(MinerDetails details) {
        System.out.println(getVersion(details));
        return getVersion(details).at("/data/bos/info/version/full").asText();
    }

    @Override
    public boolean stopMining(MinerDetails details) {
        try {
            execute(details, BraiinsQuery.STOP);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean pauseMining(MinerDetails details) {
        try {
            execute(details, BraiinsQuery.PAUSE);
            return true;
        } catch (Exception ignored) {
            // older firmware
            return stopMining(details);
        }
    }

    @Override
    public boolean resumeMining(MinerDetails details) {
        try {
            execute(details, BraiinsQuery.RESUME);
            return true;
        } catch (Exception ignored) {
            // older firmware
            return startMining(details);
        }
    }

    @Override
    public boolean setPowerTarget(MinerDetails details, long watts) {
        try {
            execute(details, BraiinsQuery.SET_POWER_TARGET, Map.of("watts", watts));
            return true;
        } catch (Exception e) {
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
            execute(details, BraiinsQuery.SET_POOL, Map.of("url", stratumUrl, "user", userName));
            return true;
        } catch (Exception e) {
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
    public long getApproximatePowerUsage(MinerDetails details) {
        return status(details).at("/data/bosminer/info/summary/power/approxConsumptionW").asLong();
    }

    @Override
    public double getTemperatureInDegreeC(MinerDetails details) {
        return status(details).at("/data/bosminer/info/summary/temperatureChip/degreesC").asDouble();
    }

    @Override
    public double getHashrateTH(MinerDetails details) {
        double gh = status(details).at("/data/bosminer/info/summary/realHashrate/ghsAvg").asDouble();

        return gh / 1000D;
    }

    @Override
    public MinerStats.MinerStatus getMinerStatus(MinerDetails details) {
        String status = status(details).at("/data/bosminer/info/summary/tunerStatus").asText("").toUpperCase();

        return switch (status) {
            case "RUNNING", "MINING" -> MinerStats.MinerStatus.MINING;

            case "PAUSED" -> MinerStats.MinerStatus.PAUSED;

            case "STOPPED" -> MinerStats.MinerStatus.STOPPED;

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

    }

    @Override
    public boolean verifyDevFee(MinerDetails minerDetails, String expectedUrl, String expectedAddress, double expectedPercentage) {
        return true;
    }

    /*
     * ============================================================
     * Convenience
     * ============================================================
     */

    public MinerStats queryStats(MinerDetails details) {
        return new GraphQLMinerStatus(getInfo(details).minerModel(), getInfo(details).macAddress(), getInfo(details).minerUID(), getCurrentPowerTarget(details), getApproximatePowerUsage(details), getHashrateTH(details), getTemperatureInDegreeC(details), getMinerStatus(details), getPools(details)).toMinerStats(details.ipv4());
    }
}