package de.verdox.pv_miner.core.miner.braiins;

public record BraiinsAPICapabilities(BraiinsVersion version, boolean supportsGraphQL, boolean supportsGRPC) {

    public static BraiinsAPICapabilities fromVersion(BraiinsVersion version) {
        return new BraiinsAPICapabilities(version, true, version.supportsGrpc());
    }
}
