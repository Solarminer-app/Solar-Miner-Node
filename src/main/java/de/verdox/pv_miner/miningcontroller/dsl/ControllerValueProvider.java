package de.verdox.pv_miner.miningcontroller.dsl;

import de.verdox.pv_miner.pvsite.PVSiteEntity;

import java.time.ZonedDateTime;

public interface ControllerValueProvider {
    double getValue(String valueId, PVSiteEntity pvSiteEntity, ControllerDSL.ValueAdjustment valueAdjustment);

    default ZonedDateTime getCurrentTime(PVSiteEntity pvSiteEntity) {
        return ZonedDateTime.now(pvSiteEntity.getZoneId());
    }
}
