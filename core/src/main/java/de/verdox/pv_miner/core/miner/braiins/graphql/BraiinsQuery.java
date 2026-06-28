package de.verdox.pv_miner.core.miner.braiins.graphql;

public enum BraiinsQuery {

    LOGIN("auth/login.graphql"), LOGOUT("auth/logout.graphql"),

    IDENTITY("miner/get_identity.graphql"),
    VERSION("miner/version.graphql"),

    STATUS("miner/status.graphql"), START("miner/start.graphql"), STOP("miner/stop.graphql"), RESTART("miner/restart.graphql"), PAUSE("miner/pause.graphql"), RESUME("miner/resume.graphql"),

    GET_POWER_TARGET("power/get-power-target.graphql"), SET_POWER_TARGET("power/set-power-target.graphql"), GET_POWER_TARGET_LIMITS("power/get-power-target-limits.graphql"), GET_TEMPERATURE_LIMITS("power/get-temperature-limits.graphql"),

    GET_HWID("bos/get-hwid.graphql"),

    ADD_POOL_GROUP_RATIO("pool/add-pool-group-ratio.graphql"),
    GET_POOLS("pool/get-pools.graphql"), SET_POOL("pool/set-pool.graphql"),

    GET_POOL_GROUPS("pool/get-pool-groups.graphql"), ADD_POOL_GROUP("pool/add-pool-group.graphql"), UPDATE_POOL_GROUP("pool/update-pool-group.graphql"), UPDATE_POOL_GROUP_QUOTA("pool/update-pool-group-quota.graphql"), REMOVE_POOL_GROUP("pool/remove-pool-group.graphql"),
    ;

    private final String resource;
    private final String query;

    BraiinsQuery(String resource) {
        this.resource = resource;
        this.query = GraphQLQueries.load(resource);
    }

    public String query() {
        return query;
    }

    public String resource() {
        return resource;
    }
}
