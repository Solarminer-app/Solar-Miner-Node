package de.verdox.pv_miner.pvsite;

import de.verdox.pv_miner.entity.Ref;

import java.util.UUID;

public class PVSiteRef extends Ref<UUID, PVSiteEntity, PVSiteRepository> {
    public PVSiteRef(UUID id, PVSiteRepository repository) {
        super(id, repository);
    }
}
