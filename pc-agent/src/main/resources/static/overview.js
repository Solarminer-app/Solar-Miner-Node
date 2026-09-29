const $ = id => document.getElementById(id);
const label = {MINING: 'Mining aktiv', PAUSED: 'Pausiert', STOPPED: 'Gestoppt', ERROR: 'Fehler'};
const number = (value, digits = 2) => new Intl.NumberFormat('de-DE', {maximumFractionDigits: digits}).format(value);
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
function render(data) {
  $('agent-state').textContent = 'Online';
  $('active-miner').textContent = data.coins?.find(c => c.id === data.activeCoin)?.name || '—';
  $('mining-state').textContent = label[data.stats?.miningStatus] || '—';
  $('installed-count').textContent = String(data.coins?.filter(c => c.binaryAvailable).length || 0);
  $('proxy-state').textContent = data.proxy?.reachable ? 'Verbunden' : 'Nicht erreichbar';
  $('proxy-mode').textContent = data.proxy?.mode === 'standalone' ? 'Lokaler Dienst' : 'Externer SolarMiner-Proxy';
  $('miner-list').replaceChildren(...(data.coins || []).map(coin => {
    const row = document.createElement('a'); row.className = 'overview-miner'; row.href = `/mining.html#${coin.id}`;
    const emblem = document.createElement('span'); emblem.className = `coin-emblem ${coin.id}`; emblem.textContent = coin.id === 'monero' ? 'ɱ' : '◈';
    const info = document.createElement('span'); const name = document.createElement('strong'); name.textContent = `${coin.name} · ${coin.algorithm}`;
    const state = document.createElement('small'); state.textContent = coin.binaryAvailable ? (label[coin.status] || 'Installiert') : 'Noch nicht installiert';
    info.append(name, state); row.append(emblem, info); return row;
  }));
  renderEarnings(data);
  $('connection').className = 'badge online'; $('connection').textContent = 'Agent verbunden';
  $('updated').textContent = `Aktualisiert ${new Date().toLocaleTimeString('de-DE')}`;
  $('notice').hidden = true;
}
async function refresh() {
  try {
    const response = await fetch('/api/agent/overview', {cache: 'no-store'});
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    render(await response.json());
  } catch (error) {
    $('connection').className = 'badge offline'; $('connection').textContent = 'Agent nicht erreichbar';
    $('agent-state').textContent = 'Offline'; $('notice').hidden = false;
    $('notice').textContent = `Agent-Daten konnten nicht geladen werden: ${error.message}`;
  }
}
$('refresh').addEventListener('click', refresh);
refresh(); setInterval(() => { if (!document.hidden) refresh(); }, 12000);
