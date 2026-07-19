# EVCC profile coverage

Generated from all 231 meter templates in the checked-in EVCC export.

- 91 templates are directly executable in SolarMiner.
- 116 protocol-specific configurations were generated.
- 140 templates remain excluded because their behavior cannot be represented safely and losslessly yet.
- SunSpec-only templates are intentionally covered by SolarMiner's native SunSpec model discovery instead of static generated files.

## MODBUS_RTU (36)

- `acrel-adw300`
- `alpha-ess-smile`
- `bgetech-ds100`
- `bgetech-ws100`
- `cg-emt1xx`
- `deye-mi`
- `deye-storage`
- `deye-string`
- `eastron-sdm120`
- `fox-ess-avocado`
- `fox-ess-h3-smart`
- `fox-ess-h3`
- `goodwe-dt`
- `goodwe-hybrid`
- `growatt-hybrid`
- `huawei-sun2000-hybrid`
- `huawei-sun2000-inverter`
- `ibc-homeone`
- `marstek-venus-a`
- `mtec-eb-gen2`
- `mtec-eb-gen3`
- `saj-h2`
- `saj-r5`
- `senergy-hybrid`
- `sermatec-hybrid`
- `siemens-7kt1665`
- `sofarsolar`
- `solaredge-hybrid`
- `solax-g2`
- `solax`
- `solis-hybrid-s`
- `solis-hybrid`
- `solis`
- `sungrow-hybrid`
- `sungrow-ihm`
- `sungrow-inverter`

## MODBUS_TCP (48)

- `acrel-adw300`
- `afore-hybrid`
- `alpha-ess-smile`
- `anker-solix-solarbank-max-ac`
- `anker-solix-x1`
- `atmoce`
- `deye-mi`
- `deye-storage`
- `deye-string`
- `fox-ess-avocado`
- `fox-ess-h3-smart`
- `fox-ess-h3`
- `goodwe-dt`
- `goodwe-hybrid`
- `growatt-hybrid`
- `huawei-emma`
- `huawei-smartlogger`
- `huawei-sun2000-hybrid`
- `huawei-sun2000-inverter`
- `ibc-homeone`
- `intilion-scalebloc`
- `kostal-ksem-inverter`
- `marstek-venus-a`
- `mtec-eb-gen3`
- `mypv-wifi-meter`
- `plexlog`
- `powerdog`
- `saj-h1`
- `saj-h2`
- `senergy`
- `siemens-7kt1665`
- `siemens-junelight`
- `sma-data-manager`
- `sma-inverter-modbus`
- `sma-sbs-15-25-modbus`
- `sma-sbs-modbus`
- `sma-si-modbus`
- `sma-webbox`
- `sofarsolar`
- `solaredge-hybrid`
- `solarmax-smt`
- `solarmax-maxstorage`
- `solax-g2`
- `solax`
- `storaxe`
- `sungrow-hybrid`
- `sungrow-ihm`
- `sungrow-inverter`

## MQTT (1)

- `solaranzeige`

## REST_API (31)

- `apsystems-ez1`
- `batterX`
- `cozify`
- `everhome-ecotracker`
- `fronius-solarapi-v1`
- `go-e-controller`
- `hoymiles-dtugateway`
- `hoymiles-opendtu`
- `iammeter-1p`
- `iammeter-3p`
- `iammeter`
- `iometer`
- `kostal-piko-hybrid`
- `kostal-piko-legacy`
- `kostal-piko-mp-plus`
- `kostal-piko-pv`
- `openems`
- `pstryk`
- `sessy-p1`
- `sessy-smart-battery`
- `smartfox`
- `solarman`
- `solarwatt-myreserve-matrix`
- `solarwatt`
- `sonnenbatterie`
- `sonnenbatterie-eco56`
- `stromleser`
- `youless`
- `zendure-3ct`
- `zendure-solarflow-ac`
- `zendure-solarflow-pro`

## Remaining limitations

| Reason | Templates |
| --- | ---: |
| `unsupported_evcc_driver_or_composite_expression` | 81 |
| `mbmd_model_register_map_not_present_in_template_export` | 27 |
| `unsupported_http_expression_or_response_shape` | 20 |
| `native_sunspec_discovery_no_static_profile_required` | 14 |
| `unsupported_modbus_expression_or_decode` | 14 |
| `http_template_has_no_configurable_host` | 8 |
| `no_supported_usage` | 8 |
| `unsupported_modbus_transport` | 6 |
| `evcc_sponsorship_requirement` | 1 |
| `missing_template_or_render` | 1 |
| `websocket_requires_message_filter_or_dynamic_parameter` | 1 |

The exact source file and all applicable reasons are recorded in `skipped.json`. A generated profile is not a hardware certification; it still needs validation against a real device before release.
