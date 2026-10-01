# SolarMiner PC-Agent Standalone

From `Solar-Miner-Node`, build a single executable JAR. On the current Windows workstation, select the installed JDK 21 first:

```powershell
$env:JAVA_HOME = 'C:\Users\Lukas\.jdks\temurin-21.0.8'
.\gradlew.bat :pc-agent:standaloneJar
```

The output is `pc-agent/build/distributions/solarminer-pc-agent-standalone.jar`. Run it from the same directory with Java 21:

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar .\pc-agent\build\distributions\solarminer-pc-agent-standalone.jar --solarminer.agent.standalone=true
```

Open `http://127.0.0.1:8084/` for the PC-Agent overview. Open **Mining**, choose CPU or GPU, then use **Herunterladen & installieren** for Monero/XMRig or Pearl/SRBMiner-MULTI. Neither miner is downloaded on agent startup. Installed miners appear in the left rail; select one to configure its pool, wallet and worker, then start it. Installation progress and errors appear on the Mining catalog. Stop the application with Ctrl+C. On another PC, copy the single JAR and run the same `java -jar` command with that PC's Java 21 installation. The optional `start-agent.bat` and `start-agent.sh` scripts work when placed beside the JAR.

The second header row reads actual balances for the configured payout destinations. For Kryptex routes it shows the XMR/PRL credit still held by the pool. For Pearl it also shows the on-chain balance of the configured payout address via pearlchain.live (one address, not an entire multi-address wallet). A Monero wallet's on-chain balance cannot be read from its public address. Unsupported pools and unavailable APIs show a dash instead of zero. `GET /api/agent/wallet-balances` supplies these amounts; successful upstream results are cached for one minute. Approximate fiat values use the agent's existing XMR/USD and PRL/USD market data plus the public Frankfurter USD exchange rate; the user can select EUR, USD or CHF in the header. The displayed aggregate includes only the balances whose amounts and prices are available.

For development, `:pc-agent:bootRun` starts Spring Boot directly from the compiled Agent and the bundled proxy classes. On a fresh development data directory it selects the local proxy; a persisted UI selection of an external proxy remains respected. Use this Gradle task as an IntelliJ run configuration with Gradle JVM 21. It does not require building the JAR first.

The JAR contains the PC-Agent and the Stratum proxy source/classes and dependencies. It starts the proxy in a separate Spring context in the same JVM; the proxy API and Stratum listener bind to loopback. The agent UI does not expose proxy controls or status. The service fetches fee targets from the SolarMiner fee backend and refuses mining unless a valid fee route is loaded. Internet access to the fee backend is required; an empty or unavailable fee response leaves mining stopped.

On a new installation, the Mining page starts with a local quick-start checklist. The normal route asks for the operator's own pool wallet. Choosing the SolarMiner standard payout is explicit: the UI shows the masked destination and complete fee breakdown, and requires a confirmation because the whole payout goes to that target. Windows sensor elevation is optional for mining; without it, hardware telemetry is unavailable but miner installation, configuration and local start remain usable. To let a SolarMiner Node control the agent, enable **Node-Steuerung** locally under Hardware first. The Node can publish its economic/PV decision to `POST /api/agent/node-assessment` with `{ "decision": "PROFITABLE"|"NOT_PROFITABLE"|"UNKNOWN", "reason": "...", "source": "SolarMiner Node" }`; the agent persists and displays that decision but does not calculate PV profitability itself.

Pearl requires a saved valid configuration, the explicitly installed SRBMiner
binary, a selected supported GPU and a reachable local proxy with a loaded fee
route. It does not start automatically.

The Mining page shows a separate live console for each agent-managed miner. Output is appended to `./solarminer-agent/logs/xmrig-console.log` and `./solarminer-agent/logs/srbminer-console.log`, including previous runs, and can be downloaded from the miner view. The page reads the files in chunks via `/api/agent/console/{monero|pearl}`. Each active log is capped at 10 MiB; when that limit is reached, the previous log is retained once as `.log.1`. These local files may contain pool login details.

Opening a miner tab only changes the dashboard view. XMRig and SRBMiner can run at the same time; each selected Pearl GPU has its own SRBMiner process, API port, status, controls and console. The CPU and GPU start/pause buttons affect only the viewed miner. Saving a Pearl configuration that merely adds GPUs keeps existing GPU processes running; changing its pool, wallet, worker or proxy stops the affected Pearl processes. Changing the proxy host stops both coin paths because their routes must be revalidated.

## Linux Docker

For the Linux `amd64` container, GPU-specific Compose overlays and the explicit
XMR/RandomX host preparation are documented in [DOCKER.md](DOCKER.md). The
container does not download or start a miner by itself; use the local agent UI
to explicitly install and configure it after startup.
