package de.verdox.pv_miner.miningcontroller;

import de.verdox.pv_miner.entity.EntityControllerService;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MinerRepository;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class MinerClusterService {

    private static final Logger LOGGER = Logger.getLogger(MinerClusterService.class.getSimpleName());

    private final EntityControllerService entityControllerService;
    private final MinerControllerConfigStorage configStorage;

    private final Map<UUID, Map<String, ClusterInstance>> siteClusters = new ConcurrentHashMap<>();

    private final ScheduledExecutorService clusterScheduler = Executors.newScheduledThreadPool(4);
    private final MinerRepository minerRepository;
    private final PVSiteRepository pVSiteRepository;

    @Autowired
    public MinerClusterService(EntityControllerService entityControllerService, MinerControllerConfigStorage configStorage, MinerRepository minerRepository, PVSiteRepository pVSiteRepository) {
        this.minerRepository = minerRepository;
        this.entityControllerService = entityControllerService;
        this.configStorage = configStorage;
        this.pVSiteRepository = pVSiteRepository;
        refreshClustersFromStorage();
    }

    public void startClustersForSite(PVSiteEntity pvSite) throws Exception {
        for (String clusterName : getAvailableClusterNames()) {
            startCluster(clusterName, pvSite);
        }
    }

    public void loginToCluster(PVSiteEntity pvSite, MinerEntity<?> miner) {
        if (pvSite == null || miner == null || miner.getClusterName() == null) {
            return;
        }
        var foundCluster = getCluster(pvSite, miner.getClusterName());
        if (foundCluster == null) {
            miner.setClusterName(null);
        } else {
            foundCluster.assignMiners(Set.of(miner));
        }
    }

    public void logoutFromCluster(PVSiteEntity pvSite, MinerEntity<?> miner) {
        if (pvSite == null || miner == null || miner.getClusterName() == null) {
            return;
        }
        var foundCluster = getCluster(pvSite, miner.getClusterName());
        if (foundCluster != null) {
            foundCluster.removeMiners(Set.of(miner));
        }
    }

    public void refreshClustersFromStorage() {
        try {
            List<String> savedConfigs = configStorage.getNameOfSavedConfigs();
            Iterable<PVSiteEntity> allSites = pVSiteRepository.findAll();

            for (PVSiteEntity site : allSites) {
                siteClusters.putIfAbsent(site.getId(), new ConcurrentHashMap<>());
                for (String configName : savedConfigs) {
                    siteClusters.get(site.getId()).putIfAbsent(configName, new ClusterInstance(configName, site.getId(), clusterScheduler));
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Laden der Cluster-Konfigurationen.", e);
        }
    }

    public List<String> getAvailableClusterNames() {
        try {
            return configStorage.getNameOfSavedConfigs();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Abrufen der Cluster-Namen.", e);
            return new ArrayList<>();
        }
    }

    public ClusterInstance getCluster(PVSiteEntity pvSite, String clusterName) {
        if (pvSite == null || clusterName == null) return null;

        siteClusters.putIfAbsent(pvSite.getId(), new ConcurrentHashMap<>());
        siteClusters.get(pvSite.getId()).putIfAbsent(clusterName, new ClusterInstance(clusterName, pvSite.getId(), clusterScheduler));

        return siteClusters.get(pvSite.getId()).get(clusterName);
    }

    public Set<MinerEntity<?>> getUnassignedMiners(PVSiteEntity pvSite) {
        pvSite = pVSiteRepository.findById(pvSite.getId()).orElseThrow();
        Set<MinerEntity<?>> unassigned = new HashSet<>();
        for (MinerEntity<?> miner : pvSite.getMiners()) {
            if(miner.getClusterName() == null || miner.getClusterName().isBlank()) {
                unassigned.add(miner);
            }
        }
        return unassigned;
    }

    public void startCluster(String clusterName, PVSiteEntity pvSite) throws Exception {
        ClusterInstance cluster = getCluster(pvSite, clusterName);
        if (cluster == null) throw new IllegalArgumentException("Cluster nicht gefunden: " + clusterName);

        MinerControllerConfig latestConfig = configStorage.get(clusterName);
        cluster.start(pvSite, latestConfig);
    }

    public void stopCluster(PVSiteEntity pvSite, String clusterName) {
        ClusterInstance cluster = getCluster(pvSite, clusterName);
        if (cluster != null) {
            cluster.stop();
        }
    }

    public void deleteCluster(String clusterName) {
        for (Map<String, ClusterInstance> siteMap : siteClusters.values()) {
            ClusterInstance cluster = siteMap.get(clusterName);
            if (cluster != null) {
                if (cluster.isRunning()) {
                    cluster.stop();
                }
                Set<MinerEntity<?>> assigned = new HashSet<>(cluster.getAssignedMiners());
                if (!assigned.isEmpty()) {
                    cluster.removeMiners(assigned);
                }
                siteMap.remove(clusterName);
            }
        }

        try {
            configStorage.delete(clusterName);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Löschen der Konfiguration: " + clusterName, e);
        }
    }


    public class ClusterInstance {
        private final String clusterName;
        private final UUID siteId;
        private final ScheduledExecutorService scheduler;

        private final Set<UUID> assignedMinerIds = ConcurrentHashMap.newKeySet();

        private ClusterController activeController;
        private ScheduledFuture<?> scheduledTask;
        private boolean isRunning = false;

        public ClusterInstance(String clusterName, UUID siteId, ScheduledExecutorService scheduler) {
            this.clusterName = clusterName;
            this.siteId = siteId;
            this.scheduler = scheduler;
        }

        public String getClusterName() {
            return clusterName;
        }

        public boolean isRunning() {
            return isRunning;
        }

        public Set<MinerEntity<?>> getAssignedMiners() {
            if (assignedMinerIds.isEmpty()) {
                return Collections.emptySet();
            }
            return new HashSet<>(minerRepository.findAllById(assignedMinerIds));
        }

        public void assignMiners(Set<MinerEntity<?>> miners) {
            if (miners.isEmpty()) return;

            List<UUID> minerIds = miners.stream().map(MinerEntity::getId).toList();
            List<MinerEntity<?>> freshMiners = minerRepository.findAllById(minerIds);

            for (MinerEntity<?> miner : freshMiners) {
                miner.setClusterName(clusterName);
            }
            minerRepository.saveAll(freshMiners);
            assignedMinerIds.addAll(minerIds);

            LOGGER.info("Assigned " + freshMiners.size() + " to cluster " + clusterName + " on site " + siteId);
        }

        public void removeMiners(Set<MinerEntity<?>> miners) {
            if (miners.isEmpty()) return;

            List<UUID> minerIds = miners.stream().map(MinerEntity::getId).toList();

            assignedMinerIds.removeAll(minerIds);

            List<MinerEntity<?>> freshMiners = minerRepository.findAllById(minerIds);

            for (MinerEntity<?> miner : freshMiners) {
                entityControllerService.getController(miner).pauseMining();
                miner.setClusterName(null);
            }

            minerRepository.saveAll(freshMiners);

            LOGGER.info("Removed " + freshMiners.size() + " from cluster " + clusterName + " on site " + siteId);
        }

        public void start(PVSiteEntity pvSite, MinerControllerConfig config) {
            if (isRunning) return;
            if (assignedMinerIds.isEmpty()) {
                LOGGER.warning("Cluster " + clusterName + " hat keine Miner zugewiesen. Start abgebrochen.");
                return;
            }

            List<MinerEntity<?>> currentMiners = minerRepository.findAllById(assignedMinerIds);

            this.activeController = new ClusterController(clusterName, new ArrayList<>(currentMiners), pvSite, config);
            this.scheduledTask = scheduler.scheduleAtFixedRate(
                    activeController::evaluate, 0, 30, TimeUnit.SECONDS
            );
            this.isRunning = true;
            LOGGER.info("Cluster " + clusterName + " Automation gestartet.");
        }

        public void stop() {
            if (!isRunning) return;
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
            }
            List<MinerEntity<?>> currentMiners = minerRepository.findAllById(assignedMinerIds);
            for (MinerEntity<?> miner : currentMiners) {
                entityControllerService.getController(miner).pauseMining();
            }
            this.isRunning = false;
            LOGGER.info("Cluster " + clusterName + " Automation gestoppt.");
        }
    }
}