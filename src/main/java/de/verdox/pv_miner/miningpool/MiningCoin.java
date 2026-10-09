package de.verdox.pv_miner.miningpool;

import java.util.Arrays;

/** Canonical coins known to the Node. Target-control support is narrower than read-only reporting support. */
public enum MiningCoin {
    BITCOIN("bitcoin", "BTC", "SHA256", "sha256", true),
    MONERO("monero", "XMR", "RandomX", "randomx", true),
    PEARL("pearl", "PRL", "PearlHash", "pearlhash", true),
    RAVENCOIN("ravencoin", "RVN", "KAWPOW", "kawpow", false),
    ETHEREUM_CLASSIC("ethereumclassic", "ETC", "ETCHash", "etchash", false),
    DECRED("decred", "DCR", "BLAKE3", "blake3_decred", false),
    QUANTUS("quantus", "QTC", "QPoW", "quantus", false);

    private final String key;
    private final String symbol;
    private final String algorithm;
    private final String agentAlgorithm;
    private final boolean automaticAssignment;

    MiningCoin(String key, String symbol, String algorithm, String agentAlgorithm, boolean automaticAssignment) {
        this.key = key;
        this.symbol = symbol;
        this.algorithm = algorithm;
        this.agentAlgorithm = agentAlgorithm;
        this.automaticAssignment = automaticAssignment;
    }

    public String key() { return key; }
    public String symbol() { return symbol; }
    public String algorithm() { return algorithm; }
    public String agentAlgorithm() { return agentAlgorithm; }
    public boolean automaticAssignment() { return automaticAssignment; }
    public boolean supportsNodeTargetControl() { return this == BITCOIN || this == MONERO || this == PEARL; }
    public String kryptexTicker() {
        return switch (this) {
            case MONERO -> "xmr";
            case PEARL -> "prl";
            case RAVENCOIN -> "rvn";
            case ETHEREUM_CLASSIC -> "etc";
            case QUANTUS -> "qtc";
            default -> null;
        };
    }

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
            case RAVENCOIN -> validBase58Check(address, 26, 40, 60, 122);
            case ETHEREUM_CLASSIC -> address.matches("0x[0-9a-fA-F]{40}");
            case DECRED -> validDecredAddress(address);
            case QUANTUS -> address.matches("qz[1-9A-HJ-NP-Za-km-z]{38,58}");
        };
    }

    private static boolean validBase58Check(String address, int minLength, int maxLength, int... versions) {
        final String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        if (address == null || address.length() < minLength || address.length() > maxLength) return false;
        java.math.BigInteger value = java.math.BigInteger.ZERO;
        for (char character : address.toCharArray()) {
            int digit = alphabet.indexOf(character);
            if (digit < 0) return false;
            value = value.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(digit));
        }
        byte[] encoded = value.toByteArray();
        if (encoded.length > 0 && encoded[0] == 0) encoded = java.util.Arrays.copyOfRange(encoded, 1, encoded.length);
        int leadingZeroes = 0;
        while (leadingZeroes < address.length() && address.charAt(leadingZeroes) == '1') leadingZeroes++;
        byte[] decoded = new byte[leadingZeroes + encoded.length];
        System.arraycopy(encoded, 0, decoded, leadingZeroes, encoded.length);
        if (decoded.length != 25) return false;
        boolean versionMatches = false;
        for (int version : versions) versionMatches |= (decoded[0] & 0xff) == version;
        if (!versionMatches) return false;
        try {
            java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
            byte[] checksum = sha.digest(sha.digest(java.util.Arrays.copyOf(decoded, 21)));
            return java.util.Arrays.equals(java.util.Arrays.copyOfRange(decoded, 21, 25), java.util.Arrays.copyOf(checksum, 4));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static boolean validDecredAddress(String address) {
        final String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        if (address == null || address.length() < 30 || address.length() > 40) return false;
        java.math.BigInteger value = java.math.BigInteger.ZERO;
        for (char character : address.toCharArray()) {
            int digit = alphabet.indexOf(character);
            if (digit < 0) return false;
            value = value.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(digit));
        }
        byte[] encoded = value.toByteArray();
        if (encoded.length > 0 && encoded[0] == 0) encoded = java.util.Arrays.copyOfRange(encoded, 1, encoded.length);
        int leadingZeroes = 0;
        while (leadingZeroes < address.length() && address.charAt(leadingZeroes) == '1') leadingZeroes++;
        byte[] decoded = new byte[leadingZeroes + encoded.length];
        System.arraycopy(encoded, 0, decoded, leadingZeroes, encoded.length);
        if (decoded.length != 26 || (decoded[0] & 0xff) != 0x07
                || ((decoded[1] & 0xff) != 0x3f && (decoded[1] & 0xff) != 0x1a)) return false;
        try {
            java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
            byte[] checksum = sha.digest(sha.digest(java.util.Arrays.copyOf(decoded, 22)));
            return java.util.Arrays.equals(java.util.Arrays.copyOfRange(decoded, 22, 26), java.util.Arrays.copyOf(checksum, 4));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
