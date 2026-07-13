package de.verdox.pv_miner.core.miner.dto;

import java.util.UUID;

public record MinerDetails(UUID id, String ipv4, int port, String username, String password) {
}