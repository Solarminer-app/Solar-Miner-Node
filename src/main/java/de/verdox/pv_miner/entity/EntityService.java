package de.verdox.pv_miner.entity;

import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerRepository;
import de.verdox.pv_miner.miner.data.MinerStats;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.miningpool.MiningPoolRepository;
import de.verdox.pv_miner.pvsite.*;
import de.verdox.pv_miner.pvsite.battery.BatteryDataDTO;
import de.verdox.pv_miner.pvsite.battery.BatteryEntity;
import de.verdox.pv_miner.pvsite.battery.BatteryEntityRepository;
import de.verdox.pv_miner.pvsite.inverter.InverterDataDTO;
import de.verdox.pv_miner.pvsite.inverter.InverterEntity;
import de.verdox.pv_miner.pvsite.inverter.InverterEntityRepository;
import de.verdox.pv_miner.pvsite.panels.PVPanels;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterDataDTO;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterEntity;
import de.verdox.pv_miner.pvsite.smartmeter.SmartMeterRepository;
import de.verdox.pv_miner.statistic.daily.DailyStatisticService;
import de.verdox.pv_miner.statistic.live.EntityStatisticsService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class EntityService {
    private static final Logger LOGGER = Logger.getLogger(EntityService.class.getSimpleName());
    public static final int PV_SITE_LIMIT = 1;

    private final PVSiteRepository pvSiteRepository;
    private final MinerRepository minerRepository;
    private final MiningPoolRepository miningPoolRepository;
    private final EntityMonitoringService entityMonitoringService;
    private final EntityStatisticsService entityStatisticsService;
    private final PVPanelsRepository pVPanelsRepository;
    private final DailyStatisticService dailyStatisticService;
    private final MinerClusterService minerClusterService;
    private final InverterEntityRepository inverterEntityRepository;
    private final BatteryEntityRepository batteryEntityRepository;
    private final SmartMeterRepository smartMeterRepository;

    public EntityService(PVSiteRepository pvSiteRepository, MinerRepository minerRepository, MiningPoolRepository miningPoolRepository, EntityMonitoringService entityMonitoringService, EntityStatisticsService entityStatisticsService, PVPanelsRepository pVPanelsRepository, DailyStatisticService dailyStatisticService, MinerClusterService minerClusterService, InverterEntityRepository inverterEntityRepository, BatteryEntityRepository batteryEntityRepository, SmartMeterRepository smartMeterRepository) {
        this.pvSiteRepository = pvSiteRepository;
        this.minerRepository = minerRepository;
        this.miningPoolRepository = miningPoolRepository;
        this.entityMonitoringService = entityMonitoringService;
        this.entityStatisticsService = entityStatisticsService;
        this.pVPanelsRepository = pVPanelsRepository;
        this.dailyStatisticService = dailyStatisticService;
        this.minerClusterService = minerClusterService;
        this.inverterEntityRepository = inverterEntityRepository;
        this.batteryEntityRepository = batteryEntityRepository;
        this.smartMeterRepository = smartMeterRepository;
    }

    public PVSiteRef pvSiteRef(UUID uuid) {
        return new PVSiteRef(uuid, pvSiteRepository);
    }

    public List<MinerEntity<?>> getFreshList(Collection<UUID> miners) {
        return minerRepository.findAllById(miners).stream().toList();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleClusterStart() {
        CompletableFuture.delayedExecutor(60, TimeUnit.SECONDS).execute(() -> {
            try {
                LOGGER.info("Clusters are starting now!");
                for (PVSiteEntity pvSiteEntity : pvSiteRepository.findAll()) {
                    var ref = pvSiteRef(pvSiteEntity.getId());
                    minerClusterService.startClustersForSite(ref);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @PostConstruct
    private void init() {
        LOGGER.info("Loading all pv sites");
        for (PVSiteEntity pvSiteEntity : pvSiteRepository.findAll()) {
            entityMonitoringService.attach(pvSiteEntity, PVSiteDataDTO.createDefault());
            for (BatteryEntity battery : pvSiteEntity.getBatteries()) {
                entityMonitoringService.attach(battery, BatteryDataDTO.DEFAULT);
            }
            for (InverterEntity inverter : pvSiteEntity.getInverters()) {
                entityMonitoringService.attach(inverter, InverterDataDTO.DEFAULT);
            }
            for (SmartMeterEntity smartMeter : pvSiteEntity.getSmartMeters()) {
                entityMonitoringService.attach(smartMeter, SmartMeterDataDTO.DEFAULT);
            }

            LOGGER.info("Loaded: " + pvSiteEntity.getName());
            LOGGER.info("Loading miners for " + pvSiteEntity.getName());
            for (MinerEntity<?> miner : pvSiteEntity.getMiners()) {
                entityMonitoringService.attach(miner, MinerStats.DEFAULT.withName(miner.getName()));
                LOGGER.info("Starting controller for: " + miner.getName());
                minerClusterService.loginToCluster(pvSiteEntity, miner);
            }
            LOGGER.info("Mining clusters will start in 1minute. We first have to gather some data...");
        }

        LOGGER.info("Loading all connected mining pools");
        for (MiningPoolEntity<?> miningPoolEntity : miningPoolRepository.findAll()) {
            entityMonitoringService.attach(miningPoolEntity, null);
        }

        LOGGER.info("Cleaning database...");
        for (MinerEntity<?> minerEntity : minerRepository.findAll()) {
            if (minerEntity.getParentEntity() == null) {
                LOGGER.log(Level.INFO, "Deleting minerEntity " + minerEntity.getId() + " that has no parent.");
                minerRepository.delete(minerEntity);
            }
        }

        for (MiningPoolEntity<?> miningPoolEntity : miningPoolRepository.findAll()) {
            if (miningPoolEntity.getParentEntity() == null) {
                LOGGER.log(Level.INFO, "Deleting mining pool entity " + miningPoolEntity.getId() + " that has no parent.");
                miningPoolRepository.delete(miningPoolEntity);
            }
        }
        LOGGER.info("Done!");

        if (pvSiteRepository.count() > EntityService.PV_SITE_LIMIT) {
            throw new IllegalStateException("This program allows only one pv site to be managed at a time. ");
        }
    }

    public PVSiteEntity save(PVSiteEntity entity) {
        var saved = pvSiteRepository.save(entity);
        entityMonitoringService.attach(entity, PVSiteDataDTO.createDefault());
        return saved;
    }

    public InverterEntity save(InverterEntity entity) {
        var saved = inverterEntityRepository.save(entity);
        entityMonitoringService.attach(entity, InverterDataDTO.DEFAULT);
        return saved;
    }

    public BatteryEntity save(BatteryEntity entity) {
        var saved = batteryEntityRepository.save(entity);
        entityMonitoringService.attach(entity, BatteryDataDTO.DEFAULT);
        return saved;
    }

    public SmartMeterEntity save(SmartMeterEntity entity) {
        var saved = smartMeterRepository.save(entity);
        entityMonitoringService.attach(entity, SmartMeterDataDTO.DEFAULT);
        return saved;
    }

    public PVPanels save(PVSiteEntity parentEntity, PVPanels entity) {
        var panels = pVPanelsRepository.save(entity);
        parentEntity.getPvPanels().add(entity);
        save(parentEntity);
        return panels;
    }

    public MiningPoolEntity<?> save(MiningPoolEntity<?> entity, PVSiteEntity parent) {
        if (parent != null) {
            entity.setParentEntity(parent);
            parent.getConnectedMiningPools().add(entity);
        }
        var saved = miningPoolRepository.save(entity);
        entityMonitoringService.attach(entity, null);
        if (parent != null) {
            pvSiteRepository.save(parent);
        }
        return saved;
    }

    public MinerEntity<?> save(MinerEntity<?> miner, PVSiteEntity parent) {
        if (parent != null) {
            miner.setParentEntity(parent);
            parent.getMiners().add(miner);
        }

        minerClusterService.loginToCluster(parent, miner);

        var saved = minerRepository.save(miner);
        if (parent != null) {
            pvSiteRepository.save(parent);
        }
        entityMonitoringService.attach(miner, MinerStats.DEFAULT.withName(miner.getName()));
        return saved;
    }

    public void delete(MiningPoolEntity<?> entity) {
        entity.getParentEntity().getConnectedMiningPools().remove(entity);
        pvSiteRepository.save(entity.getParentEntity());
        entity.setParentEntity(null);
        entityMonitoringService.detach(entity);
        miningPoolRepository.delete(entity);
        dailyStatisticService.cleanUpEntity(entity.getId());
    }

    public void delete(MinerEntity<?> miner) {
        minerClusterService.logoutFromCluster(miner.getParentEntity().getId(), miner);
        miner.getParentEntity().getMiners().remove(miner);
        pvSiteRepository.save(miner.getParentEntity());
        miner.setParentEntity(null);
        entityMonitoringService.detach(miner);
        dailyStatisticService.cleanUpEntity(miner.getId());
        minerRepository.delete(miner);
    }

    public void delete(PVSiteEntity entity) {
        for (MinerEntity<?> miner : entity.getMiners()) {
            delete(miner);
        }
        entityMonitoringService.detach(entity);
        dailyStatisticService.cleanUpEntity(entity.getId());
        pvSiteRepository.delete(entity);
        entityStatisticsService.cleanUp(entity);
    }

    public void delete(PVPanels entity) {
        var parent = entity.getParentSite();
        parent.getPvPanels().remove(entity);
        pvSiteRepository.save(parent);
        pVPanelsRepository.delete(entity);
    }
}
