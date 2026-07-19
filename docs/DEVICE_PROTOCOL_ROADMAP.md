# Native device protocol roadmap

## Current baseline

SolarMiner supports Modbus TCP, Modbus RTU, SunSpec/register profiles over both Modbus transports, extended local HTTP APIs, MQTT 3.1.1 and WebSocket telemetry. HTTP, MQTT and WebSocket share the JSON, XML and plain-text parser, unit normalization, JSON paths/XPath, regular expressions and formulas. The EVCC profile importer additionally maps common Modbus invalid-value encodings and swapped word layouts, simple JSON arithmetic, bearer authentication and basic authentication.

The setup catalog, PV-site device management, JPA device entities and React profile editor expose all five connection types. MQTT and WebSocket profiles use the HTTP-shaped message-field schema: `urlExtension` is the MQTT topic for MQTT profiles and an informational message source for WebSocket profiles; `dataPath` selects the value inside the received payload.

Profile conversion deliberately does not execute embedded Go, JavaScript, shell commands or unrestricted jq. Device definitions are configuration, not trusted executable code.

## Shared connector architecture

New protocols should use the same four layers:

1. **Transport/session** owns sockets, reconnects, timeouts, TLS and connection pooling.
2. **Value source** reads a raw value by register, topic, message path or protocol object identifier.
3. **Safe transformation pipeline** handles scaling, formulas, fallback sources, validity checks and unit normalization.
4. **Discovery probe** returns verified manufacturer/model identity plus the compatible profile sections.

Credentials belong to a device connection, never to a profile. Profiles only declare which credential fields and authentication scheme they require. Each transport needs bounded retries, exponential backoff and a circuit breaker so an unavailable device cannot stall the global entity scheduler.

## Recommended connectors

### 1. Modbus RTU / RS485

**Why:** Unlocks classic DIN-rail meters and many EVCC MBMD definitions. The current importer emits 36 directly executable RTU configurations; 27 MBMD device families still require register maps that are not contained in the extracted templates.

**Implemented baseline:**

- Separate TCP/RTU entities reuse `ModbusConfig`, register decoders, fingerprints and device sections.
- RTU connections are serialized per device path across all SolarMiner components. A shared long-lived session per bus remains an optional performance follow-up.
- Configuration: device path, baud rate, data bits, stop bits, parity, response timeout and slave ID.
- In Docker, expose selected `/dev/ttyUSB*` or `/dev/serial/by-id/*` devices explicitly.
- Add CRC/error counters and reconnect the serial session after repeated framing failures.
- Convert MBMD model names to normal SolarMiner register profiles; do not embed MBMD as a second runtime.

The production wiring, permissions and Compose setup are documented in [MODBUS_RTU_DOCKER.md](MODBUS_RTU_DOCKER.md).

### 2. MQTT

**Why:** Native integration for smart meters, Home Assistant, Tasmota, Shelly and vendor bridges, including devices that push data instead of accepting polling.

**Implemented baseline:**

- One pooled MQTT 3.1.1 client per broker/credential set, with topic subscriptions shared across device components.
- Cache the latest payload together with receive time; profiles declare a maximum age.
- Reuse JSON path, regex, number/unit normalization and formula processing from REST.
- Username/password, default JVM TLS trust and retained messages are supported. Custom CA/client-certificate upload remains a follow-up.
- Keep publishing/writes separate from read-only measurement profiles and apply explicit topic allowlists.
- Optionally consume Home Assistant MQTT discovery, but require user confirmation before adding devices.

### 3. WebSocket and DSMR/P1

**Why:** Enables push APIs and smart-meter gateways such as Volkszähler and Dutch/Luxembourg P1 devices.

**Implemented baseline:**

- Java's WebSocket client reads text messages with bounded connection/read timeouts. Persistent reconnect/backoff, heartbeat and a cross-poll message cache remain follow-ups.
- Apply the same JSON/regex transformation pipeline used by HTTP and MQTT.
- Add a DSMR parser for telegram framing, CRC validation and OBIS-to-SolarMiner field mapping.
- Support both TCP/WebSocket gateways and direct serial P1 input behind the same DSMR value source.

### 4. SMA Speedwire

**Why:** Native discovery and telemetry for SMA Energy Meter, Home Manager and compatible inverters; four imported EVCC families use it.

**Implementation:**

- Dedicated UDP transport for discovery and multicast/unicast telemetry.
- Parse packets into typed SMA measurement identifiers.
- Use device serial and system identifiers as verified fingerprints.
- Keep the parser isolated in a module with captured-packet tests and strict packet-length validation.

### 5. Home Assistant connector

**Why:** Provides a broad compatibility bridge when a device already has a maintained Home Assistant integration.

**Implementation:**

- REST for initial entity metadata and WebSocket subscriptions for state changes.
- Long-lived access token stored as a secret on the device connection.
- Profile maps entity IDs and optional attributes to SolarMiner component fields.
- Mark bridged devices clearly; direct native connectors remain preferable for control-critical values.

### 6. Prometheus

**Why:** Low-cost integration for installations that already export power metrics.

**Implementation:**

- Query `/api/v1/query` using a bounded PromQL string from an administrator-created profile.
- Accept only scalar or single-series vector results.
- Reuse HTTP authentication/TLS and cache identical queries within one polling cycle.

### 7. Vendor protocols

Implement only where demand justifies maintenance:

- **E3/DC RSCP:** authenticated/encrypted TCP session, frame codec and reconnecting session pool.
- **RCT Power:** dedicated binary TCP codec with object-ID mapping.
- **EEBUS SHIP/SPINE:** TLS pairing, certificate storage, SHIP session and SPINE feature mapping; best isolated as its own module.
- **GoodWe AA55/UDP:** datagram codec, sequence correlation and device identity probing.
- **Fritz!DECT/Tapo/vendor cloud APIs:** prefer local HTTP or MQTT when available; cloud connectors require rate limiting and token refresh.

## Profile engine follow-up

Many remaining EVCC templates do not need a new wire protocol. They need composable, safe value sources:

- ordered fallback sources;
- add/subtract/multiply/divide/min/max/absolute/sign operations;
- cached values with maximum age;
- conditional selection based on another value;
- explicit unavailable-value handling;
- shared HTTP responses so several fields do not trigger duplicate requests.

These operations should be represented as typed configuration nodes and validated when a profile is imported. They must not be implemented by executing arbitrary scripts from profiles.
