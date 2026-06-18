package de.verdox.pv_miner.entity;

import java.util.UUID;

public interface ControllableEntity<C extends EntityController> {
    UUID getId();
}
