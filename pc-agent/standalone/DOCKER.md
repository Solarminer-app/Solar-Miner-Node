# SolarMiner PC-Agent container for Linux

`verdox/solar-miner-pc-agent` always includes the Stratum proxy. In the local
Mining UI choose whether to use that local proxy or a reachable external
SolarMiner proxy. The choice is stored under the persistent data volume and
switching mode pauses active miners first. The local proxy binds only inside the
container, so it is not exposed to the LAN.

The image contains no miner and does not mine automatically. Install XMRig or
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

The agent UI on port `8084` must not be exposed to the public internet. When
using an external proxy, enter its LAN host/IP in the UI; Docker bridge networks
do not forward LAN UDP discovery broadcasts. The retained
`docker-compose.pc-agent.standalone.yml` is a host-network compatibility
profile for existing deployments; it uses the same image and the same UI mode
selection.
