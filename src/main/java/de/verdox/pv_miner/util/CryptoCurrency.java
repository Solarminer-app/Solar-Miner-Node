package de.verdox.pv_miner.util;

public enum CryptoCurrency {
    BITCOIN("btc"),
    ;
    private final String id;

    CryptoCurrency(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
