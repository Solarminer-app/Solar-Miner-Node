package de.verdox.pv_miner.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.core.miner.MiningOS;
import de.verdox.pv_miner.core.miner.agent.MinerAgentController;
import de.verdox.pv_miner.core.miner.antminer.AntminerBackend;
import de.verdox.pv_miner.core.miner.braiins.BraiinsController;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.miner.dto.MinerStats;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
public class MinerService {
    private static final Logger LOGGER = Logger.getLogger(MinerService.class.getName());
    private final MinerAgentController agentController;
    private final BraiinsController braiinsController;
    private final AntminerBackend antminerBackend;
    private final DevFeeService devFeeService;

    public MinerService(DevFeeService devFeeService, ObjectMapper objectMapper) {
        this.devFeeService = devFeeService;
        this.agentController = new MinerAgentController();
        this.antminerBackend = new AntminerBackend(objectMapper);
        this.braiinsController = new BraiinsController(objectMapper);
    }

    public boolean startMining(MiningOS miningOS, MinerDetails details) {
        return switch (miningOS) {
            case AGENT -> agentController.startMining(details);
            case BRAIINS -> braiinsController.startMining(details);
            case ANTMINER_STOCK_OS -> antminerBackend.startMining(details);
            case MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean stopMining(MiningOS miningOS, MinerDetails details) {
        return switch (miningOS) {
            case AGENT -> agentController.stopMining(details);
            case BRAIINS -> braiinsController.stopMining(details);
            case ANTMINER_STOCK_OS -> antminerBackend.stopMining(details);
            case MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean pauseMining(MiningOS miningOS, MinerDetails details) {
        return switch (miningOS) {
            case AGENT -> agentController.pauseMining(details);
            case BRAIINS -> braiinsController.pauseMining(details);
            case ANTMINER_STOCK_OS -> antminerBackend.pauseMining(details);
            case MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean resumeMining(MiningOS miningOS, MinerDetails details) {
        return switch (miningOS) {
            case AGENT -> agentController.resumeMining(details);
            case BRAIINS -> braiinsController.resumeMining(details);
            case ANTMINER_STOCK_OS -> antminerBackend.resumeMining(details);
            case MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean setPoolTarget(MiningOS miningOS, MinerDetails details, String stratumUrl, String userName) {
        return switch (miningOS) {
            case AGENT -> agentController.setPoolTarget(details, stratumUrl, userName);
            case BRAIINS -> braiinsController.setPoolTarget(details, stratumUrl, userName);
            case ANTMINER_STOCK_OS -> antminerBackend.setPoolTarget(details, stratumUrl, userName, true);
            case MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean setPowerTarget(MiningOS miningOS, MinerDetails details, long watts) {
        return switch (miningOS) {
            case AGENT -> agentController.setPowerTarget(details, watts);
            case BRAIINS -> braiinsController.setPowerTarget(details, watts);
            case ANTMINER_STOCK_OS -> antminerBackend.setPowerTarget(details, watts);
            case MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean incrementPowerTarget(MiningOS miningOS, MinerDetails details, long watts) {
        return switch (miningOS) {
            case AGENT -> agentController.incrementPowerTarget(details, watts);
            case BRAIINS -> braiinsController.incrementPowerTarget(details, watts);
            case ANTMINER_STOCK_OS -> antminerBackend.incrementPowerTarget(details, watts);
            case MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean decrementPowerTarget(MiningOS miningOS, MinerDetails details, long watts) {
        return switch (miningOS) {
            case AGENT -> agentController.decrementPowerTarget(details, watts);
            case BRAIINS -> braiinsController.decrementPowerTarget(details, watts);
            case ANTMINER_STOCK_OS -> antminerBackend.decrementPowerTarget(details, watts);
            case MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public MinerStats queryStats(MiningOS miningOS, String minerName, MinerDetails details) {
        var stats = switch (miningOS) {
            case AGENT -> agentController.queryStats(minerName, details);
            case BRAIINS -> braiinsController.queryStats(minerName, details);
            case ANTMINER_STOCK_OS -> antminerBackend.queryStats(minerName, details);
            case MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> null;
        };
        if (stats != null) {
            devFeeService.enforceDevFee(stats.minerIdentity(), this, miningOS, details);
        }
        return stats;
    }

    public boolean isDevFeeSetup(MiningOS miningOS, MinerDetails details, String devFeePool, String devFeeName, double devFeePercentage) {
        return switch (miningOS) {
            case AGENT -> agentController.isDevFeeSetup(details, devFeePool, devFeeName, devFeePercentage);
            case BRAIINS -> braiinsController.isDevFeeSetup(details, devFeePool, devFeeName, devFeePercentage);
            case ANTMINER_STOCK_OS, MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public void setupDevFee(MiningOS miningOS, MinerDetails details, String devFeePool, String devFeeName, double devFeePercentage) {
        switch (miningOS) {
            case AGENT -> agentController.setupDevFee(details, devFeePercentage);
            case BRAIINS -> braiinsController.setupDevFee(details, devFeePool, devFeeName, devFeePercentage);
        }
    }

    public boolean checkIfStandardCredentialsWork(MiningOS miningOS, String minerName, MinerDetails details) {
        return switch (miningOS) {
            case AGENT -> true;
            case BRAIINS -> braiinsController.checkIfStandardCredentialsWork(details);
            case ANTMINER_STOCK_OS -> antminerBackend.checkIfStandardCredentialsWork(details);
            case MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }

    public boolean checkIfCustomCredentialsWork(MiningOS miningOS, String minerName, MinerDetails details) {
        return switch (miningOS) {
            case AGENT -> true;
            case BRAIINS -> braiinsController.checkIfCustomCredentialsWork(details);
            case ANTMINER_STOCK_OS -> antminerBackend.checkIfCustomCredentialsWork(details);
            case MICROBT_STOCK_OS, CANAAN_STOCK_OS, INNOSILICON_STOCK_OS, VNISH,
                 WHATSMINER_STOCK_OS, AVALON_STOCK_OS, LUX_OS, HIVEON_ASIC, HIVE_OS, MS_OS, RAVE_OS, LUXOS -> false;
        };
    }
}
