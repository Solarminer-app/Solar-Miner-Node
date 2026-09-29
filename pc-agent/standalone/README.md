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

For development, `:pc-agent:standaloneBootRun` starts Spring Boot directly from the compiled Agent and proxy classes. Use this Gradle task as an IntelliJ run configuration with Gradle JVM 21; it sets `--solarminer.agent.standalone=true` and uses the workspace root as its working directory. It does not require building the JAR first.

The JAR contains the PC-Agent and the Stratum proxy source/classes and dependencies. It starts the proxy in a separate Spring context in the same JVM; the proxy API and Stratum listener bind to loopback. The agent UI does not expose proxy controls or status. The service fetches fee targets from the SolarMiner fee backend and refuses mining unless a valid fee route is loaded. Internet access to the fee backend is required; an empty or unavailable fee response leaves mining stopped.

Pearl remains disabled by default pending real SRBMiner/Kryptex shares and pool accounting.

The Mining page shows a separate live console for each agent-managed miner. The complete output is appended to `./solarminer-agent/logs/xmrig-console.log` and `./solarminer-agent/logs/srbminer-console.log`, including previous runs, and can be downloaded from the miner view. The page reads the files in chunks via `/api/agent/console/{monero|pearl}`; it does not truncate the history. These local files may contain pool login details and grow until an operator archives them.

Opening a miner tab only changes the dashboard view. XMRig and SRBMiner can run at the same time; each selected Pearl GPU has its own SRBMiner process, API port, status, controls and console. The CPU and GPU start/pause buttons affect only the viewed miner. Saving a Pearl configuration that merely adds GPUs keeps existing GPU processes running; changing its pool, wallet, worker or proxy stops the affected Pearl processes. Changing the proxy host stops both coin paths because their routes must be revalidated.
