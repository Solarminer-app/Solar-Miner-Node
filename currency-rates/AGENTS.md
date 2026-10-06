# Do not work here — this copy is a leftover

This directory is **not** the Currency Service anymore. The service lives in its
own Git repository: [`../../currency-service`](../../currency-service)
(`https://github.com/Solarminer-app/currency-service`), with its own `AGENTS.md`,
`docs/agent-wiki/`, Gradle build, CI and release pipeline. That repository is
the only place where currency, coin-price and mining-network code, tests and
contract documentation change.

Evidence of the split, checked 2026-10-06:

- `git rev-parse --show-toplevel` inside `currency-service/` returns
  `.../Solarminer/currency-service`, remote `Solarminer-app/currency-service`.
- This copy is frozen at the 2026-10-04 migration state; the standalone service
  already collects `conflux`, `decred` and `quantus` network snapshots, which
  `src/main/java/.../service/MiningNetworkDataService.java` here does not.
- This copy is still wired into `../settings.gradle.kts`,
  `../../docker-compose.node-sim.yml` and
  `../.github/workflows/docker-deploy-currency_rates_service.yml`. That wiring
  is pending removal; a green build or a published image from it is not evidence
  that `currency.solarminer.app` changed.

Rules:

- Do not edit, extend, refactor, test, release or delete anything here without an
  explicit instruction from the owner.
- Do not describe current behavior from these files or these docs; read and
  update `../../currency-service/docs/agent-wiki/` instead.
- Consumers in this repository keep calling the deployed HTTP API
  (`CURRENCY_MICRO_SERVICE_URL`); that is the only legitimate dependency on this
  service.
