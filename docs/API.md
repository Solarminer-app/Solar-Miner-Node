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
| `POST` | `/api/setup/options/{kind}/{providerId}/validate` | Validate one provider selection and its credentials/settings. |
| `POST` | `/api/setup` | Create a PV site from the completed setup request. |

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

## Currency Rates API

The Currency Rates API is read-only and already versioned under `/api/v1`. Base URL in the default deployment: `http://<node>:8081`.

Controller source: [`PublicDataController`](../currency-rates/src/main/java/de/verdox/currencyrates/currencyrates/controller/PublicDataController.java)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/public/bitcoin-stats` | Get Bitcoin price, difficulty, hashrate, subsidy and fee statistics for a local date/time zone. |
| `GET` | `/api/v1/public/exchange-rates` | Get all stored USD-based exchange rates for a local date/time zone. |
| `GET` | `/api/v1/public/exchange-rates/convert` | Get one historical conversion rate between two currencies. |

## PC Agent API (experimental)

The PC Agent is not part of the default Compose deployment. Its API and DTOs may change while the component remains work in progress.

Controller source: [`MiningController`](../pc-agent/src/main/java/de/verdox/solarminer/pcagent/controller/MiningController.java)

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/agent/identify` | Verify that a compatible PC Agent is reachable. |
| `GET` | `/api/agent` | Get current CPU/GPU mining statistics. |
| `POST` | `/api/agent/setPoolConfiguration` | Set the XMR pool, user and developer-fee percentage. |
| `POST` | `/api/agent/setPowerTarget` | Set an absolute host power target. |
| `POST` | `/api/agent/increasePowerTarget` | Increase the host power target. |
| `POST` | `/api/agent/decreasePowerTarget` | Decrease the host power target. |
| `POST` | `/api/agent/pause` | Pause PC mining. |
| `POST` | `/api/agent/resume` | Resume PC mining. |

## Maintaining the documentation

When a REST controller or DTO changes:

1. Keep the Spring mapping and parameter annotations explicit.
2. Add a descriptive `@Tag` for a new controller.
3. Update the endpoint inventory and compatibility notes in this file when behavior changes.
4. Build the affected service and inspect `/v3/api-docs.yaml` before release.
5. Treat removal/renaming of fields, enum values, paths, methods, or status codes as a breaking API change. Introduce a versioned path before making such a change for external consumers.

The React route forwarding controller is intentionally excluded because it serves HTML navigation and is not a REST interface. Likewise, internal Java classes whose names end in `Controller` but have no Spring HTTP mapping are not API controllers.
