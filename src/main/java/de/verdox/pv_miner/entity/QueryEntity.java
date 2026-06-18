package de.verdox.pv_miner.entity;

import de.verdox.pv_miner.influx.QueryResult;

import java.util.UUID;

/**
 * Query entities represent entities that hold information for queries that produce values about this entity.
 *
 * @param <Q> The Query Result type
 */
public interface QueryEntity<Q extends QueryResult> {
    UUID getId();
}
