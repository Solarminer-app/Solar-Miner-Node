const $ = id => document.getElementById(id);
const fmt = (value, digits = 2) => new Intl.NumberFormat('de-DE', { maximumFractionDigits: digits }).format(value);
const text = (id, value) => { $(id).textContent = value; };
function el(tag, className, value) { const n = document.createElement(tag); if (className) n.className = className; if (value !== undefined) n.textContent = value; return n; }
function formatMetric(value, unit) {
  if (unit !== 'B' && unit !== 'B/s') return `${fmt(value)} ${unit || ''}`.trim();
  const suffix = unit === 'B/s' ? '/s' : '';
  const units = ['B', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB'];
  let scaled = value, index = 0;
  while (Math.abs(scaled) >= 1024 && index < units.length - 1) {
    scaled /= 1024;
    index++;
  }
  return `${fmt(scaled, index === 0 ? 0 : 2)} ${units[index]}${suffix}`;
}
function render(data) {
  text('platform', `${data.platform || '—'} · ${data.architecture || '—'}`);
  text('cpu-name', data.cpuName || 'Prozessor unbekannt');
  const cpuPower = data.metrics?.['cpu.package_power'];
  const cpuTemp = data.metrics?.['cpu.temperature'];
  text('cpu-summary', [
    cpuPower?.available ? `CPU ${fmt(cpuPower.value)} W` : null,
    cpuTemp?.available ? `Temperatur ${fmt(cpuTemp.value)} °C` : null
  ].filter(Boolean).join(' · ') || 'Keine CPU-Sensorwerte verfügbar');
  const known = new Map((data.gpus || []).map(gpu => [`${gpu.vendor}:${gpu.index}`, gpu]));
  const names = data.gpuNames || [...known.values()].map(g => g.model);
  text('gpu-count', String(names.length));
  text('gpu-summary', names.length ? names.join(' · ') : 'Keine Grafikkarte erkannt');
  const devices = $('device-list'); devices.replaceChildren();
  const cpu = el('article', 'device device-large');
  cpu.append(el('span', 'device-kind', 'CPU'), el('strong', '', data.cpuName || 'Prozessor unbekannt'),
    el('small', '', `Leistung: ${cpuPower?.available ? fmt(cpuPower.value) + ' W' : 'nicht verfügbar'} · Temperatur: ${cpuTemp?.available ? fmt(cpuTemp.value) + ' °C' : 'nicht verfügbar'}`));
  devices.append(cpu);
  names.forEach((name, index) => {
    const cardData = [...known.values()].find(g => g.model === name) || [...known.values()][index];
    const card = el('article', 'device device-large');
    card.append(el('span', 'device-kind', 'GPU'), el('strong', '', name),
      el('small', '', cardData ? `${cardData.vendor} · Gerät ${cardData.index} · ${cardData.minWatts}–${cardData.maxWatts} W Leistungsbereich · ${cardData.currentWatts != null ? fmt(cardData.currentWatts) + ' W aktuell' : 'aktuelle Leistung nicht verfügbar'}` : 'GPU erkannt; Leistungsgrenzen werden vom Treiber nicht gemeldet.'));
    devices.append(card);
  });
  if (!names.length) devices.append(el('p', 'empty', 'Es wurden keine GPUs erkannt.'));
  const total = data.metrics?.['system.total_power'];
  text('total-power', total?.available ? `${fmt(total.value)} W` : '—');
  text('power-quality', total?.available ? total.source : 'Leistungsmessung nicht verfügbar');
  const metrics = Object.entries(data.metrics || {});
  const available = metrics.filter(([, value]) => value.available).length;
  text('sensor-count', String(available));
  text('sensor-summary', `${available} von ${metrics.length} Messwerten verfügbar`);
  text('collected-at', data.collectedAt ? `Stand ${new Date(data.collectedAt).toLocaleTimeString('de-DE')}` : '—');
  const sources = $('sensor-sources'); sources.replaceChildren();
  for (const [name, state] of Object.entries(data.sources || {})) sources.append(el('span', 'source', `${name}: ${state}`));
  const list = $('telemetry-list'); list.replaceChildren();
  for (const [key, metric] of metrics) {
    const row = el('div', `metric${metric.available ? '' : ' unavailable'}`);
    const value = metric.available ? formatMetric(metric.value, metric.unit) : 'Nicht verfügbar';
    const exactBytes = metric.available && (metric.unit === 'B' || metric.unit === 'B/s')
      ? `${fmt(metric.value, 0)} ${metric.unit}` : '';
    row.append(el('span', '', key), el('strong', '', value),
      el('small', '', `${metric.source || 'Quelle unbekannt'} · ${metric.directMeasurement ? 'direkte Messung' : 'abgeleitet / geschätzt'}`));
    if (exactBytes) row.title = `Rohwert: ${exactBytes}`;
    list.append(row);
  }
  const status = data.sensorServiceStatus || 'not-required';
  $('sensor-status').className = `tag ${status === 'available' || status === 'not-required' ? 'ready' : 'blocked'}`;
  text('sensor-status', status === 'available' || status === 'not-required' ? 'Sensorzugriff bereit' : `Sensorzugriff: ${status}`);
}
async function refresh() {
  try {
    const response = await fetch('/api/agent/telemetry', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    render(await response.json());
    $('connection').className = 'badge online'; text('connection', 'Agent verbunden');
    text('updated', `Aktualisiert ${new Date().toLocaleTimeString('de-DE')}`);
  } catch (error) {
    $('connection').className = 'badge offline'; text('connection', 'Agent nicht erreichbar');
    text('updated', `Telemetrie konnte nicht geladen werden: ${error.message}`);
  }
}
$('refresh').addEventListener('click', refresh);
refresh(); setInterval(refresh, 3000);
