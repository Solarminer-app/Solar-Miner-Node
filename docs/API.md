# SolarMiner REST API

This document is the entry point for developers who want to build another frontend, automate a SolarMiner node, or integrate the software into a physical product.

## Canonical API description

Every executable HTTP service generates an **OpenAPI 3** description directly from its Spring controllers and DTOs. The generated document is the canonical source for exact parameter types, required fields, enum values, request bodies, response schemas and downloadable files.

| Resource | Relative URL |
| --- | --- |
| Interactive Swagger UI | `/swagger-ui.html` |
| OpenAPI JSON | `/v3/api-docs` |
| OpenAPI YAML | `/v3/api-docs.yaml` |

For the default Docker Compose deployment these URLs are available on:

| Service | Host port | Intended use |
| --- | ---: | --- |
| SolarMiner Node | `8080` | Primary integration API and bundled frontend |
| Currency Rates | `8081` | Historical, read-only financial market data |
| SolarMiner Core | `8082` | Trusted low-level miner control |
| PC Agent | not included in Compose | Experimental local CPU/GPU mining agent; `8084` in the development profile |

Examples:

```bash
curl http://localhost:8080/v3/api-docs.yaml
curl http://localhost:8080/api/start-info
curl "http://localhost:8081/api/v1/public/exchange-rates?date=2026-07-19&timezone=Europe%2FBerlin"
```

OpenAPI-compatible tools can use the YAML or JSON URL to generate clients for TypeScript, Java, Kotlin, Swift, C#, Python and other platforms. Always generate against the exact SolarMiner release that the product ships with.

## Integration contract

- API requests and responses use JSON unless an operation explicitly declares a file response.
- Entity identifiers are UUID strings. Calendar dates use ISO `YYYY-MM-DD`; timestamps and chart time values must be interpreted according to their generated schema.
- `locale` accepts a language tag such as `de` or `en`. `currency` uses an uppercase currency code such as `EUR`, `USD` or `CHF` where the endpoint supports it. `timeZone`/`timezone` uses an IANA zone such as `Europe/Berlin`.
- Successful delete and command endpoints commonly return `204 No Content`; data queries normally return `200 OK` with JSON.
- Clients must branch on the HTTP status code. The error-body layout is not yet a versioned contract and may differ between validation failures and services.
- Most Node and Core paths are currently unversioned. Treat endpoint or DTO changes as compatibility-sensitive. The Currency Rates API already uses `/api/v1`.
- CORS is a browser restriction, not API authentication. A separate browser frontend should be served from the node origin, use a reverse proxy, or explicitly configure its allowed origin.
- There is currently no universal authentication boundary in front of these APIs. Never expose Node, Core, PC Agent, or Swagger UI directly to the Internet. A physical product should place them on a trusted device network and add authentication/TLS at its gateway.
- Prefer the Node API on port `8080` for product integrations. Call Core directly only when low-level miner control is deliberately required. Do not couple external clients to MariaDB or InfluxDB schemas.

## SolarMiner Node API

The Node API is the recommended surface for frontends and appliance integrations. Base URL in the default deployment: `http://<node>:8080`.

### Sites and dashboard

Controller sources: [`StartInfoController`](../src/main/java/de/verdox/pv_miner/controller/StartInfoController.java), [`DashboardController`](../src/main/java/de/verdox/pv_miner/controller/DashboardController.java)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/start-info` | List configured PV sites and the current site limit. |
| `GET` | `/api/pv-site/{siteId}/dashboard/init` | Load dashboard identity data, miners and pools. |
| `GET` | `/api/pv-site/{siteId}/dashboard/live` | Load current energy, mining and financial values for a locale and currency. |
| `GET` | `/api/pv-site/{siteId}/dashboard/charts` | Load live/history chart series and mining-controller history for a time zone and cluster. |

### PV-site details

Controller source: [`PVSiteDetailsController`](../src/main/java/de/verdox/pv_miner/controller/PVSiteDetailsController.java)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/pv-site/{siteId}/details` | Load the complete editable site view. |
| `POST` | `/api/pv-site/{siteId}/details/pv-devices` | Add one or more selected logical components from a PV device. |
| `DELETE` | `/api/pv-site/{siteId}/details/pv-devices/{deviceId}` | Remove a configured PV device/component from the site. |
| `PUT` | `/api/pv-site/{siteId}/details` | Update site name, time zone and financial base settings. |
| `POST` | `/api/pv-site/{siteId}/details/panel-groups` | Create a panel group and its single geographic position. |
| `PUT` | `/api/pv-site/{siteId}/details/panel-groups/{panelGroupId}` | Update an existing panel group. |
| `DELETE` | `/api/pv-site/{siteId}/details/panel-groups/{panelGroupId}` | Delete a panel group. |
| `POST` | `/api/pv-site/{siteId}/details/prices/{priceType}` | Add a dated electricity-price or feed-in-tariff entry. |
| `DELETE` | `/api/pv-site/{siteId}/details/prices/{priceType}/{validFrom}` | Delete a dated tariff entry. |
| `PUT` | `/api/pv-site/{siteId}/details/miners/{minerId}/cost` | Update a miner's acquisition cost. |

### Mining, pools and clusters

Controller sources: [`MiningController`](../src/main/java/de/verdox/pv_miner/controller/MiningController.java), [`MinerDetailsController`](../src/main/java/de/verdox/pv_miner/controller/MinerDetailsController.java), [`ClusterConfigController`](../src/main/java/de/verdox/pv_miner/controller/ClusterConfigController.java)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/pv-site/{siteId}/mining` | Load clusters, connected/unassigned miners, pools and fee distribution. |
| `POST` | `/api/pv-site/{siteId}/mining/referral` | Validate and save a referral code for this site. |
| `DELETE` | `/api/pv-site/{siteId}/mining/referral` | Remove the site's referral code. |
| `GET` | `/api/pv-site/{siteId}/mining/miners/discovery` | Scan a requested subnet for supported miners. |
| `POST` | `/api/pv-site/{siteId}/mining/miners` | Connect a discovered or manually entered miner to the site. |
| `DELETE` | `/api/pv-site/{siteId}/mining/miners/{minerId}` | Remove a miner from the site and system. |
| `POST` | `/api/pv-site/{siteId}/mining/pools` | Connect a mining pool to the site. |
| `DELETE` | `/api/pv-site/{siteId}/mining/pools/{poolId}` | Remove a mining pool from the site and system. |
| `POST` | `/api/pv-site/{siteId}/mining/clusters/{clusterName}/start` | Start cluster automation. |
| `POST` | `/api/pv-site/{siteId}/mining/clusters/{clusterName}/stop` | Stop cluster automation. |
| `POST` | `/api/pv-site/{siteId}/mining/clusters/{clusterName}/miners` | Assign currently unassigned miners to a cluster. |
| `POST` | `/api/pv-site/{siteId}/mining/clusters/{clusterName}/miners/remove` | Remove selected miners from a cluster without deleting them. |
| `POST` | `/api/pv-site/{siteId}/mining/miners/{minerId}/power-targets` | Update safe power limits and hardware lock timings for a miner. |
| `GET` | `/api/pv-site/{siteId}/mining/miners/{minerId}` | Load detailed telemetry and historical analytics for one miner. |
| `GET` | `/api/pv-site/{siteId}/mining/configs/{configName}` | Load one controller DSL configuration. |
| `POST` | `/api/pv-site/{siteId}/mining/configs` | Create or update a controller DSL configuration. |
| `POST` | `/api/pv-site/{siteId}/mining/configs/simulate` | Run the real controller DSL against preset or historical input data. |

### Setup

Controller source: [`SetupController`](../src/main/java/de/verdox/pv_miner/controller/SetupController.java)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/setup/catalog` | Load setup capabilities, providers and required fields. |
| `POST` | `/api/setup/catalog/refresh` | Refresh remotely supplied setup capabilities. |
| `GET` | `/api/setup/pv-devices/profiles` | Search compatible PV profiles, optionally by provider and name query. |
| `POST` | `/api/setup/pv-devices/discover` | Discover PV devices and matching logical component profiles, including SunSpec. |
| `GET` | `/api/setup/pv-devices/network` | Suggested private IPv4 subnet prefix for discovery; operators can override it with `solarminer.discovery.subnet-prefix` / `SOLARMINER_DISCOVERY_SUBNET_PREFIX`. |
| `POST` | `/api/setup/pv-devices/scan` | Bounded PV discovery with `{devices, subnetPrefix, checkedHosts, totalHosts, complete}`. Supports `AUTO`, `MODBUS_TCP`, `REST_API`; request fields are `providerId`, `subnetPrefix`, `port`, `slaveId`. |
| `POST` | `/api/setup/options/{kind}/{providerId}/validate` | Validate one provider selection and its credentials/settings. |
| `POST` | `/api/setup` | Create a PV site from the completed setup request. |

PV scans cover addresses `.1`–`.254` of an explicit private IPv4 prefix (for example `192.168.1.`), with 24 concurrent host probes and a 25-second deadline. `complete:false` means some hosts were not fully checked; an empty result is not proof that no devices exist. Concurrent scans receive HTTP 429 until in-flight probes have stopped. Invalid/public prefixes, invalid ports and invalid device IDs receive HTTP 400 before network access. `AUTO` checks Modbus port 502 and HTTP ports 80/443/8080/8123; a specified protocol honors the requested port, and Modbus honors `slaveId`. The legacy `/discover` response remains an array and defaults to Modbus when no provider is given, but cannot convey completion metadata.

REST discovery only uses GET entries and requires the returned payload to parse for the profile. A 401/403 is not sufficient to identify a device; protected APIs remain available through manual setup. TLS certificate validation remains enabled. In Docker, the suggested interface may belong to the container network; configure the LAN prefix or enter it in setup. When opened through a private IPv4 Node address, the UI uses that address's prefix as its suggestion. Scans are local read probes, not device-control or mining activation commands.

### PV REST and Modbus/TCP profiles

Controller source: [`PVConfigController`](../src/main/java/de/verdox/pv_miner/controller/PVConfigController.java)

REST profiles:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/config/pv/rest/catalog` | List REST templates and local/community profiles. |
| `POST` | `/api/config/pv/rest/profiles` | Create a REST profile from a template. |
| `GET` | `/api/config/pv/rest/profiles/{name}` | Load a REST profile. |
| `PUT` | `/api/config/pv/rest/profiles/{name}` | Save/rename a REST profile. |
| `DELETE` | `/api/config/pv/rest/profiles/{name}` | Delete a REST profile. |
| `GET` | `/api/config/pv/rest/profiles/{name}/export` | Download a REST profile as JSON. |
| `POST` | `/api/config/pv/rest/profiles/import` | Import a REST profile from JSON. |
| `POST` | `/api/config/pv/rest/community/{name}` | Download a community REST profile into local storage. |
| `POST` | `/api/config/pv/rest/test` | Test a REST device connection and parsed values. |

Modbus/TCP profiles:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/config/pv/modbus/tcp/catalog` | List Modbus templates and local/community profiles. |
| `POST` | `/api/config/pv/modbus/tcp/profiles` | Create a Modbus profile from a template. |
| `GET` | `/api/config/pv/modbus/tcp/profiles/{name}` | Load a Modbus profile. |
| `PUT` | `/api/config/pv/modbus/tcp/profiles/{name}` | Save/rename a Modbus profile. |
| `DELETE` | `/api/config/pv/modbus/tcp/profiles/{name}` | Delete a Modbus profile. |
| `GET` | `/api/config/pv/modbus/tcp/profiles/{name}/export` | Download a Modbus profile as JSON. |
| `POST` | `/api/config/pv/modbus/tcp/profiles/import` | Import a Modbus profile from JSON. |
| `POST` | `/api/config/pv/modbus/tcp/community/{name}` | Download a community Modbus profile into local storage. |
| `POST` | `/api/config/pv/modbus/tcp/test` | Test Modbus registers, fingerprints and parsed values. |

### Finance

Controller source: [`FinanceController`](../src/main/java/de/verdox/pv_miner/controller/FinanceController.java)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/pv-site/{siteId}/finance` | Load finance KPIs, history, tariffs and the BTC sales ledger for a date range. |
| `GET` | `/api/pv-site/{siteId}/finance/export/{reportType}` | Export a CSV or PDF report; supported report types are defined by the generated enum/schema and controller validation. |
| `POST` | `/api/pv-site/{siteId}/finance/sales` | Add a realized Bitcoin sale to the ledger. |
| `DELETE` | `/api/pv-site/{siteId}/finance/sales` | Delete the matching Bitcoin sale from the ledger. |

### Lightning wallet

Controller source: [`LightningWalletController`](../src/main/java/de/verdox/pv_miner/controller/LightningWalletController.java)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/lightning-wallet` | Load balance, liquidity, address, transactions and backend connection status. |
| `POST` | `/api/lightning-wallet/pay` | Pay a Lightning target, optionally with an explicit satoshi amount. |
| `POST` | `/api/lightning-wallet/withdraw/onchain` | Send an on-chain withdrawal with an explicit fee rate. |
| `POST` | `/api/lightning-wallet/connection/toggle` | Toggle the public-backend WebSocket connection. |

## SolarMiner Core API

Core is a low-level hardware service. Base URL in the default deployment: `http://<node>:8082`. Requests carry `MinerDetails` plus an optional/detected mining OS. Credential payloads are sensitive and must never be logged by an integrating gateway.

Controller source: [`MinerController`](../core/src/main/java/de/verdox/pv_miner/core/controller/MinerController.java)

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/miners/check-standard-credentials` | Test known default credentials against a miner. |
| `POST` | `/api/miners/check-custom-credentials` | Test credentials supplied in `MinerDetails`. |
| `POST` | `/api/miners/identify-os` | Detect miner firmware/OS from an IPv4 address; the body is a JSON string. |
| `POST` | `/api/miners/start` | Start mining. |
| `POST` | `/api/miners/stop` | Stop the miner. |
| `POST` | `/api/miners/pause` | Pause mining while retaining its configuration. |
| `POST` | `/api/miners/resume` | Resume mining. |
| `POST` | `/api/miners/pool-target` | Change pool URL, worker and optional referral routing. |
| `POST` | `/api/miners/power-target` | Set an absolute power target in watts. |
| `POST` | `/api/miners/power-target/increment` | Increase the current power target by watts. |
| `POST` | `/api/miners/power-target/decrement` | Decrease the current power target by watts. |
| `POST` | `/api/miners/stats` | Query current miner telemetry and fee routing. |
| `GET` | `/api/miners/dev-fee/overview` | Return the current developer-fee and referral hashrate distribution. |
| `GET` | `/api/miners/dev-fee/referral/validate` | Validate a referral code against the public SolarMiner backend. |

`TWENTY_ONE_ENERGY` is a local 21energy heater adapter. It is registered in Core and can be discovered and monitored through the same miner lifecycle. A newly connected heater is always monitoring-only: Core rejects start, pause, resume, power and pool-write requests until a model/firmware-specific capability record and measured level-to-watt map have been verified. This is intentional; a successful network probe is not permission to produce heat or replace a pool configuration.

## Currency Rates API

The Currency Rates API is read-only and already versioned under `/api/v1`. The central public base URL is `https://currency.solarminer.app`; a local Node Compose deployment can use `http://<node>:8081`.

Controller source: [`PublicDataController`](../currency-rates/src/main/java/de/verdox/currencyrates/currencyrates/controller/PublicDataController.java)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/public/bitcoin-stats` | Get Bitcoin price, difficulty, hashrate, subsidy and fee statistics for a local date/time zone. |
| `GET` | `/api/v1/public/exchange-rates` | Get all stored USD-based exchange rates for a local date/time zone. |
| `GET` | `/api/v1/public/exchange-rates/convert` | Get one historical conversion rate between two currencies. |
| `GET` | `/api/v1/public/coin-prices` | Get current or dated USD prices keyed by `btc`, `xmr`, `prl`, `rvn` and `etc`. |
| `GET` | `/api/v1/public/mining-networks` | Get the latest complete price/network snapshots for Monero, Pearl, Ravencoin and Ethereum Classic. |
| `GET` | `/api/v1/public/mining-networks/{coin}` | Get one snapshot by canonical key or ticker alias; unknown coins return `404`. |

Mining-network snapshots use canonical keys `monero`, `pearl`, `ravencoin` and `ethereumclassic`. Hashrate is H/s, target block time is seconds, reward is native coin per block and price is USD per coin. Each response includes collection time, age, upstream source names and a stale flag. The service retains the last complete snapshot after an upstream failure; it becomes stale after two hours instead of replacing missing inputs with zero.

## PC Agent API (experimental)

The PC Agent is not part of the default Compose deployment. Its API and DTOs may change while the component remains work in progress.

The agent serves its local dashboard at `http://<agent-host>:8084/`; mining and hardware telemetry have separate pages. The telemetry page shows the CPU and detected GPU names, all reported sensor values and a component power sum. Total watts adds CPU package power to available GPU board readings; it is marked as partial when devices have no power reading. The active coin and external proxy host are stored under `./solarminer-agent/`. XMRig and SRBMiner-MULTI start only when their configured route matches the SolarMiner Stratum proxy and its API responds. Monero uses proxy port `3335`; Pearl uses `3334`. Existing XMRig configs with a direct pool are rejected on start. The former XMRig process-switching developer fee has been removed; fee routing belongs to the proxy. The SRBMiner JSON API supplies Pearl hashrate per managed GPU; GPU temperature remains in the separate hardware telemetry path. Like the rest of the local Node stack, the agent currently relies on the trusted LAN; do not expose port 8084 to the internet.

The API has two disjoint namespaces. `/api/agent/local/**` belongs exclusively to the same-origin PC-Agent dashboard. `/api/agent/external/**` belongs exclusively to the SolarMiner Node and returns HTTP 403 for every read and write while Node control is disabled. The former shared `/api/agent/**` routes are not compatibility aliases and cannot control the agent.

The Overview and each installed miner view show a gross daily earnings estimate from the current local hashrate. The PC-Agent obtains all price and network inputs for Monero, Pearl, Ravencoin and Ethereum Classic from `https://currency.solarminer.app/api/v1/public/mining-networks`; it no longer contacts individual explorers or price providers. The agent caches successful central snapshots for ten minutes, keeps the last successful values marked as stale when the central request fails and never treats missing hashrate or market data as zero earnings. Estimates are probabilistic and do not deduct pool fees, miner fees, stale shares or the SolarMiner developer fee.

The Mining page has an independent console for each agent-managed miner. `GET /api/agent/local/console/{monero|pearl}?offset=<byte-offset>` returns `{nextOffset,data,hasMore}`, where `data` is base64-encoded UTF-8 console bytes in chunks of at most 64 KiB. Its `/download` endpoint downloads the entire log. Output is appended without truncation to `./solarminer-agent/logs/` across miner and agent restarts; operators should manage disk usage and access to pool login details in those files.

The PC-Agent supports XMRig and SRBMiner concurrently. Local coin/GPU controls exist only below `/api/agent/local/**`; Node-wide pause, resume and power targets exist only below `/api/agent/external/**`. The persisted legacy `activeCoin` selection no longer stops another miner.

For an independent installation, build `./gradlew :pc-agent:standaloneZip` from the Node repository. This builds the sibling `solarminer-stratum-proxy` repository and packages both runnable JARs plus Windows/Linux launchers; see `pc-agent/standalone/README.md`. Java 21 is required on the target PC. The launcher starts the proxy locally on loopback and fixes the agent's proxy host to `127.0.0.1`. Standalone Monero and Pearl mining are gated on an active, positive fee target from the fee backend. The bundled proxy refuses mining connections without a usable fee target, and the agent stops a running miner if the proxy or fee route disappears. The proxy's job routing performs the fee split.

The PC-Agent also exposes a host hardware snapshot for local integrations. It is sampled on request with a one-second cache and does not upload or persist readings. Linux reads available kernel `hwmon`, thermal and powercap/RAPL sensors. On Windows, the agent downloads LibreHardwareMonitor from its official upstream release and verifies the published SHA-256 digest. It configures the LHM JSON server on loopback (`127.0.0.1:8085`) and asks Windows for elevation when starting LHM, because its sensor driver requires administrator rights. The UI stays blocked until LHM responds; if it does not start, the user can retry from the UAC prompt flow. Missing or inaccessible sensor values are returned with `available: false` and `value: null`; JVM memory is labelled separately from physical host memory. Close a separately running LHM instance if it has no local JSON server enabled.

Example: `GET /api/agent/local/telemetry` returns `{"collectedAt":"2026-09-28T12:00:00Z","platform":"Linux","architecture":"amd64","metrics":{"cpu.temperature":{"value":54.2,"unit":"°C","source":"Linux hwmon/thermal","available":true,"directMeasurement":true},"cpu.package_power":{"value":72.1,"unit":"W","source":"Linux powercap/RAPL","available":true,"directMeasurement":true}},"sources":{"linux-kernel-sensors":"active; reads hwmon, thermal and powercap on demand"}}`. Dynamic hardware metrics use keys below `hwmon.*` on Linux and `hardware.*` on Windows. Power is in watts, temperatures in Celsius, memory/data in bytes, energy in joules, voltage in volts, current in amperes and fan speed in RPM.

Controller source: [`MiningController`](../pc-agent/src/main/java/de/verdox/solarminer/pcagent/controller/MiningController.java)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/agent/external/identity` | Node discovery; gated like every external route. |
| `GET` | `/api/agent/external/status` | Node-visible CPU/GPU worker statistics. |
| `GET` | `/api/agent/external/power-control` | Node-visible power range and applied target. |
| `GET` | `/api/agent/external/{telemetry|overview|proxy|earnings}` | Node read contracts. |
| `POST` | `/api/agent/external/{pause|resume}` | Pause or resume externally assigned workers. |
| `POST` | `/api/agent/external/power-target?watts=<watts>` | Apply the Node's PV-wide target. |
| `POST` | `/api/agent/external/{proxy|referral|pool-configuration}` | Node-owned routing configuration. |
| `POST` | `/api/agent/external/{monero|pearl}/configuration` | Node-owned miner configuration. |
| `GET` / `POST` | `/api/agent/local/**` | Same-origin dashboard APIs for local status, settings, mining, telemetry, benchmarks and diagnostics. |

Dynamic GPU-Power-Regelung wird aktuell nur für NVIDIA-Karten angeboten, deren `nvidia-smi` eine stabile UUID, Treibergrenzen und den zurückgelesenen Sollwert liefert. AMD-Karten und andere nicht verifizierbare GPUs bleiben sichtbar, werden aber nicht per Power-Limit geregelt, bis ein entsprechender Treiberadapter stabile IDs und eine Set/Readback-Prüfung bietet. Der Agent ändert weder Spannung noch Takt und versucht beim regulären Beenden, die vor dem ersten SolarMiner-Eingriff gelesenen Limits wiederherzustellen.

## Maintaining the documentation

When a REST controller or DTO changes:

1. Keep the Spring mapping and parameter annotations explicit.
2. Add a descriptive `@Tag` for a new controller.
3. Update the endpoint inventory and compatibility notes in this file when behavior changes.
4. Build the affected service and inspect `/v3/api-docs.yaml` before release.
5. Treat removal/renaming of fields, enum values, paths, methods, or status codes as a breaking API change. Introduce a versioned path before making such a change for external consumers.

The React route forwarding controller is intentionally excluded because it serves HTML navigation and is not a REST interface. Likewise, internal Java classes whose names end in `Controller` but have no Spring HTTP mapping are not API controllers.

## Node earnings forecast

`GET /api/pv-site/{siteId}/dashboard/earnings` returns per-coin daily gross forecasts and a site total with EUR/day, kWh/day, revenue in cents/kWh, the configured grid tariff in cents/kWh, and a completeness flag. The Node reads the PC-Agent forecast for registered agents and its own cached Bitcoin chain data for native miners. Coin identities and algorithm mapping come from `MiningCoin`; each device/chain forecast is a `MiningEarningsSource` bean. New agent coins require a `MiningCoin` entry, an agent forecast, and a USD price in currency-rates. A new native chain needs its own source bean. The Node uses the currency-rates service for all coin prices and USD/EUR conversion. Unavailable, stale, or missing values block the site profitability comparison.

The controller DSL variable `MINING_MARGIN_CENTS_PER_KWH` is estimated gross mining revenue per kWh minus the current grid tariff. It returns a failing value when data is incomplete. The optional "Profitable grid mining" mode can be added in cluster configuration; it is absent from the standard ruleset. The emergency battery mode retains first priority. Historical simulator runs reject this live-only variable because historical earnings inputs are not stored.
