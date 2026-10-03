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
  $('earnings-grid').replaceChildren(...(data.coins || []).filter(coin => !coin.experimental && (coin.configured || forecasts.some(f => f.coin === coin.id && f.available))).map(coin => {
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
let selectedAlgorithm = '', currentOverview, currentTelemetry;
const history = {hashrate: [], temperature: [], power: []};
const setText = (id, value) => { $(id).textContent = t(value); };
function renderReadiness(overview, controls, assessment) {
  const installed = (overview.coins || []).filter(c => c.binaryAvailable && !c.experimental);
  const workers = overview.stats?.workers || [];
  const active = workers.filter(w => w.miningStatus === 'MINING');
  const errors = workers.filter(w => w.miningStatus === 'ERROR');
  setText('summary-title', active.length ? 'Dein PC arbeitet.' : errors.length ? 'Ein Worker braucht Aufmerksamkeit.' : installed.length ? 'Bereit, wenn du es bist.' : 'Dein erster Miner');
  setText('summary-description', active.length ? 'Die laufenden Worker findest du unten. Manuelle Starts und das Node-Profil lassen sich unabhängig steuern.' : installed.length ? 'Aktuell läuft kein Worker. Starte einen Miner manuell oder lass den Node dein gespeichertes Profil steuern.' : 'Installiere einen passenden Miner, richte Pool und Auszahlung ein und starte anschließend bewusst.');
  setText('summary-workers', String(active.length));
  setText('summary-workers-detail', errors.length ? 'Mindestens ein Worker meldet einen Fehler.' : 'CPU und GPU getrennt erfasst');
  const ids = ['cpu', ...(overview.gpus || []).map(g => g.deviceId)];
  const enabledIds = controls ? ids.filter(id => controls.workerExternalControl?.[id] !== false && (controls.workerCoins == null || (controls.workerCoins[id] || (id === 'cpu' ? 'none' : controls.workerCoins['*'] || 'none')) !== 'none')) : [];
  setText('summary-profile', controls ? String(enabledIds.length) : '—');
  setText('summary-profile-detail', !controls ? 'Freigabe nicht verfügbar' : controls.externalControlEnabled ? 'Geräte für den Node freigegeben' : 'Node-Steuerung ausgeschaltet');
  const readiness = [
    [overview.proxy?.reachable ? 'good' : 'attention', 'Mining-Verbindung', overview.proxy?.reachable ? 'Proxy erreichbar. Die Fee-Route wird je Coin beim Start geprüft.' : 'Proxy nicht erreichbar. Prüfe die Verbindung vor dem Start.', '/proxy.html', 'Verbindung prüfen →'],
    [controls?.externalControlEnabled ? 'good' : '', 'Automatisierung', !controls ? 'Node-Freigabe konnte nicht gelesen werden.' : controls.externalControlEnabled ? 'Der Node darf das Geräteprofil steuern.' : 'Du entscheidest lokal. Der Node darf keine Miner starten.', '/mining.html#profile', 'Node-Profil öffnen →'],
    [assessment?.connected ? 'good' : '', 'SolarMiner Node', assessment?.connected ? 'Node-Bewertung verfügbar. Details zur Entscheidung bleiben beim Node.' : assessment?.connected === false ? 'Kein Node verbunden. Lokales Mining bleibt möglich.' : 'Node-Status nicht verfügbar.', null, null]
  ];
  const list = $('readiness-list'); list.replaceChildren();
  for (const [state, title, description, href, action] of readiness) {
    const card = document.createElement('article'); card.className = 'readiness-item ' + state;
    const dot = document.createElement('span'); dot.className = 'readiness-dot'; dot.setAttribute('aria-hidden', 'true');
    const copy = document.createElement('div'), heading = document.createElement('strong'), info = document.createElement('small');
    heading.textContent = t(title); info.textContent = t(description); copy.append(heading, info);
    if (href) { const link = document.createElement('a'); link.href = href; link.textContent = t(action); copy.append(link); }
    card.append(dot, copy); list.append(card);
  }
  const unconfigured = installed.find(c => !c.configured);
  const next = !installed.length ? ['Miner hinzufügen', 'Wähle CPU oder GPU und installiere den passenden Miner über das Plus.', '/mining.html', 'Miner hinzufügen →']
    : unconfigured ? ['Einrichtung abschließen', 'Pool, Auszahlung und Geräteauswahl fehlen bei einem installierten Miner.', '/mining.html#' + unconfigured.id, 'Miner einrichten →']
    : !overview.proxy?.reachable ? ['Verbindung prüfen', 'Die Mining-Verbindung ist nicht erreichbar. Prüfe lokalen oder externen Proxy.', '/proxy.html', 'Verbindung öffnen →']
    : errors.length ? ['Worker prüfen', 'Ein Miner meldet einen Fehler. Seine Konsole hilft bei der Diagnose.', '/mining.html', 'Miner öffnen →']
    : controls?.externalControlEnabled && !enabledIds.length ? ['Node-Profil festlegen', 'Die Steuerung ist erlaubt, aber noch kein Gerät dem Node zugeordnet.', '/mining.html', 'Geräte zuordnen →'] : null;
  $('next-action').hidden = !next;
  if (next) { setText('next-title', next[0]); setText('next-description', next[1]); $('next-link').href = next[2]; setText('next-link', next[3]); }
}
function drawChart(id, values) {
  const svg = $(id), valid = values.filter(Number.isFinite);
  if (!valid.length) { svg.replaceChildren(); return; }
  const low = Math.min(...valid), high = Math.max(...valid), span = high - low || Math.max(1, high * .1);
  let segment = false;
  const commands = values.map((value, index) => {
    if (!Number.isFinite(value)) { segment = false; return ''; }
    const point = (segment ? 'L' : 'M') + (index * 600 / Math.max(1, values.length - 1)) + ' ' + (138 - (value - low) / span * 118);
    segment = true; return point;
  }).join(' ');
  const grid = document.createElementNS('http://www.w3.org/2000/svg', 'path'); grid.setAttribute('d', 'M0 20H600 M0 78H600 M0 138H600'); grid.setAttribute('class', 'chart-grid');
  const line = document.createElementNS(grid.namespaceURI, 'path'); line.setAttribute('d', commands); line.setAttribute('class', 'chart-line');
  const last = values.at(-1);
  const point = document.createElementNS(grid.namespaceURI, 'circle'); point.setAttribute('cx', '600'); point.setAttribute('cy', String(138 - (Number.isFinite(last) ? (last - low) / span * 118 : 0))); point.setAttribute('r', '4'); point.setAttribute('fill', 'currentColor');
  svg.replaceChildren(grid, line); if (Number.isFinite(last)) svg.append(point);
  svg.setAttribute('aria-label', t('Messverlauf seit Öffnen dieser Seite'));
}
function track(key, value) { history[key].push(Number.isFinite(value) ? value : NaN); if (history[key].length > 48) history[key].shift(); drawChart(`${key === 'hashrate' ? 'hashrate' : key === 'temperature' ? 'temperature' : 'power'}-chart`, history[key]); }
function renderTelemetry(data, overview) {
  const workers = overview.stats?.workers || [];
  const algorithms = [...new Set(workers.map(w => w.currentAlgorithm).filter(Boolean))];
  const picker = $('chart-algorithm');
  const previous = selectedAlgorithm;
  if (!algorithms.includes(selectedAlgorithm)) selectedAlgorithm = algorithms[0] || '';
  if (!picker.options.length || [...picker.options].map(o => o.value).join(',') !== algorithms.join(',')) {
    picker.replaceChildren();
    for (const algorithm of algorithms) { const option = document.createElement('option'); option.value = algorithm; option.textContent = algorithm; picker.append(option); }
    if (!algorithms.length) { const empty = document.createElement('option'); empty.textContent = t('Noch keine Worker'); empty.value = ''; picker.append(empty); }
  }
  picker.value = selectedAlgorithm; picker.disabled = !algorithms.length;
  if (previous !== selectedAlgorithm) history.hashrate = [];
  const rate = selectedAlgorithm ? workers.filter(w => w.currentAlgorithm === selectedAlgorithm).reduce((sum, w) => sum + (Number(w.terahashPerSecond) || 0), 0) : NaN;
  const gpuTemps = Object.entries(data.metrics || {}).filter(([key, metric]) => /gpu|nvidia|radeon|graphics/i.test(key) && /temperature/i.test(key) && metric.available).map(([, metric]) => Number(metric.value)).filter(Number.isFinite);
  const cpu = data.metrics?.['cpu.temperature']; const temps = [...(cpu?.available ? [cpu.value] : []), ...gpuTemps];
  const temp = temps.length ? Math.max(...temps) : NaN;
  const total = data.metrics?.['system.total_power'];
  $('chart-hashrate').textContent = hashrate(rate * 1e12);
  $('chart-temperature').textContent = Number.isFinite(temp) ? `${number(temp, 0)} °C` : '—';
  $('chart-temperature-label').textContent = `CPU ${cpu?.available ? `${number(cpu.value, 0)} °C` : '—'} · GPU ${gpuTemps.length ? `${number(Math.max(...gpuTemps), 0)} °C` : '—'}`;
  $('chart-power').textContent = total?.available ? `${number(total.value, 0)} W` : '—';
  $('chart-workers').textContent = t('Nur dieser Algorithmus · Verlauf seit Seitenaufruf');
  track('hashrate', rate); track('temperature', temp); track('power', total?.available ? total.value : NaN);
  $('worker-grid').replaceChildren(...workers.map(worker => {
    const card = document.createElement('article'); card.className = 'worker-card';
    const name = document.createElement('strong'); name.textContent = worker.workerDisplayName || 'Worker';
    const details = document.createElement('span'); details.textContent = `${worker.currentAlgorithm || '—'} · ${hashrate((Number(worker.terahashPerSecond) || 0) * 1e12)} · ${t(label[worker.miningStatus] || '—')}`;
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
    const overview = await response.json(); currentOverview = overview; render(overview);
    window.SolarMinerMiningCache?.write(overview);
    if (!$('earnings-grid').children.length) $('earnings-grid').textContent = t('Ertragsprognosen erscheinen, sobald ein Miner eingerichtet ist und Hashrate liefert.');
    const optional = await Promise.allSettled(['/api/agent/power-control/settings', '/api/agent/node-assessment'].map(async url => { const r = await fetch(url, {cache: 'no-store'}); return r.ok ? r.json() : null; }));
    renderReadiness(overview, optional[0].status === 'fulfilled' ? optional[0].value : null, optional[1].status === 'fulfilled' ? optional[1].value : null);
    const telemetry = await fetch('/api/agent/telemetry', {cache: 'no-store'});
    if (telemetry.ok) { currentTelemetry = await telemetry.json(); renderTelemetry(currentTelemetry, overview); } else { $('chart-temperature').textContent = $('chart-power').textContent = '—'; $('chart-temperature-label').textContent = t('Sensorwerte konnten nicht geladen werden'); }
  } catch (error) {
    $('connection').className = 'badge offline'; $('connection').textContent = 'Agent nicht erreichbar';
    $('notice').hidden = false;
    $('notice').textContent = `Agent-Daten konnten nicht geladen werden: ${error.message}`;
  }
}
$('chart-algorithm').addEventListener('change', event => { selectedAlgorithm = event.target.value; history.hashrate = []; if (currentTelemetry && currentOverview) renderTelemetry(currentTelemetry, currentOverview); });
$('refresh').addEventListener('click', refresh);
refresh(); setInterval(() => { if (!document.hidden) refresh(); }, 5000);
