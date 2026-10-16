# Economic cluster configuration

`MinerControllerConfigStorage` creates `Economic.json` beside `Standard.json`
on first start and never overwrites either existing file. No miner is assigned
to the new cluster automatically. A site operator assigns miners and starts
the cluster through the existing mining UI. The economic rule is an
`economic` controller action in `DefaultCluster.ECONOMIC`; its `ECONOMIC_THRESHOLD`
value holds the grid-price margin and grid-profitable battery floor and its strategy chooses percent or absolute
Eurocent/kWh. `stepSizeWatts` stores the PV-only battery threshold as a
percentage for this action (default 70); the grid-profitable floor defaults
to 10% and can be set to 0%. Existing `CONSTANT` economic actions still
load with a 10% grid floor and are upgraded when saved through the editor.

The Node converts the site's current feed-in and grid tariffs to EUR and
compares the best measured worker forecast in EUR/kWh. A worker above the
feed-in tariff may consume PV surplus. Above 70% SoC it may also draw from
the battery when PV output falls away. Below the PV-only floor it runs only if
its measured watts fit the current potential PV surplus. A worker above the
grid price plus the configured margin may draw from the battery down to the configured grid floor, including without PV output.
Below that floor, only workers that fit within current PV surplus may run.
At a 0% grid floor, grid-profitable workers remain eligible at 0% SoC and
may continue using grid power. PV-only
worker allocations share one approximate surplus budget per cluster tick.

For PC-Agents the Node reads the C10 capability payload, requiring local
`AUTO` consent, a configured fee-ready coin route, a positive benchmark or
observed performance profile no older than 30 days, and gross USD/day market
data no older than two hours. It selects a best coin for workers in manual
power mode; workers using an Agent-local automatic power mode retain their
current coin. The Node renews a 90-second `activeWorkerIds` plan; the Agent
owns the actual per-worker
start/pause and coin assignment. Worker control of GPU rigs requires a current
PC-Agent version with the additive C10 fields. `FIXED`/disabled workers are
not controlled by this rule. Non-Agent miners are controlled as whole units
from their existing earnings source. A stopped miner uses a recent observed
operating point or a learned/nominal J/TH efficiency at its configured maximum
power target; a running miner needs telemetry no older than five minutes.
Missing economic inputs pause managed miners. The dry-run simulator rejects this rule because
historical worker profiles and market forecasts are not part of its data.

The Core proxy uses a two-second connection and three-second response timeout
for economic Agent requests so an unavailable LAN peer cannot block a cluster
tick indefinitely.

These are probabilistic gross mining estimates. Pool fees, stale/rejected
shares, actual payout and battery round-trip losses can change real returns.
The whole-miner path cannot make an individual ASIC hashboard decision, and a
stopped miner without a usable observed or efficiency profile remains off.
Real GPU, battery and tariff switching has not been validated on physical hardware.

## Local Worker operating modes (2026-10-16)

The PC-Agent's additive C10 `powerMode` field identifies locally managed power targets. For a mode other than `MANUAL`, this Node considers only the worker's `selectedCoin` forecast and includes the worker in `activeWorkerIds` without sending a coin assignment. The Agent remains responsible for its measured power point and any locally permitted coin switch. The existing `AUTO` coin-policy and worker-control consent are still required before the economic cluster may start or pause a worker. Older Agents without `powerMode` keep the previous `MANUAL` interpretation. The Agent also reports dynamic power scaling disabled in `/external/power-control` while a local automatic mode is assigned, so the ordinary miner controller treats the PC as a fixed-power on/off device.
