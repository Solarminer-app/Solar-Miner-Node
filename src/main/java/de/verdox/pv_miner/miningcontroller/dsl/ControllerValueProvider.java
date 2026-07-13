package de.verdox.pv_miner.miningcontroller.dsl;

import de.verdox.pv_miner.pvsite.PVSiteEntity;

public interface ControllerValueProvider {
    double getValue(String valueId, PVSiteEntity pvSiteEntity, ControllerDSL.ValueAdjustment valueAdjustment);
}
