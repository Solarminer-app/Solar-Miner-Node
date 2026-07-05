package de.verdox.pv_miner.core.util;

import java.util.HashMap;
import java.util.Map;

public record AsicMinerSpec(String model, String algorithm, int watts, long hs) {

    private static final Map<String, AsicMinerSpec> KNOWN_SPECS = new HashMap<>();

    private static AsicMinerSpec register(String model, String algorithm, int watts) {
        var spec = new AsicMinerSpec(model, algorithm, watts, 0);
        KNOWN_SPECS.put(model.toUpperCase(), spec);
        return spec;
    }

    public static AsicMinerSpec find(String model) {
        if (model == null) {
            return null;
        }
        return KNOWN_SPECS.get(model.toUpperCase());
    }

    static {
        Antminer.registerAll();
        Whatsminer.registerAll();
    }

    private static class Antminer {

        private static void registerAll() {

            final String SHA256 = "SHA-256";

            register("ANTMINER S23", SHA256, 3500);
            register("ANTMINER S23 Hydro", SHA256, 5510);
            register("ANTMINER S23 IMM", SHA256, 4400);
            register("ANTMINER S23 Hydro 3U", SHA256, 11020);

            register("ANTMINER S21", SHA256, 3500);
            register("ANTMINER S21 HYD.", SHA256, 5360);
            register("ANTMINER S21 PRO", SHA256, 3510);
            register("ANTMINER S21 PRO+", SHA256, 3564);
            register("ANTMINER S21+ HYD.", SHA256, 5500);
            register("ANTMINER S21+", SHA256, 3564);
            register("ANTMINER S21 XP", SHA256, 3645);
            register("ANTMINER S21 XP HYD.", SHA256, 5670);
            register("ANTMINER S21E HYD.", SHA256, 5560);
            register("ANTMINER S21E XP HYD.", SHA256, 5560);
            register("ANTMINER S21 XP IMM.", SHA256, 4255);
            register("ANTMINER S21 IMM.", SHA256, 4255);

            register("ANTMINER T21", SHA256, 3610);

            register("ANTMINER S19 XP", SHA256, 3010);
            register("ANTMINER S19J XP", SHA256, 3247);
            register("ANTMINER S19 XP HYD.", SHA256, 5345);
            register("ANTMINER S19 XP+ HYD.", SHA256, 5301);

            register("ANTMINER S19 PRO", SHA256, 3250);
            register("ANTMINER S19 PRO+", SHA256, 3355);
            register("ANTMINER S19 PRO++", SHA256, 3355);
            register("ANTMINER S19 PRO HYD.", SHA256, 5445);
            register("ANTMINER S19 PRO+ HYD.", SHA256, 5445);

            register("ANTMINER S19J", SHA256, 3100);
            register("ANTMINER S19J+", SHA256, 3355);
            register("ANTMINER S19J PRO", SHA256, 3050);
            register("ANTMINER S19J PRO+", SHA256, 3355);

            register("ANTMINER S19", SHA256, 3250);
            register("ANTMINER S19I", SHA256, 3250);
            register("ANTMINER S19A", SHA256, 3250);
            register("ANTMINER S19A PRO", SHA256, 3250);
            register("ANTMINER S19 HYD.", SHA256, 5451);
            register("ANTMINER S19K PRO", SHA256, 2760);

            register("ANTMINER T19", SHA256, 3150);
            register("ANTMINER T19 HYD.", SHA256, 5451);

            register("ANTMINER S17", SHA256, 2520);
            register("ANTMINER S17 PRO", SHA256, 2094);
            register("ANTMINER S17+", SHA256, 2920);
            register("ANTMINER S17E", SHA256, 2880);
            register("ANTMINER T17", SHA256, 2200);
            register("ANTMINER T17+", SHA256, 3200);
            register("ANTMINER T17E", SHA256, 2915);

            register("ANTMINER S15", SHA256, 1596);
            register("ANTMINER T15", SHA256, 1541);

            register("ANTMINER S11", SHA256, 1530);

            register("ANTMINER S9", SHA256, 1323);
            register("ANTMINER S9I", SHA256, 1320);
            register("ANTMINER S9J", SHA256, 1350);
            register("ANTMINER S9K", SHA256, 1148);
            register("ANTMINER S9 SE", SHA256, 1280);
            register("ANTMINER S9-HYDRO", SHA256, 1728);
            register("ANTMINER V9", SHA256, 1027);
            register("ANTMINER T9", SHA256, 1450);
            register("ANTMINER T9+", SHA256, 1432);

            register("ANTMINER S7", SHA256, 1293);
            register("ANTMINER S7-LN", SHA256, 700);
            register("ANTMINER S5", SHA256, 590);
            register("ANTMINER S5+", SHA256, 3438);
            register("ANTMINER R4", SHA256, 845);
            register("ANTMINER S4", SHA256, 1400);
            register("ANTMINER S4+", SHA256, 1450);
            register("ANTMINER S3", SHA256, 355);
            register("ANTMINER S2", SHA256, 1000);
            register("ANTMINER C1", SHA256, 800);
            register("ANTMINER S1", SHA256, 360);
            register("ANTROUTER R1", SHA256, 5);
        }
    }

    private static class Whatsminer {
        private static void registerAll() {
            final String SHA256 = "SHA-256";

            register("WHATSMINER M60", SHA256, 3300);
            register("WHATSMINER M60S", SHA256, 3300);
            register("WHATSMINER M63S", SHA256, 6800);


            register("WHATSMINER M50", SHA256, 3300);
            register("WHATSMINER M50S", SHA256, 3270);
            register("WHATSMINER M53", SHA256, 6500);


            register("WHATSMINER M30S", SHA256, 3360);
            register("WHATSMINER M30S+", SHA256, 3400);
            register("WHATSMINER M30S++", SHA256, 3472);
            register("WHATSMINER M31S", SHA256, 3220);

            register("WHATSMINER M21S", SHA256, 3220);
            register("WHATSMINER M21", SHA256, 3220);
        }
    }

    private static class Avalon {
        private static void registerAll() {
            final String SHA256 = "SHA-256";

            register("AVALON A1466", SHA256, 3260);
            register("AVALON A1346", SHA256, 3300);
            register("AVALON A1246", SHA256, 3425);
            register("AVALON A1166 PRO", SHA256, 3400);
            register("AVALON A1066", SHA256, 3250);
        }
    }

    /**
     * Input GigaHashes/s - Output H/s
     */
    private static long ghs(double ghs) {
        return (long) (ghs * 1_000_000_000L);
    }

    /**
     * Input TeraHashes/s - Output H/s
     */
    private static long ths(double ths) {
        return (long) (ths * 1_000_000_000_000L);
    }

    /**
     * Input KiloHashes/s - Output H/s
     */
    private static long khs(double khs) {
        return (long) (khs * 1_000L);
    }
}