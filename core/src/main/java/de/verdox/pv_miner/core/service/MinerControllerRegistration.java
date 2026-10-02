package de.verdox.pv_miner.core.service;

import de.verdox.pv_miner.core.miner.braiins.MinerController;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;

import java.util.Objects;
import java.util.Set;

/**
 * One immutable registration per {@code MiningOS}. Unsupported operating
 * systems deliberately have no controller and no routing action.
 */
public record MinerControllerRegistration(
        MinerController controller,
        Set<MinerCapability> capabilities,
        PoolTargetRouter poolTargetRouter
) {
    private static final PoolTargetRouter UNSUPPORTED_POOL_ROUTING = request -> false;

    public MinerControllerRegistration {
        capabilities = Set.copyOf(capabilities);
        poolTargetRouter = Objects.requireNonNull(poolTargetRouter, "poolTargetRouter");
    }

    public static MinerControllerRegistration unsupported() {
        return new MinerControllerRegistration(null, Set.of(), UNSUPPORTED_POOL_ROUTING);
    }

    public boolean supports(MinerCapability capability) {
        return capabilities.contains(capability);
    }

    public boolean isSupported() {
        return controller != null;
    }

    public boolean configurePoolTarget(PoolTargetRequest request) {
        return supports(MinerCapability.POOL_CONFIGURATION) && poolTargetRouter.configure(request);
    }

    @FunctionalInterface
    public interface PoolTargetRouter {
        boolean configure(PoolTargetRequest request);
    }

    /** C2 input, kept separate from device-specific routing and client calls. */
    public record PoolTargetRequest(
            MinerDetails minerDetails,
            String stratumUrl,
            String userName,
            String referralCode,
            String proxyIp
    ) {
        public String proxyUrl(int port) {
            if (proxyIp == null || proxyIp.isBlank()) return null;
            return "stratum+tcp://" + proxyIp + ":" + port;
        }

        public String proxyUserName() {
            if (stratumUrl == null || userName == null) return null;
            return stratumUrl.replace("stratum+tcp://", "") + ";" + userName + ";x";
        }
    }
}
