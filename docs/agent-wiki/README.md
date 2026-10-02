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
| `currency-rates/` | Currency rate service | Keep market rates separate from miner control. |
| `device-profiles/`, `tools/` | Bundled profiles and import tooling | Runtime community profile source is separate. |

## Documentation catalog

| Document | Use / status |
| --- | --- |
| [PC-Agent ownership and gaps](pc-agent.md) | Code-checked map and review queue, checked 2026-10-01. |
| [API](../API.md), [mining targets](../MINING-TARGETS.md) | Interface references; verify endpoints and behavior in code before changing them. |
| [device protocol roadmap](../DEVICE_PROTOCOL_ROADMAP.md), [21energy status](../21ENERGY-SOFTWARE-STATUS.md) | Roadmap/status; follow [21energy integration guide](../../../21ENERGY-INTEGRATION.md) for related changes. |
| [PC-Agent PV power control](../../PC-AGENT-PV-POWER-CONTROL.md) | Design and verification checklist; some API and GPU control code now exists, so it is not a complete current-state description. |
| [standalone PC-Agent](../../pc-agent/standalone/README.md), [Docker](../../pc-agent/standalone/DOCKER.md) | Run and packaging instructions; check scripts/workflows before relying on release claims. |
| [main README](../../README.md), [core help](../../core/HELP.md), [currency help](../../currency-rates/HELP.md) | Component guides; code is authoritative. |
| `src/main/resources/markdowns/` | Product help shown to users, not agent operating instructions. |

## Feature workflow

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
