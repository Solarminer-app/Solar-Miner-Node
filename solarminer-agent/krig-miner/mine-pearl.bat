@echo off
@cd /d "%~dp0"

rem krig-miner auto-detects the GPU backend: CUDA first, then ROCm (HIP runtime
rem 6 tried before 7). Optional flags to add below:
rem   --rocm-runtime 6|7   pin the HIP runtime major
rem   --no-cuda            disable the CUDA backend (AMD-only rig)
rem   --no-rocm            disable the ROCm backend (NVIDIA-only rig)

krig-miner.exe --url stratum+ssl://prl.kryptex.network:8048 --user WALLET/WORKER

pause
