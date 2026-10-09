# Setup UX and PV discovery

Owner: Node `react-frontend/app/setup/page.tsx`, `setup/SetupService`, `discovery/DiscoveryService`, `discovery/PvDiscoveryScanner`, `controller/SetupController`. Local Node API only; no cross-repository contract changed. Reviewed 2026-10-02.

## UX findings and implemented behavior

The original device step presented five protocols, server-oriented descriptions, a preselected first profile and configuration fields together. Its discovery action depended on the selected protocol and silently did nothing for RTU/MQTT/WebSocket. Empty discovery results had no recovery explanation; the array response did not distinguish a timeout from a completed scan. This was confirmed by source inspection and a fresh simulator browser session, not a user study.

The device step now starts with the purpose of collecting PV readings and distinguishes PV devices from mining devices. Operators choose network search or manual setup. Automatic search covers both supported network protocols and shows its editable range, wait state, completion/partial status, empty-result guidance and manual recovery. Manual setup names connection methods by their use, then guides model choice, address/testing and measurement-source selection. Advanced transport fields are collapsed. Profiles have loading/error/empty states and no initial arbitrary selection. Text search filters loaded profiles without discarding the current selection. Adding/removing a device controls whether Next is enabled. Mobile search controls stack vertically; mobile wizard progress shows the current step. German and English copy share the same keys.

The adjacent panel step was reviewed and revised on 2026-10-02. Previously it showed a blank group name, raw azimuth/tilt degrees, panel count/power and a large map in one form, with no explanation of what a group means or how to correct an existing one. It now introduces a roof area as panels sharing direction and tilt; asks for count and Wp first; previews kWp; offers cardinal direction choices and a tilt slider with exact-degree controls; explains that the 400 Wp, south and 30° presets are assumptions; and requests location in a dedicated section. The map loads on demand, and manual coordinates remain available when tiles or geolocation are unavailable. A chosen location carries to the next roof area, with an explicit change control. Names are optional and generated uniquely; entries can be edited and removed. Invalid values, missing location and duplicate names receive local feedback. Next requires at least one saved area. The existing setup payload remains unchanged.

Location still belongs in setup: `miningcontroller/dsl/ControllerDSL.calculateSunEvent` averages panel-group latitude/longitude to calculate sunrise and sunset for site controller actions. The UI must not silently invent coordinates. Azimuth and tilt are stored in `PVPanels`; the current Node source search found no downstream calculation using them, so the copy describes the default values as assumptions without claiming a forecast effect.

## Discovery behavior and boundaries

- `PvDiscoveryScanner` admits one scan at a time, checks up to 254 private IPv4 hosts with 24 workers and returns after a 25-second deadline. It interrupts outstanding work and holds the admission slot until that work terminates. Result snapshots and host counts are captured together; late probes cannot mutate returned results.
- Setup scan validates provider, private prefix, port and device ID before I/O. Custom Modbus IDs are passed to SunSpec and fingerprint matching; custom REST ports are honored. The scan uses a 200 ms port preflight, while single-endpoint inspection keeps its existing 800 ms preflight.
- REST probing uses one GET response and `RestPVClient.parsePayload`, rather than rereading the endpoint. POST-only profiles cannot auto-match. 401/403 and unreadable payloads cannot identify a device. Modbus fingerprint requirements remain in place. SunSpec may still generate a profile in local storage.
- Docker cannot infer the host LAN reliably from container interfaces. Configure `SOLARMINER_DISCOVERY_SUBNET_PREFIX` (e.g. `192.168.1.`); it supplies both API suggestion and default scan range. Users can override the range. A private IPv4 browser hostname provides the UI's LAN suggestion.
- Only PV discovery is changed. Miner discovery, 21energy hardware gates, Pearl mining, pool routing, fees and power control are outside this change.

## Verification

- `npm run build` in `react-frontend` and `tsc --noEmit --incremental false` pass.
- `:test --tests '*PvDiscoveryScannerTest' --tests '*DiscoveryRestProbeTest' --tests '*SetupDiscoveryTest'`: eight tests pass. Coverage includes private-network admission, timeout/cancellation/snapshot integrity, probe failure propagation, GET-only REST matching, rejection of 401/403 and wrong payloads, requested port/device ID and absence of loopback probes.
- Fresh `docker-compose.node-sim.yml` database (site count zero); Phoenix wallet volume retained. The compose file referenced old JAR names, so local verification used a temporary override pointing Node to `solar-miner-1.1.5.jar`.
- Chrome desktop 1440×1080 and mobile 390×844: inspected baseline and revised setup, no page errors or horizontal overflow. Controlled API fixtures verify empty/partial discovery feedback, found-device selection, connection verification, adding/removing devices, Next gating and profile-search recovery. Fixtures do not establish real device compatibility.
- Panel flow: Chrome before/after desktop and revised mobile screenshots inspected. Controlled browser checks cover required location, valid manual coordinates, automatic name, second area with carried location, edit/save, duplicate-name feedback, kWp calculation, Next gating, no page errors and no horizontal overflow. Vite build and TypeScript noEmit pass after this change. Hardware responses in the browser test are fixtures.

## Further UX work, not implemented

1. Make manufacturer/model the first manual choice and derive compatible protocols from profile metadata. Current catalog names alone cannot establish a preferred protocol or hardware compatibility.
2. Consider moving optional financial details out of first-run setup. Any further change to the panel-location requirement needs an explicit replacement for the sunrise/sunset input used by controller actions.
3. Validate the wording with new users and real installations. Test separate VLANs, devices behind authentication, nonstandard ports/IDs, slow hardware and real Docker LAN reachability. A successful build or a mocked browser flow is not a hardware support claim.
