# Solar-Miner-Node agent wiki

**Scope:** this Git repository only. The parent Solarminer folder is a multi-repository workspace, not a Git repository. Start with [the workspace architecture](../../../admin-portal/docs/encyclopedia/00-workspace-overview.md) and [contracts](../../../admin-portal/docs/encyclopedia/08-contracts.md) for work across repositories.

## Responsibility map

| Area | Owns | Boundary |
| --- | --- | --- |
| Root app `src/`, `app/` | Local API, persistence, PV/site orchestration, telemetry | Calls services and stores site state; hardware-specific control belongs in `core` or `pc-agent`. |
| `core/` | Miner abstractions, dispatch, ASIC/Braiins control, dev-fee path | Must not duplicate PC hardware control; consult [core help](../../core/HELP.md). |
| `cgminerapi/` | CGMiner TCP framing, commands and response DTOs used by `core` | Transport tests cannot establish framing or timeout suitability on supported ASIC firmware; keep the real-device gate explicit. |
| `pc-agent/` | Local CPU/GPU mining and hardware-specific power control | Node sends a target or decision, not per-GPU driver commands. See [PC-Agent map](pc-agent.md). |
| `pv-api/` | Shared profile serialization contract | Configurator must use the same serializer; see contract C3. |
| `react-frontend/` | Local operator UI | API and decisions stay in backend services. |
| Currency Service | **Nothing — not code in this repository.** | The Currency Service is the separate Git repository [`../../../currency-service`](../../../currency-service) (`https://github.com/Solarminer-app/currency-service`). The leftover `currency-rates/` copy from the 2026-10-04 migration was removed on 2026-10-08 (module, Gradle wiring and release workflow gone). This repository only consumes the deployed HTTP API; the production Compose stack runs the published `verdox/currency-rates-api` image built by that repository. |
| `device-profiles/`, `tools/` | Bundled profiles and import tooling | Runtime community profile source is separate. |

## Documentation catalog

| Document | Use / status |
| --- | --- |
| [Setup UX and PV discovery](setup-and-discovery.md) | Beginner-oriented device setup, bounded discovery API, verification and remaining hardware/UX gates; reviewed 2026-10-02. |
| [UI/UX audit](ui-ux-audit-2026-10-02.md) | Source and Chrome desktop/mobile review of the local Node UI, reproducible usability findings and proposed redesign priorities; 2026-10-02. Proposals are not implemented behavior. |
| [PC-Agent gesamtes UI/UX-Konzept](pc-agent-design-concept.md) | Bewertung, Aufgabenstruktur und umgesetzte Gestaltung aller sechs PC-Agent-Seiten mit Browser-Evidenz; 2026-10-03. |
| [PC-Agent UI/UX-Redesign](pc-agent-ui-redesign.md) | Verbindliche Spezifikation und Nachweis des Umbaus: Dashboard-KPIs, Miner-Instanzmodell (Coin × Build), neue Seiten Worker und Pools, gemeinsames Komponentenmodul, additive Pool-Telemetrie; mit Browser- und Test-Evidenz, 2026-10-05. |
| [PC-Agent Operations UI & energy journal](pc-agent-operations-ui.md) | Aktueller Seitenvertrag: Miner als reine Softwareverwaltung, Worker als vollständige Zuweisungs-/Steuerungsfläche, globaler Balance-Header und persistentes lokales Session-Energiejournal; 2026-10-06. Ersetzt für diese vier Seiten das ältere Coin×Build-Interaktionsmodell. |
| [PC-Agent Mining UX and Node defaults](pc-agent-ux-profile.md) | Persistent per-device defaults, local/benchmark-only exclusion, browser and backend verification; 2026-10-03. |
| [PC-Agent ownership and gaps](pc-agent.md) | Code-checked map and review queue, checked 2026-10-01. |
| [PC-Agent pool API research](pc-agent-pool-api-research-2026-10-04.md) | Public wallet balance and payout API candidates for XMR, PRL, RVN and ETC; research only, checked 2026-10-04. |
| [PC-Agent miner candidates](pc-agent-miner-candidates.md) | Modular per-coin miner-catalog contract, candidate matrix and explicit adoption gates for XMR/PRL/RVN/ETC; checked 2026-10-05. |
| [Quantus (QTC) integration](quantus-integration.md) | GPU/Stratum candidate wiring and explicit missing house-wallet, pool-share and rollout gates; 2026-10-06. |
| [PC-Agent miner integration guide](../../pc-agent/MINER-INTEGRATION-GUIDE.md) | Mandatory checklist for future agents adding a miner implementation to an existing PC-Agent coin. |
| [Public currency/network data](currency-data.md) | **Superseded for the service side** — the C9 provider/schema record now lives in [`../../../currency-service/docs/agent-wiki/currency-data.md`](../../../currency-service/docs/agent-wiki/currency-data.md). This file keeps only the Node/PC-Agent consumer view. |
| [RVN / ETC integration](rvn-etc-integration.md) | In-progress PC-Agent GPU contract and unverified gates; neither coin is enabled. |
| [API](../API.md), [mining targets](../MINING-TARGETS.md) | Interface references; verify endpoints and behavior in code before changing them. |
| [device protocol roadmap](../DEVICE_PROTOCOL_ROADMAP.md), [21energy status](../21ENERGY-SOFTWARE-STATUS.md) | Roadmap/status; follow [21energy integration guide](../../../21ENERGY-INTEGRATION.md) for related changes. |
| [PC-Agent PV power control](../../PC-AGENT-PV-POWER-CONTROL.md) | Design and verification checklist; some API and GPU control code now exists, so it is not a complete current-state description. |
| [standalone PC-Agent](../../pc-agent/standalone/README.md), [Docker](../../pc-agent/standalone/DOCKER.md) | Run and packaging instructions; check scripts/workflows before relying on release claims. |
| [main README](../../README.md), [core help](../../core/HELP.md) | Component guides; code is authoritative. |
| Currency Service guides | In the separate repository: [`AGENTS.md`](../../../currency-service/AGENTS.md), [`README.md`](../../../currency-service/README.md), [`PUBLIC-DEPLOYMENT.md`](../../../currency-service/PUBLIC-DEPLOYMENT.md). |
| `src/main/resources/markdowns/` | Product help shown to users, not agent operating instructions. |

## Feature workflow

### Public currency and mining-network data

**Ownership:** this service is not code in this repository. It is the separate Git repository [`../../../currency-service`](../../../currency-service) (`https://github.com/Solarminer-app/currency-service`), which owns the providers, persistence, public routes, tests and the C9 record. The leftover `currency-rates/` copy from the 2026-10-04 migration was deleted on 2026-10-08 together with its Gradle and CI wiring. Node and PC-Agent code may only call the deployed API.

`currency-rates` is a public central service, not a trust-LAN endpoint. Production Nodes use `CURRENCY_MICRO_SERVICE_URL`, defaulting to `https://currency.solarminer.app`; local Compose may use the internal `http://currency-service:8080`. Only versioned, read-only aggregated snapshots belong under `/api/v1/public/**`. Wallets, worker names, pools, referrals, individual telemetry and administration do not.

For every mining coin that becomes relevant to Node or public profitability calculations, extend that repository first with both price and mining-network collection. Record the canonical key/ticker, provider, units and precision, timestamp, refresh cadence, persisted snapshot, stale/unavailable behavior and tests. A coin-price record alone is not a network-statistics contract. Follow C9 in the workspace contracts and the currency-data requirement in `NEW-MINING-COIN-GUIDE.md`; do not add another coin-specific BTC-style table for new assets.

### Public telemetry egress

`telemetry/TelemetryReporter` sends periodic anonymized samples only for opted-in PV sites. An opt-out sends a minimal `{uuid, telemetryOptIn:false}` revocation without measurements; delivery is retried until acknowledged in the current process. The public admin endpoint still authenticates only by body UUID, so this revocation and normal telemetry are not yet protected by per-node proof of possession (cross-repo issue F22/B05).

The standalone PC-Agent exposes its own Benchmarks page and default-off benchmark-sharing consent. `BenchmarkSharingService` sends active hardware results every 15 minutes to the Admin benchmark-only endpoint; no Node process is required. Manual sessions show phase and remaining time, compare local medians with the public group, and restore managed miners that were running before the sequential run. See C8a in the shared contract.

1. Inspect `git status`, relevant code/tests and this map. Write down the owner and affected cross-repo contracts before coding.
2. Extend the existing miner/device abstraction when a new implementation shares lifecycle or telemetry semantics. Keep protocol-specific parsing and driver calls at the edge. Do not add coin or GPU conditionals to unrelated orchestration layers.
3. Define capability and unsupported states explicitly. For hardware control, do not infer a zero measurement from missing telemetry. For a new coin/algorithm, complete [the integration guide](../../../NEW-MINING-COIN-GUIDE.md), including fee route, pool accounting, portal and rollout evidence; document non-applicable steps.
4. Verify the smallest meaningful path, then the affected contract on both sides. A successful build alone does not prove a mining or payment path works.
5. Add a dated entry to [work log](work-log.md) with changed contracts, evidence, open gates and links. Update stale wiki statements in the same change.

## Current review queue

- The PC-Agent miner paths share some orchestration but still have direct XMR/Pearl dependencies in `MiningService`; see [PC-Agent map](pc-agent.md). Extract a common lifecycle contract when a third miner makes duplication concrete, preserving separate protocol and hardware adapters.
- C03 defines the desired global power target as deliberately volatile: an agent restart begins idle and cannot replay a stale Node target. The pure plan and its last application result are documented in [the PC-Agent map](pc-agent.md); hardware validation remains open.
- Hardware-specific GPU power behavior needs real driver/OS verification. Source inspection cannot establish that a limit is safely applied on every supported device.
- CGMiner transport now has loopback coverage for delayed, NUL-terminated, EOF-terminated, timed-out and malformed responses. Before C05 rollout, a device/integration operator must capture framing from supported ASIC firmware and verify that the 5-second connect and 10-second inactive-read limits remain safe under load.
