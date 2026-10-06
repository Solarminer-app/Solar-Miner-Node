# Quantus (QTC) GPU integration record

Status: **fee target configured locally; miner selection/start remain blocked pending end-to-end validation; no production support claim** (2026-10-07).

## Contract and candidate path

| Field | Current decision |
| --- | --- |
| Canonical key / aliases | `quantus`; `qtc` at provider/API boundaries |
| Mining coin / payout | QTC; payout wallet starts with `qz`; no BTC/Lightning conversion |
| Algorithm | QPoW, Poseidon2 over Goldilocks; SRBMiner algorithm name `quantus` |
| Device path | GPU only in PC-Agent; reuses the already installed SRBMiner-MULTI package and GPU process/API adapter |
| Pool candidate | Kryptex QTC pool, `qtc.kryptex.network:7049` (TCP; TLS is supported upstream, but the local miner-to-proxy hop is plain TCP) |
| Proxy listener | QTC Stratum adapter, port `3339` |
| Worker mapping | Agent encodes selected upstream URL; proxy rewrites Kryptex login to `qz…/worker` |
| Miner fee | SRBMiner-MULTI QPoW 2.5%, per Kryptex pool's published miner command guide |
| Pool fee | Kryptex currently publishes changing/inconsistent values across its live UI and guide; do not treat the local transparency estimate as authoritative |
| SolarMiner / referral targets | House target configured in fee-backend source and local runtime config at 2.5%; no referral QTC target configured |

The quantusminer.com native pool exposes a custom WebSocket protocol and its native Quantus node miner uses authenticated QUIC, not Stratum. Those paths are intentionally not adapted to the SolarMiner V1 Stratum listener. Kryptex documents SRBMiner-MULTI with `--algorithm quantus`, QTC wallet and port 7049, so it is the candidate for the proxy adapter. The proxy adapter extends the shared GPU JSON-RPC path (`mining.subscribe`, `mining.authorize`, `mining.set_difficulty`, `mining.notify`, `mining.submit`); actual Kryptex message compatibility is not yet captured.

## Implemented source path

- Currency Service uses CoinGecko's `quantus` ID and QTCScan `/explorer-data.json`. It reads schema 1, difficulty, one-hour estimated network hashrate in H/s, target block seconds and block reward already in QTC. Provider `updated_at` is retained as the snapshot time and becomes stale after three minutes. Missing values reject the refresh and preserve the previous complete row.
- Proxy registers `quantusStratumProtocol` and a configured port `3339`; PC-Agent standalone proxy startup configures the same port.
- PC-Agent GPU worker, forecast, software catalog, console, proxy reachability, and fee-readiness paths recognize `quantus`. The managed process uses SRBMiner and cannot start unless its SolarMiner proxy route and house fee target are ready. The operator supplied the SolarMiner QTC payout address; fee-backend now has a local/default house target for Kryptex at `qtc.kryptex.network:7049`, worker suffix `/solarminer`, and the standard 2.5% share. Catalog selection remains disabled, and a separate `solarminer.quantus.enabled=false` runtime gate prevents direct API calls from starting/configuring it while rollout is pending.

## Gates and non-applicable scope

- **Blocking:** the QTC house target is only in source/default and this workspace's ignored runtime config; the deployed fee service has not been provisioned or reloaded. No referral QTC target exists yet. The portal needs a documented compatible account/worker contract. Agent selection/start remain disabled until the deployed house target is returned through the proxy and real user/house shares and pool credits are verified.
- External proxy deployments must publish TCP `3339` and permit it through the host/network firewall; Dockerfile `EXPOSE` is metadata only. This repository has no proxy Compose/firewall configuration to update.
- No real GPU start, Kryptex subscribe/login/job/submit capture, accepted share, house/referral share, pool credit, or payout has been verified. Generic GPU adapter reuse is not proof of QTC Stratum compatibility.
- No Quantus browser miner or Quantus-native WebSocket pool adapter is applicable to this Stratum route; those are different transports and miner protocols.
- Solar-Miner-Node's ASIC/Braiins control, integrated wallet, Lightning, and BTC payment paths are not applicable to the standalone PC-Agent QTC mining/pool-payout path. Node-side public profitability display, portal referral provisioning and rollout still need explicit integration work before a cross-product support claim.
- Hardware-specific optimization checklist, current miner binary version/release digest and platform matrix remain unverified. The existing SRBMiner installer is shared because this is the same executable package, but QTC-specific version support and local API fields need capture.

## Verification and rollback

No tests or builds were run for this change. The fee gate is designed to keep the route stopped. Rollback consists of removing the QTC catalog/worker exposure and proxy port configuration; no existing coin configuration is rewritten. Before opening the gate, deploy/reload the fee target, test the QTC adapter with a fake pool, then the Kryptex test path, record real accepted shares and house/referral credits, and verify start/stop and one-GPU exclusivity on each claimed OS/GPU family.

## Sources

- [Quantus mining guide](https://docs.quantus.com/docs/guides/mining/) and [official external miner protocol](https://docs.quantus.com/docs/deep-dives/miner-protocol/): QPoW and the native QUIC mining interface.
- [Kryptex QTC setup](https://pool.kryptex.com/en/articles/how-to-mine-quantus-en) and [live QTC pool page](https://pool.kryptex.com/qtc): SRBMiner command, Stratum endpoint, supported GPU families and pool-specific configuration.
- [QTCScan public API](https://qtcscan.com/api/) and [`explorer-data.json`](https://qtcscan.com/explorer-data.json): schema, units, timestamps and estimated network-hashrate method.
- [CoinGecko Quantus listing](https://www.coingecko.com/en/coins/quantus): API ID `quantus`.
