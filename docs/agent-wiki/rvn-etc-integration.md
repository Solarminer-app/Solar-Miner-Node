# RVN and ETC PC-Agent integration record

Status: **PC-Agent prepared; end-to-end mining disabled**, checked 2026-10-03. Follow the workspace [new-coin guide](../../../NEW-MINING-COIN-GUIDE.md) before enabling either coin. No accepted user, house or referral shares have been demonstrated.

| Field | Ravencoin | Ethereum Classic |
| --- | --- | --- |
| Canonical key / ticker | `ravencoin` / `RVN` | `ethereumclassic` / `ETC` |
| Mining algorithm | KAWPOW | ETCHash |
| Initial miner | SRBMiner-MULTI, Windows/Linux x64 GPU | SRBMiner-MULTI, Windows/Linux x64 GPU |
| Initial user pool | 2Miners `rvn.2miners.com:6060` | 2Miners `etc.2miners.com:1010` |
| Payout | Native RVN to user-owned RVN address | Native ETC to user-owned ETC address |
| Pool login | `ADDRESS.WORKER`, password `x` | `ADDRESS.WORKER`, password `x` |
| SolarMiner / referral | House wallet supplied; referral accounts and credits unverified | House wallet supplied; referral accounts and credits unverified |

Sources: [Ravencoin](https://ravencoin.org/), [Ethereum Classic miners FAQ](https://www.ethereumclassic.org/faqs/miners/), [SRBMiner algorithms and fees](https://github.com/doktor83/SRBMiner-Multi/blob/master/README.md), [SRBMiner parameters](https://github.com/doktor83/SRBMiner-Multi/blob/master/Parameters), [2Miners RVN](https://rvn.2miners.com/help), [2Miners ETC](https://etc.2miners.com/help), [2Miners API](https://apidoc.2miners.com/). The pool also offers BTC/TON payouts and TLS ports; these are excluded from the first contract because payout currency and wire behavior differ. Minimum payout and API account responses still need live verification.

### Observed Stratum handshake, 2026-10-03

Using the installed SRBMiner-MULTI 3.7.1 and the visible Windows RTX 2080 Ti, a local fake pool recorded these initial miner calls. No real share was mined or submitted:

| Coin | Miner subscribe | Miner authorize | Additional call |
| --- | --- | --- | --- |
| RVN | `params:["SRBMiner-MULTI/3.7.1"]` | `params:["RVN_ADDRESS", "x"]` | `mining.extranonce.subscribe` |
| ETC | `params:["SRBMiner-MULTI/3.7.1", "EthereumStratum/1.0.0"]` | `params:["0xETC_ADDRESS", "x"]` | `mining.extranonce.subscribe` |

Passing a separate `--worker` argument for RVN did **not** change the observed authorize username; the upstream address cannot simply be encoded in that argument. A `--wallet ADDRESS.WORKER` login was observed in the earlier probe, so any dynamic routing encoding must be checked there and removed by the proxy before upstream authorize. These observations do not prove submit layout or miner acceptance of target switches.

Read-only TCP probes to 2Miners using the public example addresses (no `mining.submit`) returned:

- RVN `subscribe.result:["065bbf80","49"]`, authorize `true`, `mining.set_target` with a 64-digit hex target, `mining.notify` with eight parameters including job ID at index 0, then extranonce-subscribe `true`.
- ETC `subscribe.result:[["mining.notify", SESSION_ID, "EthereumStratum/1.0.0"], EXTRANONCE]`, authorize `true`, `mining.set_difficulty`, four-parameter `mining.notify` with job ID at index 0, extranonce-subscribe `true`, then `mining.set_extranonce`.

The two target/difficulty and subscribe shapes are different. The [EthereumStratum/1.0.0 specification](https://github.com/nicehash/Specifications/blob/master/EthereumStratum_NiceHash_v1.0.0.txt) describes ETC-style submit as `[worker, jobId, nonce]`, but the installed miner's actual submit and 2Miners acceptance still need capture. RVN submit shape and extranonce switching remain unverified. Probe scripts are local-only under workspace `.codex-qa/`; they contain public documentation example addresses and no secrets.

Two bounded ETC fake-pool mining attempts (75 and 60 seconds) replied to the installer's subscribe/authorize/extranonce calls with a captured 2Miners job and easy difficulty. Neither produced `mining.submit`; both SRBMiner processes were terminated at the timeout. The cause is not established from this probe. Do not treat the synthetic adapter tests as proof that SRBMiner accepts a switched fee job.

## Current code and implementation order

1. PC-Agent now has `GpuCoinMinerService` for RVN/KAWPOW and ETC/ETCHash, reusing the verified SRBMiner installer, GPU inventory/power service and Pearl GPU ID mapping. It persists separate pool/wallet/worker/GPU selections, validates RVN Base58Check mainnet versions and ETC 0x hex format, puts `ADDRESS.sm1.BASE64URL_POOL.WORKER` in `--wallet`, and checks the configured SolarMiner proxy, live listener and house fee target in the final start path. It monitors jobs and fee readiness, stops stale/failed processes, separates Pearl and GPU-coin process ownership, and exposes status, GPU control, console, fees and local forms. General power/PV dispatch chooses the selected GPU coin. The UI has explicit, non-toggle GPU prerequisites. This is **prepared code**, not proof of mining support. No default/donation payout is offered without a real wallet.
2. Proxy has BTC/XMR/PRL listeners. RVN and ETC now have distinct registered prototype adapters, with no listener port configured. The planned login envelope is `ADDRESS.sm1.BASE64URL_POOL.WORKER` in SRBMiner's `--wallet`; the proxy removes this suffix before forwarding `ADDRESS.WORKER` to the user pool. Fake-pool tests cover job/submit ID mapping and target replay for both observed dialect shapes. Actual miner submits, accepted shares, reconnect, TLS and fee switching remain unverified; do not enable a listener from this code alone.
3. Fee backend now has canonical RVN/ETC configs and aliases with the operator-supplied house addresses on native 2Miners accounts. Its response marks the house target explicitly; the proxy's existing DTO accepts that flag. The PC-Agent validates the RVN Base58Check address and ETC 0x format. Address ownership, central deployed configuration, actual pool credit, referral accounts and proxy refresh remain unverified. Unknown coins still return HTTP 200 and empty targets; that is not a working route.
4. Node core/frontend needs coin metadata and address validation, pool/agent DTOs, PV control, monitoring, units, revenue and payout state. Existing BTC Lightning and Braiins data must stay coin-specific. Admin portal may store RVN/ETC referral addresses now, while both routing flags remain false. Pool API accounting, balances, payouts and reconciliation are not implemented.
5. Release only after real RTX 2080 Ti / Windows 11 and Linux coverage where claimed, user/house/referrer accepted-share and payout reconciliation, PV stop/restart, migration, BTC/XMR/PRL regression, deployment ports and rollback. The local Windows GPU is visible to `nvidia-smi`; Docker Desktop access was denied to this sandbox, so no container or GPU mining test ran.

## Gate inventory from the new-coin guide

| Guide step | State |
| --- | --- |
| 0 feasibility | Miner and pool candidates documented; house addresses supplied and format-checked. Ownership and live share evidence missing. |
| 1 coin/pool contract | Proposed native-payout combination and observed subscribe/authorize/notify above; PC-Agent validates RVN Base58Check version 60/122 and ETC 0x hex format. Pool-specific worker limits, TLS and accepted shares remain to verify. |
| 2 proxy and fee | Disabled proxy adapters and synthetic routing tests exist. Fee-backend RVN/ETC configs and explicit house flags now pass local tests. Actual miner submit, switching, accepted shares, central deployment and payout remain unverified; no listener is enabled. |
| 3 PC-Agent | Configuration, per-GPU process lifecycle, proxy/fee guard, status, local UI, power dispatch and bounded prerequisite checklist implemented. `:pc-agent:test --offline`, `node --check` and `git diff --check` pass on 2026-10-03. Live Windows 11 / RTX 2080 Ti job and share acceptance, Linux GPU behavior, and Docker Desktop runtime remain unverified. ASIC and other OS paths are **not applicable to this first PC-Agent GPU scope**. |
| 4 Node backend/frontend | Not implemented; mining and financial UI must remain unavailable for these coins. |
| 5 admin/referrer | Disabled wallet/target preparation in progress; pool accounting missing. |
| 6 other services | Landing calculator and Lightning wallet are **not applicable to native-payout PC-Agent MVP**; no RVN/ETC estimates or on-chain custody will be claimed. Documentation and monitoring remain required. |
| 7 verification/rollout | No builds prove mining; no accepted user, house or referral shares, revenue or payout evidence. No production enablement. |

The operator supplied SolarMiner RVN address `RHaGK3iARQdKgZ6VPDP4N5chP3aVgUUfz7` and ETC address `0x21211c699D409Ca3802D955caD80Ccc034004993` on 2026-10-03. Format and RVN checksum pass; ownership and 2Miners credits are not yet evidenced. User test wallets are pending. No public donation address is used.
