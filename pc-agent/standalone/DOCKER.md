# SolarMiner PC-Agent containers for Linux

Two images are released from the same version tag:

- `verdox/solar-miner-pc-agent` is the normal agent. It has **no embedded
  proxy** and must be configured with a reachable external SolarMiner proxy.
- `verdox/solar-miner-pc-agent-standalone` embeds the local Stratum proxy.

Neither image contains a miner or mines automatically. Install XMRig or
SRBMiner from the local Mining UI after startup; the agent downloads only the
official release and verifies its release metadata.

The image is Linux `amd64` only. This is intentional: the current official
SRBMiner Linux package used for Pearl is x64. Start from this directory:

```sh
sudo sh ./setup-xmr-linux.sh
docker compose -f ../docker-compose.pc-agent.yml up -d
```

The setup script configures persistent 2-MiB huge pages for RandomX and creates
`/opt/solarminer-pc-agent/data`. Run the compose command from
`/opt/solarminer-pc-agent` after copying the compose files there, or replace
`./data` in the base compose file with `/opt/solarminer-pc-agent/data`.

For the standalone image, use `docker-compose.pc-agent.standalone.yml` instead
of the base compose file. It uses host networking because it runs the embedded
proxy and LAN UDP discovery.

For NVIDIA add the NVIDIA overlay after installing NVIDIA Container Toolkit:

```sh
docker compose -f docker-compose.pc-agent.yml -f docker-compose.pc-agent.nvidia.yml up -d
```

For AMD add the AMD overlay. The host needs a working AMDGPU/ROCm OpenCL stack:

```sh
docker compose -f docker-compose.pc-agent.yml -f docker-compose.pc-agent.amd.yml up -d
```

The Intel overlay only exposes `/dev/dri` to the agent. It is useful for host
hardware discovery, but must not be used to claim Pearl mining: SRBMiner's
current PearlHash support matrix lists AMD and NVIDIA, not Intel. A verified
Intel PearlHash miner and end-to-end pool test are required before enabling
that route.

The standalone image exposes port `8084` on the host LAN and must not be
exposed to the public internet. Do not run another standalone agent or proxy on
ports `8084`, `8090`, `3334`, `3335`, or UDP `8091` on the same host.
