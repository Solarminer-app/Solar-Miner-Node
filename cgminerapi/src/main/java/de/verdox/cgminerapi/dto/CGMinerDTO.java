package de.verdox.cgminerapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public sealed interface CGMinerDTO
        permits CGMinerDTO.Status,
        CGMinerDTO.Version,
        CGMinerDTO.Config,
        CGMinerDTO.Summary,
        CGMinerDTO.Pools,
        CGMinerDTO.Devs,
        CGMinerDTO.Notify,
        CGMinerDTO.DevDetails,
        CGMinerDTO.Stats,
        CGMinerDTO.Check,
        CGMinerDTO.Coin,
        CGMinerDTO.Debug,
        CGMinerDTO.UsbStats,
        CGMinerDTO.Lcd,
        CGMinerDTO.Pga,
        CGMinerDTO.PgaCount,
        CGMinerDTO.Asc,
        CGMinerDTO.AscCount {

    /*
     * ============================================================
     * Common
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Status(
            @JsonProperty("STATUS")
            List<Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Entry(
                @JsonProperty("STATUS")
                String status,

                @JsonProperty("When")
                long when,

                @JsonProperty("Code")
                int code,

                @JsonProperty("Msg")
                String msg,

                @JsonProperty("Description")
                String description
        ) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Version(
            @JsonProperty("VERSION")
            List<Entry> version,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Entry(

                @JsonProperty("CGMiner")
                String cgminer,

                @JsonProperty("BMMiner")
                String bmMiner,

                @JsonProperty("API")
                String api,

                @JsonProperty("Miner")
                String miner,

                @JsonProperty("CompileTime")
                String compileTime,

                @JsonProperty("Type")
                String type
        ) {
        }
    }

    /*
     * ============================================================
     * CONFIG
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Config(
            @JsonProperty("CONFIG")
            List<Entry> config,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Entry(
                @JsonProperty("ASC Count")
                Integer ascCount,

                @JsonProperty("PGA Count")
                Integer pgaCount,

                @JsonProperty("Pool Count")
                Integer poolCount,

                @JsonProperty("Strategy")
                String strategy,

                @JsonProperty("Log Interval")
                Integer logInterval,

                @JsonProperty("Device Code")
                String deviceCode,

                @JsonProperty("OS")
                String os,

                @JsonProperty("Failover-Only")
                Boolean failoverOnly,

                @JsonProperty("Hotplug")
                Integer hotplug,

                @JsonProperty("ScanTime")
                Integer scanTime,

                @JsonProperty("Queue")
                Integer queue,

                @JsonProperty("Expiry")
                Integer expiry
        ) {
        }
    }

    /*
     * ============================================================
     * SUMMARY
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            @JsonProperty("SUMMARY")
            List<Entry> summary,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Entry(
                @JsonProperty("Elapsed")
                Long elapsed,

                @JsonProperty("Found Blocks")
                Long foundBlocks,

                @JsonProperty("GHS av")
                Double ghsAv,

                @JsonProperty("GHS 5s")
                Double ghs5s,

                @JsonProperty("GHS 30m")
                Double ghs30m,

                @JsonProperty("Getwork")
                Long getwork,

                @JsonProperty("MHS av")
                Double mhsAv,

                @JsonProperty("Work Utility")
                Double workUtility,

                @JsonProperty("Device Hardware%")
                Double deviceHardware,

                @JsonProperty("Device Rejected%")
                Double deviceRejected,

                @JsonProperty("Pool Rejected%")
                Double poolRejected,

                @JsonProperty("Pool Stale%")
                Double poolStale,

                @JsonProperty("Best Share")
                Long bestShare
        ) {
        }
    }

    /*
     * ============================================================
     * POOLS
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pools(
            @JsonProperty("POOLS")
            List<Pool> pools,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Pool(
                @JsonProperty("Pool")
                Integer pool,

                @JsonProperty("URL")
                String url,

                @JsonProperty("User")
                String user,

                @JsonProperty("Status")
                String status,

                @JsonProperty("Priority")
                Integer priority,

                @JsonProperty("Accepted")
                Long accepted,

                @JsonProperty("Rejected")
                Long rejected,

                @JsonProperty("Stale")
                Long stale,

                @JsonProperty("Difficulty Accepted")
                Double difficultyAccepted,

                @JsonProperty("Difficulty Rejected")
                Double difficultyRejected,

                @JsonProperty("Difficulty Stale")
                Double difficultyStale,

                @JsonProperty("Pool Rejected%")
                Double poolRejected,

                @JsonProperty("Pool Stale%")
                Double poolStale,

                @JsonProperty("Last Share Time")
                Long lastShareTime,

                @JsonProperty("Last Share Difficulty")
                Double lastShareDifficulty,

                @JsonProperty("Best Share")
                Long bestShare,

                @JsonProperty("Has Stratum")
                Boolean hasStratum,

                @JsonProperty("Stratum Active")
                Boolean stratumActive,

                @JsonProperty("Stratum URL")
                String stratumUrl,

                @JsonProperty("Quota")
                Double quota,

                @JsonProperty("Work Difficulty")
                Double workDifficulty,

                @JsonProperty("Proxy Type")
                String proxyType,

                @JsonProperty("Proxy")
                String proxy
        ) {
        }
    }

    /*
     * ============================================================
     * DEVS
     * ============================================================
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Devs(
            @JsonProperty("DEVS")
            List<Device> devs,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Device(
                @JsonProperty("Name")
                String name,

                @JsonProperty("ASC")
                Integer asc,

                @JsonProperty("PGA")
                Integer pga,

                @JsonProperty("Status")
                String status,

                @JsonProperty("Accepted")
                Long accepted,

                @JsonProperty("Rejected")
                Long rejected,

                @JsonProperty("Hardware Errors")
                Long hardwareErrors,

                @JsonProperty("MHS av")
                Double mhsAv,

                @JsonProperty("Total MH")
                Double totalMh,

                @JsonProperty("Diff1 Work")
                Long diff1Work,

                @JsonProperty("Difficulty Accepted")
                Double difficultyAccepted,

                @JsonProperty("Difficulty Rejected")
                Double difficultyRejected,

                @JsonProperty("Last Share Difficulty")
                Double lastShareDifficulty,

                @JsonProperty("Last Share Time")
                Long lastShareTime,

                @JsonProperty("Last Share Pool")
                Integer lastSharePool,

                @JsonProperty("Last Valid Work")
                Long lastValidWork,

                @JsonProperty("Device Elapsed")
                Long deviceElapsed,

                @JsonProperty("Device Hardware%")
                Double deviceHardware,

                @JsonProperty("Device Rejected%")
                Double deviceRejected
        ) {
        }
    }

    /*
     * ============================================================
     * NOTIFY
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Notify(
            @JsonProperty("NOTIFY")
            List<Entry> notifications,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Entry(
                @JsonProperty("Name")
                String name,

                @JsonProperty("ID")
                Integer id,

                @JsonProperty("Last Well")
                Long lastWell
        ) {
        }
    }

    /*
     * ============================================================
     * DEVDETAILS
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DevDetails(
            @JsonProperty("DEVDETAILS")
            List<Map<String, Object>> devdetails,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
    }

    /*
     * ============================================================
     * STATS
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stats(
            @JsonProperty("STATS")
            List<Map<String, Object>> stats,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
    }

    /*
     * ============================================================
     * CHECK
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Check(
            @JsonProperty("CHECK")
            List<Entry> check,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Entry(
                @JsonProperty("Exists")
                Boolean exists,

                @JsonProperty("Access")
                Boolean access
        ) {
        }
    }

    /*
     * ============================================================
     * COIN
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Coin(
            @JsonProperty("COIN")
            List<Entry> coin,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Entry(
                @JsonProperty("Hash Method")
                String hashMethod,

                @JsonProperty("Current Block Time")
                Double currentBlockTime,

                @JsonProperty("Current Block Hash")
                String currentBlockHash,

                @JsonProperty("LP")
                Boolean lp,

                @JsonProperty("Network Difficulty")
                Double networkDifficulty
        ) {
        }
    }

    /*
     * ============================================================
     * DEBUG
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Debug(
            @JsonProperty("DEBUG")
            List<Entry> debug,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Entry(
                @JsonProperty("Silent")
                Boolean silent,

                @JsonProperty("Quiet")
                Boolean quiet,

                @JsonProperty("Verbose")
                Boolean verbose,

                @JsonProperty("Debug")
                Boolean debug,

                @JsonProperty("RPCProto")
                Boolean rpcProto,

                @JsonProperty("PerDevice")
                Boolean perDevice,

                @JsonProperty("WorkTime")
                Boolean workTime
        ) {
        }
    }

    /*
     * ============================================================
     * USBSTATS
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsbStats(
            @JsonProperty("USBSTATS")
            List<Map<String, Object>> usbstats,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
    }

    /*
     * ============================================================
     * LCD
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lcd(
            @JsonProperty("LCD")
            List<Map<String, Object>> lcd,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
    }

    /*
     * ============================================================
     * PGA
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Pga(
            @JsonProperty("PGA")
            List<Map<String, Object>> pga,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PgaCount(
            @JsonProperty("PGAS")
            List<Entry> pgas,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Entry(
                @JsonProperty("Count")
                Integer count
        ) {
        }
    }

    /*
     * ============================================================
     * ASC
     * ============================================================
     */

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Asc(
            @JsonProperty("ASC")
            List<Map<String, Object>> asc,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AscCount(
            @JsonProperty("ASCS")
            List<Entry> ascs,

            @JsonProperty("STATUS")
            List<Status.Entry> status,

            @JsonProperty("id")
            int id
    ) implements CGMinerDTO {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Entry(
                @JsonProperty("Count")
                Integer count
        ) {
        }
    }
}