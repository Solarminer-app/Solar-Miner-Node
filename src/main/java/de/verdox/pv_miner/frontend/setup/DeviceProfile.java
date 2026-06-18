package de.verdox.pv_miner.frontend.setup;

import java.util.List;

public record DeviceProfile(String name, List<String> supportedProtocols) {
    @Override
    public String toString() {
        return name + " (" + String.join("/", supportedProtocols) + ")";
    }
}
