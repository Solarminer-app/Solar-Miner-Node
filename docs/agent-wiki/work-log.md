# Agent work log

## 2026-10-07 — GPU-Hashrate bleibt bei kurzen SRBMiner-Lücken sichtbar

- Ursache im Agenten: `GpuCoinMinerService` überschreibt die an Dashboard und Worker gelieferte Hashrate bei jeder API-Antwort sofort mit null, wenn `gpu.total` und das ältere `1min` fehlen oder null sind. SRBMiner nennt seit 3.7.0 `1m` als Statistikintervall; dieses Feld wird jetzt zusätzlich gelesen. Eine positive Hashrate wird bei einzelnen Nullwerten oder kurzen API-Fehlern maximal 20 Sekunden für denselben laufenden Miner vorgehalten.
- Die Health-/Abbruchprüfung bleibt auf dem Rohwert; Stop, Fehler, Prozessneustart oder längere Datenlücken löschen den angezeigten Wert. Die Änderung betrifft den lokalen PC-Agent und keinen Proxy-, Fee- oder Cross-Repository-Vertrag.
- Verifikation: fokussierter `GpuCoinMinerServiceTest` mit `1m`/`1min`/`gpu.total` und Cache-Ablauf erfolgreich (`:pc-agent:test --tests ...GpuCoinMinerServiceTest --offline --no-daemon`), `git diff --check` erfolgreich. Kein laufender QTC-Miner war aus dieser Umgebung zugänglich; die genaue lückenhafte API-Antwort bleibt unbestätigt.

## 2026-10-07 — QTC SRBMiner reconnect cause in proxy job format

- Local `quantus-NVIDIA-2` and `quantus-NVIDIA-3` miner consoles recorded `PARSE error: Quantus notification has no job object` immediately before repeated reconnects. Both had received jobs and reported accepted shares, so the events represented real miner sessions rather than only dashboard noise. The sibling proxy's QTC adapter now wraps follow-up jobs under `params.job` and keeps the login job under `result.job`.
- Focused proxy tests passed and `:pc-agent:standaloneJar --offline --no-daemon` rebuilt the Agent with the corrected embedded proxy. The artifact was not started or deployed; live stability, fee switching and pool-credit checks remain open.

## 2026-10-07 — Dashboard-Energiekarte zeigt Ladefehler statt Endlos-Platzhalter

- Bei einem fehlgeschlagenen `/api/agent/local/energy`-Abruf blieb „Verbrauch & Kosten“ dauerhaft im Ladezustand; ein Klick auf „Tarif“ hatte ohne geladene Einstellungen keine Wirkung. Die vier optionalen Dashboard-Abrufe haben nun je fünf Sekunden Zeitlimit. Die Energiekarte zeigt den Fehler, bei HTTP 404 mit Neustart-/Update-Hinweis; der Tarifknopf ist bis zum erfolgreichen Laden deaktiviert. Wiederholte Aktualisierungen können die Karte nach einer Erholung des Endpunkts füllen.
- Der Energie-Endpunkt und seine Mess-/Kostenberechnung wurden nicht geändert. Ein lokaler Live-Agent war in dieser Sandbox nicht erreichbar; dessen konkreter HTTP-Status ist daher nicht verifiziert. Verifikation: JavaScript-Syntaxprüfung und `git diff --check`.

## 2026-10-07 — Ertrag pro kWh in den laufenden Dashboard-Workern

- Die Live-Worker-Karten zeigen neben dem Tagesertrag nun den Bruttoertrag pro kWh in der vom Nutzer gewählten Anzeigewährung. Die Berechnung verwendet denselben anteiligen Tagesertrag wie die Karte und die gemessene Workerleistung; ohne Prognose oder positive Leistung erscheint „—“. Kein API- oder Mining-Vertrag geändert.
- Verifikation: `node --check pc-agent/src/main/resources/static/overview.js` und `git diff --check` erfolgreich. Kein Live-Browser- oder Hardwarelauf ausgeführt.

## 2026-10-07 — GPU relay preamble excluded from proxy miner events

- The sibling proxy now waits for a real SRBMiner frame before opening visible miner telemetry. A connection carrying only the PC-Agent relay's internal `solarminer.route` preamble no longer emits `Miner connected/disconnected` for RVN, ETC, DCR or QTC. The proxy regression covers all four coins; `:pc-agent:standaloneJar --offline --no-daemon` rebuilt successfully with the changed embedded proxy source.
- No PC-Agent route or fee policy changed. The rebuilt JAR was not restarted or deployed, and a live proxy/miner log is still required to determine whether the reported running process also has genuine upstream reconnects.

## 2026-10-07 — PC-Agent coin quotes and saved-wallet header

- Added a separate `/api/agent/local/market-prices` read of Currency Service `/coin-prices`, so BTC/DCR/QTC quotes are not blocked by missing mining-network snapshots. Forecasts still require a complete fresh C9 network row; an isolated quote is not a profitability result. Market quotes use a ten-minute refresh and at most two-hour last-good age.
- The global header now derives its wallet chips from the saved per-coin configurations in Agent overview. Saving a wallet immediately triggers a header reload, including DCR/QTC and other configured GPU coins. XMR/PRL retain their real pool/on-chain balances where supported; other coins explicitly show that pool credit is unavailable instead of presenting zero as a balance. BTC quote is shown independently, since this PC-Agent has no BTC wallet/miner configuration.
- Full `:pc-agent:test :pc-agent:standaloneJar --offline --no-daemon` passed; JavaScript syntax checks passed. The central public Currency Service still omitted DCR/QTC at observation time; its own repository needs rollout. No running Agent or central service was restarted or deployed; browser/device and real pool-balance checks remain open.

## 2026-10-07 — PC-Agent Worker actions use per-device pending state

- Cause: `workers.js` used one `busy` flag for all rows, so starting, pausing or changing one worker disabled every other worker's Start/Pause and Change buttons until the request and reload completed.
- Change: the Worker page tracks pending requests by `deviceId`; it disables only the affected row and closes a submitted assignment dialog so other workers can be controlled. The global Node-control switch remains guarded while a worker request changes the shared settings profile. No API contract changed.
- Verification: JavaScript syntax and `git diff --check` pass. Concurrent browser interaction has not been exercised against a live PC-Agent.

## 2026-10-07 — PC-Agent miner status during GPU startup

- Cause: the RVN/ETC/DCR/QTC GPU service marked a live SRBMiner process `PAUSED` until its pool/API health flag became true; Pearl did the same for individual GPUs. The worker API compared display algorithm labels with miner identifiers, so a running Quantus worker fell back to `STOPPED`. The shared dashboard mapper likewise failed to associate technical GPU algorithm identifiers with their coins.
- Change: managed GPU process state now stays `MINING` during pool connection and API warm-up, with `poolHealthy`/connection detail still reporting readiness. Worker and shared browser mappings use the miner's technical algorithm identifiers for RVN, ETC, DCR and QTC. No mining route or proxy contract changed.
- Verification: focused `WorkerAssignmentServiceTest` and `GpuCoinMinerServiceTest` passed with Gradle offline; Node syntax check and a direct shared-component mapping check passed for all four GPU coins. Live miner/pool startup was not available for this change.

## 2026-10-07 — PC-Agent ETC readiness without Stratum probe traffic

- The running GPU monitor calls `ProxyConfigurationService.miningReady` about every five seconds. Its former raw TCP connect/close to ETC port 3337 produced the operator's paired proxy `Miner connected`/`Miner disconnected` events with no Stratum message. `stratumReachable` now reads the existing HTTP `/api/dashboard` coin `listenerStatus` and checks the configured port; no miner socket is opened for readiness. The sibling proxy also counts miners only after their first Stratum frame and acknowledges the optional extranonce extension locally.
- Verification: `ProxyConfigurationServiceTest` exercises an online/offline dashboard with no Stratum listener, and full `:pc-agent:test :pc-agent:standaloneJar --offline --no-daemon` passed (75 tests). The sibling proxy full suite passed (24 tests). The updated standalone JAR is in `pc-agent/build/distributions/solarminer-pc-agent-standalone.jar`; no running process was restarted or deployed. Real SRBMiner longevity, accepted shares and fee accounting remain unverified.

## 2026-10-06 — GPU Stratum connection and Quantus protocol repair

- Owner: PC-Agent `mining/GpuStratumRelay`, `pearl/GpuCoinMinerService`, sibling proxy adapters/MinerSession; shared C2 documentation updated in admin-portal. Existing UI and configuration work was preserved.
- A process-scoped loopback relay supplies the encoded route to the configured SolarMiner proxy before forwarding unmodified miner bytes. This resolves the subscribe-before-authorize routing deadlock without fake extranonce. It supports miner reconnects and closes sockets/listener on process failure, exit or stop. ETC uses documented SRBMiner `--esm 2`, RVN `--nicehash true`; QTC retains its own login dialect.
- A bounded Kryptex QTC probe confirmed the old Ethereum-like Quantus adapter was wrong: login returns a pool session token and an object job carrying mining_hash, target, difficulty and extranonce. The embedded source now includes a dedicated Quantus adapter, request-ID correlation, hex RVN/ETC job IDs, negotiated nonce parameters and configured-route handshake replay. See [protocol evidence and gates](../../../solarminer-stratum-proxy/docs/agent-wiki/gpu-stratum-repair-2026-10-06.md).
- Verification: JDK `/home/lukas/.jdks/graalvm-ce-21.0.2`; `sh gradlew :pc-agent:test :pc-agent:standaloneJar --offline --no-daemon` succeeded. **74 PC-Agent tests pass**, including two real TCP relay tests and command-mode assertions. Three stale mocks/expected coin lists were updated for already-present DCR/QTC paths. Sibling proxy `sh gradlew test --offline --no-daemon` succeeded with **23 tests**, including a Netty/TCP handshake test. Diff checks pass.
- Artifact: `pc-agent/build/distributions/solarminer-pc-agent-standalone.jar`, containing the updated embedded proxy. No running process was restarted, no GPU mining was started, no real share or payout was submitted, and no service was deployed. External proxy users must upgrade both Agent and proxy. Accepted user/house/referral shares and actual pool credits remain open verification gates.

## 2026-10-05 — PC-Agent UI/UX redesign: dashboard KPIs, miner-instance model, new Worker and Pools pages

- Scope and owner: complete redesign of the standalone PC-Agent web UI (`pc-agent/src/main/resources/static`), specified in [PC-Agent UI/UX-Redesign](pc-agent-ui-redesign.md). No cross-repository contract changed; the additive `MinerStats.Worker.pool` telemetry added earlier is the only backend input the new pages need.
- Information architecture: eight pages in three groups (Betrieb / Optimierung / System). New `workers.html` (all CPU/GPU workers across every miner) and `pools.html` (pool target per coin). Existing URLs unchanged; `mining.html` keeps its address but its coin bar became an instance switcher.
- Mining model: **miner instance = coin × miner build**, matching what `MinerCatalogService` and `selectedMiner` already model. Several builds per coin can be installed, exactly one is the active build, and switching is an explicit action that keeps the pool/wallet/device configuration. The detail view is split into Status, Geräte, Konfiguration (three guided steps with defaults), Erweitert and Konsole; the library groups packages by coin with multi-select install.
- Shared components: new `components.js` is the single source for number/unit formatting, status pills, sparklines, sortable tables and the scope bar, so Dashboard, Miner, Worker and Pools use one language. New `pool-catalog.js` replaces the hardcoded `<option>` pool lists; `design.css` carries the token-driven visual system.
- Frontend resilience pattern: SSE `GET /api/agent/local/events` is the primary source; `stream.onerror` only closes the stream, and a 2 s interval reconnects and polls when data is older than 4 s (1.5 s without a stream). Only a failed `fetch` marks data stale, so a blocked event stream no longer freezes the page.
- Worker table: fixed column layout with the identity and action columns pinned inside the horizontal scroll zone; per-row start/pause resolves GPU routes through `overview.gpus`, because `GpuState` carries only vendor+index while `MinerStats.Worker.deviceId` is the power-service id.
- Worker list (second round of user feedback: even the paused tab was wrong — the same GPU was listed twice, once running under one coin and once paused under another, which implies more simultaneous miners than the hardware allows). The list is now the running set only (`MINING`, `ERROR`): a paused miner contributes no row and no running coin chip, and there is no status filter at all, because switching between running and idle devices would offer a view that must not exist. Capacity is stated instead of implied — the hashrate KPI reads `2/2 GPUs im Einsatz`, and any card that runs in no miner raises `1 GPU läuft gerade in keinem Miner · Geräte zuteilen →` above the table. Coin, hardware and search stay a separate scope axis with `n ausgeblendet · Filter zurücksetzen`. The dashboard worker panel now uses the same rule through the new shared `ui.operatingRows()` / `ui.idleDeviceNotice()` helpers, so both surfaces list one row per device and name hardware that runs in no miner; its KPI detail reads `Aktiv / gemeldet` because the reported count still includes idle miners.
- Pool switch: posts the new target together with the existing wallet, worker, proxy route and devices, so switching a pool cannot erase a payout configuration. CPU coins deliberately omit `proxyUrl`/`devices`.
- Pools headline (same objection, third surface): `Verbundene Worker 0/13` summed every coin's device selection, so one GPU selected for three coins counted three times and the denominator could never be reached by this agent. The first KPI is now `Laufende Geräte n/m` over distinct hardware (GPU cards + CPU) with the stable-pool-contact count as its detail line; the per-coin fact is renamed `Pool-Kontakt` and stays scoped to that one coin's selection, where the ratio is a true statement about a single miner.
- i18n: German remains the source language; `i18n.js` gained the missing exact entries and regex patterns for the redesigned pages, including dynamic lines such as `1 running worker`, `2/2 GPUs in use`, `No miner is using 1 GPU.`, `1 hidden · Reset filters`, `Pool fee 1%` and `Best share difficulty`. Entries for the dropped status tabs (`Alle Status`, `Fehler anzeigen`, `n/… Worker aktiv`) were deleted instead of left as dead translations.
- Verification: `.codex-qa/pc-agent-redesign.cjs` (headless Chromium, fixture-driven) — all 14 checks pass: eight pages render without page/console errors and without horizontal overflow at 1440 px and 390 px; mining tabs, per-GPU pause, wizard save payload, library open and per-build install; worker list contains only running workers, the fixture's paused duplicate of `GPU-demo-0` produces no row and no running coin chip, no status filter buttons exist, pausing a card leaves the list and raises the freed-GPU notice plus `1/2 GPUs im Einsatz`, and the scope reset restores the CPU worker; the dashboard worker table is asserted to hold the same running set; pool-switch payload, coin search, and a device denominator that stays at the installed hardware even though both GPUs are selected for three coins; full English rendering on Worker, Pools and Miner. The fixture now applies pause/resume to worker status and GPU `running`, so the capacity statements are exercised instead of asserted against static data. `sh gradlew :pc-agent:test --offline` → BUILD SUCCESSFUL. Screenshots in `.codex-qa/screenshots/pc-agent-redesign/`.
- Open gate: nothing was exercised against a real XMRig/SRBMiner process, real pools or real GPUs. The fixtures reproduce the verified API shapes; a live agent run must still confirm reported difficulty, latency, stale shares and per-GPU measured watts.

## 2026-10-05 — Compact multi-miner installation and normalized share telemetry

- Scope and owner: standalone PC-Agent Mining UI and miner-native XMRig/SRBMiner telemetry; the existing additive Core/Node worker-share contract is preserved.
- UI/UX change: the Mining catalog is grouped by actual software package rather than coin, so the shared SRBMiner installation is no longer repeated for PRL/RVN/ETC. Missing packages can be selected together and their downloads started as one action. Compact package rows show hardware, algorithms, supported coins, fee range, install state and direct coin entry points. Miner details now separate daily status, setup and diagnostics into task tabs; the protected removal and Defender recovery flows remain available.
- Telemetry change: `MinerShareTelemetry` normalizes XMRig good/total counters and both observed SRBMiner direct/nested counter shapes. Counters remain nullable when the API does not report them, are reset per process session, and are not exposed as live values for stopped workers. The local overview and Node miner details continue to display accepted/rejected counters without treating unavailable data as zero.
- Verification: `:pc-agent:test` passed in full; focused `MinerShareTelemetryTest`, `MinerCatalogServiceTest`, `MiningControllerValidationTest` and Core's `MinerAgentControllerConfigurationTest` passed. Root `compileJava`, React `tsc --noEmit`, JavaScript syntax checks and `git diff --check` passed. No compatible browser executable was available in the Linux environment, so the final visual breakpoint pass remains open.
- Hardware/pool gate: parser fixtures prove supported JSON shapes, not that every SRBMiner release/pool emits counters. Capture a live XMRig and SRBMiner API response during rollout and compare counters with the miner console/pool dashboard; miner-native shares are operational telemetry, not payout accounting.

## 2026-10-05 — PC-Agent worker share telemetry reaches Node miner details

- Cause: the PC-Agent already reports `acceptedShares` and `rejectedShares` for XMRig and SRBMiner workers, but Core's `MinerStats.Worker` and the Node-side REST DTO ended at `deviceId`. Jackson therefore discarded the additive Agent fields before the miner-details page could use them.
- Change: both Node worker-stat contracts now preserve nullable accepted/rejected share counters; `MinerAgentController` keeps them while normalizing idle power, and the miner detail endpoint/UI displays them per worker. Older miners and ASIC backends remain compatible and report no counters (`null`). The contract is additive, so rolling out Node/Core after an existing Agent is safe.
- Evidence: `MinerAgentControllerConfigurationTest.preservesAgentShareCountersWhenReadingWorkerTelemetry` serves a representative Agent status document and asserts both counters survive Core's read/normalization. `git diff --check` and `node --check pc-agent/src/main/resources/static/agent.js` pass. Gradle verification remains blocked in this environment because neither `JAVA_HOME` nor a `java` executable is available.
- Boundary: this fixes miner-native worker telemetry. A proxy share endpoint is a separate future contract for uniform proxy-observed accounting; it must count submit responses per protocol/session and cannot describe miners that bypass the proxy. The current external Agent namespace intentionally returns HTTP 403, including status reads, while `externalControlEnabled=false`; enable it and assign a worker/coin to expose the Agent worker list to the Node.

## 2026-10-05 — Disjoint PC-Agent local and external APIs

- Cause: the explicit external power routes checked `externalControlEnabled`, but legacy and shared routes did not. In particular, an older Node could call `POST /api/agent/pause`, stop Pearl and produce the diagnostic `Frontend pause request123` even with Node control disabled. Proxy, referral, miner-configuration and legacy power routes crossed the same ownership boundary.
- Contract change: all dashboard APIs moved to `/api/agent/local/**`; all SolarMiner-Node APIs moved to `/api/agent/external/**`. No shared or legacy control alias remains. Core discovery, status, telemetry, route configuration, coin configuration, earnings and control clients were updated together. This is an intentionally breaking Agent/Core contract and both artifacts must be rolled out together.
- Enforcement: `AgentWriteAccessFilter` requires a same-origin browser request for the complete local namespace. It requires `externalControlEnabled=true` for the complete external namespace, including GET status, discovery and telemetry. When disabled, nothing in the Node namespace reaches a controller. Existing per-worker and benchmark checks remain defense in depth.
- Verification: focused filter and external-controller tests cover disabled reads/writes, enabled external dispatch, local same-origin access and rejection of the legacy shared path. `:pc-agent:compileJava`, `:core:compileJava` and root `:compileJava` pass on Java 21. The complete PC-Agent suite passes; the full Core suite retains its two pre-existing failures in `MinerServiceRegistrationTest` and `SshGpuServiceTest` documented elsewhere in this log.

## 2026-10-04 — Central XMR/PRL/RVN/ETC currency and network snapshots

- Owner and contract: `currency-rates` now implements C9's coin-keyed latest snapshot for canonical `monero`, `pearl`, `ravencoin` and `ethereumclassic`. Public list and alias-aware detail routes include H/s, difficulty, block seconds, native block reward, USD price, timestamp/age, sources and bounded stale state. The price map also adds `rvn` and `etc`; current PRL uses Pearl's public price when CoinGecko omits its configured ID.
- Consumer: PC-Agent `EarningsForecastService` removed direct xmrchain, Kraken, Pearl and price-provider requests. It calls only `https://currency.solarminer.app/api/v1/public/mining-networks`, keeps a ten-minute local cache/last-good fallback and now produces XMR/PRL/RVN/ETC forecasts from algorithm-matched local workers. The Node ignores additive agent coins until its own complete `MiningCoin` path is registered, so the new RVN/ETC forecast does not enable Node automation prematurely.
- Persistence/failure: one complete row per canonical coin stores the provider collection instant. Incomplete refreshes are not persisted and do not write zeroes; retained rows become stale after two hours. Provider/reward details and guide scope are in [public currency/network data](currency-data.md).
- Verification: all 16 Currency-Service tests and all 50 PC-Agent tests pass under Java 21, including provider parsing, reward-era, alias, persistence, controller-contract and central HTTP-consumer coverage. The root app compiles and its focused `MiningCoinTest` passes. A temporary H2-backed service using live upstreams returned four fresh snapshots plus all five price keys; ETC alias lookup also passed. `currency.solarminer.app/api/v1/public/**` returned a Traefik 404 before rollout, so public reachability, MariaDB migration and restart persistence remain open.
- Coin-guide boundary: only the currency/network-data requirement and consumer were changed. Wallet, Stratum, fee, pool accounting, portal, hardware and release steps are non-applicable to this changeset; existing Pearl evidence is unchanged and RVN/ETC release gates remain closed.

## 2026-10-04 — RVN/ETC GPU-only SRBMiner start parameter

- Cause and change: `GpuCoinMinerService` started KAWPOW and ETCHash through SRBMiner's generic `--algorithm` option. On the available NVIDIA host this reached the proxy and received jobs, but the GPU initialization either reported insufficient allocatable memory or remained at 0 H/s. The same executable, GPU, proxy route and login produced hashes when started through SRBMiners explicit `--algorithm-gpu` option. RVN/ETC command construction now uses that GPU-only option; pool/login, GPU ID, API and fee-routing arguments are unchanged.
- Hardware evidence: SRBMiner-MULTI 3.7.1 on NVIDIA Titan RTX and driver 580.178.04 reached 31.80 MH/s KAWPOW and 55.38–55.45 MH/s ETCHash through the local SolarMiner proxy. ETC created its 4415 MB DAG. Both bounded diagnostic processes were stopped after confirmation. The earlier generic-parameter RVN run logged `not enough free memory [need 2271 MB : have 1306 MB]` and 0 H/s on the same GPU.
- Verification: focused `GpuCoinMinerServiceTest` passes on JDK 21 and asserts both coin commands use `--algorithm-gpu` and never the generic option. No accepted share, pool credit, fee switch, payout, Windows retest or RTX 2080 Ti retest was observed; the end-to-end release gate remains open.

## 2026-10-04 — Public pool API research for PC-Agent coins

- Scope: documentation-only assessment for XMR, PRL, RVN and ETC; no mining, proxy, fee, API or release contract changed. Findings and source links are in [PC-Agent pool API research](pc-agent-pool-api-research-2026-10-04.md).
- Evidence: current `WalletBalanceService` and wallet UI were checked against public pool/operator documentation. 2Miners documents wallet-specific balances and payments for RVN/ETC; MoneroOcean's official UI source calls wallet stats/payments; Goldenpool documents PRL wallet balances and payouts. Direct pool-status JSON was observed for ETC 2Miners and HashVault XMR. Live wallet credits, payout records and Stratum/fee compatibility for the new candidates remain unverified.
- Existing concurrent worktree changes were preserved. No build/test is meaningful for this research-only change; `git diff --check` was run.

## 2026-10-04 — RVN/ETC SRBMiner epoch error research

- No code or contract change. The [RVN/ETC integration record](rvn-etc-integration.md#research-on-couldnt-set-epoch-2026-10-04) now links documented SRBMiner cases involving insufficient free VRAM, driver buffer-allocation limits and Windows miner bugs, and compares them with the local RTX 2080 Ti logs. The exact local cause remains unknown. Direct-pool failures and verified PCI mapping exclude the proxy and iGPU assignment for this host; no new mining run, driver change or accepted share was performed.

## 2026-10-04 — PC-Agent GPU power-target check on Windows and Linux

- Owner: PC-Agent. No API or code contract changed. See [PC-Agent GPU power-target verification](pc-agent.md#gpu-power-target-verification-2026-10-04).
- Evidence: Windows `nvidia-smi` read a RTX 2080 Ti at 300 W with a 100–366 W range; the running Agent reported dynamic scaling support. A same-value `nvidia-smi -i <UUID> -pl 300` under the sandbox account failed with `Insufficient Permissions`, while readback stayed at 300 W. Source inspection confirms the Agent uses that command and verifies readback. The Windows launcher has no elevation step.
- Linux NVIDIA follows the same command path and its Docker overlay requests GPU access plus NVIDIA `compute,utility`; no Linux hardware execution was available. AMD remains start/stop only. Targeted Gradle tests could not run because this sandbox could not download Gradle 8.14.5. The outstanding gate is an Agent-token write/readback/restoration probe on Windows and a real NVIDIA-container probe on Linux.

## 2026-10-03 — RVN/ETC Kryptex choice and SolarMiner payout default

- Scope: PC-Agent local RVN/ETC pool configuration plus the GPU proxy's user-pool login rewrite; shared contract recorded in the admin encyclopedia and details in [RVN/ETC integration](rvn-etc-integration.md). Existing concurrent Windows sensor edits were preserved.
- Change: local forms offer Kryptex Global/Europe/North America, custom pool, or explicitly consented SolarMiner payout. Empty RVN/ETC wallet resolves only a marked fee-backend house target and saves its pool, wallet and worker together. Its `WALLET.WORKER` login is split for validation; an own wallet retains the selected pool. The proxy forwards `WALLET/WORKER` when the user pool host is Kryptex. No fee target or mining-start gate was relaxed.
- Evidence: Kryptex's official RVN/ETC pool pages publish ports 7031/7033 and wallet/worker login. Controller, payout-target and proxy unit cases were added; `node --check` passes. Gradle test execution was unavailable in this sandbox because the wrapper could not download its distribution, and the cached installation failed to load `native-platform.dll`. No real share or payout evidence; the existing end-to-end release gates remain open.

## 2026-10-03 — Windows PC-Agent sensor elevation and storage safety

- Scope: PC-Agent Windows telemetry bootstrap and standalone operator guidance; no mining, proxy, Node, pool or fee contract changed.
- Change: LibreHardwareMonitor no longer starts automatically when the Agent becomes ready. It can only be started through the existing explicit local Telemetry UI action. Before that process launches, its config now writes `/storage/enabled=false`, disabling disk/SMART enumeration at the collection source rather than merely filtering returned telemetry. The optional-monitor enable setting remains configurable, with legacy `windows-lhm.autostart` respected as a fallback for explicit-start availability.
- Reason and evidence: a fresh Windows 11 host repeatedly displayed elevation prompts for `diskpart.exe` while the Agent's telemetry polling was active; closing the Agent stopped the prompts although LibreHardwareMonitor remained open. The Agent's current source calls the local LibreHardwareMonitor JSON API for telemetry, while its own elevation command launches only `LibreHardwareMonitor.exe`. LibreHardwareMonitor's upstream configuration documents `/storage/enabled=false` as disabling storage collection at source.
- Verification: source inspection and `git diff --check` passed. `:pc-agent:test --no-daemon` was attempted, but this workspace has no JDK 21: Gradle is launched with JDK 25 and stops before compilation even though `build.gradle.kts` requires Java 21. Runtime verification is still required on the affected Windows host: start the Agent without opening Telemetry (no UAC prompt), then explicitly start sensors and confirm no `diskpart.exe` prompt while CPU/GPU telemetry remains available.

## 2026-10-03 — RVN / ETC PC-Agent integration assessment

- Owner: PC-Agent / Node integration. The [RVN / ETC record](rvn-etc-integration.md) captures canonical keys, selected initial miner/pool combination, required cross-repository contracts, inapplicable first-scope paths and release gates.
- Source evidence: SRBMiner-MULTI documents `kawpow` and `etchash`; 2Miners documents a pool path for each. Short local fake-pool captures recorded SRBMiner 3.7.1 subscribe/authorize calls. Read-only 2Miners probes recorded real subscribe, authorize, difficulty/target and notify responses for both coins. Exact shapes are in the integration record. Local `MiningService`, proxy configuration and Node coin catalog have no RVN/ETC path. This change adds no Node mining code and does not claim support.
- Environment: Windows `nvidia-smi` detected RTX 2080 Ti and SRBMiner listed it as CUDA GPU1; Docker Desktop daemon access was denied. No `mining.submit`, accepted share or fee-accounting test was run. House and test wallets are not yet available.

## 2026-10-02 — Local Node frontend UI/UX audit

- Scope: analysis only of the local React/Vite Node operator UI. No product code, API contract, device control, mining route or payment was changed. See [the detailed audit](ui-ux-audit-2026-10-02.md).
- Evidence: source inspection plus Chrome renderings with synthetic API responses at desktop 1440×1000 and mobile 390×844; header geometry checked from 390 through 1920 px. Local diagnostics/screenshots are at workspace `.codex-qa/uiux-audit*.cjs` and `.codex-qa/screenshots/uiux-2026-10-02/`.
- Reproduced: clipped header controls, unreachable preferences at intermediate breakpoints, start/dashboard profile-check errors rendered as loading, missing focus entry/Escape behavior in selected dialogs, setup draft loss after reload and a wallet error without retry. Static color pairs used for small dashboard labels measure below 4.5:1. Source evidence confirms default-on setup telemetry, inconsistent preferences/formatting and gross-only wording behind the profitable-grid UI.
- Deliverable: 18 prioritized findings, proposed navigation/dashboard/automation/finance design, accessibility recommendations, phased implementation and user-study tasks. Recommendations remain unimplemented. Browser fixtures establish UI behavior only; no real hardware/pools/payments, full wizard completion, full accessibility certification or user study was performed. No product build/test was needed for documentation-only changes; `git diff --check` was run.

## 2026-10-02 — Beta Docker publishing

- Scope: `beta` branch Docker publishing for Solar-Miner-Node; no API or mining contract changes.
- Change: `.github/workflows/docker-beta.yml` publishes commit-specific tags ending in `-beta`, per-architecture moving tags such as `latest-amd64-beta` / `latest-arm64-beta`, and multi-architecture `latest-beta` manifests for Core and Currency Rates. Frontend JVM tags retain the JVM discriminator (for example `latest-amd64-jvm-beta` and `latest-jvm-beta`). PC-Agent publishes linux/amd64 tags `latest-amd64-beta` and `latest-beta`, and embeds the proxy's `beta` branch. The workflow also publishes a commit-specific GitHub prerelease with the standalone JAR, checksum and beta start scripts after the image and standalone JAR builds succeed.
- Launcher: `start-agent-beta.bat` / `.ps1` select the newest `pc-agent-beta-*` prerelease, verify the JAR checksum and use a separate `%LOCALAPPDATA%\SolarMiner\PC-Agent-Beta` install directory.
- Isolation: beta workflow does not publish release version tags or `latest`; release workflows remain tag-triggered.
- Verification: source/workflow review only; no GitHub Actions run, Docker build, PowerShell execution or test was run here. Registry/release permissions, the proxy `beta` ref checkout and actual launcher/image startup remain to verify.

### 2026-10-02 — PC-Agent benchmark sample-based completion

- Scope: standalone PC-Agent benchmark lifecycle and UI; request now selects only LIVE or INSTALLED mode.
- Change: removed fixed 30–300 second duration; each active worker now needs 12 valid positive-hashrate observations sampled every two seconds. A worker that stops or leaves the active state before reaching the target is reported as skipped/incomplete; sessions remain cancellable. INSTALLED continues to restore the previous miner state.
- Verification: source inspection and diff review only; tests/build not run.
- Limits: a worker that remains marked MINING but never reports a positive rate can keep the session open until cancelled; pool startup has existing SRBMiner health timeout, but no universal benchmark timeout is imposed.
- UI: interim per-worker medians and observation counts are rendered during collection. Reloading the benchmark page re-reads the active in-memory session and restores phase, sample progress and current results.
- User's existing frontend navigation change in `benchmarks.html` was preserved.

### 2026-10-02 — Benchmark warm-up and Pearl state restoration

- Scope: standalone PC-Agent benchmark lifecycle for XMRig CPU and SRBMiner GPU paths; no Node, proxy, fee or public API contract changed.
- Finding and change: the sample-based run started Pearl and immediately filtered the worker snapshot to `MINING`. SRBMiner intentionally remains display-`PAUSED` until its asynchronous local API confirms pool/job health, so the benchmark skipped Pearl before the first health poll. INSTALLED runs now retain all configured workers of their phase as expected workers, wait up to 95 seconds for their first active status (matching SRBMiner's 90-second job-start limit plus scheduling margin), then collect twelve positive samples. The same phase/status rule applies to Monero CPU and Pearl GPU workers; LIVE remains read-only and only includes workers already active. The previous Pearl state is captured from managed process IDs rather than the pool-health-gated display status, preventing a connecting Pearl process from being left paused after the run.
- Verification: `git diff --check`; `:pc-agent:test --tests de.verdox.solarminer.pcagent.mining.BenchmarkSessionServiceTest --no-daemon` and the complete `:pc-agent:test --no-daemon` passed with Temurin 21. The focused test covers a warming GPU, CPU/GPU phase selection, immediate errors, post-start loss and the startup deadline.
- Remaining gate: actual Windows/Linux XMRig and SRBMiner runs, including a slow real pool connection and process restoration, still require host verification. A worker that stays active yet reports zero hashrate remains cancellable rather than receiving an artificial measurement timeout.

### 2026-10-02 — Reset miner console at process restart

- Scope: PC-Agent's local XMRig/SRBMiner console files and Mining frontend; no Node or external API contract changed.
- Change: a managed XMRig start now truncates its console file before recording its start marker. A SRBMiner GPU start does the same for its GPU-specific console; when no other managed Pearl GPU is running, it also resets the aggregate Pearl console. The console endpoint now returns a per-run identifier, and the frontend clears its rendered terminal plus byte offset when that identifier changes. Therefore a truncated file cannot leave old DOM output visible or cause an offset-based gap.
- Verification: `MinerConsoleServiceTest` covers truncation and run-ID change; `node --check pc-agent/src/main/resources/static/agent.js` and `:pc-agent:test --no-daemon` passed with Temurin 21.
- Remaining gate: visual verification in a browser while restarting XMRig, a single Pearl GPU and a multi-GPU Pearl run remains required.

### 2026-10-02 — Explain benchmark miner pauses in the console

- Scope: PC-Agent benchmark orchestration and local XMRig/SRBMiner consoles; no Node, proxy, fee or external API contract changed.
- Change: benchmark phases now write `[Benchmark]` entries after start, worker discovery, worker loss, phase completion, intentional pause and final state restoration. Pearl writes the entries to its aggregate console and every known GPU console. The intentional post-measurement stop is therefore distinguishable from an SRBMiner exit, and a skipped/failed worker includes its worker key in the same terminal.
- Verification: `:pc-agent:test --no-daemon` passed with Temurin 21; visual runtime confirmation remains open.

### 2026-10-02 — English PC-Agent console messages

- Scope: PC-Agent-generated XMRig, SRBMiner and benchmark console entries; no mining protocol or API contract changed.
- Change: process start/stop, watchdog, payout, failure and benchmark orchestration messages are now English. The reset marker for a new console run is also English. Raw stdout/stderr from XMRig, SRBMiner and the operating system is preserved verbatim because it is external diagnostic output rather than a PC-Agent-authored message.
- Verification: `:pc-agent:test --no-daemon` passed with Temurin 21.

### 2026-10-02 — Attribute PC-Agent miner stops

- Scope: PC-Agent XMRig/SRBMiner process-stop diagnostics; no Node, proxy or mining wire contract changed.
- Change: a thread-scoped stop context now reaches the actual XMRig and SRBMiner process-stop boundary. The console writes `Stop requested by: <source>` immediately before the normal stopped entry. Benchmark stops identify their precise phase reason; local power/pause controls and remote-control API stops identify their control path. Nested calls preserve the original, more specific initiator.
- Verification: `:pc-agent:compileJava --no-daemon` and `:pc-agent:test --no-daemon` passed with Temurin 21. Host/browser verification remains pending.

### 2026-10-02 — Show benchmark upload outcomes

- Scope: standalone PC-Agent manual and periodic benchmark uploads.
- Change: the agent retains the latest upload outcome and exposes it to the Benchmarks page. Success, failure, no eligible measurements and disabled sharing are shown; failed manual uploads retain their sample batch in memory for a retry button. Periodic upload failures are also shown and remain scheduled for the next retry interval.
- Verification: source inspection and `git diff --check` only; tests/build not run.
- Limits: retained retry data and upload status are in memory and are lost if the PC-Agent restarts.

### 2026-10-02 — PC-Agent navigation consistency

- Scope and owner: standalone PC-Agent static frontend in Solar-Miner-Node; no API or cross-repository contract changed.
- Implemented behavior and code evidence: all six page headers now render the same navigation entries in the same order (Overview, Mining, Benchmarks, Hardware, Telemetrie, Proxy), with only the current page marked selected. Files: `pc-agent/src/main/resources/static/{index,mining,benchmarks,hardware,telemetry,proxy}.html`.
- Verification performed and result: source comparison and `git diff --check`; tests/build not run.
- Remaining gaps / hardware or rollout gates: none for this static navigation correction.
- Documentation updated: this work-log entry.

Use a short dated entry for changes that affect architecture, contracts, mining support or documentation truth. Link commits/issues when available. Do not paste secrets or raw wallet credentials.

## 2026-10-02 — Exclude idle PC power from mining statistics

- Scope: PC-Agent worker aggregation, Node agent-stat normalization, site/live dashboard power, mining profitability estimates and stored miner power history. No API shape changed.
- Change: only sum worker power while that worker is `MINING`; zero idle worker power when the Node reads agent stats; don't classify combined host sensor power as mining draw; exclude inactive miners from earnings forecasts and write zero mining power/efficiency for inactive history samples. Site aggregation also ignores non-mining miner statuses.
- Verification: source inspection and `git diff --check`; tests/build not run. Existing stored historical samples are not rewritten.

## Entry template

### YYYY-MM-DD — change

- Scope and owner:
- Implemented behavior and code evidence:
- Cross-repository contracts checked:
- Verification performed and result:
- Remaining gaps / hardware or rollout gates:
- Documentation updated:

### 2026-10-02 — C03 PC-Agent budget planning and safe restart state

- Task-ID/status/owner: C03 — `lokal implementiert – Live-Gate offen`; die Workflow-Abnahmevoraussetzung A01 → C02 ist zusätzlich offen, weil die additive Power-Control-Antwort die Agent/Core-Grenze berührt. `pc-agent` in Solar-Miner-Node. The PC-Agent/device-integration owner owns the remaining supported-host checks. No fee, proxy, Core-controller behavior or hardware-driver contract was changed.
- Problem and evidence: `MiningService.setTarget` previously calculated CPU/GPU allocation inline with worker/driver side effects, retained only mutable desired-watt fields, and exposed no requested-versus-applied outcome. Its `//TODO` suggested persistence without defining a safe restart behavior.
- Implemented behavior and code evidence: `PowerBudgetPlanner` deterministically allocates only locally eligible CPU and GPUs with verified dynamic limits, preserving the prior policy of reserving selected GPUs' minimum watts before allocating the remaining budget to CPU. `MiningService` applies the resulting plan through the existing XMRig, SRBMiner and GPU-driver services. On a GPU-limit write failure it stops GPU workers and applies a CPU-only fallback when possible; failures and incomplete process starts are represented explicitly in `PowerApplicationState`. The state starts `IDLE` with zero watts after every agent process start, is not persisted, and therefore cannot auto-start mining. `AgentPowerController` retains every existing response field and adds requested/applied CPU/GPU target, result status and failure reason.
- Cross-repository contracts checked: Core's `MinerAgentController` reads the established `currentTargetWatts`, range and usage fields from `/api/agent/power-control/external-status` as a map. The new fields are additive and ignored by existing Core consumers. The audit workflow nevertheless requires A01's versioned C2 fixture and C02 acceptance before C03's Agent/Core-facing result can be accepted. C7 host telemetry is untouched; unavailable sensor semantics remain unchanged.
- Verification performed and result: `PowerBudgetPlannerTest` covers CPU-only, GPU-only, mixed allocation, manual pause/external opt-out represented as unavailable capability, explicit pause, partial GPU fallback state and safe restart state. `javac --release 21` compiled `PowerBudgetPlanner` and `PowerApplicationState`; the focused test and then `gradlew.bat -Dorg.gradle.java.home=C:\Users\Lukas\.jdks\temurin-21.0.8 :pc-agent:test --no-daemon` both passed. `git diff --check` passed.
- Behaviour matrix: a valid CPU-only, GPU-only or mixed target deterministically produces the same allocation policy as before; zero pauses; below-minimum/no-eligible targets are rejected after a safe pause; manual pause and an external worker opt-out remove that worker's capability; failed GPU-limit writes use a CPU-only partial result when safe; a fresh `PowerApplicationState` is `IDLE` with zero watts. These cases are named in `PowerBudgetPlannerTest`; process/driver effects remain a real-host probe.
- Remaining gaps / hardware or rollout gates: the complete `:pc-agent:test` suite passed, but a supported Windows/Linux host must exercise CPU/GPU mixed budgets, GPU limit read-back/write failure, miner process failures, and measured wattage. AMD remains start/stop-only. The response reports an application result but cannot prove physical wattage without driver telemetry and a real host.
- Migration, rollback and next step: no persisted data or existing response field was changed; the added status fields are additive and current Core map readers ignore them. Rollback removes the planner/state handoff and the additive fields, restoring the former inline allocation. Next C03 gates: complete A01 → C02 contract acceptance, then the PC-Agent/device-integration owner records driver/process probes. Only both gates permit classification as `verifiziert`.
- Documentation updated: this work-log entry, `pc-agent.md`, the repository README review queue, and `architecture-audit/18-evidence-index.md` / `20-implementation-status.md`.

### 2026-10-02 — C05 CGMiner transport bounds

- Status, scope and owner: `lokal implementiert – Live-Gate offen`. Task C05 is owned by `cgminerapi` in Solar-Miner-Node; Core remains the runtime consumer through `BraiinsController`. A device/integration operator owns the remaining real-ASIC probe.
- Problem and verified cause: `CGMinerClient.executeRaw` opened an unbounded socket and stopped after a read whenever `InputStream.available()` returned zero. A temporarily empty buffer could therefore truncate a delayed response, while an unresponsive miner could hold the caller indefinitely. The client also changed its caller-owned `ObjectMapper` and printed the complete request to stdout.
- Behavior matrix and implemented change: a normal delayed response is accumulated until NUL or EOF and parsed; an empty interval beyond the read bound raises `SocketTimeoutException` rather than returning partial JSON; malformed/truncated EOF remains a Jackson `IOException`; NUL completes the frame without waiting for peer close. `CGMinerClient` now applies a 5-second connect and 10-second inactive-read timeout, copies the mapper before applying local settings, and logs only parameter presence.
- Preserved invariants and contracts: command names, request JSON, response DTOs and the `BraiinsController` call site are unchanged. No REST or cross-repository contract changed; Antminer CGI and Braiins GraphQL/gRPC are outside this task.
- Verification performed and result: with `$env:JAVA_HOME='C:\Users\Lukas\.jdks\temurin-21.0.8'`, `.\gradlew.bat :cgminerapi:test --tests de.verdox.cgminerapi.CGMinerClientTest --no-daemon` passed all five loopback cases. Under the same JDK, `.\gradlew.bat :cgminerapi:test :core:test --no-daemon` passed the complete CGMiner module and `:core:compileJava`; the Core test task executed 16 tests but remained red in `MinerServiceRegistrationTest` and `SshGpuServiceTest`, both outside the C05 diff. `git diff --check` passed.
- Prerequisites and evidence limit: the repository contains captured JSON response bodies, but their provenance does not prove whether supported firmware terminates TCP frames with NUL, EOF or another timing pattern. The loopback tests characterize the locally supported NUL/EOF semantics; they do not satisfy the required real-firmware framing gate.
- Remaining gate and next verification: before rollout, the device/integration owner must capture a representative response from every supported ASIC firmware family, confirm framing, exercise delayed responses under load, and verify that the timeout bounds do not reject a valid device. Until then C05 is not `verifiziert` or production-approved.
- Migration and rollback: there is no data or wire migration. Rollback restores the previous `CGMinerClient` transport implementation; it would also restore the known unbounded/truncating behavior. Independent audit work may continue, but C05 closes only after the real-device probe and a green relevant Core consumer test.
- Documentation updated: repository responsibility map, review queue and this work-log entry. The architecture-audit snapshot was not changed because its C05 finding and acceptance gate remain accurate.

### 2026-10-03 — Currency service public endpoint and coin-data rule

- **Architecture/owner:** `currency-rates` is designated as the standalone public read service at `https://currency.solarminer.app/api/v1/public/**`; Landing's `api.solarminer.app` remains a separate landing facade. Production Node configuration now defaults `solarmining.currency-service.url` to that public URL and preserves the `CURRENCY_MICRO_SERVICE_URL` override. Local Compose continues to supply its internal `http://currency-service:8080` value.
- **Public boundary:** the service may expose versioned, read-only aggregated currency, coin-price and network snapshots. It must never expose wallet addresses, workers, pool credentials, referral state, individual telemetry or administration. Current public routes and their wire shapes remain unchanged; this change does not itself deploy DNS/Traefik or create a new network-statistics endpoint.
- **New-coin rule:** every coin used by a Node or public profitability calculation must gain price and mining-network tracking with canonical key/ticker, source, unit/precision, timestamp, cadence, persistence, bounded stale/error semantics and provider/contract/consumer tests. Existing BTC/XMR/PRL price collection alone is insufficient; ETC requires the canonical `ethereumclassic`/`ETC` data path when it is introduced. New assets must use a coin-keyed snapshot model rather than another BTC-only table. The shared C9 contract and `NEW-MINING-COIN-GUIDE.md` now make this a mandatory gate.
- **Verification/rollout:** configuration and documentation change only. `currency-rates` provider collection was not extended and no public deployment/DNS/ingress probe was made. Add the currency service to central Traefik with the `currency.solarminer.app` router, TLS and read-only public-route checks before representing the host as deployed.

### 2026-10-03 — Currency service MariaDB Compose compatibility

- **Problem/fix:** local and planned central Compose configurations set `MYSQL_URL`, `MYSQL_USER` and `MYSQL_PASSWORD`, but `currency-rates` previously ignored them and always used its H2 URL. `application.properties` now maps those variables to Spring's datasource configuration with the same local H2 fallback, and the module declares the MariaDB JDBC runtime driver.
- **Deployment contract:** `currency-rates/PUBLIC-DEPLOYMENT.md` supplies the central Compose additions for Traefik, a private MariaDB network and TLS host `currency.solarminer.app`. It deliberately omits host `ports` for the service/database, keeps local Compose private, and explains DNS, persistence, ingress and future restricted-CORS/rate-limit checks.
- **Verification:** with Temurin 21, `gradlew.bat :currency-rates:test --no-daemon` passed after the approved Gradle/MariaDB dependency download. The suite uses H2 and validates code/test compatibility; no MariaDB container, DNS record, Traefik ingress or public provider response was started or verified.

### 2026-10-02 — C02 Core controller registration and routing boundary

- Scope and owner: Task C02, `core` in Solar-Miner-Node. This is a local implementation pending its workflow prerequisites and acceptance; no mining device, pool, proxy, or other repository was changed.
- Implemented behavior and code evidence: `MinerService` now creates one instance each of the Agent, Antminer, Braiins and monitoring-only 21energy adapters, then registers an immutable descriptor for every `MiningOS`. The descriptor owns supported capabilities and the device-specific pool-routing action. Pool target dispatch no longer switches on `MiningOS`; the C2 username remains `<pool-host:port>;<worker>;x`, Agent remains on proxy port 3335, Antminer remains on 3333, and Braiins retains its native-fee route. Missing proxy IP now returns `false` for proxy-routed configurations instead of formatting `stratum+tcp://null`. 21energy remains without power-control or pool-routing capability.
- Cross-repository contracts checked: C2 only; its existing credential format and ports were preserved. No proxy or fee-backend contract change was made.
- Verification performed and result: added `MinerServiceRegistrationTest` covering same-instance stats/control/pool dispatch, C2 formatting, absent proxy and unsupported OS paths, and the 21energy monitor-only gate. `javac --release 21` compiled the two new registration types against the existing Core classes, and `git diff --check` passed. The focused Gradle test could not start in this environment: the project wrapper now resolves, but Gradle 8.14.5's Kotlin DSL fails before project evaluation under the installed JDK 25.0.1 (`IllegalArgumentException: 25.0.1`); the project requires JDK 21.
- Remaining gaps / hardware or rollout gates: run `:core:test --tests de.verdox.pv_miner.core.service.MinerServiceRegistrationTest` with JDK 21, then the existing Core suite. Core now refuses unsupported dynamic-power calls, but the root Node UI still derives its capability badges from its separate `MiningOS` enum; a cross-process capability API would need a separately versioned Node/Core change.
- Prerequisite, rollback and next task: A01's versioned producer/consumer C2 fixture is required before C02 can be accepted; this local test does not replace it. No migration or wire-version change was made. Rollback is limited to restoring the previous internal `MinerService` wiring. After A01, rerun the focused test and Core suite with JDK 21, then classify C02 again.
- Documentation updated: this work-log entry.

### 2026-10-02 — Currency-service scheduling and snapshot integrity

- Task-ID/status: B01 — `lokal implementiert – Live-Gate offen`. The focused module suite passes with the local Temurin Java 21; a real historical-provider payload and deployed scheduler behavior remain to be verified.
- Owner/repositories: `currency-rates` in Solar-Miner-Node; no other repository was changed.
- Problem and cause: the audit found `@Scheduled` methods without `@EnableScheduling`, a dated request path fixed to `@latest`, raw HTTP calls without status/timeout bounds, and mutable five-request Bitcoin state that could be persisted as a mixed snapshot.
- Change and preserved invariants: `CurrencyRatesMicroService` enables scheduling. `DataGathererService` requests `@YYYY-MM-DD` for historical rates, publishes only complete immutable Bitcoin snapshots, clears a failed refresh before persistence, and has an optional startup-refresh switch for isolated tests. `HttpTextClient`/`JdkHttpTextClient` centralize finite timeout and non-2xx handling. Existing `/api/v1/public` routes, JSON fields/units, UTC semantics, H2 schema/path, and no-backfill behavior remain unchanged.
- Contracts/consumers: checked the Node `CurrencyMicroServiceRestClient`; `/bitcoin-stats`, `/exchange-rates`, `/exchange-rates/convert`, and `/coin-prices` retain their established response DTOs. No versioning or consumer update was needed.
- Tests/build/probes: added fake-upstream historical-date/persistence, complete/failed snapshot, HTTP non-2xx/timeout, and real Spring Boot scheduler tests. `git -c safe.directory=H:/Jetbrains_Workspace/Solarminer/Solar-Miner-Node diff --check` passed. With `JAVA_HOME=C:\Users\Lukas\.jdks\temurin-21.0.8`, `gradlew.bat :currency-rates:test --no-daemon` passed. The initial JDK-25 Gradle configuration issue was therefore environmental, not a module failure. A read-only `Invoke-WebRequest` probe of `@2025-02-03/.../usd.json` timed out after 15 seconds from this environment; it did not establish provider unavailability generally.
- Open gates and next check: from a deployment-capable network, capture a valid real dated provider payload and inspect H2 records before any separately reviewed data repair. Production monitoring must confirm scheduled outbound refresh behavior and provider units.
- Migration/rollback/next task: no migration or stored-data change. Roll back by reverting the B01 source/test changes. B01 is ready for integration verification but is not production-verified. Independently ready audit tasks remain subject to their own workflow prerequisites.
- Documentation updated: `architecture-audit/02-currency-service.md`, `07-refactoring-roadmap.md`, `18-evidence-index.md`, and this entry.

### 2026-10-02 — Windows miner antivirus block handling

- Scope and owner: PC-Agent XMRig/SRBMiner installers and Mining catalog; no cross-repository API contract changed. The existing `downloadStatus` field gains `BLOCKED_BY_ANTIVIRUS`.
- Implemented behavior: `WindowsAntivirusBlock` identifies a Windows file-access error caused by antivirus/PUA detection. Both installers report a dedicated status and safe guidance; the catalog shows Protection history instructions and offers only a user-triggered retry. No antivirus setting is changed. SRBMiner Windows uses the documented 3.7.0 release tag; Linux release selection remains unchanged.
- Evidence and verification: source paths are `WindowsAntivirusBlock`, both download services, `agent.js`, and `WindowsAntivirusBlockTest`. The official GitHub SRBMiner 3.7.0 tag exists; archive size and SHA-256 checks remain in the installer. Both JavaScript syntax checks, Java 21 compilation of the new classifier, and `git diff --check` passed. The focused Gradle test could not run offline: the wrapper attempted a blocked distribution download; the locally installed Gradle then could not resolve the Foojay settings plugin from its sandbox cache.
- Open rollout gate: inspect the exact Defender detection on an affected Windows host, submit any confirmed false positive to Microsoft, and verify the pinned asset's installation and mining on that host. A checked digest or a pinned version does not prove Defender acceptance.

### 2026-10-02 — User-controlled Defender folder exclusion guidance

- Scope and owner: PC-Agent Mining catalog and local overview. Monero and Pearl readiness now include `installDirectory`, derived from the same path used by their installers, so custom Pearl binary locations are reflected.
- Implemented behavior: after a Windows antivirus block, the catalog displays the exact folder and a copyable Microsoft `Add-MpPreference -ExclusionPath` command only when opened on the affected PC via loopback. It escapes PowerShell single quotes and rejects control characters. The operator must inspect Defender's finding and execute the command in an elevated PowerShell; SolarMiner makes no antivirus setting changes.
- Verification: JavaScript syntax checks and repository diff check passed. Java/Gradle verification remains blocked by unavailable offline Foojay settings dependency. No real Defender configuration was changed or verified.
- Open gate: run on an affected Windows host and verify that Defender accepts the chosen folder exclusion and the official miner archive. Managed policies or other antivirus products may override a local Defender exception.

### 2026-10-01 — mining PDF coin reward precision

- Scope and owner: Node mining PDF generation in `TaxReportService`.
- Implemented behavior and code evidence: daily and monthly/overall coin amounts now use coin precision (up to eight BTC decimals) instead of the two-decimal energy format. The mining value calculation and pool reward source are unchanged.
- Cross-repository contracts checked: no API or shared contract change; the fix only affects rendered PDF text.
- Verification performed and result: added `TaxReportServiceTest` for sub-cent BTC daily rewards and their sum; `git diff --check` passed. Gradle test execution was attempted offline but the configured Gradle distribution was unavailable and network access was denied, so the test result remains unverified.
- Remaining gaps / hardware or rollout gates: run the focused test and inspect an exported PDF in an environment with Gradle and pool data.
- Documentation updated: this work-log entry.

## 2026-10-01 — agent wiki baseline

- Scope and owner: repository-wide map and PC-Agent ownership map.
- Evidence: package layout under `pc-agent/src/main/java`, `AgentPowerController`, `MiningService`, `LocalGpuPowerService`.
- Verification: source inspection and documentation link check; no runtime, hardware or pool verification.
- Remaining gates: GPU driver matrix, third-miner abstraction decision, persisted power-target semantics.

### 2026-10-01 — earnings page reload route

- Scope and owner: React SPA route handling in Solar-Miner-Node.
- Implemented behavior and code evidence: added `/site/{siteId}/earnings` to `ReactFrontendController` so direct browser requests and reloads forward to the SPA entry point; the client-side route and earnings API endpoint already existed.
- Cross-repository contracts checked: none; local frontend routing only.
- Verification performed and result: confirmed route exists in the SPA router and API mapping; build/tests not run.
- Remaining gaps / hardware or rollout gates: none identified for this routing fix.
- Documentation updated: this work-log entry.

### 2026-10-01 — earnings data diagnostics

- Scope and owner: Node earnings API and React earnings page.
- Implemented behavior and code evidence: API now returns per-coin missing/stale input diagnostics and source labels, plus snapshot-level exchange-rate/tariff diagnostics. The page displays these details and reports HTTP/network errors. PC-Agent refresh failures retain a provider label and safe exception class; server logs retain the detailed exception.
- Cross-repository contracts checked: PC-Agent forecast fields (`sources`, `unavailableReason`, `stale`, `updatedAt`) remain consumed as provided; the Node earnings response adds optional JSON fields.
- Verification performed and result: source call sites and DTO construction inspected; `git diff --check` clean. Build/runtime and tests not run.
- Remaining gaps / hardware or rollout gates: none for diagnostics; unavailable GPU/ASIC power sensors still need their own device-level fix.
- Documentation updated: this work-log entry.
- Follow-up: XMR/PRL Node estimates now use the PC-Agent forecast's own USD price and no longer require a second CoinGecko price for the per-coin estimate; the overall EUR conversion still requires the Node exchange-rate service.

### 2026-10-01 — per-worker PC-Agent external control

- Scope and owner: PC-Agent local external-control settings, worker stats and external lifecycle; Solar-Miner-Node repository.
- Implemented behavior and code evidence: persisted local opt-outs keyed by `cpu` or stable GPU device ID; Hardware page exposes per-worker toggles. Agent `/api/agent` reports only globally and individually permitted workers. Node-originated resume, pause and power-target commands operate only on permitted workers; disabled workers remain available to local UI controls. A Node-only `/api/agent/power-control/external-status` omits opted-out GPU identities.
- Cross-repository contracts checked: Node `MinerAgentController` consumes `/api/agent`, `/api/agent/power-control/external-status`, and external command routes. Existing `control-settings.json` files default all workers to enabled when no per-worker map exists.
- Verification performed and result: source/call-site inspection, `git diff --check`, and `node --check` for the Hardware UI passed. `:pc-agent:compileJava` was attempted; Gradle could not download the configured distribution because the environment blocks network access. No tests were run.
- Remaining gaps / hardware or rollout gates: start/stop and actual power behavior still require PC-Agent runtime verification on supported CPU/GPU hardware. GPUs without dynamic power support cannot participate in power-budget allocation.
- Documentation updated: PC-Agent ownership map and this work-log entry.

### 2026-10-01 — remove installed miner from catalog

- Scope and owner: PC-Agent miner catalog, uninstall API and XMRig/SRBMiner package ownership.
- Implemented behavior and code evidence: installed miner cards now offer **Miner entfernen** with confirmation. The endpoint stops the selected miner before deleting XMRig or SRBMiner. XMRig configuration and Pearl configuration are retained; SRBMiner package files are tracked in a local manifest and removed without deleting the saved config.
- Cross-repository contracts checked: no Node contract change; the existing per-coin download and overview routes remain in use.
- Verification performed and result: source review, JavaScript syntax and diff checks; Java compile and runtime checks are pending.
- Remaining gaps / hardware or rollout gates: pre-manifest SRBMiner installations have only the executable identified as owned; legacy support files are intentionally not recursively deleted.
- Documentation updated: PC-Agent ownership map and this work-log entry.

# 2026-10-01 — External start failures and miner removal discoverability

- PC-Agent `MiningService` now reports external start success only when every requested worker starts, preventing a running XMR CPU worker from masking a failed Pearl GPU start.
- `ClusterController` checks controller action results before writing state locks for start and power-target actions. Failed commands emit a tick warning and remain eligible for a later controller retry.
- Added a Miner entfernen action to the installed miner detail view; catalog removal remains available too.
- Evidence: source review of `MiningService`, `ClusterController` and static PC-Agent UI. JavaScript syntax and repository diff checks run after editing; Gradle compilation remains unavailable because the wrapper distribution cannot be downloaded in this restricted network environment.

## 2026-10-01 — benchmark publication threshold contract

- Producer behavior is unchanged: `TelemetryReporter` sends opted-in active-device observations with positive hashrate. Its comment now reflects the admin service's one-sample public threshold.
- Contract evidence: admin `BenchmarkService` defaults to one sample per 14-day hardware group; `admin-portal/docs/encyclopedia/08-contracts.md` records that a group with `sampleCount=1` exposes that device's metrics.
- Verification: source and diff checks only; no live telemetry or Java build was available in this network-restricted environment.

## 2026-10-02 — Windows PC-Agent release launcher

- Scope and owner: `pc-agent/standalone/start-agent.bat` and `start-agent.ps1` in Solar-Miner-Node. No cross-repository contract changed.
- The batch launcher now requests its matching PowerShell release asset with basic parsing and explicit TLS 1.2/User-Agent settings. The PowerShell launcher downloads the SHA-256 asset to a temporary file, reads it as text, validates the expected JAR checksum format, and uses basic parsing for all downloads. This avoids Windows PowerShell 5.1 treating the response content as a byte array before `Trim()`.
- Verification: Windows PowerShell 5.1 mocked release and download responses; a valid SHA-256 fixture downloaded and verified the JAR, while malformed checksum text was rejected. Live GitHub/Adoptium downloads and a full agent start were not exercised.
- Rollout gate: publish both updated launcher assets in a new PC-Agent release; the existing release continues serving its old PowerShell script until then.
- Follow-up: `start-agent.bat` now runs an adjacent `start-agent.ps1` first, so the checked-out source pair works immediately even while the latest published release still contains the old script. The standalone download path remains the fallback when no adjacent script exists. A Windows `cmd.exe` test with a local fixture confirmed the adjacent-script branch; live release download and agent startup remain unverified.

## 2026-10-02 — Pearl GPU console identity

- Scope and owner: PC-Agent `PearlMinerService`; no cross-repository or wire contract changed. The frontend and console files already use the same vendor/index key.
- Finding: SRBMiner GPU IDs were matched to `nvidia-smi` GPU indices through CUDA indices. CUDA and `nvidia-smi` can enumerate multiple NVIDIA cards in different orders, causing a selected card's console to contain another card's process output.
- Change: resolve the selected NVIDIA GPU UUID to its PCI bus address with `nvidia-smi`, then match that address to SRBMiner's device listing before starting the process. A missing or ambiguous match fails the start instead of selecting an unrelated GPU. AMD mapping is unchanged.
- Verification: `PearlMinerDeviceListTest` now covers ANSI-colored listings, disabled devices, and swapped CUDA/NVIDIA ordering. `git diff --check` passed. The Gradle test could not run because the wrapper distribution download was blocked by the environment; a real two-GPU start and console check remain pending.

## 2026-10-02 — PC-Agent miner process discovery and shutdown

- Scope and owner: PC-Agent XMRig/SRBMiner lifecycle and Mining overview in `Solar-Miner-Node`; no cross-repository API shape changed.
- Change: new `MinerProcessRegistry` discovers processes by their executable names across Windows/Linux. Externally started miners now appear as running in worker/coin status, and local start actions refuse to launch a duplicate. Per-coin stops and Spring `@PreDestroy` handlers terminate matching processes and their descendants, including processes not launched by this agent instance.
- Status limits: an external process is recognized as running, but the agent does not adopt its local API session; pool health, per-GPU ownership and hashrate are reported as unknown/zero. Process matching requires the standard XMRig or SRBMiner-MULTI executable name.
- Verification: source inspection only; no tests or builds were run in this change. Windows/Linux shutdown, externally started process visibility and duplicate-start behavior still need runtime verification.

## 2026-10-02 — D04 / F25 / F28 opt-out egress

- Owner: root app `telemetry/TelemetryReporter`; contract C8 with admin-portal.
- Change: opted-out sites send only `{uuid, telemetryOptIn:false}` to request revocation, with no measurements; failed delivery is retried on the next scheduled run. Successful revocations are suppressed for the rest of the process lifetime, and opting in again resumes normal batches.
- Verification: source review only; no test/build/HTTP probe was run. Restart retry and end-to-end public API behavior remain to verify.
- Security gate: this uses the existing public UUID-based endpoint. Until B05/F22 adds proof of possession, identity spoofing remains possible and the endpoint is not considered secured.

## 2026-10-02 — Standalone PC-Agent benchmark sessions and sharing

- Scope/owners: PC-Agent `BenchmarkSessionService`, `BenchmarkSharingService`, local Benchmarks UI and admin-portal `StandaloneBenchmarkIngestService`; shared contract C8a.
- Change: added timed LIVE measurement and sequential INSTALLED mode (Monero then Pearl when configured), progress/status/cancel APIs, per-device median hashrate and measured power summaries, public match lookup, and restoration of managed CPU/GPU workers. Sequential mode refuses to interrupt externally started XMRig/SRBMiner processes because their state cannot be safely restored.
- Consent/egress: benchmark sharing is an independent persisted switch, default off. When enabled, active worker observations are sent every 15 minutes and after manual runs. The agent keeps a random participant UUID and sends UUID-derived device pseudonyms. Turning sharing off queues a metrics-free revocation; failed delivery retries on the next interval.
- Admin: new public POST `/api/telemetry/standalone-benchmarks` only updates benchmark eligibility/samples; standalone participants have no location, PV/energy/hashrate network totals and never enter the location rollup.
- Verification: `:pc-agent:compileJava` and admin backend `compileJava` both succeeded with Gradle 8.14.5/JDK 21 using repository access; `node --check` passed for the changed JavaScript and `git diff --check` passed. No tests were added or run. Runtime, installed miner timing, Windows/Linux restoration, deployed endpoint reachability, consent revocation and real published comparison remain unverified. Existing UUID-only public write identity remains a security limitation shared with current telemetry.

## 2026-10-02 — Protect active benchmarks from miner control commands

- Scope: PC-Agent benchmark admission and local/Node power-control commands; API shapes unchanged.
- Change: serialize benchmark start with target, pause and resume commands. While a benchmark is running, commands return HTTP 409 instead of interrupting benchmark workers. The same monitor makes the idle check and command atomic with benchmark start.
- Verification: source inspection and `git diff --check`; tests/build not run. Live Node/PC-Agent contention remains unverified.

## 2026-10-02 — Request Defender exclusion from local PC-Agent UI

- Scope: Windows Defender handling in the standalone PC-Agent. No Node or cross-repository contract changed.
- Change: after an antivirus-blocked miner install, the loopback Mining UI offers a button to exclude only the known Monero or Pearl install directory. A new endpoint accepts only loopback requests and only while that coin reports `BLOCKED_BY_ANTIVIRUS`. `WindowsDefenderExclusionService` invokes PowerShell with `-Verb RunAs`; the elevated script applies `Add-MpPreference` and reads `Get-MpPreference` to confirm the path. The UI reports success, UAC cancellation, timeout, or command/policy failure.
- Limits: all files in the selected install directory become excluded from Defender scanning. This requires a native Windows PC-Agent process; Linux/Docker cannot elevate on a Windows host. Enterprise policy or Defender tamper protection may reject the change.
- Verification: source inspection only; no tests/builds run. Native Windows UAC approval/cancellation and Defender read-back still require host verification.

## 2026-10-02 — Log remote PC-Agent miner control events

- Scope: Node-originated PC-Agent power-control commands and per-worker external-control settings; no wire contract changed.
- Change: successful remote pause, resume, target/start and worker permission changes now append timestamped English `[Remote Control]` entries to the persistent XMRig or Pearl console logs. Pearl events go to the aggregate console and enabled per-GPU consoles; permission changes go to the addressed worker console. Failed commands do not get reported as successful changes.
- Verification: source review and `git diff --check`; no tests/build run. End-to-end Node to PC-Agent UI visibility remains unverified at runtime.

## 2026-10-02 — Beginner device setup and bounded PV discovery

- Owner: React setup UI, Node setup controller/service and PV discovery; no cross-repository contract changed. See [setup UX and PV discovery](setup-and-discovery.md) and [local API](../API.md).
- UI: network search and manual connection are separate entry points; model/address/testing/measurement-source steps, optional transport settings, empty/error/partial recovery, explicit add/remove/Next behavior, German/English copy and mobile current-step indicator. Profile filtering retains the chosen profile; late validation responses cannot verify changed settings.
- Discovery: private IPv4 range and settings validation, one admitted scan, 24 host workers, 25-second deadline with immutable completion metadata. Honors custom Modbus device IDs and REST ports. REST uses a single GET response, never POST, and no longer identifies devices solely from 401/403. Docker can configure its LAN prefix through SOLARMINER_DISCOVERY_SUBNET_PREFIX; browser LAN IPv4 provides another UI suggestion.
- Verification: Vite production build, TypeScript noEmit, Java compile/bootJar and eight targeted JUnit tests pass. Chrome desktop/mobile setup checks pass with no page errors/overflow. Controlled device-response fixtures cover empty/partial/found results, connection check, add/remove and Next gating. Fresh simulator (site count zero) verifies the configured LAN prefix, HTTP 400 for invalid input, HTTP 429 for overlapping scans, and a completed 254-host probe against an unused private range/custom port (zero devices, approximately 2.2 seconds).
- Runtime: the old compose JAR filenames required a temporary local override at workspace .codex-qa/setup-compose.yml to run the newly built solar-miner-1.1.5.jar. Only simulator setup DB was reset; Phoenix wallet retained. Current simulator serves the revised setup on localhost:8080/setup.
- Open gates: real device discovery, authenticated APIs, slow/VLAN/Docker hardware networks and novice user study. Browser fixtures and successful builds do not establish hardware compatibility. Manufacturer-first manual selection and simplifying financial/panel steps remain documented UX follow-ups, not implemented behavior.

## 2026-10-02 — Guided panel-group setup

- Owner: Node React setup and German/English copy; setup request fields and backend validation are unchanged. Source check: `ControllerDSL.calculateSunEvent` consumes saved panel coordinates for sunrise/sunset, so location is still required.
- Change: the step explains roof-area grouping, places panel count/Wp first, displays total kWp, provides cardinal direction choices, tilt control and exact-degree inputs, loads the map when requested, offers manual coordinates, reuses the selected site location and allows editing/removal. Default Wp/direction/tilt values are labeled as assumptions. Names are generated if omitted, duplicate names are rejected locally and Next requires a saved area.
- Verification: Vite production build, TypeScript noEmit and browser checks for missing location, manual location, multiple areas, editing, duplicate-name feedback, calculated power and mobile overflow all pass. Screenshots were inspected before and after the change. No hardware measurement or user study was performed.

## 2026-10-03 — PC-Agent RVN/ETC GPU preparation

- Owner: PC-Agent; shared planned login contract with the disabled proxy adapters. `GpuCoinMinerService` supports RVN/KAWPOW and ETC/ETCHash through SRBMiner-MULTI, with separate persisted configuration, mainnet RVN Base58Check/ETC hex wallet validation, per-GPU process and API monitoring, proxy listener/house-fee start gate, fee-loss stop, and fee/console/status/UI wiring. `MiningService` uses the active GPU coin for power and remote dispatch. SRBMiner process ownership prevents Pearl's status and stop path from capturing these managed processes.
- Verification: `:pc-agent:test --offline` passes with JDK 21, including new address/login and proxy-port tests; `node --check pc-agent/src/main/resources/static/agent.js` and `git diff --check` pass. Installed SRBMiner 3.7.1 lists `kawpow` (0.85%) and `etchash` (0.65%). The [RVN/ETC integration record](rvn-etc-integration.md) tracks the full new-coin guide.
- Gate: the operator supplied RVN/ETC house addresses; local Fee-Backend configs now contain them, and the PC-Agent format/checksum tests pass. No proxy listener, deployed fee-target verification or real share/pool credit exists. The PC-Agent fails closed and no production support is claimed. Windows 11/RTX 2080 Ti and Docker Desktop mining remain to test after a complete fee path and user test wallets exist.

## 2026-10-03 — PC-Agent Mining UX and Node defaults

- Added persistent device-to-coin assignments and local/benchmark-only exclusion. External target/resume/pause and worker stats/capacity use the assignment, preserving existing permission flags and migrating legacy settings. New installs require explicit assignment. Coin changes serialize with benchmarks and stop the old assignment before saving.
- Preserved the miner rail/+ flow, added distinct coin labels, a collapsible Node profile, named miner headings and Status/Setup/Diagnostics views; Hardware links to the single profile editor. Added German/English core copy and mobile/focus improvements.
- Evidence: full :pc-agent:test passes with JDK 21; NodeMiningProfileTest and desktop/mobile browser fixture checks pass. Screenshots inspected; syntax/diff checks pass. Node core endpoint/Map consumers checked in MinerAgentController. No new coin release or hardware/pool verification claimed. Details and migration behavior: [UX/profile record](pc-agent-ux-profile.md).

## 2026-10-03 — PC-Agent gesamtes UI/UX-Konzept

- Bewertet und überarbeitet: Übersicht, Miner, Leistungsgrenzen, Benchmarks, Sensoren und Verbindung. Gemeinsame Anwendungshülle mit Betrieb/Optimierung/System, Desktop-Seitenleiste, mobilem Menü, Fokusführung und gemeinsamen Offline-Zuständen. Status/Handlungsbedarf stehen vor Finanzen; Profile bleiben von manuellen Mining-Ansichten getrennt.
- Verhaltensverbesserungen: getrennte Algorithmus-Hashraten, Hardware-Entwürfe mit bewusstem Anwenden/Verwerfen, explizite Proxy-Kandidatenauswahl, erklärte Benchmark-Modi mit geschützten Aktionen/Consent, durchsuchbare Sensoren und Inline-Sensorfehler. Deutsche/englische Haupttexte ergänzt.
- Verifikation: 41 Backend-Tests erfolgreich; JavaScript-Syntax geprüft. Browser-Fixtures prüfen alle sechs Seiten am Desktop und mobil, DE/EN, Entwürfe/Polling, Proxy-Auswahl/Aktivierung, Benchmark-Start/Abbruch/Teilen, Sensorfilter/Fehler, Erststart und Offline. Ausgangs- und Ergebnis-Screenshots visuell geprüft. Keine Nutzungsstudie oder Hardware-/Pool-Zertifizierung. [Bewertung und Gesamtkonzept](pc-agent-design-concept.md).

## 2026-10-03 — Miner beim Seitenaufruf unabhängig laden

- Ursache im Quellcode: Die Miner-Leiste wurde erst nach der umfassenden `/overview`-Antwort (GPU-Erkennung, Status, Ertrags-/Proxy-/Fee-Daten) und anschließendem `/node-assessment` gerendert. Eine separate Node-Störung konnte sogar den lokalen Agent als offline markieren.
- Neuer lokaler GET `/api/agent/miner-catalog` prüft ausschließlich installierte CPU-/GPU-Binaries. Das Frontend zeigt damit Leiste und Installationszustand vor der vollständigen Übersicht; Auswahl bleibt erhalten, Startaktionen warten auf Betriebsdaten. Späte Katalogantworten überschreiben keine aktuelle Übersicht. Übersicht wird vor optionaler Node-Bewertung gerendert; parallele Übersicht-Polls werden vermieden und Node-Abrufe auf fünf Sekunden begrenzt.
- Evidenz: 42 Backend-Tests erfolgreich mit JDK 21 (`:pc-agent:test --offline`); Controller-Test prüft ausschließlich Dateiverfügbarkeitsaufrufe sowie Entfernung des gemeinsamen GPU-Binaries. Chrome-Fixtures blockieren Übersicht und Node-Antwort getrennt, prüfen frühe Leiste, sichere Startbuttons, erhaltene Auswahl und verspätete Katalogantwort nach Entfernung. Gesamte Desktop-/Mobile-/DE-/EN-Prüfung weiterhin grün; Syntax/Diff geprüft. Keine gemessene Produktionslatenz oder Hardwareverifikation behauptet. Vertrag ist rein lokal, keine Node-/Pool-/Fee-Vertragsänderung.

## 2026-10-03 — Mining-Inhalt an das Gesamtbild angeglichen

- PC-Agent UI: `mining.css` ergänzt die gemeinsame Gestaltung mit einer durchgehenden Hierarchie für Seitenkopf, lokalen Miner, Betriebswerte, Bereichsnavigation und Katalog. Profil folgt dem lokalen Inhalt und bleibt per Kopfbutton mit Fokusführung sowie Direktlink erreichbar; Entfernen liegt unter Installation verwalten in Einrichtung. Vertikale Coin-Leiste bleibt am Desktop, horizontal auf schmalen Geräten. Große Erststart-Hilfe erscheint ausschließlich ohne installierte Binaries. Keine Geräte-/Mining-/Fee-Verträge geändert.
- Korrigierte Statusdarstellung: poolgesunde laufende GPUs ohne zusätzliches Detail melden Pool verbunden statt Noch nicht gestartet; vorhandene Wattziele ohne gemeldetes Maximum zeigen eine fehlende Obergrenze statt ein fehlendes Ziel.
- Evidenz: JavaScript-Syntax und Git-Diff geprüft; Chrome-Fixtures `.codex-qa/pc-agent-design.cjs` erfolgreich für Hierarchie, Profilfokus, Installationsverwaltung, Filter, Erststart, verzögerte Antworten, alle sechs Seiten am Desktop/mobil und DE/EN bis 320 px. `.codex-qa/pc-agent-workspace.cjs` bestätigt gespeicherte Gerätezuordnungen über Miner-Wechsel, lokale Ausschlüsse, Bereichsnavigation und globale Freigabe. Betriebs-/Katalog-/Einrichtungs-/Diagnose-/Profil-Screenshots visuell kontrolliert. Kein Hardwaretest oder Benutzerstudie.

## 2026-10-03 — RVN/ETC gezielte Live-Proben und Konsolen-Startfehler

- Der PC-Agent startete SRBMiner für RVN, warf aber nach dem Prozessstart wegen unbekannter RVN/ETC-Konsolenkennung eine unbehandelte Ausnahme. Dadurch blieb ein unüberwachter Prozess und ETC kollidierte auf derselben GPU. `MinerConsoleService` und `MinerConsoleController` akzeptieren nun beide Coins sowie ihre GPU-IDs. Nach Agent-Neustart: Konsolen HTTP 200, Start und Stop beider Coins separat erfolgreich.
- Verifikation: JDK 21, gezielte `:pc-agent:test --offline` für `MinerConsoleServiceTest`, `MinerConsoleControllerTest`, `GpuCoinMinerServiceTest` erfolgreich. Reale 8084-/Proxy-/SRBMiner-API-Proben stehen im [RVN/ETC-Integrationsprotokoll](rvn-etc-integration.md). Beide Coins bleiben ohne gesunden Pool-Job oder akzeptierten Share und sind nicht freigegeben.

## 2026-10-04 — RVN/ETC nach Agent-Neustart erneut gemessen

- Der gestartete Agent hatte Proxy und House-Fee-Routen für beide Coins bereit. RVN und ETC starteten nacheinander auf NVIDIA GPU ID 1; ihre SRBMiner-APIs meldeten jeweils `time_connected="0"`, `uptime=0`, `last_job_received=0` und null Shares. Nach den begrenzten Proben wurden beide Miner über den Agenten gestoppt. Details und Kontext der eingeschränkten Fake-Pool-Probe: [RVN/ETC-Integrationsprotokoll](rvn-etc-integration.md). Kein neuer Codevertrag und keine Freigabe.

## 2026-10-04 — GPU-Coin-Handshake und echte Hashrate getrennt geprüft

- Der eingebettete Proxy antwortet SRBMiners blockierendem Subscribe nun vor dem dynamischen Authorize; danach überträgt er den echten Upstream-Extranonce. Mit neu gestartetem `pc-agent:bootRun` erhielten RVN und ETC live Kryptex-Jobs. Der PC-Agent fordert für einen gesunden GPU-Status zusätzlich positive Hashrate und beendet dauerhaft bei 0 H/s bleibende Prozesse nach einer Startfrist mit konkreter Diagnose.
- Verifikation: gezielte GPU-Proxy-Tests und gesamtes `:pc-agent:test --offline` unter JDK 21 erfolgreich. Direkte Pool-Läufe ohne SolarMiner-Proxy zeigten dieselben SRBMiner-Epoch-Fehler auf NVIDIA GPU ID 1 und 0 H/s; ETCs `--gpu-table-slow-build` half nicht. Kein akzeptierter User-/House-/Referral-Share, keine Pool-Gutschrift oder Auszahlung. [Messungen und offene Gates](rvn-etc-integration.md).

## 2026-10-05 — PC-Agent GPU-Auswahl und Live-Minerstatus untersucht

- Laufzeitbeleg von `192.168.178.126:8084/api/agent/local/overview`: vier NVIDIA TITAN RTX wurden erkannt, aber die gespeicherten RVN- und ETC-Konfigurationen enthielten jeweils ausschließlich `devices:"NVIDIA:0"`. Deshalb kann der Agent für diese Konfigurationen nur GPU 0 starten; der per-GPU Status zeigt die übrigen Karten ausdrücklich als nicht ausgewählt. Dies ist keine Beschränkung von `GpuCoinMinerService`: er startet jede gespeicherte `vendor:index`-Auswahl als separaten SRBMiner-Prozess mit der über `--list-devices` ermittelten SRBMiner-ID.
- ETC-Laufzeitbeleg: Die `ethereumclassic-NVIDIA-0`-Konsole zeigte Verbindung zu `127.0.0.1:3337`, Pool-Jobs und rund 55.5 MH/s. RVN wurde laut `ravencoin-NVIDIA-0`-Konsole durch einen lokalen Pause-Befehl beendet, nicht durch einen Proxy- oder GPU-Startfehler. Beide Coins behalten die dokumentierten Release-Gates: keine verifizierten akzeptierten Shares, Pool-Gutschriften oder Auszahlungen.
- Lokaler UI-Vertrag ergänzt: `AgentEventController` stellt `/api/agent/local/events` als SSE-Stream bereit; `agent.js` aktualisiert Overview und Konsolenfortschritt ereignisgesteuert und entfernt die fortlaufenden Browser-Polls. Keine Node-, Proxy-, Fee- oder Pool-Vertragsänderung.
- Verifikation: `node --check pc-agent/src/main/resources/static/agent.js` und `git diff --check` erfolgreich. Die gezielten Gradle-Tests konnten in dieser Shell nicht ausgeführt werden, weil weder `java` noch `JAVA_HOME` vorhanden waren; die Kompilierung und ein Lauf des neuen SSE-Endpunkts bleiben vor Deployment zu prüfen.

## 2026-10-05 — PC-Agent Miner-Konsolen fortlaufend und GPU-sicher

- Ursache und Änderung: Die SSE-Umstellung löste die Nachladung von Miner-Konsolen nur bei einer Overview-Änderung aus. Reine XMRig-/SRBMiner-Logzeilen konnten damit unsichtbar bleiben. Die Mining-UI tailt die aktuell gewählte Konsole nun wieder im Sekundenintervall (bei noch vorhandenen Chunks ohne Verzögerung), zusätzlich zu unmittelbaren Abrufen nach Overview-Ereignissen.
- RVN/ETC: Beim Start einer zweiten GPU wurde das aggregierte Coin-Log bisher erneut initialisiert und damit die Ausgabe der ersten GPU gelöscht. `GpuCoinMinerService` initialisiert das Gesamtlog nun ausschließlich für den ersten laufenden Worker und schreibt für weitere Worker eine Startzeile hinein; die einzelnen GPU-Logs bleiben unverändert.
- Vertrag: ausschließlich lokale PC-Agent-UI-/Log-Zustellung, kein Node-, Proxy-, Fee- oder Pool-Vertrag geändert.
- Verifikation: `node --check pc-agent/src/main/resources/static/agent.js` erfolgreich. Die gezielten Gradle-Tests können in der vorliegenden Shell mangels `java`/`JAVA_HOME` nicht laufen; der Live-Check mit mindestens zwei RVN-/ETC-GPUs steht aus.

## 2026-10-05 — PC-Agent Worker-Telemetrie im Home-Dashboard

- Der lokale Overview-Vertrag erweitert `MinerStats.Worker` additiv um `acceptedShares` und `rejectedShares`. XMRig liest sie aus `results.shares_good` und `results.shares_total`; SRBMiner akzeptiert die API-Formen `pool.accepted/rejected` und `pool.shares.accepted/rejected`. Nicht von der Miner-API gemeldete Zähler bleiben `null` und erscheinen im Dashboard als „—“, nicht als irreführendes Null-Ergebnis. Das ist ausschließlich ein lokaler PC-Agent-Vertrag; Node-, Proxy-, Fee- und Pool-Verträge bleiben unverändert.
- Die Home-Übersicht enthält nun eine horizontal scrollbare Worker-Telemetrie-Tabelle mit Worker, Algorithmus, Status, Hashrate, akzeptierten/abgelehnten Shares, Temperatur und Leistungsaufnahme. CPU-Temperatur wird aus der CPU-Telemetrie bezogen, NVIDIA-GPU-Temperatur über die stabile Geräte-ID und den `nvidia-smi`-Sensor; nicht zuordenbare Sensoren bleiben sichtbar unbekannt.
- Verifikation: `node --check pc-agent/src/main/resources/static/overview.js` und `git diff --check` erfolgreich. Java/Gradle-Tests konnten in dieser Shell nicht ausgeführt werden, weil `java` und `JAVA_HOME` fehlen. Ein Lauf gegen XMRig und die unterstützte SRBMiner-Version muss die tatsächlichen Share-Feldnamen noch bestätigen.

## 2026-10-05 — RVN-KAWPOW-Job-Watchdog korrigiert

- Ursache: Vier Titan-RTX-RVN-Worker erhielten über den lokalen Proxy einen KAWPOW-Job, hashten mit rund 30–34 MH/s und lieferten akzeptierte Shares. Der PC-Agent stoppte sie dennoch, weil sein Health-Check `last_job_received >= 120` Sekunden unabhängig von der weiter positiven Hashrate als fehlenden Pool-Job behandelte.
- Änderung: `GpuCoinMinerService` akzeptiert nach dem ersten gültigen Job eine weiterhin positive SRBMiner-Hashrate als Nachweis eines nutzbaren Jobs. Ein ausbleibender erster Job wird weiterhin nach 90 Sekunden abgebrochen; Worker ohne Hashrate bleiben weiterhin durch den separaten 180-/120-Sekunden-GPU-Check geschützt. Proxy- und Fee-Readiness bleiben harte Start-/Laufzeitvoraussetzungen.
- Vertrag/Gate: kein Wire- oder Fee-Vertrag geändert. Dies belegt erfolgreiche User-Pool-Shares im beobachteten Lauf, aber nicht House-/Referral-Umschaltung, Pool-Gutschrift oder Auszahlung.
- Verifikation: `:pc-agent:test --tests de.verdox.solarminer.pcagent.pearl.GpuCoinMinerServiceTest` mit Gradle 8.14.5 und GraalVM JDK 21 erfolgreich; `git diff --check` erfolgreich. Ein längerer Lauf über mindestens eine Fee-Umschaltung bleibt offen.
### 2026-10-05 — Modular PC-Agent miner catalog and candidate research

- Scope/owner: Solar-Miner-Node `pc-agent`; no pool, fee-backend, Node external API or mining-route contract was changed. The change is a local, additive UI/API selection contract only.
- Implementation: `MinerCatalogService` centralizes stable miner IDs and per-coin metadata (device, algorithm, disclosed dev fee, pros/cons, provenance, experimental marker and live installer state), persists a selected miner ID per coin atomically, and rejects unknown coin/miner combinations. It dispatches downloader calls by selected miner ID, so a new official downloader is registered centrally rather than added as a coin-specific controller branch. `MiningController` exposes `GET /api/agent/local/miner-options`, additive `selectedMiner`/`miners` overview fields and `POST /api/agent/local/{coin}/miner?minerId=...`; the catalog card renders the metadata and will render a selection control automatically when a coin has multiple implementations. Current entries faithfully describe the existing XMRig and SRBMiner paths only; no new executable has been added or enabled.
- Boundary preserved: selecting software only changes metadata state. It does not start a process, change a wallet/pool, bypass proxy/fee guards, or promote RVN/ETC beyond their existing experimental state. An actual future miner must bring its own adapter and installer verification and pass lifecycle/statistics/power/proxy/fee/real-share gates.
- Evidence/verification: added `MinerCatalogServiceTest` for metadata, known-selection validation and persistence; updated controller catalog assertions. `git diff --check` passes. This environment has no `java` executable or JDK 21 toolchain, so Gradle compilation/tests could not run here; execute `:pc-agent:test --tests de.verdox.solarminer.pcagent.mining.MinerCatalogServiceTest --tests de.verdox.solarminer.pcagent.controller.MiningControllerValidationTest` under JDK 21 before accepting the change.
- Research: added [PC-Agent miner candidates](pc-agent-miner-candidates.md), using primary upstream documentation for XMRig, SRBMiner, TeamRedMiner, lolMiner and a Pearl candidate. It records TeamRedMiner (AMD RVN/ETC) as the first evaluation candidate, but no candidate is a supported route yet.

### 2026-10-05 — Miner selection UI and future-miner agent instruction

- Scope/owner: Solar-Miner-Node `pc-agent`. No mining route was enabled, no binary was downloaded, and no cross-repository wire or fee contract changed.
- Implementation: the local catalog now exposes TeamRedMiner as an AMD-only RVN/ETC comparison option with documented fee, pros/cons and a `selectable=false`/unavailable-reason state. The generic browser selector renders all options but disables unintegrated ones; the server rejects direct selection of a non-selectable ID. This gives users an understandable comparison without allowing a fake download or start operation. SRBMiner's catalog fees were aligned with its upstream KAWPOW (0.85%) and ETCHash (0.65%) table.
- Future-agent contract: added `pc-agent/MINER-INTEGRATION-GUIDE.md` and linked it from the repository `AGENTS.md` and wiki. It requires a dedicated verified installer, lifecycle adapter, telemetry/parser, GPU exclusivity/power integration, final proxy/fee gate and real-share evidence before an option becomes selectable.
- Evidence and remaining gates: `node --check pc-agent/src/main/resources/static/agent.js` and `git diff --check` pass. Java test execution remains unavailable here because no `java`/`JAVA_HOME` is configured. TeamRedMiner has no installer, process/API adapter, proxy handshake capture or accepted user/house/referral-share evidence yet; RVN/ETC release state remains unchanged.

### 2026-10-05 — Independent miner installations and coin switching

- Implementation: `MinerCatalogService.download(coin, minerId)` and `POST /api/agent/local/{coin}/miners/{minerId}/download` address a concrete software ID. The existing coin-default download route remains as compatibility behavior. A selection now succeeds only for an installed, selectable option; the browser disables uninstalled choices and provides an install button for each selectable software option. Installing one software ID does not remove or alter any other installed miner.
- Contract: the selected miner is persisted per coin, so different coins can retain different selected miner IDs. A shared package such as SRBMiner is intentionally one binary installation serving compatible coins; a future TeamRedMiner installation is independent. The currently displayed TeamRedMiner candidate remains non-selectable until its adapter exists.
- Verification: added controller coverage for the explicit download route and catalog coverage for direct software-ID dispatch. Java test execution remains blocked by missing Java/JDK in this environment.

## 2026-10-06 — PC-Agent operations UI and persistent local energy journal

- Reframed the four operating pages around operator tasks. Dashboard now leads with live worker cards, per-worker gross USD/day forecast and session energy. Miner is a package-level software library with no process/device controls. Worker lists every physical CPU/GPU, including idle hardware, and owns compatible miner assignment, local/Node permission and start/pause. Pools keeps target management and links back to Worker. The XMR/PRL balance strip now stays directly below the header on all four pages, with a known fiat subtotal.
- Added additive local APIs: `GET /api/agent/local/workers`, assignment/start/pause per `deviceId`, and `GET /api/agent/local/energy` plus tariff settings. Assignment stops the old worker, rejects unavailable/incompatible software and synchronizes existing GPU coin configurations without implicitly starting a replacement. No Node external, proxy wire, fee or pool accounting contract changed.
- Added `EnergyJournalService`: five-second server-side integration of positive measured CPU-package/GPU-board watts, per-device sessions, 30-second gap cutoff, atomic active checkpoint, monthly JSONL completion log, tariff settings, measurement coverage and today/7-/30-day summaries. Missing sensors remain unknown; component energy is not claimed as calibrated wall energy.
- After visual review, collapsed the Dashboard into one desktop operating canvas: live worker cards and energy/cost summary sit side by side. Removed the repeated lower KPI, session-chart, pool, worker-table and earnings blocks; their detail remains in Worker and Pools.
- Evidence: `JAVA_HOME=/home/lukas/.jdks/graalvm-ce-21.0.2 sh gradlew :pc-agent:test --offline --no-daemon` → 70 tests successful. `node --check` passes for all changed operating-page controllers. `.codex-qa/pc-agent-operations.cjs` passes desktop/mobile Chromium assertions for all four pages, assignment editor, wallet strip, live/energy panels and overflow; screenshots inspected. Real wall-meter comparison, AMD measurements and live long-session recovery remain open. See [operations UI record](pc-agent-operations-ui.md).

## 2026-10-06 — Worker-Zuweisung nicht mehr von Telemetrie blockiert

- Live-Ursache: `/api/agent/local/workers`, Miner-Katalog, Energie und Einstellungen antworteten, während `/api/agent/local/telemetry` länger als acht Sekunden ohne Antwort blieb. `workers.js` wartete mit einem gemeinsamen `Promise.all` auf alle fünf Antworten und renderte deshalb weder Hardwarezeilen noch Zuweisungsaktionen.
- Änderung: Hardwareinventar und Miner-Katalog sind nun der einzige kritische Ladepfad und werden sofort gerendert. Energie und Node-Einstellungen werden danach optional ergänzt; die langsame Telemetrieabfrage ist für die Zuweisungsseite entfernt. Fehlt kompatible installierte Software, erklärt der Dialog die Sperre und verlinkt direkt zur Installation.
- Verifikation: Live-Agent auf `127.0.0.1:8084` zeigt fünf Worker ohne Konsolenfehler im Kernpfad. CPU kann frei bleiben, XMR ist bei nicht installiertem XMRig nachvollziehbar gesperrt; GPU/RVN mit installiertem SRBMiner ist auswählbar und der Speichern-Button aktiv. Keine Zuweisung wurde während der Prüfung geschrieben.

## 2026-10-06 — Globale Sprache und Anzeigewährung vereinheitlicht

- Eine browserlokale Sprach- und Währungspräferenz gilt nun auf allen PC-Agent-Seiten. Wallet-Summen, per-Worker-Bruttoertrag, Energiekosten und Stromtarif verwenden denselben locale-sensitiven Formatter; ein Wechsel wird auf der offenen Seite sofort angewendet und bleibt über Navigation/Neuladen erhalten.
- `FiatRateService` stellt unter `GET /api/agent/local/fiat-rates` stündlich gecachte USD-Basiskurse für EUR/USD/CHF aus dem SolarMiner Currency-Service bereit. Der Browser behält den letzten gültigen Kurs sieben Tage als Offline-Fallback. Ohne verfügbaren Kurs bleibt der Wert in seiner tatsächlichen Ursprungswährung.
- Ergänzte dynamische Übersetzungen beseitigen gemischte deutsch/englische Texte in Dashboard, Miner-Software und Worker. Verifikation: vollständige `:pc-agent:test --offline`-Suite mit 72 Tests erfolgreich; Chromium-QA wechselt einmal auf CHF und Englisch und bestätigt Persistenz, umgerechnete Werte, keine USD-Reste in Worker-Erträgen sowie keine erkannten deutschen UI-Texte auf den vier Betriebsseiten.

## 2026-10-06 — PC-Agent Englisch als Standardsprache und Übersetzungslücken geschlossen

- Die Standardsprache ist nun Englisch, wenn im Browser noch keine Sprache gewählt wurde. Eine gespeicherte Deutsch-Auswahl bleibt erhalten; Dokument-`lang`, Beschriftung und Formatierungs-Locale folgen der aktiven Sprache.
- Durchsuchte alle PC-Agent-HTML-, JavaScript- und CSS-Dateien auf sichtbare deutsche Texte. Ergänzte zentrale englische Übersetzungen für Seitenbeschreibungen, Worker-Zuweisung, Sensorzugriff, Proxy-Aktionen, Status-/Fehlermeldungen und die neue Operations-Navigation. Keine API- oder Mining-Verträge geändert.
- Verifikation: `node --check pc-agent/src/main/resources/static/i18n.js` und `git diff --check` erfolgreich. Kein Browserlauf in diesem Änderungsschritt; die bestehende Chromium-QA im vorherigen Eintrag deckt die vier Betriebsseiten ab, aber die übrigen Agent-Seiten sind hier nur per Quelltextsuche geprüft.

## 2026-10-06 — Decred-BLAKE3 experimenteller PC-Agent-Pfad

- Currency Service now publishes canonical `decred`/DCR price and mining-network inputs; the PC-Agent consumes them for its gross forecast. Miner catalog, worker assignment and SRBMiner command construction include `blake3_decred`; the user pool preset is Suprnova Stratum.
- The bundled/external proxy recognizes a Decred protocol bean on port 3338 and the PC-Agent validates a mainnet Base58Check DCR payout address. A live fee target is deliberately absent, so `feeReady("decred")` remains false and the managed miner cannot start.
- No share/proxy/pool capture, accepted DCR share, pool credit or fee account was verified. Protocol implementation, parser fixtures, currency persistence/provider tests and Windows/Linux miner starts still require verification. No tests were added or run in this step.

## 2026-10-06 — Coin-Fee-Gate dynamisch statt DCR-Sonderfreigabe

- Die feste DCR-Sperre wurde entfernt. `feeReady(coin)` prüft jeden syntaktisch
  gültigen kanonischen Coin-Key und verlangt ein `house:true` Fee-Ziel mit
  positivem Prozentanteil, Ziel-ID, Pool-Adresse und Worker. Fehlende,
  referral-only, ungültige oder nicht erreichbare Antworten bleiben gesperrt.
- Minerstart und SRBMiner-Laufzeitmonitor fragen denselben Fee-Status ab. Damit
  wird ein neuer Coin automatisch startbar, sobald das Fee-Backend einen
  gültigen SolarMiner-Account für diesen Coin ausliefert; DCR bleibt bis dahin
  gesperrt. Das schaltet keine Miner automatisch ein.
- Verifikation: Quelltextprüfung, keine Tests oder Builds ausgeführt.
## 2026-10-06 — Quantus QTC PC-Agent GPU path prepared

- Added the QTC forecast consumer, SRBMiner QPoW command selection, GPU worker
  routing, proxy URL/port `3339`, fee readiness, console routing and UI metadata.
  The miner catalog entry is non-selectable until the fee account is provisioned.
- SolarMiner QTC payout address is pending. There is no fee-backend house target,
  pool submit capture, accepted user/house/referral share, account credit or
  payout evidence. This is not production mining support.
- No build or tests were run. See [Quantus integration record](quantus-integration.md)
  for protocol boundaries, limitations and rollback.

## 2026-10-06 — Zuweisungsgrund für nicht freigegebene Miner anzeigen

- Die Worker-Zuweisung blendet Miner-Einträge mit `selectable=false` nicht mehr
  kommentarlos aus. Sie zeigt den Kataloggrund an, markiert den Eintrag als
  derzeit nicht zuweisbar und deaktiviert Speichern sowie den Installationslink.
- Coins, für die kein Miner freigegeben ist, bleiben in der Coin-Auswahl sichtbar,
  erscheinen dort aber ausgegraut und tragen den Freigabestatus im Label.
- Für SRBMiner-MULTI/Quantus bleibt die Zuweisung gesperrt: Das QTC-Hauskonto
  und verifizierte Pool-/Fee-Gutschriften fehlen weiterhin. Das ist die bereits
  dokumentierte Integrationsgrenze, keine fehlende SRBMiner-Installation.
- Keine Backend- oder Mining-Freigabe geändert. Verifikation: Quelltextprüfung;
  kein Build und keine Tests ausgeführt.
### 2026-10-06 — Linux CPU-/GPU-Temperaturen im PC-Agent

- Ursache: `LinuxSensorReader` fiel bei fehlender CPU-Beschriftung auf den alphabetisch ersten hwmon-Temperaturwert zurück. Das kann ein NVMe-Sensor sein; auf der verfügbaren Linux-Hostansicht liegt `hwmon0` tatsächlich bei `nvme`, während `k10temp` CPU-Sensoren danach kommen. Die CPU-Anzeige konnte dadurch falsch oder fehlend sein.
- Änderung: CPU-Temperaturen werden nur noch aus bekannten CPU-/Package-Sensoren (`coretemp`, `k10temp`, `zenpower`, `Tctl`/`Tdie` usw.) beziehungsweise passend benannten Thermal-Zones gelesen. Es wird kein fremder Sensorwert als CPU-Temperatur ausgegeben. Die GPU-Karten in `telemetry.js` rendern jetzt auch die bereits vom Backend gelieferten NVIDIA-/AMD-Temperaturen.
- Scope: lokaler PC-Agent; kein Node-/Admin-Telemetrie-Wire-Vertrag geändert. AMD-Karten werden anhand DRM-Kartenindex zugeordnet; ein globaler AMD-Fallback wird nur bei genau einem verfügbaren AMD-Temperaturwert verwendet.
- Verifikation: lokale Sysfs-Namen bestätigen den NVMe-vor-CPU-Fall; `node --check pc-agent/src/main/resources/static/telemetry.js` und `git diff --check` erfolgreich. Java/Gradle sowie ein Lauf auf dem betroffenen Linux-PC waren in dieser Umgebung nicht verfügbar; Hardwareabdeckung bleibt dort zu bestätigen.

### 2026-10-06 — Sensorseite zeigt Fehler statt dauerhaftem Loading

- Die Sensorseite hatte für `GET /api/agent/local/telemetry` kein Timeout. Bei hängendem Request blieb der statische Starttext „Loading sensors“ unbegrenzt stehen; ein Renderfehler wurde ebenfalls nur als Offline-Zustand gemeldet.
- Der Abruf hat jetzt acht Sekunden Timeout und verhindert parallele Polls. API-Fehler, Timeout und Renderfehler werden im Sensorstatus sichtbar ausgewiesen; die englische Oberfläche hat passende Übersetzungen.
- Verifikation: `node --check` für `telemetry.js` und `i18n.js`, außerdem `git diff --check`. Kein API-/Wire-Vertrag geändert; ein Live-Agent-Request war hier nicht möglich.

## 2026-10-06 — Embedded Stratum dashboard through the PC-Agent

- The bundled Stratum proxy intentionally binds its HTTP and Stratum listeners to loopback (`127.0.0.1`); its dashboard on port 8090 therefore is not directly available from other hosts. Added a narrow GET-only reverse route on the PC-Agent at `/proxy-dashboard/`, reachable through the Agent web port 8084. The Agent proxies only dashboard HTML/assets and the two read-only dashboard API paths to its loopback proxy; all other paths/methods remain unavailable.
- The PC-Agent Connection page exposes the dashboard link only while local proxy mode is selected and running. The proxy's own page now uses relative asset/API paths so it renders from both `/` and the Agent mount path. No Stratum or fee contract changed and port 8090 remains loopback-only.
- Verification: source inspection only; no build, tests, or browser session run. Expected local URL: `http://127.0.0.1:8084/proxy-dashboard/`.
- Follow-up diagnostic: the reported `/proxy-dashboard/` response is the PC-Agent's normal UI, consistent with the currently running Agent predating this controller. The new explicit slash route is present in source. A Gradle compile was attempted but the sandbox cannot write the existing Gradle wrapper lock under `/home/lukas/.gradle`; no updated JAR was built or deployed here, so the active Agent must be rebuilt and restarted before this URL can route to the dashboard.

## 2026-10-06 — Currency-Service: Zuständigkeit klar dem eigenen Repo zugewiesen

- Anlass: `currency-rates/` in diesem Repo ist ein Überbleibsel der Ausgliederung vom 2026-10-04. Der Service lebt im eigenen Git-Repo `currency-service` (`https://github.com/Solarminer-app/currency-service`); Agenten konnten die Node-Kopie trotzdem als aktuelle Implementierung lesen, weil Wiki, Enzyklopädie und das Coin-Integrations-Guide sie dort verorteten.
- Änderung: `AGENTS.md` (Workspace und dieses Repo), `docs/agent-wiki/README.md` (Responsibility-Map, Dokumentenkatalog, Feature-Workflow), `docs/agent-wiki/currency-data.md` (Banner: Service-Sicht nach `currency-service` verschoben), `README.md` sowie `NEW-MINING-COIN-GUIDE.md` sagen jetzt eindeutig: Code, Tests, Release und Vertragsdoku des Currency-Service ändern sich ausschließlich in `currency-service`; dieses Repo konsumiert nur die HTTP-API (`CURRENCY_MICRO_SERVICE_URL`). Zusätzlich liegt eine Warndatei in `currency-rates/AGENTS.md`.
- Evidenz: `git rev-parse --show-toplevel` in `currency-service/` ergibt `.../Solarminer/currency-service`, Remote `Solarminer-app/currency-service`. `diff -rq` der beiden `src`-Bäume zeigt drei abweichende Dateien (`OpenApiConfiguration`, `CoinGeckoPriceService`, `MiningNetworkDataService`) — alle zugunsten des eigenen Repos; die Kopie hier hat keine `conflux`-, `decred`- oder `quantus`-Collector.
- Offene Aufräum-Gate (nicht ausgeführt, Entscheidung nötig): `settings.gradle.kts` enthält weiterhin `include("currency-rates")`, `docker-compose.node-sim.yml` baut den Port 8081 aus `./Solar-Miner-Node/currency-rates/build/libs`, und `.github/workflows/docker-deploy-currency_rates_service.yml` released bei `currency-rates-v*`-Tags. Ein Build oder Image aus diesem Repo beweist daher nichts über `currency.solarminer.app`.

## 2026-10-06 — Correct embedded dashboard asset namespace

- The user's bootRun log showed the proxy's DispatcherServlet on port 8090, while `/proxy-dashboard/` still returned the PC-Agent home page. Root cause: the embedded proxy source set intentionally omitted proxy resources, and the proxy Spring context shared the PC-Agent classpath, so `GET /` resolved PC-Agent `static/index.html`.
- The PC-Agent build now packages only the proxy dashboard files under `static/proxy-dashboard/`. The proxy exposes those assets and its dashboard APIs under `/embedded-dashboard/**`; the PC-Agent reverse route maps its public `/proxy-dashboard/**` paths to that namespace. Standalone proxy `/` remains unchanged. Explicit trailing-slash route is included.
- Verification: `JAVA_HOME=/home/lukas/.jdks/graalvm-ce-21.0.2 ... sh gradlew :pc-agent:compileJava :pc-agent:embeddedProxyClasses --offline --no-daemon` succeeded. Confirmed the three assets are present in `pc-agent/build/resources/embeddedProxy/static/proxy-dashboard/`. No tests or live browser request were run. Restart PC-Agent `:pc-agent:bootRun` to load this packaging change.

## 2026-10-07 — QTC fee-target status after wallet provisioning

- The operator supplied the QTC house payout address and fee-backend now has a
  local/default target, but no deployed target or accepted pool credit is
  verified. Updated the catalog explanation so it no longer says the wallet is
  missing; QTC remains unselectable until the route is confirmed end to end.
- No mining or start gate was opened. See the [QTC integration record](quantus-integration.md).

## 2026-10-07 — Dashboard link follows the selected proxy

- The PC-Agent Connection page now offers a compact “Proxy-Dashboard öffnen” button for either mode. In local mode it opens the bundled dashboard through the Agent's restricted `/proxy-dashboard/` route, and in external mode it opens the selected proxy host's dashboard on port 8090. The external link is hidden when no host is configured; the local link is hidden until the managed proxy is running.
- Opens in a separate tab. No proxy API or routing contract changed.
- Verification: source inspection and `git diff --check`; no build, tests, or browser session run.

## 2026-10-07 — Localize proxy dashboard button

- Added the English translation for the Connection page's proxy dashboard link, so it follows the PC-Agent's saved language preference.
- Verification: source inspection and `git diff --check`; no build, tests, or browser session run.

## 2026-10-07 — Pools-Seite kompakter und Custom-Ziel stabil

- Pool-Karten verwenden weniger Innenabstände und engere Abstände in Fakten- und Zielauswahl; die Inhalte und Pool-Endpunkte bleiben unverändert.
- Live-Overview-Updates bauen die Pools-Karten erneut auf. Die Custom-Pool-Angabe speichert deshalb den geöffneten Zustand und den Entwurf pro Coin, sodass beim Tippen das Formular nicht zuklappt oder den Text verliert.
- Verifikation: `node --check` für `pools.js` und `git diff --check`; keine Builds oder Tests ausgeführt.

## 2026-10-07 — QTC-Kryptex-Pools als Poolvorschläge

- Ergänzte für Quantus die acht auf Kryptex' QTC-Poolseite aufgeführten TCP-Ziele (Global, Europa, Nordamerika, Südamerika, Singapur, Hongkong, Russland und Naher Osten) auf Port 7049.
- Die Vorschläge zeigen die veröffentlichte Poolgebühr von 3 %. Das ändert keine QTC-Mining- oder Fee-Freigabe; QTC bleibt gemäß dokumentiertem Gate gesperrt.
- Verifikation: Abgleich der Hosts und Poolgebühr mit der [offiziellen QTC-Poolseite](https://pool.kryptex.com/qtc), `node --check` für `pool-catalog.js` und `git diff --check`. Keine Builds oder Tests ausgeführt.

## 2026-10-07 — QTC activation follows the live fee target

- Removed the PC-Agent-only `solarminer.quantus.enabled` flag and its configuration/start guards. The Quantus SRBMiner catalog option is selectable like the other implemented GPU paths.
- The existing `ProxyConfigurationService.feeReady("quantus")` check remains the sole activation gate: it requires a reachable proxy response with a valid SolarMiner house target. A missing or invalid target keeps starts stopped; no local configuration can override it.
- Verification: `MinerCatalogServiceTest` asserts that the Quantus adapter is selectable. `ProxyConfigurationServiceTest` also covers the configured QTC port. `JAVA_HOME=/home/lukas/.jdks/graalvm-ce-21.0.2 sh gradlew :pc-agent:test --tests de.verdox.solarminer.pcagent.mining.MinerCatalogServiceTest --tests de.verdox.solarminer.pcagent.mining.ProxyConfigurationServiceTest --offline --no-daemon` passed.

## 2026-10-07 — Pool-Ansicht trennt Zielwahl von Worker-Einrichtung

- Die Pools-Seite verwendet pro Coin jetzt einen kompakten Auswahl- und Übernehmen-Block statt einer vollständigen Kachel für jedes Pool-Ziel. Besonders die acht QTC-Ziele benötigen dadurch nur noch eine Zeile; das eigene Ziel bleibt einklappbar.
- Der Status basiert für diese Ansicht auf dem tatsächlich gespeicherten `poolUrl`, nicht auf der separaten Worker-/Miner-Bereitschaft. Ein erfolgreich gespeichertes Quantus-Ziel erscheint daher als „Pool gesetzt“ und nicht mehr irreführend als „Nicht eingerichtet“. Worker-Zuweisung und Miner-Installation bleiben unter „Worker“ getrennt sichtbar.
- Verifikation: `node --check pc-agent/src/main/resources/static/pools.js`, `node --check pc-agent/src/main/resources/static/components.js` und `git diff --check` erfolgreich. Kein Live-Agent oder Poolwechsel in dieser Umgebung ausgeführt.

## 2026-10-07 — Live-Updates schließen Pool-Auswahl nicht mehr

- `overview`-Events und Polling-Antworten bauen die Pool-Karten nicht mehr neu auf, solange ein Eingabefeld, Textfeld oder Pool-Dropdown aktiv ist. Dadurch bleibt ein gerade geöffnetes natives Auswahlmenü offen und ein getippter Wert stabil.
- Nach dem Verlassen des Controls rendert die Seite den zuletzt erhaltenen Overview-Stand. Ein erfolgreicher Poolwechsel bleibt unverändert über den normalen Refresh-Pfad sichtbar.

## 2026-10-07 — Poolwechsel ohne versteckten Browserdialog

- „Übernehmen“ ist nun die ausdrückliche Bestätigung des Poolwechsels. Die zusätzliche `window.confirm`-Abfrage wurde entfernt, da sie in der eingebetteten PC-Agent-Oberfläche wie ein wirkungsloser Klick erscheinen konnte.
- Beim Absenden erscheint sofort „Poolwechsel wird gespeichert …“; anschließend zeigt die Seite entweder die gespeicherte Route oder den API-Fehler, etwa bei einer noch fehlenden QTC-Wallet.
- Verifikation: `node --check pc-agent/src/main/resources/static/pools.js` und `git diff --check` erfolgreich. Kein Live-Agent-Request in dieser Umgebung.

## 2026-10-07 — Eigene Wallet-Konfiguration pro Coin

- Ergänzt `wallets.html` als klare Auszahlungsseite im Betriebsmenü. Sie speichert je Coin Pool-Ziel, eigene Wallet und Worker-Namen über die bestehenden Coin-Konfigurationsendpunkte. Für GPU-Coins werden vorhandene Geräte übernommen; bei der ersten Konfiguration werden alle erkannten GPUs vorbereitet. Die tatsächliche Zuweisung bleibt getrennt unter Worker.
- Es gibt bewusst keinen zweiten Wallet-Speicher: Die gespeicherte Miner-Konfiguration ist die Quelle für Wallet, Pool und Worker. Deshalb kann die Pools-Seite die Wallet bei einem späteren Zielwechsel automatisch unverändert weiterverwenden.
- Miner-Software und Pools führen direkt zur Wallet-Seite. Verifikation: `node --check` für `wallets.js`, `workspace.js` und `pools.js` sowie `git diff --check` erfolgreich. Kein Live-Agent-Request ausgeführt.

## 2026-10-07 — Poolwechsel verlangt gespeicherte eigene Wallet vor dem API-Request

- Die kompakte Pools-Seite sendet beim Wechsel bewusst die bestehende Wallet/Worker-Konfiguration mit. Fehlt sie, endet ein GPU-Coin wie Quantus in der Server-Validierung beziehungsweise im fehlenden Standard-Auszahlungsziel mit HTTP 400.
- Der Client bricht diesen Fall jetzt vor dem Request ab und zeigt den direkten Hinweis zur Wallet-Seite. Damit wird keine leere QTC-Wallet mehr an `/quantus/configuration` gesendet.

## 2026-10-07 — Wallet-Worker-Muster mit Browser-`v`-Flag kompatibel

- Das HTML-`pattern` für Worker-Namen enthielt ein nicht maskiertes Minuszeichen. Chromium wertet Pattern nun mit dem Unicode-`v`-Flag aus und verwarf deshalb das gesamte Muster; `reportValidity()` blockierte auch bei korrekter Wallet jeden Speicherversuch.
- Das Minuszeichen ist jetzt explizit maskiert. Erlaubt bleiben 1–32 Buchstaben, Ziffern, Unterstriche und Bindestriche – passend zur Servervalidierung.

## 2026-10-07 — Separate Pools-Ansicht entfernt

- Wallets ist nun die einzige Bedienfläche für die Coin-Konfiguration: Pool-Ziel, Wallet und Worker-Name werden gemeinsam über die bestehenden Coin-Konfigurationsendpunkte gespeichert. Die separate `pools.html`/`pools.js`-Ansicht und ihr Navigationspunkt wurden entfernt; der gemeinsame `pool-catalog.js` bleibt als Auswahlquelle für Wallets erhalten.
- Dashboard- und Worker-Verweise führen für Änderungen an Wallets statt an die entfernte Ansicht. Keine API-, Proxy-, Fee- oder Mining-Verträge geändert.
- Verifikation: `node --check` für `workspace.js`, `wallets.js`, `overview.js`, `workers.js` und `pool-catalog.js`, `rg` ohne verbliebene `pools.html`-/`pools.js`-Verweise sowie `git diff --check` erfolgreich. Kein Browser- oder Live-Agent-Lauf ausgeführt.

## 2026-10-07 — Wallets lokalisiert und Pool-Anbieter sichtbar

- Sämtliche Wallets-Texte laufen nun über `SolarMinerI18n`; dynamisch erzeugte Status-, Formular- und Fehlermeldungen werden ebenfalls übersetzt.
- Katalogeinträge führen einen Pool-Anbieternamen. Die Auswahl zeigt zum Beispiel `Kryptex · Europe · 1 % Pool fee` beziehungsweise `Suprnova · Europe`; die Pool-URL bleibt dabei unverändert.
- Verifikation: `node --check` für `wallets.js`, `pool-catalog.js` und `i18n.js`, keine verbliebenen Verweise auf die entfernte Pools-Ansicht sowie `git diff --check` erfolgreich. Kein Browser- oder Live-Agent-Lauf ausgeführt.

## 2026-10-07 — Fee-Verteilungsmodus im PC-Agent wählbar (Zufällig/Ausgewogen)

- Der PC-Agent reicht die Operator-Wahl jetzt an den verwalteten Proxy durch: neuer Schalter `solarminer.agent.fee-roll-mode` (`random` Standard | `stateful`) wird in `ManagedProxyService.commandFor` als `--proxy.fee.roll-mode=` übergeben; `setRollMode` startet den Kindprozess bei Änderung neu.
- Persistenz wie beim Proxy-Modus: `./solarminer-agent/fee-roll-mode.txt` neben `proxy-mode.txt`; `ProxyConfigurationService.setRollMode` validiert, speichert atomar und aktiviert sofort. `activateStoredMode`/`setMode` übernehmen die Einstellung bei jedem Start/Moduswechsel.
- Neuer Endpunkt `POST /api/agent/local/proxy/roll-mode?mode=random|stateful` (pausiert vorher alle Miner, wie der Moduswechsel) und `feeRollMode` im `ProxyOverview`. Der Proxy-Tab zeigt ein neues Feld „Fee-Verteilung" mit den Buttons Zufällig/Ausgewogen, Status-Tag und i18n-Katalogeinträgen. Gilt nur für den lokalen Proxy; ein externer Proxy behält seine eigene Einstellung.
- Verifikation: `JAVA_HOME=/home/lukas/.jdks/graalvm-ce-21.0.2 sh gradlew :pc-agent:compileJava :pc-agent:test` → BUILD SUCCESSFUL, inkl. neuem Test `feeRollModeDefaultsToRandomAndPersistsTheOperatorChoice` (Standard random, Persistenz über Neuladen, unbekannte Werte abgelehnt). Proxy-Seite: siehe `solarminer-stratum-proxy` Work-Log (32 Tests grün).
- Nicht verifiziert: kein Browser- und kein Live-Agent-Lauf; der Neustart des Proxy-Kindprozesses beim Moduswechsel wurde nicht gegen einen laufenden Miner erprobt.

## 2026-10-07 — Proxy-Startfenster bleibt offen oder zeigt leeren Text

- Ursache im Frontend: Das Proxy-Gate abonnierte den SSE-Kanal, startete aber nicht den `SolarMinerLive`-Watchdog. Wenn der EventStream nach einem nicht bereiten Snapshot stillstand, wurde der REST-Snapshot nicht erneut gelesen; das blockierende Fenster konnte so trotz gestartetem Proxy offen bleiben. Titel und Beschreibung waren bis zum ersten Event leer.
- Änderung: Das Gate startet den vorhandenen Watchdog und zeigt sofort verständlichen Starttext. Ungültige/leere Snapshots überschreiben den Text nicht; der Fehlerfall ohne Status bleibt sichtbar.
- Verifikation: Codepfad und EventStream/Watchdog-Verhalten geprüft. Kein Test, Browserlauf oder Live-Agent-Lauf ausgeführt.

### Follow-up: laufender Agent meldete dauerhaft `starting`

- Read-only-Abfrage gegen den laufenden PC-Agent zeigte `ready=false`, `state=starting`, Version `1.0.4`, leere Detailmeldung; auf Proxy-Port 8090 lauschte kein Prozess. Der bestehende Retry-Endpunkt startete denselben gespeicherten Proxy, danach meldete das Gate `ready=true`, `state=running`.
- Ursache/Härtung: Der Boot-Start war vom geplanten `keepRunning()`-Zyklus abhängig. `gate()` startet nun einen verfügbaren Release selbst, meldet `ready` erst nach erfolgreicher Health-Probe und gibt bei einem festhängenden Start einen Fehlerstatus statt eines endlosen `starting` zurück.
- Verifikation: `:pc-agent:compileJava` und `:pc-agent:standaloneJar --offline --no-daemon` erfolgreich. Kein Testlauf. Der neu gebaute Standalone-JAR ist erstellt; der laufende Agent wurde nicht neu gestartet. Sein aktueller Proxy wurde über Retry erfolgreich gestartet.

## 2026-10-07 — Wallet-Salden über Pool-Adapter vereinheitlicht

- `WalletBalanceService` nimmt jetzt die konfigurierten Wallet-/Pool-Paare aller PC-Agent-Coins entgegen und delegiert an `PoolBalanceProvider`-Implementierungen. Der neue Kryptex-Adapter mappt Monero, Pearl, Ravencoin, Ethereum Classic und Quantus auf die Kryptex-API-Coin-IDs. Cache und Fehlerstatus sind je Provider/Coin/Wallet getrennt; der Pearl-On-Chain-Saldo bleibt separat.
- `WalletTarget` liefert die gespeicherte Pool-URL mit. Die globale Wallet-Anzeige richtet sich nach dem Backend-Status und nicht nach einer UI-seitigen XMR/PRL-Allowlist. DCR bleibt `UNSUPPORTED_POOL`, bis es einen Adapter für das konfigurierte Suprnova gibt.
- Verifikation: `git diff --check` und JavaScript-Syntaxprüfung erfolgreich. Der Java-Compile wurde mit `:pc-agent:compileJava --offline --no-daemon` versucht, konnte Gradle aber wegen `Failed to load native library 'libnative-platform.so'` in der Sandbox nicht starten. Kein Test, Browser- oder Live-Wallet-Abruf durchgeführt. Kryptex-API-Endpunkt und sichtbare Coin-Pools anhand der [offiziellen API-Doku](https://pool.kryptex.com/en/api) und [Poolseiten](https://pool.kryptex.com/qtc) abgeglichen. Auszahlungshistorie bleibt außerhalb dieses Umfangs.

## 2026-10-07 — Proxy-Gate beim Seitenwechsel verborgen halten

- Das Proxy-Gate wird jetzt zunächst mit `hidden` angelegt. Es wird erst sichtbar, wenn ein empfangener Agent-Status meldet, dass der Proxy noch nicht bereit ist; ein bereiter Proxy schließt es weiterhin direkt. Damit erzeugt die Statusabfrage beim Navigieren zwischen Seiten kein sichtbares Popup.
- Verifikation: JavaScript-Syntaxprüfung und `git diff --check`; Standalone-JAR neu gebaut. Kein Browser-Test; der laufende PC-Agent wurde nicht neu gestartet.
## 2026-10-07 — PC-Agent coin compatibility audit

- Verglichen: PC-Agent EarningsForecastService coin catalogue mit Node Agent-Earnings-Adapter, MiningCoin/Wallet-API, Dashboard „Mining result today“ und Finanzhistorie.
- Ergebnis: vollständige Kette fehlt für RVN/ETC/DCR/QTC; Node registriert aktuell nur BTC/XMR/PRL. Wallets und tatsächliche tägliche Finanzwerte sind ebenfalls nur BTC/XMR/PRL-fähig (XMR-Adresse ohne öffentlichen Saldo). CFX ist auch im PC-Agent kein Mining-Forecast.
- Detaillierte Coin-Matrix, Quellpfade und Folgeschritte: [PC-Agent/Node Kompatibilitätsaudit](pc-agent-node-coin-compatibility-2026-10-07.md).
- Nur Quellprüfung; keine Produktcodeänderung und keine Tests. Vorhandene uncommitted Arbeit blieb unangetastet.
## 2026-10-07 — Node Coin-Anzeige und Finanzdaten an PC-Agent angeglichen

- `MiningCoin` kennt nun XMR/PRL/RVN/ETC/DCR/QTC und mappt die Agent-Algorithmus-IDs case-insensitiv. Neue Coins erscheinen in Watch-Wallets und Ertragsprognosen. Die Node-Target-Verwaltung bleibt sicher auf BTC/XMR/PRL begrenzt, da Agent-Config-Write-Endpunkte für RVN/ETC/DCR/QTC noch fehlen; Backend lehnt solche Target-Schreibvorgänge ausdrücklich ab.
- Kryptex-Balances unterstützen XMR/PRL/RVN/ETC/QTC. Watch-Wallets können Kryptex-Konten zur tatsächlichen Reward-Historie beitragen; Tages- und Finanzansichten zeigen die tatsächlichen Reward-/Preis-Chartdaten und kennzeichnen fehlende Daten unbewertet. DCR bleibt in Pool-Balance und Reward-Historie `UNSUPPORTED`/`HISTORY_UNAVAILABLE`, weil für den aktuellen PC-Agent-Pool kein Node-Adapter vorhanden ist.
- Dashboard-Gesamtertrag und Mining-Netto werden nicht mehr als BTC-only ausgegeben; wenn ein beobachteter Coin-Rewardsource oder historische Kurs fehlt, bleiben Betrag und Netto nicht verfügbar. BTC-Verkauf/Bestand/Kosten-pro-BTC bleiben BTC-spezifisch. UI-Texte und globale Coin-Symbole wurden ergänzt.
- Verifikation: `react-frontend/node_modules/.bin/tsc --noEmit --pretty false` erfolgreich. Java-Kompilierung blockiert: `./gradlew` ist nicht ausführbar; `sh ./gradlew` konnte den Gradle-Lock im read-only `/home/lukas/.gradle/wrapper/dists` nicht anlegen. Direkter Gradle-Lauf mit Cache-Kopie in `/tmp` scheiterte vor Buildstart mit `Could not determine a usable wildcard IP for this machine`. Keine Tests ausgeführt. Offizielle Kryptex-API-Doku führt Miner-Balance und Reward-Chart auf: https://pool.kryptex.com/en/api.
- Vollständige Matrix und Grenzen: [Kompatibilitätsaudit](pc-agent-node-coin-compatibility-2026-10-07.md).

## 2026-10-07 — PC-Agent bleibt bei deaktivierter Node-Steuerung auffindbar

- `GET /api/agent/external/identity` ist eine gezielte Ausnahme vom globalen externen Steuerungs-Gate. Node Discovery kann den Agenten dadurch weiterhin erkennen, auch wenn der Betreiber Remote-Steuerung deaktiviert hat.
- Alle anderen externen Agent-Routen bleiben hinter `externalControlEnabled`; die Ausnahme gilt nur für GET auf exakt `/identity`.
- Filter-Test aktualisiert für erlaubte Erkennung und weiterhin abgelehnte Status-/Steuerungsrouten. Tests wurden in diesem Schritt nicht ausgeführt.

## 2026-10-08 — currency-rates-Überbleibsel aus diesem Repository entfernt

- `currency-rates/` (eingefrorene Kopie des 2026-10-04-Migrationsstands) gelöscht, inkl. `git rm` des gesamten Quellbaums.
- Gradle-Verdrahtung entfernt: `settings.gradle.kts` (`include("currency-rates")`), `build.gradle.kts` (`currencyRatesVersion`/`currencyRatesImage`, `project(":currency-rates")`, `printCurrencyRatesVersion`, `printCurrencyRatesImage`), `gradle.properties` (`currencyRatesVersion`, `currencyRatesImage`).
- CI entfernt: Workflow `docker-deploy-currency_rates_service.yml` gelöscht; `docker-beta.yml` ohne currency-rates-Matrix, -Outputs und -Manifeste. Release des Images `verdox/currency-rates-api` läuft ausschließlich über das `currency-service`-Repo (Tag `v*`).
- Lokale Currency-Kopie bleibt bewusst erhalten (Anforderer): `compose.yml` startet weiterhin `verdox/currency-rates-api:latest` + `mariadb-currency-service`; der handgemachte `entrypoint` auf `./currency-rates.jar` wurde durch `SPRING_PROFILES_ACTIVE=production` ersetzt, damit das offizielle Image mit eigenem ENTRYPOINT läuft. `docker-compose.node-sim.yml` baut den Sim-Container jetzt aus `./currency-service/build/libs/currency-rates.jar` (Artefaktname des gepflegten Repos).
- Doku aktualisiert: `docs/API.md`, `docs/agent-wiki/README.md`, `docs/agent-wiki/currency-data.md`, Workspace-`DOCKER-NODE-SIM.md`/`DOCKER-LOCAL.md`. `AGENTS.md` (Node + Workspace) konnte nicht automatisch geschrieben werden (geschützte Anweisungsdatei, Genehmigung nicht erteilt) — muss manuell nachgezogen werden: der "leftover copy / pending removal"-Abschnitt ist überholt.
- Verifikation: `sh gradlew -q projects` zeigt nur noch `:cgminerapi :core :pc-agent :proto :pv-api`; `sh gradlew compileJava :pc-agent:compileJava :core:compileJava` erfolgreich (EXIT 0); `docker compose config -q` für `compose.yml` und `docker-compose.node-sim.yml` fehlerfrei. Kein `currency-rates`-Verweis mehr in Build-/CI-/Compose-Dateien außer dem Image-Namen in `compose.yml`.

## 2026-10-08 — pc-agent als eigenes Repository ausgelagert und hier entfernt

- `pc-agent/` gelöscht; `settings.gradle.kts` (`include("pc-agent")`), `build.gradle.kts` (`pcAgentVersion`/`pcAgentImage`, `project(":pc-agent")`, `printPcAgent*`-Tasks) und `gradle.properties` bereinigt.
- CI: `docker-deploy-pc-agent.yml` gelöscht; `docker-beta.yml` ohne `publish-pc-agent`, `build-pc-agent-beta-release-assets`, `publish-pc-agent-beta-release` und ohne `pc_agent_image`-Output. Beta-Builds des Agenten laufen jetzt im Repo `Solarminer-app/pc-agent` (Branch `beta` → `beta.yml`, gleiche Image-Tags `latest-beta`/`latest-amd64-beta` und `pc-agent-beta-<shortsha>`-Prereleases).
- Der Node-Code (Root-App, Core) war nie compile-time-abhängig; Kopplung bleibt rein HTTP/UDP (`:8084`, Discovery). Keine Code-Änderungen nötig.
- Doku: `README.md`-Abschnitt 5 auf externes Repo umgestellt; `docs/API.md`-Abschnitt „PC Agent API" nach `pc-agent/docs/API.md` verschoben (Zeiger bleibt); `PC-AGENT-PV-POWER-CONTROL.md` und alle `pc-agent*.md`/`rvn-etc-integration.md` Wiki-Seiten ins Agent-Repo verschoben; Wiki-README auf externe Verweise umgestellt. `AGENTS.md` (Node) erwähnt weiterhin `pc-agent/MINER-INTEGRATION-GUIDE.md` — geschützte Datei, manuell nachziehen.
- Verifikation: `sh gradlew -q projects` → nur `:cgminerapi :core :proto :pv-api`; `sh gradlew compileJava :core:compileJava` EXIT 0; `docker-beta.yml`-Job-Kette konsistent (metadata → build-native-images → publish-multi-arch-tags); kein `pc-agent`-Verweis mehr in Build-/CI-/Compose-Dateien.
