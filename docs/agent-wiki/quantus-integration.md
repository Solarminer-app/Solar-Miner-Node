# Quantus (QTC) GPU integration record

Status: **the PC-Agent has no Quantus-specific local rollout gate; start remains conditioned on a live valid fee target; no production support claim** (2026-10-07).

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

The quantusminer.com native WebSocket and Quantus node authenticated QUIC paths are not adapted to this listener. Kryptex documents SRBMiner-MULTI and port 7049. A bounded 2026-10-06 diagnostic confirmed the pool uses `login` with named params and returns `result.id`, `status:OK` and an embedded object job with `job_id`, `mining_hash`, `target`, `extranonce` and `difficulty`. The old Ethereum-like adapter was replaced by a dedicated `QuantusStratumProtocol`; its named `submit` and fee-session routing have fixture coverage, and local SRBMiner consoles now report accepted shares. Pool-side credit remains unverified. See the [repair record](../../../solarminer-stratum-proxy/docs/agent-wiki/gpu-stratum-repair-2026-10-06.md).

On 2026-10-07, local SRBMiner 3.7.1 QTC logs showed real jobs and miner-reported accepted shares but also `PARSE error: Quantus notification has no job object` followed by reconnects. The sibling proxy sent subsequent `job` notifications with fields directly under `params`; SRBMiner requires `params.job` and optional `params.clean_jobs`. The proxy now emits that nested notification while retaining the direct `result.job` login shape. The updated proxy is included in the rebuilt standalone PC-Agent JAR; sustained runtime, proxy fee switching and pool-side share credit remain to be verified.

## Implemented source path

- Currency Service uses CoinGecko's `quantus` ID and QTCScan `/explorer-data.json`. It reads schema 1, difficulty, one-hour estimated network hashrate in H/s, target block seconds and block reward already in QTC. Provider `updated_at` is retained as the snapshot time and becomes stale after three minutes. Missing values reject the refresh and preserve the previous complete row.
- Proxy registers `quantusStratumProtocol` and a configured port `3339`; PC-Agent standalone proxy startup configures the same port.
- PC-Agent GPU worker, forecast, software catalog, console, proxy reachability, and fee-readiness paths recognize `quantus`. The managed process uses SRBMiner and cannot start unless its SolarMiner proxy route and house fee target are ready. The operator supplied the SolarMiner QTC payout address; fee-backend now has a local/default house target for Kryptex at `qtc.kryptex.network:7049`, worker suffix `/solarminer`, and the standard 2.5% share. There is no Quantus-specific local feature flag or catalog/start/configuration block: `ProxyConfigurationService.feeReady("quantus")` determines whether a valid target is available at start time.
- PC-Agent reads SRBMiner's current `hashrate.1m` as a fallback when `hashrate.gpu.total` is zero, while retaining the legacy `1min` fallback. One missing/zero API sample no longer blanks Dashboard and Worker immediately: the last positive value is held for at most 20 seconds for the same running process. This display smoothing does not extend the raw-hashrate health timeout. The exact intermittent QTC API payload still needs capture on a running miner.

## Gates and non-applicable scope

- **Operational prerequisite:** the QTC house target must be deployed and returned through the configured proxy. The PC-Agent fails closed when that live target is absent, invalid, or unreachable; no local setting can override this. The Admin portal now stores/masks per-referral QTC wallets and can form a Kryptex `wallet/worker` target, but its production routing flag defaults to false. Real user/house/referral shares and pool credits remain unverified.
- External proxy deployments must publish TCP `3339` and permit it through the host/network firewall; Dockerfile `EXPOSE` is metadata only. This repository has no proxy Compose/firewall configuration to update.
- A live Kryptex login/embedded-job response was observed without any share submission or real payout address. No real GPU start, submit capture, accepted user/house/referral share, pool credit or payout has been verified. Login success is not proof of address validation or mining support.
- No Quantus browser miner or Quantus-native WebSocket pool adapter is applicable to this Stratum route; those are different transports and miner protocols.
- Solar-Miner-Node's ASIC/Braiins control, integrated wallet, Lightning, and BTC payment paths are not applicable to the standalone PC-Agent QTC mining/pool-payout path. Node-side public profitability display, portal referral provisioning and rollout still need explicit integration work before a cross-product support claim.
- Hardware-specific optimization checklist, current miner binary version/release digest and platform matrix remain unverified. The existing SRBMiner installer is shared because this is the same executable package, but QTC-specific version support and local API fields need capture.

## Verification and rollback

The 2026-10-06 protocol repair runs full proxy/PC-Agent regression suites and builds the standalone PC-Agent with its updated embedded proxy (see work log). It adds a process-scoped loopback relay supplying the route before the miner's handshake; external proxy mode requires both components to be upgraded together. No existing pool/wallet settings are rewritten. Before claiming operational support, record real accepted user/house/referral shares, credits, nonce changes, start/stop and one-GPU exclusivity on each claimed OS/GPU family.

## Sources

- [Quantus mining guide](https://docs.quantus.com/docs/guides/mining/) and [official external miner protocol](https://docs.quantus.com/docs/deep-dives/miner-protocol/): QPoW and the native QUIC mining interface.
- [Kryptex QTC setup](https://pool.kryptex.com/en/articles/how-to-mine-quantus-en) and [live QTC pool page](https://pool.kryptex.com/qtc): SRBMiner command, Stratum endpoint, supported GPU families and pool-specific configuration.
- [QTCScan public API](https://qtcscan.com/api/) and [`explorer-data.json`](https://qtcscan.com/explorer-data.json): schema, units, timestamps and estimated network-hashrate method.
- [CoinGecko Quantus listing](https://www.coingecko.com/en/coins/quantus): API ID `quantus`.
