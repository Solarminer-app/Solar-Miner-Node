# PC-Agent: ownership and extension points

Checked against `pc-agent/src/main/java` on 2026-10-01. This describes source structure, not hardware or pool certification. Read [the repository wiki](README.md), [standalone instructions](../../pc-agent/standalone/README.md), and the [PV-power design](../../PC-AGENT-PV-POWER-CONTROL.md) for the task at hand.

| Concern | Current owner | Extension rule |
| --- | --- | --- |
| HTTP boundary | `controller/` including `MiningController`, `AgentPowerController`, `TelemetryController` | Validate requests and delegate; no driver or pool business logic here. |
| CPU mining | `xmr/XmrMinerService`, `XmrConfigService`, `xmr/download/` | Keep XMRig/RandomX details here. |
| GPU/Pearl mining | `pearl/PearlMinerService`, `SrbDownloadService` | Keep SRBMiner/Pearl process, install-manifest cleanup and protocol details here. Read the [Pearl record](../../../PEARL-INTEGRATION.md). |
| Local power allocation | `mining/MiningService` | Owns total target and CPU/GPU allocation. The Node should not duplicate this allocation. |
| GPU driver power | `pearl/LocalGpuPowerService` | Detect capabilities, apply user bounds, write and read back limits. Unsupported GPUs remain start/stop only. The `pearl` package name currently hides a reusable hardware concern; consider moving it when another GPU miner uses it. |
| Proxy and fee | `mining/Proxy*`, `ManagedProxyService`, `StandaloneFeeGuard`, `FeeTransparencyService` | Fee validity gates start; keep pool/fee routing outside miner process implementations where possible. |
| Hardware sensors | `lowlevel/sensor/` | Report source, units and availability; missing is not zero. |
| Local settings and assessment | `mining/AgentControlSettingsService`, `NodeAssessmentService` | Node provides its economic/PV decision; agent controls local mining. |
| Operator UI | `src/main/resources/static/` | Render service state; do not reimplement fee or power decisions in JavaScript. |

## Verified seams and gaps

- `AgentPowerController` exposes `GET /api/agent/power-control` for the local Hardware UI and `GET /external-status` for the Node, plus `POST /target`, `/external/target`, `/external/pause`, `/external/resume`. External commands check the global `externalControlEnabled` permission and per-worker settings (`cpu` or the GPU device ID). The Node-only status omits opted-out GPU identities, while `GET /api/agent` filters disabled workers and their aggregate stats; local mining controls remain independent. The PV-power document's example JSON is a design sketch; use the controller records for the current wire format.
- `MiningService.setTarget` allocates a total target across XMR CPU and eligible Pearl GPUs. It still directly depends on both miner services; a third miner should prompt an explicit lifecycle/capability interface instead of another branch in orchestration.
- `LocalGpuPowerService` currently implements dynamic limits via `nvidia-smi`; its AMD discovery reports unsupported dynamic control without a stable ID. A code path existing is not proof of verified hardware behavior.
- `MiningService` stores desired power targets in memory and contains a persistence TODO. Check restart behavior before relying on target continuity.
- The miner catalog exposes install/remove actions. Removal stops the selected miner, deletes its executable and tracked SRBMiner package files, and preserves pool/wallet configuration.
- The shipped standalone instructions describe a local embedded proxy and explicit miner installation. Verify release assets and actual fee routes during rollout, especially for new coins.

## Adding a miner

Document algorithm, device capability, installer provenance, process lifecycle, status units, power-control semantics, fee route and failure behavior. Keep the new miner's executable/API adapter in its own package. Add a shared interface only where two or more implementations have the same semantics; avoid a generic interface that hides unsupported operations. Follow the [coin integration guide](../../../NEW-MINING-COIN-GUIDE.md) and record real pool/fee accounting evidence before marking support complete.
