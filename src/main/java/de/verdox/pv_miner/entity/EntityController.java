package de.verdox.pv_miner.entity;

import de.verdox.pv_miner.SpringContextHelper;
import de.verdox.pv_miner.influx.QueryResult;
import lombok.Getter;

@Getter
public abstract class EntityController {
    private final ControllableEntity<?> controllableEntity;

    public EntityController(ControllableEntity<?> controllableEntity) {
        this.controllableEntity = controllableEntity;
    }

    public <Q extends QueryResult, E extends QueryEntity<Q>> Q getLastData() {
        if (!(this instanceof QueryEntity<?>)) {
            return null;
        }
        return SpringContextHelper.getBean(EntityQueryService.class).getLastResult((E) getControllableEntity(), null);
    }
}
