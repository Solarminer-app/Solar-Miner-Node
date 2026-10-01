# Agent work log

Use a short dated entry for changes that affect architecture, contracts, mining support or documentation truth. Link commits/issues when available. Do not paste secrets or raw wallet credentials.

## Entry template

### YYYY-MM-DD — change

- Scope and owner:
- Implemented behavior and code evidence:
- Cross-repository contracts checked:
- Verification performed and result:
- Remaining gaps / hardware or rollout gates:
- Documentation updated:

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
