package de.verdox.pv_miner.core.miner.braiins.graphql;

import java.time.Instant;

record CookieDetails(
        String token,
        Instant expiresAt
) {
    boolean valid() {
        return Instant.now()
                .isBefore(
                        expiresAt
                );
    }
}
