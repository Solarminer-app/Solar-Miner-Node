package de.verdox.pv_miner.pvsite;

import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.data.MinerStats;

import java.util.function.Consumer;

public interface PVSiteQueryStrategy<PV_SITE_TYPE extends PVSiteEntity> extends EntityQueryService.Strategy<PV_SITE_TYPE, PVSiteDataDTO> {

    default double approximateMiningPowerDrawKw(EntityQueryService entityQueryService, PV_SITE_TYPE pvSiteType) {
        double cumulated = 0;
        for (MinerEntity<?> miner : pvSiteType.getMiners()) {
            try {
                var stats = entityQueryService.getLastResult(miner, MinerStats.DEFAULT);
                cumulated += stats.approximatedPowerUsageWatts();
            } catch (Throwable e) {}
        }
        return cumulated / 1000d;
    }

    default PVSiteDataDTO createData(Consumer<PVSiteDataDTO.Builder> builderSupplier) {
        var builder = PVSiteDataDTO.builder();
        builderSupplier.accept(builder);
        return builder.build();
    }
}
