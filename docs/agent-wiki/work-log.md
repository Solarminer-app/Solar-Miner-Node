# Agent work log

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
