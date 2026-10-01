package de.verdox.pv_miner.miningpool;

import java.util.Arrays;

/** Canonical mining and payout coins supported by the Node configuration views. */
public enum MiningCoin {
    BITCOIN("bitcoin", "BTC", "SHA256", true),
    MONERO("monero", "XMR", "RandomX", true),
    PEARL("pearl", "PRL", "PearlHash", true);

    private final String key;
    private final String symbol;
    private final String algorithm;
    private final boolean automaticAssignment;

    MiningCoin(String key, String symbol, String algorithm, boolean automaticAssignment) {
        this.key = key;
        this.symbol = symbol;
        this.algorithm = algorithm;
        this.automaticAssignment = automaticAssignment;
    }

    public String key() { return key; }
    public String symbol() { return symbol; }
    public String algorithm() { return algorithm; }
    public boolean automaticAssignment() { return automaticAssignment; }

    public static MiningCoin from(String value) {
        if (value == null) throw new IllegalArgumentException("Mining coin is required");
        return Arrays.stream(values()).filter(coin -> coin.key.equalsIgnoreCase(value)
                || coin.symbol.equalsIgnoreCase(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown mining coin"));
    }

    public boolean validAddress(String address) {
        if (address == null) return false;
        return switch (this) {
            case BITCOIN -> address.matches("(?:bc1[ac-hj-np-z02-9]{11,87}|[13][a-km-zA-HJ-NP-Z1-9]{25,34})");
            case MONERO -> address.matches("[1-9A-HJ-NP-Za-km-z]{95,120}");
            case PEARL -> address.matches("prl1[023456789acdefghjklmnpqrstuvwxyz]{20,120}");
        };
    }
}
