# Decred (DCR) / BLAKE3 integration record

Status: **experimental code path, disabled at fee gate** (2026-10-06).

## Contract

| Field | Value |
|---|---|
| Canonical key / ticker | `decred` / `DCR` |
| Algorithm | `blake3_decred` in SRBMiner; `BLAKE3` in public network snapshots |
| Hardware/software | GPU via the existing SRBMiner-MULTI installer (Windows/Linux x64) |
| Pool candidate | `stratum+tcp://dcr.suprnova.cc:9333` (EU); Suprnova also documents US and Asia hosts |
| Pool login | Mainnet DCR address plus `.worker`; password `x` |
| Proxy listener | `3338`, coin key `decred` |
| Public inputs | CoinGecko `decred`; 2Miners DCR aggregate stats |

Suprnova documents SRBMiner's `blake3_decred` algorithm and Stratum endpoints.
Decred's official `dcrpool` implements a Decred Stratum pool, and Decred's
miner documentation describes the username as payout address plus worker.
The implemented proxy bean reuses the GPU Stratum V1 job/submit router and now
uses the published Haste `mining.subscribe` response shape, including the
12-byte extranonce2 field. The selected Suprnova pool's exact wire behavior has
**not** been captured against this adapter; do not enable it for production
before checking subscribe, extranonce, job and submit messages.

## Implemented source path

- Currency Service adds `decred`/`dcr`, CoinGecko pricing and 2Miners network
  stats. The snapshot only accepts a live `blockReward`; partial data is
  rejected, because a total Decred subsidy is not equivalent to the work
  reward.
- PC-Agent catalog, shared SRBMiner lifecycle, worker assignment, earnings,
  console, energy journal and local proxy config include DCR. The config
  validator checks mainnet Base58Check P2PKH/P2SH address version bytes.
- Proxy registers `DecredStratumProtocol` at port 3338.
- The fee backend currently has no Decred SolarMiner house target. The
  PC-Agent checks fee readiness from the live target response for any coin and
  requires a marked `house` target with a positive percentage, target ID, pool
  address and worker. Missing, referral-only, malformed or unavailable fee data
  blocks start. If a valid DCR house target is provisioned later, DCR can pass
  this gate without a code change. No payout address has been invented.
- The Admin portal now accepts and masks a referrer's DCR wallet and can form a
  Suprnova referral target, but `ADMIN_DECRED_FEE_ROUTING_ENABLED` defaults to
  false. No SolarMiner DCR house target or accepted DCR referral share is
  verified, so this only stores the credential; it does not enable routing.

## Gates before any enablement

1. Capture subscribe, authorize, notification, difficulty, submit, reject,
   stale, reconnect and disconnect messages from the selected real pool. Test
   both miner-to-user and fee-target share attribution before confirming the
   reused proxy handler is correct.
2. Provision same-algorithm Decred pool accounts for SolarMiner and any
   referrer; add fee-backend configuration and verify accepted-share accounting.
   The PC-Agent start gate opens automatically when it sees a valid SolarMiner
   house target.
3. Confirm the live DCR block-reward field from the provider and its units;
   add currency parsing, persistence, alias and public response fixtures.
4. Verify install checksum/release, process/API telemetry, exclusive GPU
   start/stop, wallet validation and accepted user/house/referral shares on
   Windows and Linux. Check mining power and revenue remain denominated in DCR.
5. Complete Node registry, admin/referrer portal fee-target enablement and
   rollout only after pool credit/payout reconciliation. The portal now has a
   gated credential field; none of those paths is enabled by this record.

No miner start, pool share, proxy fee split, pool credit, payout, fee-backend
target or deployment was verified. No tests were run for this changeset.
