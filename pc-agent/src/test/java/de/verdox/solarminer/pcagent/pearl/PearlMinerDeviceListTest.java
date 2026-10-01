package de.verdox.solarminer.pcagent.pearl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PearlMinerDeviceListTest {
    @Test
    void mapsColoredCudaListingFromLinuxPseudoTerminal() {
        String output = "\u001b[0m\u001b[1;32mGPU0\u001b[0m  \u001b[1;33m[CUDA][0] [0000:01:00.0] : nvidia_titan_rtx\u001b[0m\r\n"
                + "\u001b[0m\u001b[1;32mGPU1\u001b[0m  \u001b[1;33m[CUDA][1] [0000:21:00.0] : nvidia_titan_rtx\u001b[0m\r\n"
                + "\u001b[0m\u001b[1;32mGPU2\u001b[0m  \u001b[1;33m[CUDA][2] [0000:44:00.0] : nvidia_titan_rtx\u001b[0m\r\n"
                + "\u001b[0m\u001b[1;32mGPU3\u001b[0m  \u001b[1;33m[CUDA][3] [0000:62:00.0] : nvidia_titan_rtx\u001b[0m\r\n";

        for (int index = 0; index < 4; index++)
            assertEquals(index, PearlMinerService.findGpuId(output, "NVIDIA", index));
    }

    @Test
    void keepsDisabledDeviceExcluded() {
        assertEquals(-1, PearlMinerService.findGpuId(
                "GPU1  [CUDA][0] : nvidia_titan_rtx disabled by default", "NVIDIA", 0));
    }
}
