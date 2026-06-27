package de.verdox.pv_miner.core.miner.antminer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Objects (DTOs) for mapping Bitmain Antminer CGI-BIN JSON responses and requests.
 * These records map directly to the endpoints defined in {@link AntminerCGIEndpoint}.
 */
public class AntminerDTOs {

    /**
     * Response mapped from {@link AntminerCGIEndpoint#BLINK_STATUS}.
     * Represents the status of the physical locator LED on the miner.
     *
     * @param blink True if the locator LED is currently blinking to identify the miner physically, false otherwise.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BlinkStatusResponse(
            @JsonProperty("blink") boolean blink
    ) {
    }

    /**
     * Shared status header object used in standard CGMiner/BMiner API responses
     * (like POOL_INFO, SUMMARY, and STATS).
     *
     * @param status     The status code (e.g., "S" for Success, "E" for Error, "W" for Warning).
     * @param when       UNIX timestamp of when the response was generated.
     * @param msg        A short message describing the response type (e.g., "summary", "stats").
     * @param apiVersion The version of the internal CGMiner/BMiner API.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiStatus(
            @JsonProperty("STATUS") String status,
            @JsonProperty("when") long when,
            @JsonProperty("Msg") String msg,
            @JsonProperty("api_version") String apiVersion
    ) {
    }

    /**
     * Shared info header object used in standard CGMiner/BMiner API responses.
     * Contains basic firmware build information.
     *
     * @param minerVersion The internal mining software version (e.g., "uart_trans.1.3").
     * @param compileTime  The date and time the firmware was compiled.
     * @param type         The hardware type identifier (e.g., "Antminer S19").
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiInfo(
            @JsonProperty("miner_version") String minerVersion,
            @JsonProperty("CompileTime") String compileTime,
            @JsonProperty("type") String type
    ) {
    }

    /**
     * Response mapped from {@link AntminerCGIEndpoint#SYSTEM_INFO}.
     * Provides basic operating system, hardware, and network details of the miner.
     *
     * @param minerType               The marketing name of the miner (e.g., "Antminer S19").
     * @param netType                 The active network assignment method (e.g., "DHCP" or "Static").
     * @param netDevice               The network interface name (e.g., "eth0").
     * @param macAddr                 The MAC address of the network interface.
     * @param hostname                The network hostname of the miner.
     * @param ipAddress               The current active IP address.
     * @param netmask                 The current active subnet mask.
     * @param gateway                 The current active default gateway.
     * @param dnsServers              The current active DNS servers.
     * @param systemMode              The underlying OS (e.g., "GNU/Linux").
     * @param systemKernelVersion     The Linux kernel version running on the control board.
     * @param systemFilesystemVersion The build date/version of the root filesystem.
     * @param firmwareType            The firmware release channel (e.g., "Release").
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SystemInfoResponse(
            @JsonProperty("minertype") String minerType,
            @JsonProperty("nettype") String netType,
            @JsonProperty("netdevice") String netDevice,
            @JsonProperty("macaddr") String macAddr,
            @JsonProperty("hostname") String hostname,
            @JsonProperty("ipaddress") String ipAddress,
            @JsonProperty("netmask") String netmask,
            @JsonProperty("gateway") String gateway,
            @JsonProperty("dnsservers") String dnsServers,
            @JsonProperty("system_mode") String systemMode,
            @JsonProperty("system_kernel_version") String systemKernelVersion,
            @JsonProperty("system_filesystem_version") String systemFilesystemVersion,
            @JsonProperty("firmware_type") String firmwareType
    ) {
    }

    /**
     * Response mapped from {@link AntminerCGIEndpoint#MINER_TYPE}.
     * Provides specific hardware control board versions.
     *
     * @param minerType The base model name (e.g., "Antminer S19").
     * @param subtype   The specific control board or sub-model revision (e.g., "AMLCtrl_BHB42801").
     * @param fwVersion The firmware compilation date/version.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MinerTypeResponse(
            @JsonProperty("miner_type") String minerType,
            @JsonProperty("subtype") String subtype,
            @JsonProperty("fw_version") String fwVersion
    ) {
    }

    /**
     * Response mapped from {@link AntminerCGIEndpoint#GET_NETWORK_INFO}.
     * Contrasts the active network settings with the configured/saved network settings.
     *
     * @param netType        The active network assignment type.
     * @param netDevice      The physical network interface.
     * @param macAddr        The MAC address.
     * @param ipAddress      The active IP address.
     * @param netmask        The active subnet mask.
     * @param confNetType    The *configured* network type (what it should use on boot).
     * @param confHostname   The *configured* hostname.
     * @param confIpAddress  The *configured* static IP (empty if DHCP).
     * @param confNetmask    The *configured* static netmask.
     * @param confGateway    The *configured* static gateway.
     * @param confDnsServers The *configured* static DNS servers.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NetworkInfoResponse(
            @JsonProperty("nettype") String netType,
            @JsonProperty("netdevice") String netDevice,
            @JsonProperty("macaddr") String macAddr,
            @JsonProperty("ipaddress") String ipAddress,
            @JsonProperty("netmask") String netmask,
            @JsonProperty("conf_nettype") String confNetType,
            @JsonProperty("conf_hostname") String confHostname,
            @JsonProperty("conf_ipaddress") String confIpAddress,
            @JsonProperty("conf_netmask") String confNetmask,
            @JsonProperty("conf_gateway") String confGateway,
            @JsonProperty("conf_dnsservers") String confDnsServers
    ) {
    }

    /**
     * Response mapped from {@link AntminerCGIEndpoint#GET_MINER_CONFIG}.
     * Contains the current mining configuration, including pools and hardware tuning settings.
     *
     * @param pools            The list of configured mining pools.
     * @param apiListen        Whether the CGMiner API is listening for external connections.
     * @param apiNetwork       Whether the API is bound to the network (not just localhost).
     * @param apiGroups        Permissions granted to API accesses.
     * @param apiAllow         IP whitelists for API access.
     * @param bitmainFanCtrl   Whether automatic fan control is managed by Bitmain's logic.
     * @param bitmainFanPwm    Fixed fan speed percentage (PWM) if auto-control is disabled.
     * @param bitmainUseVil    Internal voltage regulation setting.
     * @param bitmainFreq      Base frequency configuration for the ASIC chips.
     * @param bitmainVoltage   Base voltage configuration for the ASIC chips.
     * @param bitmainCcDelay   Clock control delay setting.
     * @param bitmainPwth      Power threshold setting.
     * @param bitmainWorkMode  The operating mode (e.g., "0" = normal, "1" = sleep/low power).
     * @param bitmainFreqLevel The dynamic frequency scaling level target.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MinerConfigResponse(
            @JsonProperty("pools") List<PoolConfig> pools,
            @JsonProperty("api-listen") boolean apiListen,
            @JsonProperty("api-network") boolean apiNetwork,
            @JsonProperty("api-groups") String apiGroups,
            @JsonProperty("api-allow") String apiAllow,
            @JsonProperty("bitmain-fan-ctrl") boolean bitmainFanCtrl,
            @JsonProperty("bitmain-fan-pwm") String bitmainFanPwm,
            @JsonProperty("bitmain-use-vil") boolean bitmainUseVil,
            @JsonProperty("bitmain-freq") String bitmainFreq,
            @JsonProperty("bitmain-voltage") String bitmainVoltage,
            @JsonProperty("bitmain-ccdelay") String bitmainCcDelay,
            @JsonProperty("bitmain-pwth") String bitmainPwth,
            @JsonProperty("bitmain-work-mode") String bitmainWorkMode,
            @JsonProperty("bitmain-freq-level") String bitmainFreqLevel
    ) {
        /**
         * Represents a single pool entry in the miner configuration.
         *
         * @param url  The Stratum URL of the mining pool.
         * @param user The worker name (e.g., "username.worker1").
         * @param pass The worker password (often empty or "x").
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record PoolConfig(
                @JsonProperty("url") String url,
                @JsonProperty("user") String user,
                @JsonProperty("pass") String pass
        ) {
        }
    }

    /**
     * Response mapped from {@link AntminerCGIEndpoint#POOL_INFO}.
     * Provides live statistics about the miner's connections to its configured pools.
     *
     * @param statusObj Standard API status header.
     * @param infoObj   Standard API info header.
     * @param pools     A list containing live connection and share statistics for each pool.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PoolInfoResponse(
            @JsonProperty("STATUS") ApiStatus statusObj,
            @JsonProperty("INFO") ApiInfo infoObj,
            @JsonProperty("POOLS") List<PoolDetail> pools
    ) {
        /**
         * Live statistics for a specific mining pool connection.
         *
         * @param index     The priority index of the pool (0 = primary, 1 = secondary, etc.).
         * @param url       The Stratum URL of the pool.
         * @param user      The worker name used to connect.
         * @param status    The connection status (e.g., "Alive", "Dead", "Disabled").
         * @param priority  The pool priority sequence.
         * @param getworks  The number of jobs (works) received from the pool.
         * @param accepted  The number of valid shares accepted by the pool.
         * @param rejected  The number of invalid shares rejected by the pool.
         * @param discarded The number of shares discarded locally by the miner.
         * @param stale     The number of stale shares submitted (submitted too late).
         * @param diff      The current requested difficulty from the pool.
         * @param diff1     Shares evaluated at difficulty 1.
         * @param diffa     Total accepted difficulty shares.
         * @param diffr     Total rejected difficulty shares.
         * @param diffs     Total stale difficulty shares.
         * @param lsdiff    Last share difficulty.
         * @param lstime    Time since the last valid share was submitted.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record PoolDetail(
                @JsonProperty("index") int index,
                @JsonProperty("url") String url,
                @JsonProperty("user") String user,
                @JsonProperty("status") String status,
                @JsonProperty("priority") int priority,
                @JsonProperty("getworks") int getworks,
                @JsonProperty("accepted") int accepted,
                @JsonProperty("rejected") int rejected,
                @JsonProperty("discarded") int discarded,
                @JsonProperty("stale") int stale,
                @JsonProperty("diff") String diff,
                @JsonProperty("diff1") long diff1,
                @JsonProperty("diffa") long diffa,
                @JsonProperty("diffr") long diffr,
                @JsonProperty("diffs") long diffs,
                @JsonProperty("lsdiff") long lsdiff,
                @JsonProperty("lstime") String lstime
        ) {
        }
    }

    /**
     * Response mapped from {@link AntminerCGIEndpoint#SUMMARY}.
     * Provides a high-level summary of the miner's overall hashing performance.
     *
     * @param statusObj Standard API status header.
     * @param infoObj   Standard API info header.
     * @param summary   A list (usually containing one item) with global hashing statistics.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SummaryResponse(
            @JsonProperty("STATUS") ApiStatus statusObj,
            @JsonProperty("INFO") ApiInfo infoObj,
            @JsonProperty("SUMMARY") List<SummaryDetail> summary
    ) {
        /**
         * Global hashing statistics.
         *
         * @param elapsed   Time in seconds since the miner process started.
         * @param rate5s    Average hashrate over the last 5 seconds.
         * @param rate30m   Average hashrate over the last 30 minutes.
         * @param rateAvg   Overall average hashrate since boot.
         * @param rateIdeal The theoretical maximum hashrate based on current frequency settings.
         * @param rateUnit  The unit of hashrate measurement (e.g., "GH/s").
         * @param hwAll     Total number of hardware errors across all hashboards.
         * @param bestShare The highest difficulty share found during this session.
         * @param status    System status flags for different components (network, fans, temp).
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record SummaryDetail(
                @JsonProperty("elapsed") long elapsed,
                @JsonProperty("rate_5s") double rate5s,
                @JsonProperty("rate_30m") double rate30m,
                @JsonProperty("rate_avg") double rateAvg,
                @JsonProperty("rate_ideal") double rateIdeal,
                @JsonProperty("rate_unit") String rateUnit,
                @JsonProperty("hw_all") int hwAll,
                @JsonProperty("bestshare") long bestShare,
                @JsonProperty("status") List<DeviceStatus> status
        ) {
        }

        /**
         * General status indicators for miner subsystems.
         *
         * @param type   The subsystem being reported (e.g., "rate", "network", "fans", "temp").
         * @param status The status character ("s" for success/ok, "e" for error).
         * @param code   Internal status code (0 usually means OK, -1 means error).
         * @param msg    Human-readable description of the status (e.g., "Can not connect to pool").
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record DeviceStatus(
                @JsonProperty("type") String type,
                @JsonProperty("status") String status,
                @JsonProperty("code") int code,
                @JsonProperty("msg") String msg
        ) {
        }
    }

    /**
     * Response mapped from {@link AntminerCGIEndpoint#STATS}.
     * Provides granular, low-level data covering individual hashboards, ASICs, fans, and temperatures.
     *
     * @param statusObj Standard API status header.
     * @param infoObj   Standard API info header.
     * @param stats     A list (usually containing one item) with detailed hardware stats.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatsResponse(
            @JsonProperty("STATUS") ApiStatus statusObj,
            @JsonProperty("INFO") ApiInfo infoObj,
            @JsonProperty("STATS") List<StatsDetail> stats
    ) {
        /**
         * Detailed hardware statistics for the entire machine.
         *
         * @param elapsed   Time in seconds since the miner process started.
         * @param rate5s    Total hashrate over the last 5 seconds.
         * @param rate30m   Total hashrate over the last 30 minutes.
         * @param rateAvg   Overall average hashrate since boot.
         * @param rateIdeal Theoretical maximum hashrate.
         * @param rateUnit  The unit of hashrate (e.g., "GH/s").
         * @param chainNum  The number of detected hashboards (chains).
         * @param fanNum    The number of detected cooling fans.
         * @param fans      A list containing the current RPM (Revolutions Per Minute) of each fan.
         * @param hwpTotal  Total hardware error percentage across all boards.
         * @param minerMode The current operating mode.
         * @param freqLevel The target frequency level.
         * @param chains    Detailed statistics for each individual hashboard (chain).
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record StatsDetail(
                @JsonProperty("elapsed") long elapsed,
                @JsonProperty("rate_5s") double rate5s,
                @JsonProperty("rate_30m") double rate30m,
                @JsonProperty("rate_avg") double rateAvg,
                @JsonProperty("rate_ideal") double rateIdeal,
                @JsonProperty("rate_unit") String rateUnit,
                @JsonProperty("chain_num") int chainNum,
                @JsonProperty("fan_num") int fanNum,
                @JsonProperty("fan") List<Integer> fans,
                @JsonProperty("hwp_total") double hwpTotal,
                @JsonProperty("miner-mode") int minerMode,
                @JsonProperty("freq-level") int freqLevel,
                @JsonProperty("chain") List<ChainDetail> chains
        ) {
        }

        /**
         * Detailed statistics for a single hashboard (chain).
         *
         * @param index        The index/slot of the hashboard (e.g., 0, 1, 2).
         * @param freqAvg      The average running frequency of the ASICs on this board.
         * @param rateIdeal    Theoretical hashrate for this specific board.
         * @param rateReal     Actual hashrate produced by this specific board.
         * @param asicNum      Number of detected working ASIC chips on this board.
         * @param asic         A string map representing ASIC status (often 'O' for good, 'X' for bad).
         * @param tempPic      Temperatures reported by the PIC microcontroller on the board.
         * @param tempPcb      Temperatures reported by sensors on the Printed Circuit Board (exhaust temp).
         * @param tempChip     Temperatures reported inside the ASIC chips themselves.
         * @param hw           Hardware errors specific to this board.
         * @param eepromLoaded True if the board's EEPROM data was read successfully.
         * @param sn           The serial number of this specific hashboard.
         * @param hwp          Hardware error percentage for this specific board.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record ChainDetail(
                @JsonProperty("index") int index,
                @JsonProperty("freq_avg") double freqAvg,
                @JsonProperty("rate_ideal") double rateIdeal,
                @JsonProperty("rate_real") double rateReal,
                @JsonProperty("asic_num") int asicNum,
                @JsonProperty("asic") String asic,
                @JsonProperty("temp_pic") List<Integer> tempPic,
                @JsonProperty("temp_pcb") List<Integer> tempPcb,
                @JsonProperty("temp_chip") List<Integer> tempChip,
                @JsonProperty("hw") int hw,
                @JsonProperty("eeprom_loaded") boolean eepromLoaded,
                @JsonProperty("sn") String sn,
                @JsonProperty("hwp") double hwp
        ) {
        }
    }

    /**
     * Request payload for setting network configuration.
     * Endpoint is usually a dedicated network config script (e.g., `cgi-bin/set_network_conf.cgi`).
     *
     * @param ipHost    The hostname to assign to the miner.
     * @param ipPro     The IP protocol mode (e.g., 1 for DHCP, 2 for Static).
     * @param ipAddress The static IP address to assign (leave empty if DHCP).
     * @param ipSub     The static subnet mask (leave empty if DHCP).
     * @param ipGateway The static default gateway (leave empty if DHCP).
     * @param ipDns     The static DNS server(s) (leave empty if DHCP).
     */
    public record SetNetworkConfigRequest(
            @JsonProperty("ipHost") String ipHost,
            @JsonProperty("ipPro") int ipPro,
            @JsonProperty("ipAddress") String ipAddress,
            @JsonProperty("ipSub") String ipSub,
            @JsonProperty("ipGateway") String ipGateway,
            @JsonProperty("ipDns") String ipDns
    ) {
    }

    /**
     * Request payload for updating the miner's configuration via {@link AntminerCGIEndpoint#SET_MINER_CONFIG}.
     *
     * @param bitmainFanCtrl Enables/Disables automatic fan control.
     * @param bitmainFanPwm  Sets the manual fan speed percentage (if auto-control is disabled).
     * @param minerMode      Sets the operating mode (e.g., normal, sleep, low power).
     * @param freqLevel      Sets the target frequency/performance tier.
     * @param pools          The list of 3 mining pools to apply.
     */
    public record SetMinerConfigRequest(
            @JsonProperty("bitmain-fan-ctrl") boolean bitmainFanCtrl,
            @JsonProperty("bitmain-fan-pwm") String bitmainFanPwm,
            @JsonProperty("miner-mode") String minerMode,
            @JsonProperty("freq-level") String freqLevel,
            @JsonProperty("pools") List<Pool> pools
    ) {
        /**
         * Represents a pool to be saved in the configuration.
         *
         * @param url  The Stratum URL.
         * @param user The worker username.
         * @param pass The worker password.
         */
        public record Pool(
                @JsonProperty("url") String url,
                @JsonProperty("user") String user,
                @JsonProperty("pass") String pass
        ) {
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private boolean bitmainFanCtrl = true;
            private String bitmainFanPwm = "20";
            private String minerMode = "0";
            private String freqLevel = "100";
            private final List<Pool> pools = new ArrayList<>();

            public Builder fanControl(boolean autoControl, int manualPwmPercent) {
                this.bitmainFanCtrl = autoControl;
                this.bitmainFanPwm = String.valueOf(manualPwmPercent);
                return this;
            }

            public Builder sleepMode(boolean sleep) {
                this.minerMode = sleep ? "1" : "0";
                return this;
            }

            public Builder addPool(String url, String user, String pass) {
                if (this.pools.size() < 3) {
                    this.pools.add(new Pool(url, user, pass));
                }
                return this;
            }

            public SetMinerConfigRequest build() {
                List<Pool> paddedPools = new ArrayList<>(this.pools);
                while (paddedPools.size() < 3) {
                    paddedPools.add(new Pool("", "", ""));
                }

                return new SetMinerConfigRequest(
                        bitmainFanCtrl,
                        bitmainFanPwm,
                        minerMode,
                        freqLevel,
                        paddedPools
                );
            }
        }
    }

    /**
     * Request payload for changing the administrative password of the web interface.
     * Endpoint is typically an admin/password CGI script.
     *
     * @param currentPassword The current active password used to authenticate.
     * @param newPassword     The new desired password.
     * @param confirmPassword Confirmation of the new desired password (must match newPassword).
     */
    public record PasswordRequest(
            @JsonProperty("curPwd") String currentPassword,
            @JsonProperty("newPwd") String newPassword,
            @JsonProperty("confirmPwd") String confirmPassword
    ) {
    }
}