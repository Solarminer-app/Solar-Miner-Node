package de.verdox.pv_miner.core.miner.braiins.graphql;

public enum BraiinsQuery {

    LOGIN("auth/login.graphql"), LOGOUT("auth/logout.graphql"),

    VERSION("miner/version.graphql"),

    STATUS("miner/status.graphql"), START("miner/start.graphql"), STOP("miner/stop.graphql"), RESTART("miner/restart.graphql"), PAUSE("miner/pause.graphql"), RESUME("miner/resume.graphql"),

    GET_POWER_TARGET("power/get-power-target.graphql"), SET_POWER_TARGET("power/set-power-target.graphql"),

    GET_POOLS("pool/get-pools.graphql"), SET_POOL("pool/set-pool.graphql");

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
