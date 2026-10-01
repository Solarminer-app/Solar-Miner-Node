# The build context must be the Solar-Miner-Node repository root and contain
# embedded-proxy-src/, populated by the release workflow from the proxy repo.
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace
COPY . .

FROM builder AS agent-builder
RUN chmod +x gradlew \
    && ./gradlew :pc-agent:standaloneJar \
        -Psolarminer.proxy.project-dir=embedded-proxy-src \
        --no-daemon

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        libdrm2 \
        ocl-icd-libopencl1 \
        tini \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /data

# The agent, its explicitly installed miners and their logs live below /data.
# This is deliberately a volume so container replacement never loses configs.
VOLUME ["/data"]

EXPOSE 8084 8091/udp

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8084/api/agent/telemetry > /dev/null || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "java", "-jar", "/opt/solarminer/solarminer-pc-agent.jar"]

FROM runtime
COPY --from=agent-builder /workspace/pc-agent/build/distributions/solarminer-pc-agent-standalone.jar /opt/solarminer/solarminer-pc-agent.jar
EXPOSE 8090 3334 3335
CMD ["--solarminer.agent.standalone=false"]
