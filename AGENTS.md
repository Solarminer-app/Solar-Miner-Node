# Agent entry point — Solar-Miner-Node

Read [the repository wiki](docs/agent-wiki/README.md) before changing code. For PC-Agent work, also read [its ownership map](docs/agent-wiki/pc-agent.md). When adding another miner implementation to an existing coin, read and follow [the PC-Agent miner integration guide](pc-agent/MINER-INTEGRATION-GUIDE.md). Check `git status` first and preserve existing changes.

**`currency-rates/` in this repository is a leftover copy — do not work in it.** The Currency Service is a separate Git repository at [`../currency-service`](../currency-service) (`https://github.com/Solarminer-app/currency-service`) and owns all currency, coin-price and mining-network code, tests and contract documentation. The copy here is frozen at the 2026-10-04 migration state and is behind the live service. Do not edit, extend, refactor, test, release or document it, and do not mirror changes into it; read `../currency-service/AGENTS.md` and `../currency-service/docs/agent-wiki/` instead. The module is still listed in `settings.gradle.kts` and still has `docker-deploy-currency_rates_service.yml`, so building or releasing it from this repository does not change `currency.solarminer.app` — that wiring is pending removal.

For Java work, check whether IntelliJ IDEA MCP is available and choose it when IDE navigation, references, diagnostics, or run configurations would help; otherwise use repository tools. Keep MCP queries focused.

Cross-repository changes follow the [workspace guide](../AGENTS.md), [architecture overview](../admin-portal/docs/encyclopedia/00-workspace-overview.md), and [contracts](../admin-portal/docs/encyclopedia/08-contracts.md). New coin or algorithm work requires the [end-to-end guide](../NEW-MINING-COIN-GUIDE.md). Pearl and 21energy changes also require their dedicated integration records in the workspace root.

Treat source and tests as the authority for current behavior. Mark a wiki statement as verified only with file or test evidence; distinguish design proposals from shipped behavior. Record cross-repository contract changes in every affected repository.
