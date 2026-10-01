@echo off
setlocal
set "INSTALL_DIR=%LOCALAPPDATA%\SolarMiner\PC-Agent"
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $r=Invoke-RestMethod 'https://api.github.com/repos/Solarminer-app/Solar-Miner-Node/releases/latest'; $a=$r.assets | Where-Object name -eq 'start-agent.ps1' | Select-Object -First 1; if(-not $a){throw 'The latest PC-Agent release has no launcher asset.'}; Invoke-WebRequest -Uri $a.browser_download_url -OutFile $env:LOCALAPPDATA\SolarMiner\PC-Agent\start-agent.ps1; & $env:LOCALAPPDATA\SolarMiner\PC-Agent\start-agent.ps1"
if errorlevel 1 (
  echo.
  echo SolarMiner PC-Agent could not be downloaded or started. Check your internet connection and try again.
  pause
  exit /b 1
)
