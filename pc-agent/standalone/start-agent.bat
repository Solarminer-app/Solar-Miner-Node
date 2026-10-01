@echo off
cd /d "%~dp0"
java -jar "solarminer-pc-agent-standalone.jar" %* --solarminer.agent.standalone=true
