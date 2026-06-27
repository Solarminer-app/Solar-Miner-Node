package de.verdox.pv_miner.core.miner.antminer;

import lombok.Getter;

@Getter
public enum AntminerCGIEndpoint {
    BLINK_STATUS("cgi-bin/get_blink_status.cgi"),
    SYSTEM_INFO("cgi-bin/get_system_info.cgi"),
    POOL_INFO("cgi-bin/pools.cgi"),
    STATS("cgi-bin/stats.cgi"),
    SUMMARY("cgi-bin/summary.cgi"),
    GET_MINER_CONFIG("cgi-bin/get_miner_conf.cgi"),
    SET_MINER_CONFIG("cgi-bin/set_miner_conf.cgi"),
    GET_NETWORK_INFO("cgi-bin/get_network_info.cgi"),
    MINER_TYPE("cgi-bin/miner_type.cgi"),
    REBOOT("cgi-bin/reboot.cgi"),
    ;
    private String endpoint;

    AntminerCGIEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

}
