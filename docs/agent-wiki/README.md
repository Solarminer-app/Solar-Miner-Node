# Solar-Miner-Node agent wiki

**Scope:** this Git repository only. The parent Solarminer folder is a multi-repository workspace, not a Git repository. Start with [the workspace architecture](../../../admin-portal/docs/encyclopedia/00-workspace-overview.md) and [contracts](../../../admin-portal/docs/encyclopedia/08-contracts.md) for work across repositories.

## Responsibility map

| Area | Owns | Boundary |
| --- | --- | --- |
| Root app `src/`, `app/` | Local API, persistence, PV/site orchestration, telemetry | Calls services and stores site state; hardware-specific control belongs in `core` or `pc-agent`. |
| `core/` | Miner abstractions, dispatch, ASIC/Braiins control, dev-fee path | Must not duplicate PC hardware control; consult [core help](../../core/HELP.md). |
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

1. Inspect `git status`, relevant code/tests and this map. Write down the owner and affected cross-repo contracts before coding.
2. Extend the existing miner/device abstraction when a new implementation shares lifecycle or telemetry semantics. Keep protocol-specific parsing and driver calls at the edge. Do not add coin or GPU conditionals to unrelated orchestration layers.
3. Define capability and unsupported states explicitly. For hardware control, do not infer a zero measurement from missing telemetry. For a new coin/algorithm, complete [the integration guide](../../../NEW-MINING-COIN-GUIDE.md), including fee route, pool accounting, portal and rollout evidence; document non-applicable steps.
4. Verify the smallest meaningful path, then the affected contract on both sides. A successful build alone does not prove a mining or payment path works.
5. Add a dated entry to [work log](work-log.md) with changed contracts, evidence, open gates and links. Update stale wiki statements in the same change.

## Current review queue

- The PC-Agent miner paths share some orchestration but still have direct XMR/Pearl dependencies in `MiningService`; see [PC-Agent map](pc-agent.md). Extract a common lifecycle contract when a third miner makes duplication concrete, preserving separate protocol and hardware adapters.
- `MiningService` has a TODO to persist the desired global power target. Resolve restart semantics before treating that target as durable.
- Hardware-specific GPU power behavior needs real driver/OS verification. Source inspection cannot establish that a limit is safely applied on every supported device.
