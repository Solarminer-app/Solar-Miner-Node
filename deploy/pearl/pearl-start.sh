#!/usr/bin/env bash
set -euo pipefail

: "${PEARL_PROXY_URL:?missing Pearl proxy URL}"
: "${PEARL_POOL_URL:?missing Pearl upstream pool URL}"
: "${PEARL_WALLET:?missing Pearl payout wallet}"
: "${PEARL_WORKER:?missing Pearl worker}"

proxy_address="${PEARL_PROXY_URL#stratum+tcp://}"
[[ "$PEARL_PROXY_URL" == stratum+tcp://* ]] || { echo 'Pearl proxy must use stratum+tcp://' >&2; exit 1; }
encoded_pool="$(printf %s "$PEARL_POOL_URL" | base64 -w0 | tr '+/' '-_' | tr -d '=')"
args=(--disable-cpu --algorithm pearlhash --pool "$proxy_address"
  --wallet "$PEARL_WALLET" --worker "sm1.${encoded_pool}.${PEARL_WORKER}"
  --tls false --api-enable --api-port 12000)
if [[ "${PEARL_DEVICES:-all}" != all ]]; then args+=(--gpu-id "$PEARL_DEVICES"); fi
exec /opt/solarminer/SRBMiner-MULTI "${args[@]}"
