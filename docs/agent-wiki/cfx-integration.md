# Conflux (CFX) / Octopus integration record

Status: **research and currency snapshot only; mining is not enabled** (2026-10-06).

## Intended coin contract

| Field | Value |
|---|---|
| Canonical key / ticker | `conflux` / `CFX` |
| Algorithm | Octopus |
| Hardware candidate | NVIDIA GPU; miner support is vendor/version specific |
| Pool candidate | F2Pool `cfx.f2pool.com:6800` |
| Mining account | F2Pool account name and worker, not a wallet address |
| Public network/price inputs | Currency Service `conflux` snapshot, network source `cfx.2miners.com`, CoinGecko ID `conflux-token` |

The pool's official guide lists Rigel for Octopus and uses account-name plus
worker credentials. This is a Stratum pool, but its login contract differs
from the current PC-Agent GPU adapter's wallet-based SRBMiner route. The
Conflux node's own documented Stratum-like interface is a separate solo-mining
protocol and is not evidence of compatibility with the pool dialect.

## Implemented evidence

- Currency Service adds CFX to the public coin-keyed mining snapshot and batched
  USD quote. It uses the provider's live `blockReward` field and rejects
  incomplete data rather than baking in a subsidy value.
- No PC-Agent Octopus miner, installation path, managed process/API telemetry,
  or forecast has been enabled.
- No SolarMiner proxy listener or CFX protocol adapter has been enabled.
- Fee-backend has no CFX SolarMiner or referral payout target. The user chose to
  keep new mining routes disabled until a fee target is provided.

## Required before enablement

1. Select and pin a maintained Octopus miner/release for supported OS/device
   combinations; implement checksum-verified installation, lifecycle, GPU
   exclusion and telemetry in the PC-Agent.
2. Capture the selected pool's actual subscribe/authorize, job, difficulty,
   submit, error and reconnect messages. Implement a CFX-specific proxy
   protocol and credential transformation only after those fixtures exist.
3. Provision compatible SolarMiner and referral pool accounts, then add the
   canonical `conflux` fee configuration. Verify target switching and accepted
   share credit on each account.
4. Add provider, persistence, public API, proxy and PC-Agent contract tests;
   verify a real GPU run and accepted pool share before enabling miner
   selection. Public snapshot availability alone does not enable mining.
5. Update the Node coin registry and profitability automation only after the
   complete pool/accounting gates pass. CFX mining produces CFX, not BTC or
   Lightning revenue.

No wallet/account credentials, pool submit, accepted share, fee share, PC-Agent
miner start or production deployment has been verified.
