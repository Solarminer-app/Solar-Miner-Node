const $ = id => document.getElementById(id);
const i18n = window.SolarMinerI18n;
const t = i18n.t;
const label = {MINING: 'Mining aktiv', PAUSED: 'Pausiert', STOPPED: 'Gestoppt', ERROR: 'Fehler'};
const number = (value, digits = 2) => new Intl.NumberFormat(i18n.locale, {maximumFractionDigits: digits}).format(value);
function hashrate(value) {
  if (!(value > 0)) return '—';
  const units = ['H/s', 'kH/s', 'MH/s', 'GH/s', 'TH/s', 'PH/s', 'EH/s']; let unit = 0;
  while (value >= 1000 && unit < units.length - 1) { value /= 1000; unit++; }
  return `${number(value, value >= 100 ? 0 : 2)} ${units[unit]}`;
}
function renderEarnings(data) {
  const forecasts = data.earnings || [];
  $('earnings-grid').replaceChildren(...(data.coins || []).map(coin => {
    const forecast = forecasts.find(value => value.coin === coin.id);
    const card = document.createElement('article'); card.className = 'earning-card';
    const head = document.createElement('div'); head.className = 'coin-top';
    const title = document.createElement('strong'); title.textContent = `${coin.name} (${coin.ticker})`;
    const state = document.createElement('span'); state.className = `tag ${forecast?.available ? 'ready' : ''}`;
    state.textContent = forecast?.stale ? 'DATEN VERALTET' : forecast?.available ? 'LIVE-SCHÄTZUNG' : 'NOCH NICHT VERFÜGBAR';
    head.append(title, state);
    const value = document.createElement('strong'); value.className = 'earning-value';
    value.textContent = forecast?.available ? `${number(forecast.coinsPerDay, 6)} ${coin.ticker}` : '—';
    const fiat = document.createElement('span'); fiat.className = 'earning-fiat';
    fiat.textContent = forecast?.available ? `≈ ${number(forecast.usdPerDay, 2)} USD / Tag` : forecast?.unavailableReason || 'Marktdaten werden geladen';
    const meta = document.createElement('small'); meta.textContent = `${hashrate(forecast?.hashrateHps)} · Kurs ${forecast?.priceUsd > 0 ? number(forecast.priceUsd, 4) + ' USD' : '—'}`;
    card.append(head, value, fiat, meta); return card;
  }));
}
const history = {hashrate: [], temperature: [], power: []};
function drawChart(id, values) {
  const svg = $(id), valid = values.filter(Number.isFinite);
  if (!valid.length) { svg.replaceChildren(); return; }
  const low = Math.min(...valid), high = Math.max(...valid), span = high - low || Math.max(1, high * .1);
  const points = values.map((v, i) => `${i * 600 / Math.max(1, values.length - 1)},${138 - (Number.isFinite(v) ? (v - low) / span * 118 : 0)}`).join(' ');
  const grid = document.createElementNS('http://www.w3.org/2000/svg', 'path'); grid.setAttribute('d', 'M0 20H600 M0 78H600 M0 138H600'); grid.setAttribute('class', 'chart-grid');
  const line = document.createElementNS('http://www.w3.org/2000/svg', 'polyline'); line.setAttribute('points', points); line.setAttribute('class', 'chart-line');
  svg.replaceChildren(grid, line);
}
function track(key, value) { history[key].push(Number.isFinite(value) ? value : NaN); if (history[key].length > 48) history[key].shift(); drawChart(`${key === 'hashrate' ? 'hashrate' : key === 'temperature' ? 'temperature' : 'power'}-chart`, history[key]); }
function renderTelemetry(data, overview) {
  const workers = overview.stats?.workers || [];
  const workerRate = workers.reduce((sum, w) => sum + (Number(w.terahashPerSecond) || 0), 0);
  const rate = workerRate || Number(overview.stats?.terahashPerSecond) || 0;
  const gpuTemps = Object.entries(data.metrics || {}).filter(([key, metric]) => /gpu|nvidia|radeon|graphics/i.test(key) && /temperature/i.test(key) && metric.available).map(([, metric]) => Number(metric.value)).filter(Number.isFinite);
  const cpu = data.metrics?.['cpu.temperature']; const temps = [...(cpu?.available ? [cpu.value] : []), ...gpuTemps];
  const temp = temps.length ? Math.max(...temps) : NaN;
  const total = data.metrics?.['system.total_power'];
  $('chart-hashrate').textContent = hashrate(rate * 1e12);
  $('chart-temperature').textContent = Number.isFinite(temp) ? `${number(temp, 0)} °C` : '—';
  $('chart-temperature-label').textContent = `CPU ${cpu?.available ? `${number(cpu.value, 0)} °C` : '—'} · GPU ${gpuTemps.length ? `${number(Math.max(...gpuTemps), 0)} °C` : '—'}`;
  $('chart-power').textContent = total?.available ? `${number(total.value, 0)} W` : '—';
  $('chart-workers').textContent = workers.length ? `${workers.length} Worker · ${workers.map(w => w.workerDisplayName).filter(Boolean).join(', ')}` : 'Keine Worker aktiv';
  track('hashrate', rate); track('temperature', temp); track('power', total?.available ? total.value : NaN);
  $('worker-grid').replaceChildren(...workers.map(worker => {
    const card = document.createElement('article'); card.className = 'worker-card';
    const name = document.createElement('strong'); name.textContent = worker.workerDisplayName || 'Worker';
    const details = document.createElement('span'); details.textContent = `${hashrate((Number(worker.terahashPerSecond) || 0) * 1e12)} · ${Number.isFinite(Number(worker.temperatureCelsius)) ? `${number(worker.temperatureCelsius, 0)} °C` : '—'} · ${label[worker.miningStatus] || '—'}`;
    card.append(name, details); return card;
  }));
}
function render(data) {
  renderEarnings(data);
  $('connection').className = 'badge online'; $('connection').textContent = 'Agent verbunden';
  $('updated').textContent = t(`Aktualisiert ${new Date().toLocaleTimeString(i18n.locale)}`);
  $('notice').hidden = true;
}
async function refresh() {
  try {
    const response = await fetch('/api/agent/overview', {cache: 'no-store'});
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const overview = await response.json(); render(overview);
    const telemetry = await fetch('/api/agent/telemetry', {cache: 'no-store'});
    if (telemetry.ok) renderTelemetry(await telemetry.json(), overview);
  } catch (error) {
    $('connection').className = 'badge offline'; $('connection').textContent = 'Agent nicht erreichbar';
    $('notice').hidden = false;
    $('notice').textContent = `Agent-Daten konnten nicht geladen werden: ${error.message}`;
  }
}
$('refresh').addEventListener('click', refresh);
refresh(); setInterval(() => { if (!document.hidden) refresh(); }, 5000);
