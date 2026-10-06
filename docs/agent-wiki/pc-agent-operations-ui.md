# PC-Agent Operations UI and local energy journal

Stand: 6. Oktober 2026. Owner: `pc-agent`. This is the current task boundary for Dashboard, Miner-Software, Worker and Pools. Source and tests remain authoritative.

## Operator model

| Page | Owns | Must not own |
| --- | --- | --- |
| Dashboard | Live operating state, active workers, current forecast, pool health, session energy | Installation or device assignment forms |
| Miner-Software | One card per actual software package; install/remove/readiness/provenance | Coin/device assignment, pool setup, start/pause |
| Worker | Complete CPU/GPU inventory, device-to-coin and miner assignment, local/Node mode, start/pause, live and energy data | Binary installation internals |
| Pools | Pool targets, payout relationship, connection facts and pool changes | Hardware ownership |

The global balance strip is shown directly below the header on all four operating pages. Pool and on-chain balances remain separate positions; the fiat value is explicitly the known subtotal. The current service can read Kryptex XMR/PRL pool credits and the configured PRL on-chain address. It cannot derive a Monero on-chain wallet balance from a public address, and RVN/ETC balance providers are not implemented.

Language (`solarminer.pc-agent.language`) and display currency (`solarminer.agent.currency`) are browser-local global preferences shared by every PC-Agent page. `GET /api/agent/local/fiat-rates` caches the current USD-based EUR/USD/CHF rates from the SolarMiner Currency-Service for one hour. Wallet values, gross earnings, energy costs and tariff labels use the same converter and locale-aware formatter. A last-good browser rate cache is retained for seven days; if no conversion is available, the UI keeps the truthful source currency instead of relabeling the amount.

The Dashboard deliberately has one compact operating canvas: readiness notices, live worker cards and the energy summary. Historical page-session charts, duplicate KPI rows, pool lists, worker tables and a second earnings section were removed from it; those details belong to Worker and Pools. Per-worker cards already contain the relevant hashrate, watts, temperature, gross USD/day estimate, pool target and session energy.

## Local worker contract

`GET /api/agent/local/workers` returns one entry per physical CPU/GPU, including idle devices. `POST /api/agent/local/workers/{deviceId}/assignment` accepts `coin`, `minerSoftwareId` and `externalControlEnabled`. It stops an old assignment before persisting the new one, selects only an installed/selectable compatible miner, and synchronizes the assigned GPU list into an existing coin configuration. Start and pause are available at `/{deviceId}/start` and `/{deviceId}/pause`.

Pool/wallet credentials remain coin-level configuration because the miner/proxy processes use one route per coin. Assigning the final GPU away from a configured coin leaves those credentials stored for later use; the persistent `workerCoins` profile is authoritative for whether a physical device is assigned. A change can stop affected processes and never implicitly starts the replacement.

## Energy journal

`EnergyJournalService` samples server-side every five seconds, independent of an open browser. For every `MINING` device it trapezoid-integrates consecutive positive power readings into Wh. A gap over 30 seconds, a missing/non-positive reading, stopped process or changed coin/software is never extrapolated.

Files below `solarminer-agent/energy/`:

- `active-sessions.json`: atomic crash/restart checkpoint;
- `sessions-YYYY-MM.jsonl`: append-only completed sessions;
- `settings.json`: electricity price and display currency.

`GET /api/agent/local/energy` returns active/recent sessions and today/7-day/30-day aggregates. `POST /api/agent/local/energy/settings` stores the local tariff. Sessions retain device, hardware, coin, miner software, algorithm, timestamps, runtime, measured seconds, Wh, average/maximum power, average hashrate, share deltas, source and end reason.

Measurement scope is explicit: CPU package and GPU board readings are component energy, not guaranteed wall energy. Motherboard, PSU conversion loss, fans, unsupported AMD cards and other unmeasured consumers may be absent. `measurementCoverage` exposes measured time; unknown readings never become zero. A future smart-plug source should be stored separately as wall energy instead of silently replacing component measurements.

## Verification and open gates

- `:pc-agent:test --offline`: 72 tests successful under GraalVM JDK 21, including fiat-rate parsing, measured integration, missing-sensor behavior and worker-assignment compatibility.
- `.codex-qa/pc-agent-operations.cjs`: Dashboard, software catalog, complete worker inventory/editor and Pools pass in fixture-driven Chromium at 1440 px and 390 px, without page/console errors or horizontal document overflow. Screenshots are under `.codex-qa/screenshots/pc-agent-operations/`.
- JavaScript syntax checks pass for `overview.js`, `workers.js` and `software.js`.
- Not verified: long-running real-device Wh comparison against a calibrated wall meter, Windows sleep/crash recovery, AMD power readings, or real pool balances beyond the existing provider contracts. USD earnings remain gross forecasts, not accounting entries.

## Embedded proxy dashboard route (2026-10-06)

The embedded proxy binds to loopback on HTTP port 8090. When local proxy mode is running, the Connection page links to the proxy dashboard mounted through the PC-Agent at `/proxy-dashboard/` (Agent web port 8084). The reverse route permits only GET requests for dashboard HTML, CSS, JavaScript and `/api/dashboard[/**]`; it does not expose the proxy's fee/network APIs or any general URL forwarding. This routing change is source-implemented but has not yet been browser-verified.
