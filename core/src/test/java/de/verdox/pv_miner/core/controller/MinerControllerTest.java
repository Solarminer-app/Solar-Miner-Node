package de.verdox.pv_miner.core.controller;

import de.verdox.pv_miner.core.miner.MiningOS;
import de.verdox.pv_miner.core.miner.dto.MinerDetails;
import de.verdox.pv_miner.core.service.MinerDiscoveryService;
import de.verdox.pv_miner.core.service.MinerService;
import de.verdox.pv_miner.core.service.DevFeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinerControllerTest {
    private MinerService minerService;
    private MinerController controller;

    @BeforeEach
    void setUp() {
        minerService = mock(MinerService.class);
        controller = new MinerController(minerService, mock(MinerDiscoveryService.class), mock(DevFeeService.class));
    }

    @Test
    void customCredentialsCanBeCheckedBeforeMinerHasAnId() {
        MinerDetails details = new MinerDetails(null, "192.168.1.42", 80, "root", "secret");
        when(minerService.checkIfCustomCredentialsWork(MiningOS.ANTMINER_STOCK_OS, details)).thenReturn(true);

        assertTrue(controller.checkIfCustomCredentialsWork(details, MiningOS.ANTMINER_STOCK_OS));

        verify(minerService).checkIfCustomCredentialsWork(MiningOS.ANTMINER_STOCK_OS, details);
    }

    @Test
    void standardCredentialsCanBeCheckedBeforeMinerHasAnId() {
        MinerDetails details = new MinerDetails(null, "192.168.1.42", 80, null, null);
        when(minerService.checkIfStandardCredentialsWork(MiningOS.ANTMINER_STOCK_OS, details)).thenReturn(true);

        assertTrue(controller.checkIfStandardCredentialsWork(details, MiningOS.ANTMINER_STOCK_OS));

        verify(minerService).checkIfStandardCredentialsWork(MiningOS.ANTMINER_STOCK_OS, details);
    }
}
