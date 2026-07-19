#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const sourceRoot = path.resolve(repoRoot, process.argv[2] || "Templates taken from evcc/definition/meter");
const outputRoot = path.resolve(repoRoot, process.argv[3] || "device-profiles/bundled");
const debugTemplate = process.env.EVCC_IMPORT_DEBUG || "";

const supportedUsages = new Set(["grid", "pv", "battery"]);
const modbusDecodes = new Map([
  ["uint16", ["uint16", 1]],
  ["uint16nan", ["uint16nan", 1]],
  ["int16", ["int16", 1]],
  ["int16nan", ["int16nan", 1]],
  ["uint32", ["uint32", 2]],
  ["uint32nan", ["uint32nan", 2]],
  ["uint32s", ["uint32s", 2]],
  ["int32", ["int32", 2]],
  ["int32nan", ["int32nan", 2]],
  ["int32s", ["int32s", 2]],
  ["int64", ["int64", 4]],
  ["int64nan", ["int64nan", 4]],
  ["uint64", ["uint64", 4]],
  ["uint64nan", ["uint64nan", 4]],
  ["uint64snan", ["uint64snan", 4]],
  ["float32", ["float32", 2]],
  ["float32s", ["float32s", 2]],
  ["float32nans", ["float32nans", 2]],
]);

const requiredFields = {
  inverter: ["current_dc_power", "current_dc_voltage", "current_ac_power", "current_ac_voltage", "grid_frequency", "total_energy_yield", "internal_temperature", "status_code"],
  battery: ["state_of_charge", "current_power", "current_max_charge_power", "current_max_discharge_power", "state_of_health", "temperature"],
  smart_meter: ["total_active_power", "total_imported", "total_exported", "power_l1", "power_l2", "power_l3", "voltage_l1", "voltage_l2", "voltage_l3"],
};

function cleanScalar(value) {
  const trimmed = value.trim();
  const quote = trimmed[0];
  return trimmed.length >= 2 && (quote === "'" || quote === '"') && trimmed.at(-1) === quote
    ? trimmed.slice(1, -1).trim()
    : trimmed;
}

function indentation(line) {
  return line.match(/^\s*/)[0].length;
}

function extractRender(source) {
  const lines = source.split(/\r?\n/);
  const index = lines.findIndex((line) => /^render:\s*\|\s*$/.test(line));
  if (index < 0) return null;
  const body = lines.slice(index + 1);
  const nonBlank = body.filter((line) => line.trim());
  const minIndent = nonBlank.length ? Math.min(...nonBlank.map(indentation)) : 0;
  return body.map((line) => line.slice(Math.min(minIndent, line.length))).join("\n");
}

function extractParamBlock(source, name) {
  const header = source.split(/^render:\s*\|\s*$/m)[0];
  const lines = header.split(/\r?\n/);
  const start = lines.findIndex((line) => new RegExp(`^\\s*-\\s+name:\\s*${name}\\s*$`).test(line));
  if (start < 0) return "";
  const baseIndent = indentation(lines[start]);
  let end = lines.length;
  for (let index = start + 1; index < lines.length; index++) {
    if (indentation(lines[index]) === baseIndent && /^\s*-\s+(?:name|preset):/.test(lines[index])) {
      end = index;
      break;
    }
  }
  return lines.slice(start, end).join("\n");
}

function choices(block) {
  const match = block.match(/choice:\s*\[([^\]]+)]/);
  return match ? [...match[1].matchAll(/["']([^"']+)["']/g)].map((item) => item[1]) : [];
}

function numericSetting(block, key, fallback) {
  const match = block.match(new RegExp(`^\\s*${key}:\\s*(\\d+)`, "m"));
  return match ? Number(match[1]) : fallback;
}

function scalarSetting(block, key, fallback = "") {
  const match = block.match(new RegExp(`^\\s*${key}:\\s*(.+?)\\s*$`, "m"));
  return match ? cleanScalar(match[1]) : fallback;
}

function serialDefaults(block) {
  const comset = scalarSetting(block, "comset", "8N1").toUpperCase();
  const match = comset.match(/^(7|8)(N|E|O)(1|2)$/);
  return {
    baudRate: numericSetting(block, "baudrate", 9600),
    dataBits: match ? Number(match[1]) : 8,
    parity: match ? ({ N: "NONE", E: "EVEN", O: "ODD" })[match[2]] : "NONE",
    stopBits: match ? Number(match[3]) : 1,
    slaveId: numericSetting(block, "id", 1),
  };
}

function extractProducts(source) {
  const header = source.split(/^render:\s*\|\s*$/m)[0];
  const brands = [...header.matchAll(/brand:\s*([^,}\r\n]+)/g)].map((match) => cleanScalar(match[1]));
  const descriptions = [...header.matchAll(/generic:\s*([^,}\r\n]+)/g)].map((match) => cleanScalar(match[1]));
  const products = [];
  for (let index = 0; index < Math.max(brands.length, descriptions.length); index++) {
    products.push([brands[index], descriptions[index]].filter(Boolean).join(" ").trim());
  }
  return [...new Set(products.filter(Boolean))];
}

function expandSimpleDefinitions(render) {
  const definitions = new Map();
  const withoutDefinitions = render.replace(/\{\{-?\s*define\s+"([^"]+)"\s*-?}}([\s\S]*?)\{\{-?\s*end\s*-?}}/g, (_whole, name, body) => {
    definitions.set(name, body.trim());
    return "";
  });
  return withoutDefinitions.replace(/\{\{\s*include\s+"([^"]+)"\s+\.\s*}}/g, (_whole, name) => definitions.get(name) ?? _whole);
}

function evaluateUsage(render, usage) {
  let evaluated = expandSimpleDefinitions(render);
  const inline = /\{\{-?\s*if\s+(eq|ne)\s+\.usage\s+"([^"]+)"\s*-?}}([^\r\n]*?)\{\{-?\s*else\s*-?}}([^\r\n]*?)\{\{-?\s*end\s*-?}}/g;
  for (let pass = 0; pass < 10 && inline.test(evaluated); pass++) {
    inline.lastIndex = 0;
    evaluated = evaluated.replace(inline, (_whole, operator, expected, yes, no) =>
      (operator === "eq" ? usage === expected : usage !== expected) ? yes : no);
  }

  const output = [];
  const stack = [];
  let active = true;
  for (const line of evaluated.split(/\r?\n/)) {
    const directive = line.trim().match(/^\{\{-?\s*(.*?)\s*-?}}$/)?.[1];
    if (!directive) {
      if (active) output.push(line);
      continue;
    }
    let match = directive.match(/^if\s+(eq|ne)\s+\.usage\s+"([^"]+)"$/);
    if (match) {
      const condition = match[1] === "eq" ? usage === match[2] : usage !== match[2];
      stack.push({ parent: active, condition });
      active = active && condition;
      continue;
    }
    if (directive === "else") {
      const current = stack.at(-1);
      if (!current) return null;
      active = current.parent && !current.condition;
      continue;
    }
    if (directive === "end") {
      const current = stack.pop();
      if (!current) return null;
      active = current.parent;
      continue;
    }
    // Conditions belonging to a usage branch that is already inactive must not make an otherwise
    // representable usage fail. Track their nesting until the matching end without evaluating them.
    if (!active && /^if\b/.test(directive)) {
      stack.push({ parent: false, condition: false });
      continue;
    }
    if (/^include\s+"(?:modbus|mqtt|battery-params)"\s+\./.test(directive)) continue;
    if (/^include\s+"modbus"\s+\./.test(directive)) continue;
    if (directive.includes(".usage")) return null;
    if (/^(?:if|else if|with|range|define)\b/.test(directive)) return null;
    if (active) output.push(line);
  }
  return stack.length ? null : output.join("\n");
}

function topLevelBlocks(render) {
  const blocks = new Map();
  let current = null;
  for (const line of render.split(/\r?\n/)) {
    const match = line.match(/^([A-Za-z][A-Za-z0-9_-]*):\s*$/);
    if (match) {
      current = match[1];
      blocks.set(current, []);
    } else if (current) {
      blocks.get(current).push(line);
    }
  }
  return blocks;
}

function listItems(lines) {
  const items = [];
  let current = null;
  for (const line of lines) {
    const match = line.match(/^\s*-\s+source:\s*([A-Za-z0-9_-]+)/);
    if (match) {
      current = [`source: ${match[1]}`];
      items.push(current);
    } else if (current) {
      current.push(line);
    }
  }
  return items;
}

function numericValue(lines, key, fallback = null) {
  const matches = lines.join("\n").matchAll(new RegExp(`^\\s*${key}:\\s*(-?(?:0x[0-9a-f]+|\\d+(?:\\.\\d+)?))\\s*(?:#.*)?$`, "gmi"));
  const values = [...matches].map((match) => Number(match[1]));
  return values.length === 1 && Number.isFinite(values[0]) ? values[0] : fallback;
}

function parseModbusItem(lines) {
  const text = lines.join("\n");
  const sources = [...text.matchAll(/^\s*source:\s*([^\s#]+)/gm)].map((match) => match[1]);
  if (sources.length !== 1 || sources[0] !== "modbus") return null;
  // Template variables for timeout, transport or caching do not affect the register value itself.
  if (/^\s*(?:bitmask|offset):/m.test(text)) return null;
  const address = numericValue(lines, "address");
  const registerType = text.match(/^\s*type:\s*(holding|input)\s*(?:#.*)?$/m)?.[1];
  const decode = text.match(/^\s*decode:\s*([^\s#]+)\s*(?:#.*)?$/m)?.[1]?.toLowerCase();
  const decoded = modbusDecodes.get(decode);
  const scale = numericValue(lines, "scale", 1);
  if (!Number.isInteger(address) || address < 0 || address > 65_535 || !registerType || !decoded || !Number.isFinite(scale)) return null;
  return {
    startAddress: address,
    size: decoded[1],
    scaleFactor: scale,
    formula: "x",
    parameterType: { primitive: decoded[0] },
    operationType: registerType === "holding" ? "READ_HOLDING_REGISTER" : "READ_INPUT_REGISTER",
    byteOrder: "BIG_ENDIAN",
  };
}

function uriExtension(rawUri) {
  let uri = cleanScalar(rawUri);
  uri = uri.replace(/^\{\{\s*\.schema\s*}}:\/\//, "http://");
  const match = uri.match(/^https?:\/\/\{\{\s*\.host\s*}}(?::(?:\d+|\{\{\s*\.port\s*}}))?(\/.*)?$/);
  if (!match || /\{\{/.test(match[1] || "")) return null;
  return match[1] || "/";
}

function simpleJsonPath(jq) {
  const value = cleanScalar(jq).replace(/\s+#.*$/, "").trim();
  if (!value.startsWith(".")) return null;
  let index = 0;
  let result = "$";
  let tokens = 0;
  while (index < value.length) {
    if (value[index] === ".") {
      index++;
      if (index >= value.length) return null;
    }
    if (value[index] === "[") {
      const match = value.slice(index).match(/^\[(?:(\d+)|["']([^"']+)["'])]/);
      if (!match) return null;
      result += match[1] !== undefined ? `[${match[1]}]` : `[${JSON.stringify(match[2])}]`;
      index += match[0].length;
      tokens++;
      continue;
    }
    if (value[index] === '"') {
      const end = value.indexOf('"', index + 1);
      if (end < 0) return null;
      result += `[${JSON.stringify(value.slice(index + 1, end))}]`;
      index = end + 1;
      tokens++;
      continue;
    }
    const identifier = value.slice(index).match(/^[A-Za-z_][A-Za-z0-9_-]*/)?.[0];
    if (!identifier) return null;
    result += `.${identifier}`;
    index += identifier.length;
    tokens++;
  }
  return tokens ? result : null;
}

function simpleJsonExpression(jq) {
  const value = cleanScalar(jq).replace(/\s+#.*$/, "").trim();
  if (!value || value.includes("//") || /[|a-zA-Z_=]/.test(value.replace(/\.[A-Za-z_][A-Za-z0-9_-]*/g, ""))) return null;
  const pathPattern = /\.(?:[A-Za-z_][A-Za-z0-9_-]*|"[^"]+"|\[(?:\d+|["'][^"']+["'])])(?:\.(?:[A-Za-z_][A-Za-z0-9_-]*|"[^"]+"|\[(?:\d+|["'][^"']+["'])])|\[(?:\d+|["'][^"']+["'])])*/g;
  let pathCount = 0;
  const normalized = value.replace(pathPattern, (candidate) => {
    const path = simpleJsonPath(candidate);
    if (!path) return candidate;
    pathCount++;
    return path;
  });
  if (!pathCount || !/^[0-9.$\[\]"'+\-*/%()\sA-Za-z_-]+$/.test(normalized)) return null;
  const withoutPaths = normalized.replace(/\$(?:(?:\.[A-Za-z_][A-Za-z0-9_-]*)|(?:\[(?:\d+|"[^"]+")]))+/g, "1");
  if (!/^[0-9.+\-*/%()\s]+$/.test(withoutPaths)) return null;
  return `expr:${normalized}`;
}

function parseRestItem(lines) {
  const text = lines.join("\n");
  const sources = [...text.matchAll(/^\s*source:\s*([^\s#]+)/gm)].map((match) => match[1]);
  if (sources.length !== 1 || sources[0] !== "http") return null;
  if (/^\s*(?:body|script):/m.test(text)) return null;
  if (/^\s*headers:/m.test(text)) {
    const headerValues = [...text.matchAll(/^\s*-\s+([^:\s]+):/gm)].map((match) => match[1].toLowerCase());
    if (headerValues.some((header) => header !== "content-type" && header !== "accept")) return null;
  }
  const authenticationType = /^\s*auth:/m.test(text)
    ? text.match(/^\s*type:\s*(basic|bearer|digest)\s*$/m)?.[1]?.toUpperCase()
    : "NONE";
  if (!authenticationType || authenticationType === "DIGEST") return null;
  const uri = text.match(/^\s*uri:\s*(.+?)\s*$/m)?.[1];
  const jq = text.match(/^\s*jq:\s*(.+?)\s*$/m)?.[1];
  const regex = text.match(/^\s*regex:\s*(.+?)\s*$/m)?.[1];
  const extension = uri ? uriExtension(uri) : null;
  const dataPath = jq ? (simpleJsonPath(jq) || simpleJsonExpression(jq)) : null;
  const method = text.match(/^\s*method:\s*([^\s#]+)/m)?.[1]?.toUpperCase() || "GET";
  const scale = numericValue(lines, "scale", 1);
  const responseType = dataPath ? "JSON" : "PLAIN_TEXT";
  const extraction = dataPath || (regex ? `regex:${cleanScalar(regex)}` : "");
  if (!extension || !["GET", "POST"].includes(method) || !Number.isFinite(scale)) return null;
  const entry = {
    urlExtension: extension,
    httpMethod: method,
    responseType,
    dataPath: extraction,
    scaleFactor: scale,
    formula: "x",
    parameterType: { primitive: "double" },
  };
  Object.defineProperty(entry, "_authenticationType", { value: authenticationType, enumerable: false });
  return entry;
}

function parseMqttItem(lines) {
  const text = lines.join("\n");
  const sources = [...text.matchAll(/^\s*source:\s*([^\s#]+)/gm)].map((match) => match[1]);
  if (sources.length !== 1 || sources[0] !== "mqtt") return null;
  const topic = text.match(/^\s*topic:\s*(.+?)\s*$/m)?.[1];
  const scale = numericValue(lines, "scale", 1);
  if (!topic || /\{\{/.test(topic) || !Number.isFinite(scale)) return null;
  return {
    urlExtension: cleanScalar(topic),
    httpMethod: "GET",
    responseType: "PLAIN_TEXT",
    dataPath: "",
    scaleFactor: scale,
    formula: "x",
    parameterType: { primitive: "double" },
  };
}

function authenticationTypes(measurements) {
  return Object.values(measurements)
    .flatMap((value) => Array.isArray(value) ? value : [value])
    .filter(Boolean)
    .map((entry) => entry._authenticationType)
    .filter(Boolean);
}

function parseMeasurements(render, protocol) {
  const parser = protocol === "modbus" ? parseModbusItem : protocol === "mqtt" ? parseMqttItem : parseRestItem;
  const blocks = topLevelBlocks(render);
  const result = {};
  for (const key of ["power", "energy", "returnenergy", "soc"]) {
    if (blocks.has(key)) result[key] = parser(blocks.get(key));
  }
  for (const key of ["powers", "voltages", "currents"]) {
    if (blocks.has(key)) result[key] = listItems(blocks.get(key)).map(parser).filter(Boolean);
  }
  return result;
}

function zeroEntry(primary) {
  return { ...primary, scaleFactor: 1, formula: "0" };
}

function scaled(entry, multiplier) {
  return entry ? { ...entry, scaleFactor: entry.scaleFactor * multiplier } : null;
}

function buildSection(protocol, usage, measurements, title) {
  const primary = measurements.power;
  if (!primary || (usage === "battery" && !measurements.soc)) return null;
  const templateId = protocol === "modbus"
    ? (usage === "grid" ? "smart_meter" : usage === "pv" ? "inverter" : "battery")
    : (usage === "grid" ? "rest_smart_meter" : usage === "pv" ? "rest_inverter" : "rest_battery");
  const kind = usage === "grid" ? "smart_meter" : usage === "pv" ? "inverter" : "battery";
  const actual = {};
  if (kind === "smart_meter") {
    actual.total_active_power = measurements.power;
    actual.total_imported = scaled(measurements.energy, 1000);
    actual.total_exported = scaled(measurements.returnenergy, 1000);
    (measurements.powers || []).slice(0, 3).forEach((entry, index) => actual[`power_l${index + 1}`] = entry);
    (measurements.voltages || []).slice(0, 3).forEach((entry, index) => actual[`voltage_l${index + 1}`] = entry);
  } else if (kind === "inverter") {
    actual.current_ac_power = measurements.power;
    actual.total_energy_yield = scaled(measurements.energy, 1000);
    actual.current_ac_voltage = measurements.voltages?.[0];
  } else {
    actual.current_power = measurements.power;
    actual.state_of_charge = measurements.soc;
  }
  const entries = {};
  const syntheticZeroFields = [];
  const fields = protocol !== "modbus" && kind === "battery"
    ? requiredFields[kind].map((field) => field === "current_max_charge_power" ? "max_charge_power" : field === "current_max_discharge_power" ? "max_discharge_power" : field)
    : requiredFields[kind];
  for (const field of fields) {
    if (actual[field]) entries[field] = actual[field];
    else {
      entries[field] = zeroEntry(primary);
      syntheticZeroFields.push(field);
    }
  }
  return {
    key: kind,
    kind,
    syntheticZeroFields,
    section: { templateId, name: `${title} – ${kind.replace("_", " ")}`, entries },
  };
}

function safeFileName(name) {
  return name.replace(/[^A-Za-z0-9._-]+/g, "-").replace(/^-+|-+$/g, "").toLowerCase();
}

function ensureSafeOutput() {
  const relative = path.relative(repoRoot, outputRoot);
  if (relative.startsWith("..") || path.isAbsolute(relative) || path.basename(outputRoot) !== "bundled") {
    throw new Error(`Refusing to replace unsafe output directory: ${outputRoot}`);
  }
}

function writeJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

ensureSafeOutput();
if (!fs.existsSync(sourceRoot)) throw new Error(`EVCC template directory not found: ${sourceRoot}`);
fs.rmSync(outputRoot, { recursive: true, force: true });
fs.mkdirSync(path.join(outputRoot, "modbus"), { recursive: true });
fs.mkdirSync(path.join(outputRoot, "modbus-rtu"), { recursive: true });
fs.mkdirSync(path.join(outputRoot, "rest"), { recursive: true });
fs.mkdirSync(path.join(outputRoot, "mqtt"), { recursive: true });
fs.mkdirSync(path.join(outputRoot, "websocket"), { recursive: true });

const generated = [];
const skipped = [];
for (const name of fs.readdirSync(sourceRoot).filter((file) => file.endsWith(".yaml")).sort()) {
  const sourcePath = path.join(sourceRoot, name);
  const source = fs.readFileSync(sourcePath, "utf8");
  const template = source.match(/^template:\s*([^\s#]+)/m)?.[1];
  const render = extractRender(source);
  if (!template || !render) {
    skipped.push({ source: name, reason: "missing_template_or_render" });
    continue;
  }
  if (/\bsponsorship\b/i.test(source.split(/^render:\s*\|\s*$/m)[0])) {
    skipped.push({ source: name, reason: "evcc_sponsorship_requirement" });
    continue;
  }
  const usages = choices(extractParamBlock(source, "usage")).filter((usage) => supportedUsages.has(usage));
  if (!usages.length) {
    skipped.push({ source: name, reason: "no_supported_usage" });
    continue;
  }
  const products = extractProducts(source);
  const title = products[0] || template;
  let produced = false;
  const limitations = [];

  const modbusBlock = extractParamBlock(source, "modbus");
  const usesDirectModbus = /source:\s*modbus\b/.test(render);
  const modbusChoices = choices(modbusBlock);
  if (usesDirectModbus && (modbusChoices.includes("tcpip") || modbusChoices.includes("rs485"))) {
    const sections = {};
    const sectionMetadata = [];
    for (const usage of usages) {
      const evaluated = evaluateUsage(render, usage);
      if (!evaluated) continue;
      const measurements = parseMeasurements(evaluated, "modbus");
      const built = buildSection("modbus", usage, measurements, title);
      if (debugTemplate === template) console.error(JSON.stringify({ template, protocol: "modbus", usage, measurements, built, evaluated }, null, 2));
      if (!built) continue;
      sections[built.key] = built.section;
      sectionMetadata.push({ key: built.key, type: built.kind, syntheticZeroFields: built.syntheticZeroFields });
    }
    if (Object.keys(sections).length) {
      const fileName = `${safeFileName(template)}.json`;
      const config = { fingerprint: null, sections, addressOffset: 0 };
      const firstEntry = Object.values(Object.values(sections)[0].entries)[0];
      if (modbusChoices.includes("tcpip")) {
        writeJson(path.join(outputRoot, "modbus", fileName), config);
        generated.push({
          profile: template, protocol: "MODBUS_TCP", source: `definition/meter/${name}`,
          output: `modbus/${fileName}`, products, sections: sectionMetadata,
          defaults: { port: numericSetting(modbusBlock, "port", 502), slaveId: numericSetting(modbusBlock, "id", 1) },
          detection: { status: "MANUAL_PROFILE_SELECTION_REQUIRED", reason: "EVCC provides no stable expected identity value.", probeCandidate: { address: firstEntry.startAddress, size: firstEntry.size, parameterType: firstEntry.parameterType.primitive, operationType: firstEntry.operationType } },
        });
      }
      if (modbusChoices.includes("rs485")) {
        writeJson(path.join(outputRoot, "modbus-rtu", fileName), config);
        generated.push({
          profile: template, protocol: "MODBUS_RTU", source: `definition/meter/${name}`,
          output: `modbus-rtu/${fileName}`, products, sections: sectionMetadata,
          defaults: serialDefaults(modbusBlock),
          detection: { status: "MANUAL_PROFILE_SELECTION_REQUIRED", reason: "RTU discovery cannot safely probe an unknown shared bus.", probeCandidate: { address: firstEntry.startAddress, size: firstEntry.size, parameterType: firstEntry.parameterType.primitive, operationType: firstEntry.operationType } },
        });
      }
      produced = true;
    } else {
      limitations.push("unsupported_modbus_expression_or_decode");
    }
  } else if (usesDirectModbus) {
    limitations.push("unsupported_modbus_transport");
  }

  const usesMqtt = /source:\s*mqtt\b/.test(render);
  if (usesMqtt) {
    const topicDefault = scalarSetting(extractParamBlock(source, "topic"), "default", "");
    const mqttRender = render.replace(/\{\{\s*\.topic\s*}}/g, topicDefault);
    const sections = {};
    const sectionMetadata = [];
    for (const usage of usages) {
      const evaluated = evaluateUsage(mqttRender, usage);
      if (!evaluated) continue;
      const built = buildSection("mqtt", usage, parseMeasurements(evaluated, "mqtt"), title);
      if (!built) continue;
      sections[built.key] = built.section;
      sectionMetadata.push({ key: built.key, type: built.kind, syntheticZeroFields: built.syntheticZeroFields });
    }
    if (Object.keys(sections).length) {
      const fileName = `${safeFileName(template)}.json`;
      writeJson(path.join(outputRoot, "mqtt", fileName), { authenticationType: "NONE", sections });
      generated.push({ profile: template, protocol: "MQTT", source: `definition/meter/${name}`,
        output: `mqtt/${fileName}`, products, sections: sectionMetadata,
        defaults: { topicPrefix: topicDefault }, detection: { status: "MANUAL_PROFILE_SELECTION_REQUIRED", reason: "Broker and topic prefix are installation-specific." } });
      produced = true;
    } else limitations.push("unsupported_mqtt_topic_or_payload_expression");
  }

  const usesWebSocket = /source:\s*ws\b/.test(render);
  if (usesWebSocket) limitations.push("websocket_requires_message_filter_or_dynamic_parameter");
  if (/source:\s*sunspec\b/.test(render)) limitations.push("native_sunspec_discovery_no_static_profile_required");
  if (/^\s*type:\s*mbmd\b/m.test(render)) limitations.push("mbmd_model_register_map_not_present_in_template_export");

  const usesDirectHttp = /source:\s*http\b/.test(render);
  if (usesDirectHttp && extractParamBlock(source, "host")) {
    const sections = {};
    const sectionMetadata = [];
    const profileAuthenticationTypes = new Set();
    for (const usage of usages) {
      const evaluated = evaluateUsage(render, usage);
      if (!evaluated) continue;
      const measurements = parseMeasurements(evaluated, "rest");
      authenticationTypes(measurements).forEach((type) => profileAuthenticationTypes.add(type));
      const built = buildSection("rest", usage, measurements, title);
      if (debugTemplate === template) console.error(JSON.stringify({ template, protocol: "rest", usage, measurements, built, evaluated }, null, 2));
      if (!built) continue;
      sections[built.key] = built.section;
      sectionMetadata.push({ key: built.key, type: built.kind, syntheticZeroFields: built.syntheticZeroFields });
    }
    if (Object.keys(sections).length) {
      const authenticatedTypes = [...profileAuthenticationTypes].filter((type) => type !== "NONE");
      if (authenticatedTypes.length > 1) {
        limitations.push("mixed_http_authentication_types");
        continue;
      }
      const authenticationType = authenticatedTypes[0] || "NONE";
      const fileName = `${safeFileName(template)}.json`;
      writeJson(path.join(outputRoot, "rest", fileName), { authenticationType, sections });
      const firstEntry = Object.values(Object.values(sections)[0].entries)[0];
      generated.push({
        profile: template, protocol: "REST_API", source: `definition/meter/${name}`,
        output: `rest/${fileName}`, products, sections: sectionMetadata,
        authenticationType,
        credentials: authenticationType === "BASIC" ? "Enter username:password in the device credentials field." : authenticationType === "BEARER" ? "Enter the bearer token." : "None",
        detection: { status: "SEMANTIC_HTTP_PROBE", urlExtension: firstEntry.urlExtension, dataPath: firstEntry.dataPath },
      });
      produced = true;
    } else {
      limitations.push("unsupported_http_expression_or_response_shape");
    }
  } else if (usesDirectHttp) {
    limitations.push("http_template_has_no_configurable_host");
  }

  if (!produced) {
    if (!usesDirectModbus && !usesDirectHttp && !usesMqtt && !usesWebSocket) limitations.push("unsupported_evcc_driver_or_composite_expression");
    skipped.push({ source: name, reason: "not_losslessly_representable", limitations });
  }
}

const generatedTemplateCount = new Set(generated.map((profile) => profile.profile)).size;
const generatedByProtocol = Object.fromEntries(
  [...new Set(generated.map((profile) => profile.protocol))].sort().map((protocol) => [
    protocol,
    generated.filter((profile) => profile.protocol === protocol).length,
  ]),
);
const limitationCounts = {};
for (const template of skipped) {
  const reasons = template.limitations?.length ? template.limitations : [template.reason];
  for (const reason of reasons) limitationCounts[reason] = (limitationCounts[reason] || 0) + 1;
}

const manifest = {
  source: { project: "evcc", repository: "https://github.com/evcc-io/evcc", input: path.relative(repoRoot, sourceRoot).replaceAll("\\", "/"), license: "MIT; sponsorship-required templates excluded" },
  policy: { supportedUsages: [...supportedUsages], supportedProtocols: ["MODBUS_TCP", "MODBUS_RTU", "REST_API", "MQTT", "WEBSOCKET"], fingerprints: "Never fabricated; see detection per profile.", placeholderFields: "Unsupported SolarMiner fields reuse a readable source with formula 0 and are listed as syntheticZeroFields." },
  summary: {
    inputTemplates: fs.readdirSync(sourceRoot).filter((file) => file.endsWith(".yaml")).length,
    generatedTemplates: generatedTemplateCount,
    generatedConfigurations: generated.length,
    generatedProfiles: generated.length,
    generatedByProtocol,
    skippedTemplates: skipped.length,
  },
  profiles: generated,
};
writeJson(path.join(outputRoot, "manifest.json"), manifest);
writeJson(path.join(outputRoot, "skipped.json"), { templates: skipped });

const readme = `# EVCC-derived SolarMiner device profiles

These files were generated by \`tools/evcc-profile-importer.mjs\` from the EVCC meter templates in \`${path.relative(repoRoot, sourceRoot).replaceAll("\\", "/")}\`.

Only PV inverters (EVCC usage \`pv\`), batteries (\`battery\`) and grid smart meters (\`grid\`) are considered. Supported Modbus values include signed/unsigned integers, floats, swapped words and common invalid-value sentinels. Direct register profiles are emitted for each transport declared by EVCC: Modbus TCP and/or Modbus RTU. HTTP profiles may use GET/POST, simple JSON paths, bounded arithmetic across numeric JSON values, plain numeric text, regular expressions, bearer authentication or basic authentication. Numeric MQTT topics are supported as message profiles. Profiles requiring arbitrary scripts, MBMD register maps unavailable in the template export, complex jq execution or message filtering remain excluded.

Basic-auth profiles expect \`username:password\` in SolarMiner's device credentials field. Authentication requirements are recorded per profile in \`manifest.json\`.

## Detection and fingerprints

EVCC templates generally do not contain a stable manufacturer/model value. Therefore Modbus fingerprints are deliberately \`null\` and the profile must be selected manually until a real device-specific identity value has been verified. The manifest contains a readable probe candidate, but it is not a fingerprint. REST profiles contain a semantic endpoint/path probe that SolarMiner can validate.

Fields required by the SolarMiner schema but unavailable in EVCC are represented as zero-valued placeholders and are listed per section in \`manifest.json\`. Generated profiles must be tested against real hardware before being promoted to the community repository.

## Regeneration

\`node tools/evcc-profile-importer.mjs\`

The output directory is replaced on every run. Review \`manifest.json\` and \`skipped.json\` after updating the source templates.

## Attribution

The source templates are from [evcc](https://github.com/evcc-io/evcc) and are used under its MIT license. Templates declaring an EVCC sponsorship requirement are excluded by the importer. SolarMiner's generated profile format and importer remain subject to this repository's license.
`;
fs.writeFileSync(path.join(outputRoot, "README.md"), readme, "utf8");

const protocolSections = Object.entries(generatedByProtocol).map(([protocol, count]) => {
  const names = generated.filter((profile) => profile.protocol === protocol).map((profile) => `- \`${profile.profile}\``).join("\n");
  return `## ${protocol} (${count})\n\n${names}`;
}).join("\n\n");
const limitationTable = Object.entries(limitationCounts)
  .sort((left, right) => right[1] - left[1] || left[0].localeCompare(right[0]))
  .map(([reason, count]) => `| \`${reason}\` | ${count} |`)
  .join("\n");
const coverage = `# EVCC profile coverage

Generated from all ${manifest.summary.inputTemplates} meter templates in the checked-in EVCC export.

- ${generatedTemplateCount} templates are directly executable in SolarMiner.
- ${generated.length} protocol-specific configurations were generated.
- ${skipped.length} templates remain excluded because their behavior cannot be represented safely and losslessly yet.
- SunSpec-only templates are intentionally covered by SolarMiner's native SunSpec model discovery instead of static generated files.

${protocolSections}

## Remaining limitations

| Reason | Templates |
| --- | ---: |
${limitationTable}

The exact source file and all applicable reasons are recorded in \`skipped.json\`. A generated profile is not a hardware certification; it still needs validation against a real device before release.
`;
fs.writeFileSync(path.join(outputRoot, "COVERAGE.md"), coverage, "utf8");

console.log(JSON.stringify(manifest.summary));
