package de.verdox.solarminer.pcagent.mining;

import com.fasterxml.jackson.databind.JsonNode;

/** Normalizes the share-counter shapes exposed by the supported local miner APIs. */
public final class MinerShareTelemetry {
    private MinerShareTelemetry() { }

    public record Counters(Long accepted, Long rejected) {
        public static Counters unavailable() { return new Counters(null, null); }
    }

    public static Counters xmrig(JsonNode results) {
        if (results == null || results.isMissingNode() || results.isNull()) return Counters.unavailable();
        Long accepted = number(results, "shares_good", "accepted", "accepted_shares");
        Long rejected = number(results, "shares_rejected", "rejected", "rejected_shares");
        Long total = number(results, "shares_total", "total");
        if (rejected == null && total != null && accepted != null) rejected = Math.max(0, total - accepted);
        if (accepted == null && total != null && rejected != null) accepted = Math.max(0, total - rejected);
        return new Counters(accepted, rejected);
    }

    public static Counters srbMiner(JsonNode pool) {
        if (pool == null || pool.isMissingNode() || pool.isNull()) return Counters.unavailable();
        JsonNode nested = pool.path("shares");
        Long accepted = first(number(pool, "accepted", "accepted_shares"), number(nested, "accepted", "accepted_shares"));
        Long rejected = first(number(pool, "rejected", "rejected_shares"), number(nested, "rejected", "rejected_shares"));
        return new Counters(accepted, rejected);
    }

    private static Long number(JsonNode node, String... names) {
        if (node == null) return null;
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isIntegralNumber()) return Math.max(0, value.asLong());
        }
        return null;
    }

    private static Long first(Long primary, Long fallback) { return primary != null ? primary : fallback; }
}
