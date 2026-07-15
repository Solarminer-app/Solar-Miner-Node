package de.verdox.pv_miner.setup;

import de.verdox.pv_miner.configfetcher.ConfigFetcherService;
import de.verdox.pv_miner.entity.EntityControllerService;
import de.verdox.pv_miner.entity.EntityQueryService;
import de.verdox.pv_miner.entity.EntityService;
import de.verdox.pv_miner.frontend.setup.MinerSetupStep;
import de.verdox.pv_miner.frontend.setup.PVSetupStep;
import de.verdox.pv_miner.frontend.setup.EconomicsStep;
import de.verdox.pv_miner.miner.MinerApiClient;
import de.verdox.pv_miner.miner.MinerEntity;
import de.verdox.pv_miner.miner.MiningOS;
import de.verdox.pv_miner.miningcontroller.MinerClusterService;
import de.verdox.pv_miner.miningcontroller.MinerControllerConfigStorage;
import de.verdox.pv_miner.miningpool.MiningPoolEntity;
import de.verdox.pv_miner.pvsite.HistoricalPrice;
import de.verdox.pv_miner.pvsite.PVPanels;
import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.util.Money;
import de.verdox.pv_miner_extensions.miner.AgentMinerEntity;
import de.verdox.pv_miner_extensions.miner.AntminerEntity;
import de.verdox.pv_miner_extensions.miner.BraiinsOSAsicMinerEntity;
import de.verdox.pv_miner_extensions.inverter.modbustcp.ModbusPVSite;
import de.verdox.pv_miner_extensions.inverter.modbustcp.ModbusConfigStorage;
import de.verdox.pv_miner_extensions.inverter.rest.RestPVSite;
import de.verdox.pv_miner_extensions.inverter.rest.RestConfigStorage;
import de.verdox.solarminer.modbustcp.ModbusConfigCreatorTemplate;
import de.verdox.solarminer.rest.RestConfigCreatorTemplate;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class WizardSaveService {

    private final EntityService entityService;
    private final MinerClusterService minerClusterService;
    private final MinerApiClient minerApiClient;
    private final EntityQueryService entityQueryService;
    private final EntityControllerService entityControllerService;
    private final ModbusConfigStorage modbusConfigStorage;
    private final ConfigFetcherService configFetcherService;
    private final RestConfigStorage restConfigStorage;

    public WizardSaveService(EntityService entityService, MinerClusterService minerClusterService, MinerApiClient minerApiClient, EntityQueryService entityQueryService, EntityControllerService entityControllerService, ModbusConfigStorage modbusConfigStorage, ConfigFetcherService configFetcherService, RestConfigStorage restConfigStorage) {
        this.entityService = entityService;
        this.minerClusterService = minerClusterService;
        this.minerApiClient = minerApiClient;
        this.entityQueryService = entityQueryService;
        this.entityControllerService = entityControllerService;
        this.modbusConfigStorage = modbusConfigStorage;
        this.configFetcherService = configFetcherService;
        this.restConfigStorage = restConfigStorage;
    }

    @Transactional
    public void saveSetupData(
            Set<PVSetupStep.DiscoveredPVDevice> pvDevices,
            Set<MinerSetupStep.MinerConfigEntry> miners,
            MiningPoolEntity<?> pool,
            EconomicsStep.EconomicsData economicsData,
            List<PVPanels> panelsList) {

        PVSiteEntity savedSite = null;

        for (PVSetupStep.DiscoveredPVDevice device : pvDevices) {

            if ("Modbus-TCP".equals(device.getProtocol())) {
                saveModbusConfigIfTakenFromCache(device);

                ModbusPVSite modbusSite = new ModbusPVSite();
                modbusSite.setName("Modbus PV " + device.getIpAddress());
                modbusSite.setIpAddress(device.getIpAddress());
                modbusSite.setPort(device.getPort());
                modbusSite.setModbusConfigName(device.getSelectedConfigName());
                modbusSite.setSlaveId(device.getModbusSlaveId());

                applyEconomicsDataToSite(modbusSite, economicsData);

                savedSite = entityService.save(modbusSite);
            } else if ("Rest-API".equals(device.getProtocol())) {
                saveRestConfigIfTakenFromCache(device);

                RestPVSite restSite = new RestPVSite();
                restSite.setName("Rest PV " + device.getIpAddress());
                restSite.setHostName(device.getIpAddress());
                restSite.setPort(device.getPort());
                restSite.setRestPVConfigName(device.getSelectedConfigName());
                restSite.setApiToken(device.getRestAPIToken());

                applyEconomicsDataToSite(restSite, economicsData);

                savedSite = entityService.save(restSite);
            }
            break;
        }


        if (savedSite != null && panelsList != null && !panelsList.isEmpty()) {
            for (PVPanels panel : panelsList) {
                panel.setParentEntity(savedSite);
                entityService.save(savedSite, panel);
            }
        }

        if (savedSite != null && pool != null) {
            addPoolToExistingSite(savedSite, pool);
        }
        if (savedSite != null && miners != null && !miners.isEmpty()) {
            saveMinersForSite(savedSite, miners);
        }
    }

    private void saveRestConfigIfTakenFromCache(PVSetupStep.DiscoveredPVDevice device) {
        if (!restConfigStorage.doesConfigExistOnDisk(RestConfigCreatorTemplate.HOME_ASSISTANT_PV, device.getSelectedConfigName())) {
            configFetcherService.getRestPVConfig(device.getSelectedConfigName()).ifPresent(restPVConfig -> {
                try {
                    restConfigStorage.save(RestConfigCreatorTemplate.HOME_ASSISTANT_PV, device.getSelectedConfigName(), restPVConfig);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private void saveModbusConfigIfTakenFromCache(PVSetupStep.DiscoveredPVDevice device) {
        if (!modbusConfigStorage.doesConfigExistOnDisk(ModbusConfigCreatorTemplate.PV_SITE, device.getSelectedConfigName())) {
            configFetcherService.getModbusConfig(device.getSelectedConfigName()).ifPresent(modbusConfig -> {
                try {
                    modbusConfigStorage.save(ModbusConfigCreatorTemplate.PV_SITE, device.getSelectedConfigName(), modbusConfig);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }


    private void applyEconomicsDataToSite(PVSiteEntity site, EconomicsStep.EconomicsData economicsData) {
        if (economicsData == null) {
            site.setSetupDate(LocalDate.now());
            return;
        }

        site.setSetupDate(economicsData.setupDate());
        site.setPvCost(new Money(economicsData.pvCost(), economicsData.currency()));


        HistoricalPrice electricityPrice = new HistoricalPrice(economicsData.setupDate(), new Money(economicsData.electricityPrice(), economicsData.currency()));
        site.getElectricityPriceHistory().add(electricityPrice);


        if (economicsData.feedInTariff() != null && economicsData.feedInTariff() > 0) {
            HistoricalPrice feedInTariff = new HistoricalPrice(economicsData.setupDate(), new Money(economicsData.feedInTariff(), economicsData.currency()));
            site.getFeedInTariffHistory().add(feedInTariff);
        }
    }

    @Transactional
    public void addMinersToExistingSite(PVSiteEntity existingSite, Set<MinerSetupStep.MinerConfigEntry> miners) {
        if (existingSite != null && miners != null && !miners.isEmpty()) {
            saveMinersForSite(existingSite, miners);
        }
    }

    @Transactional
    public void addPoolToExistingSite(PVSiteEntity existingSite, MiningPoolEntity<?> pool) {
        if (existingSite != null && pool != null) {
            entityService.save(pool, existingSite);
        }
    }

    private void saveMinersForSite(PVSiteEntity site, Set<MinerSetupStep.MinerConfigEntry> miners) {
        var firstConnectedMiningPool = site.getConnectedMiningPools().stream().findAny().orElse(null);

        Set<MinerEntity<?>> minersToAssign = new HashSet<>();
        for (MinerSetupStep.MinerConfigEntry entry : miners) {
            var minerInfo = entry.getMinerInfo();

            MinerEntity<?> miner;
            if (minerInfo.os().equals(MiningOS.BRAIINS)) {
                var braiins = new BraiinsOSAsicMinerEntity();
                braiins.setName(minerInfo.model());
                braiins.setHost(minerInfo.ipAddress());
                braiins.setPort(50051);
                braiins.setUsername(entry.getUsername());
                braiins.setPassword(entry.getPassword());
                miner = braiins;
            } else if (minerInfo.os().equals(MiningOS.AGENT)) {
                var agent = new AgentMinerEntity();
                agent.setHost(minerInfo.ipAddress());
                agent.setPort(8084);
                miner = agent;
            }
            else if(minerInfo.os().equals(MiningOS.ANTMINER_STOCK_OS)) {
                var antminer = new AntminerEntity();
                antminer.setName(minerInfo.model());
                antminer.setHost(minerInfo.ipAddress());
                antminer.setPort(80);
                antminer.setUsername(entry.getUsername());
                antminer.setPassword(entry.getPassword());
                miner = antminer;
            }
            else {
                throw new UnsupportedOperationException("OS " + minerInfo.os() + " not supported.");
            }

            try {
                var stats = entityQueryService.query(miner);
                miner.setMinPowerTarget(stats.minPowerTarget() > 0 ? stats.minPowerTarget() : 800);
                miner.setMaxPowerTarget(stats.defaultPowerTarget() > 0 ? stats.defaultPowerTarget() : 1200);
            }
            catch (Throwable ignored) {

            }


            miner.setParentEntity(site);
            minersToAssign.add(miner);
            entityService.save(miner, site);

            if (firstConnectedMiningPool != null) {
                try {
                    var details = entityControllerService.getController(miner).details();
                    var identity = entityQueryService.query(miner).minerIdentity();
                    String defaultWorkerName = entityQueryService.query(firstConnectedMiningPool).getDefaultWorkerName();
                    System.out.println("Setting mining pool to " + firstConnectedMiningPool.getStratumV1Url());
                    minerApiClient.setMiningPoolTarget(
                            minerInfo.os(),
                            details,
                            firstConnectedMiningPool.getStratumV1Url(),
                            defaultWorkerName,
                            identity,
                            site.getReferralCode()
                    );
                    miner.setCurrentMiningPoolTarget(firstConnectedMiningPool.getUrlIdentifier());
                    entityService.save(miner, site);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        var cluster = minerClusterService.getCluster(site.getId(), MinerControllerConfigStorage.STANDARD_CLUSTER_NAME);
        cluster.assignMiners(minersToAssign);
        try {
            minerClusterService.startCluster(MinerControllerConfigStorage.STANDARD_CLUSTER_NAME, entityService.pvSiteRef(site.getId()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
