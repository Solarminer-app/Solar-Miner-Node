package de.verdox.pv_miner.miningpool;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;

/** Public Kryptex payout balance for an XMR or PRL target, with no inferred balance for other pools. */
@Service
public class TargetPoolBalanceService {
    private final KryptexPoolApiService kryptex;

    public TargetPoolBalanceService(KryptexPoolApiService kryptex) { this.kryptex = kryptex; }

    public Snapshot read(MiningTargetEntity target) {
        MiningCoin coin;
        try { coin = MiningCoin.from(target.getCoin()); }
        catch (IllegalArgumentException ignored) { return null; }
        if (coin.kryptexTicker() == null) return null;
        if (target.getPayoutAddress() == null) return null;
        URI pool;
        try { pool = URI.create(target.getStratumUrl()); }
        catch (IllegalArgumentException exception) { return null; }
        String host = pool.getHost();
        if (host == null || !(host.equalsIgnoreCase("kryptex.network")
                || host.toLowerCase(java.util.Locale.ROOT).endsWith(".kryptex.network"))) return null;
        KryptexPoolApiService.Snapshot snapshot = kryptex.read(coin, target.getPayoutAddress());
        return new Snapshot(snapshot.amount(), snapshot.fetchedAt(), snapshot.expiresAt());
    }

    public record Snapshot(BigDecimal amount, Instant fetchedAt, Instant expiresAt) { }
}
