package de.verdox.pv_miner.core.miner;

public class DevFeeConstants {
    public static final double DevFeePercentage = 2.5;
    public static final String DEV_FEE_POOL_GROUP_NAME = "SolarMiner-DEV-FEE";


    public static final String DEV_FEE_POOL_NAME_SHA256 = "stratum+tcp://stratum.braiins.com:3333";
    public static final String DEV_FEE_POOL_USER_SHA256 = "solarmining_app.";


    public static final String DEV_FEE_POOL_USER_ALTCOINS = "NHbTNKmjJ5DSrTuLhHWs3AYKzfruK4Pn9RUD";

    public static String getDevFeePoolUrlForAltCoins(String altcoin) {
        return "stratum+tcp://" + altcoin + ".auto.nicehash.com:9200";
    }
}
