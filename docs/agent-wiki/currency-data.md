# Public currency and mining-network data

Checked against `currency-rates` and `pc-agent` on 2026-10-04. This is the implementation record for contract C9; deployment at `currency.solarminer.app` remains a separate rollout step.

## Public contract

`GET /api/v1/public/mining-networks` returns the latest complete snapshots. `GET /api/v1/public/mining-networks/{coin}` accepts the canonical key and documented ticker aliases. Unknown or never-successfully-collected coins return 404. Fields and units are:

| Field | Meaning |
| --- | --- |
| `coin`, `ticker`, `algorithm` | Canonical identity and display metadata |
| `networkHashrateHps` | Network hashrate in hashes per second |
| `difficulty` | Provider-reported chain difficulty as a floating-point value |
| `targetBlockSeconds` | Expected or observed block interval in seconds |
| `blockReward` | Base native-coin reward per block; pool fees, transaction fees, uncle rewards and MEV are excluded |
| `priceUsd` | USD per native coin |
| `updatedAt`, `ageSeconds` | Persisted collection instant and response-time age |
| `networkSource`, `priceSource` | Upstream provenance |
| `available`, `stale` | Complete snapshot and bounded freshness state |

Successful snapshots replace the row keyed by canonical coin. Failed or incomplete refreshes never write zeroes and retain the prior row. The collector refreshes every ten minutes; an otherwise valid row is marked stale after two hours. `coin-prices` remains backward-compatible and adds `rvn` and `etc`; current PRL falls back to Pearl's public price endpoint because CoinGecko's configured `pearl-research` ID did not return a quote in the 2026-10-04 live probe.

## Coin providers and calculations

| Canonical key / ticker | Network source | Price source | Reward and interval |
| --- | --- | --- | --- |
| `monero` / XMR | [`xmrchain.net` network and latest block](https://xmrchain.net/api/networkinfo) | [CoinGecko](https://docs.coingecko.com/reference/simple-price) | Latest coinbase outputs converted from atomic units; provider target seconds |
| `pearl` / PRL | [`pearlchain.live/api/explorer/stats`](https://pearlchain.live/api/explorer/stats) | [Pearl price response](https://pearlchain.live/api/explorer/price), currently identifying CoinPaprika | Provider reward, target and provider stale flag |
| `ravencoin` / RVN | [2Miners public RVN stats](https://rvn.2miners.com/api/stats) | [CoinGecko](https://docs.coingecko.com/reference/simple-price) | 5,000 RVN shifted each 2,100,000-block era; provider average block seconds. The interval and initial subsidy are defined in [Ravencoin Core `chainparams.cpp`](https://github.com/RavenProject/Ravencoin/blob/master/src/chainparams.cpp). |
| `ethereumclassic` / ETC | [2Miners public ETC stats](https://etc.2miners.com/api/stats) | [CoinGecko](https://docs.coingecko.com/reference/simple-price) | 5 ETC × 0.8 per 5,000,000-block era under [ECIP-1017](https://ecips.ethereumclassic.org/ECIPs/ecip-1017); provider average block seconds. Transaction fees, uncle rewards and MEV are intentionally absent. |

The 2Miners values are public aggregated network statistics, not user pool balances or proof of SolarMiner share credit. The PC-Agent uses one central request only, caches it for ten minutes and publishes forecasts for RandomX, PearlHash, KAWPOW and ETCHash. Its source list retains both `currency.solarminer.app` and upstream provenance.

The local Node consumer treats the agent forecast array as additive and ignores coin keys that are not yet registered in the Node's complete `MiningCoin` path. Consequently RVN/ETC forecasts are available in the PC-Agent without falsely enabling those coins in Node profitability automation; their full Node registration still follows the new-coin guide.

## Coin-guide scope

This work fills only guide section 6 (currency/network data) and the related consumer/contract tests. It does not change wallet formats, miner processes, Stratum, proxy ports, fee targets, pool accounting, portal provisioning, hardware capability, deployment flags or rollout status. Those steps are therefore non-applicable to this changeset, not completed by it. Pearl retains its existing production record; RVN/ETC remain blocked on accepted user/house/referral shares, pool credit and payout reconciliation documented in `rvn-etc-integration.md`.

Verification covers provider fixture parsing, reward-era calculations, alias resolution, JPA persistence, public JSON shape and a PC-Agent HTTP consumer fixture. All 16 Currency-Service and 50 PC-Agent tests pass on Java 21; the root app compiles and its focused `MiningCoinTest` passes. A temporary H2-backed live service returned four fresh network snapshots, all five price keys and the ETC alias detail response. The production host returned a Traefik 404 for the new and existing public routes before rollout, so public reachability and persistence after central MariaDB/container restart remain open gates.
