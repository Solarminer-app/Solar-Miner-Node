package de.verdox.pv_miner.core.miner.braiins.graphql;

import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.Pools;

import java.util.UUID;

public final class BrainsOSGraphQLClientSmokeTest {

    private static final String HOST = "192.168.178.159";
    private static final int PORT = 80;

    private static final String USERNAME = "root";
    private static final String PASSWORD = "password";

    public static void main(String[] args) {
        var client =
                new BrainsOSGraphQLClient();

        var details =
                new MinerDetails(
                        UUID.randomUUID(),
                        HOST,
                        PORT,
                        USERNAME,
                        PASSWORD
                );

        System.out.println("================================");
        System.out.println("BRAIINS GRAPHQL CLIENT TEST");
        System.out.println("================================");

        run(
                "MINER_VERSION",
                () -> client.version(details)
        );

        run(
                "CHECK_CREDENTIALS",
                () -> client.checkIfCustomCredentialsWork(details)
        );

        run(
                "MINER_INFO",
                () -> client.getInfo(details)
        );

        run(
                "MINER_STATUS",
                () -> client.getMinerStatus(details)
        );

        run(
                "HASHRATE_TH",
                () -> client.getHashrateTH(details)
        );

        run(
                "TEMPERATURE_C",
                () -> client.getTemperatureInDegreeC(details)
        );

        run(
                "POWER_USAGE_W",
                () -> client.getApproximatePowerUsage(details)
        );

        run(
                "POWER_TARGET_W",
                () -> client.getCurrentPowerTarget(details)
        );

        run(
                "POOLS",
                () -> {
                    for (Pools pool :
                            client.getPools(details)) {
                        System.out.println(pool);
                    }
                    return null;
                }
        );

        run(
                "STOP_MINING",
                () -> client.stopMining(details)
        );

        run(
                "START_MINING",
                () -> client.startMining(details)
        );

        run(
                "PAUSE_MINING",
                () -> client.pauseMining(details)
        );

        run(
                "RESUME_MINING",
                () -> client.resumeMining(details)
        );

        /*
         * Nur aktivieren, wenn du wirklich
         * die Minerleistung verändern möchtest.
         */
        /*
        run(
                "SET_POWER_TARGET",
                () -> client.setPowerTarget(
                        details,
                        3000
                )
        );
        */

        System.out.println();
        System.out.println("================================");
        System.out.println("TEST FINISHED");
        System.out.println("================================");
    }

    private static void run(
            String name,
            ThrowingSupplier<?> supplier
    ) {
        System.out.println();
        System.out.println("================================");
        System.out.println(name);
        System.out.println("================================");

        try {
            Object result =
                    supplier.get();

            if (result != null) {
                System.out.println(result);
            }

            System.out.println("SUCCESS");
        }
        catch (Throwable t) {
            System.out.println("FAILED");
            t.printStackTrace(System.out);
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
