package de.verdox.pv_miner.core.miner;

import java.util.HashMap;
import java.util.Map;

public record MinerStandardCredentials(String username, String password) {
    private static final Map<MiningOS, MinerStandardCredentials> STANDARD_CREDENTIALS = new HashMap<>();

    static {
        create(MiningOS.ANTMINER_STOCK_OS, "root", "root");
        create(MiningOS.WHATSMINER_STOCK_OS, "admin", "admin");
        create(MiningOS.AVALON_STOCK_OS, "root", "root");
        create(MiningOS.INNOSILICON_STOCK_OS, "admin", "admin");

        create(MiningOS.BRAIINS, "root", "password");
        create(MiningOS.VNISH, "root", "root");
        create(MiningOS.LUX_OS, "root", "root");
        create(MiningOS.HIVEON_ASIC, "root", "root");

        create(MiningOS.HIVE_OS, "user", "1");
        create(MiningOS.MS_OS, "minerstat", "msos");
        create(MiningOS.RAVE_OS, "root", "admin");
    }

    public static MinerStandardCredentials byOS(MiningOS miningOS) {
        return STANDARD_CREDENTIALS.get(miningOS);
    }

    private static MinerStandardCredentials create(MiningOS miningOS, String username, String password) {
        var credentials = new MinerStandardCredentials(username, password);
        STANDARD_CREDENTIALS.put(miningOS, credentials);
        return credentials;
    }
}
