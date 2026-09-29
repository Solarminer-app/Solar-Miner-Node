package de.verdox.pv_miner.core.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SshGpuServiceTest {
    private final SshGpuService service = new SshGpuService("", "");

    @Test
    void rejectsHostAndUserInjectionBeforeInvokingSsh() {
        assertThrows(IllegalArgumentException.class,
                () -> service.powerLimits(new SshGpuService.Target("rig;touch /tmp/x", "miner", 0), "NVIDIA"));
        assertThrows(IllegalArgumentException.class,
                () -> service.powerLimits(new SshGpuService.Target("rig.example", "miner;id", 0), "AMD"));
        assertThrows(IllegalArgumentException.class,
                () -> service.powerLimits(new SshGpuService.Target("rig.example", "miner", -1), "NVIDIA"));
    }

    @Test
    void startIsClosedWithoutVerifiedFeeRouting() {
        assertThrows(IllegalStateException.class,
                () -> service.setPearlServiceRunning("rig.example", "miner", true));
    }
}
