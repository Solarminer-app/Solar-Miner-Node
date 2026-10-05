const $ = id => document.getElementById(id);
const i18n = window.SolarMinerI18n;
const t = i18n.t;
const statusLabels = { MINING: 'Mining aktiv', PAUSED: 'Pausiert', STOPPED: 'Gestoppt', ERROR: 'Fehler' };
let latest = null, busy = false, deviceFilter = 'all';
let refreshing = false, assessing = false, initialCatalog = null, eventStream = null;
let overviewFresh = false, overviewSavedAt = null;
let stateRevision = 0, refreshQueued = false;
const cachedControls = new Map();
function invalidateMiningViewCache() {
  stateRevision++;
  window.SolarMinerMiningCache?.clear();
}
const gpuCoinIds = ['ravencoin', 'ethereumclassic'];
const viewIds = ['monero', 'pearl', ...gpuCoinIds];
let selectedView = viewIds.includes(location.hash.slice(1)) ? location.hash.slice(1) : 'catalog';
let pendingInstall = null;
const selectedInstallers = new Set();
let defenderAction = null, defenderMessage = null;
let selectedGpuIndices = new Set(), gpuSelectionDirty = false, gpuSelectionVersion = null;
const gpuCoinSelection = {ravencoin: new Set(), ethereumclassic: new Set()};
const gpuCoinSelectionVersion = {};
const gpuCoinSelectionDirty = {ravencoin: false, ethereumclassic: false};

function initGpuCoinForms() {
  const labels = {ravencoin: ['Ravencoin', 'RVN', 'KAWPOW'], ethereumclassic: ['Ethereum Classic', 'ETC', 'ETCHash']};
  for (const coin of gpuCoinIds) {
    const [name, ticker, algorithm] = labels[coin];
    const form = document.createElement('form');
    form.id = `${coin}-form`; form.className = 'form-block'; form.hidden = true;
    form.innerHTML = `<h3>${name} · ${algorithm} / GPU</h3>
      <p class="muted">Wähle Kryptex mit eigener ${ticker}-Wallet oder bestätige das SolarMiner-Standardziel. Die Fee-Route muss vor dem Start freigeschaltet sein.</p>
      <label for="${coin}-pool-select">Mining-Pool</label>
      <select id="${coin}-pool-select">
        <option value="solarminer">SolarMiner-Standard · ohne eigene Auszahlung</option>
        <option value="stratum+tcp://${coin === 'ravencoin' ? 'rvn' : 'etc'}.kryptex.network:${coin === 'ravencoin' ? '7031' : '7033'}">Kryptex · Global</option>
        <option value="stratum+tcp://${coin === 'ravencoin' ? 'rvn' : 'etc'}-eu.kryptex.network:${coin === 'ravencoin' ? '7031' : '7033'}">Kryptex · Europa</option>
        <option value="stratum+tcp://${coin === 'ravencoin' ? 'rvn' : 'etc'}-us.kryptex.network:${coin === 'ravencoin' ? '7031' : '7033'}">Kryptex · Nordamerika</option>
        <option value="custom">Erweitert · eigene Pool-Adresse</option>
      </select>
      <div id="${coin}-pool-custom" class="advanced-pool" hidden><label for="${coin}-pool">Eigene Pool-Adresse</label>
        <input id="${coin}-pool" type="text" placeholder="stratum+tcp://pool.example:3333" autocomplete="off" spellcheck="false">
        <p>Format: stratum+tcp://host:port oder stratum+ssl://host:port</p></div>
      <label for="${coin}-wallet">${ticker} Wallet</label>
      <input id="${coin}-wallet" type="text" autocomplete="off" spellcheck="false">
      <p id="${coin}-payout-note" class="muted"></p>
      <label for="${coin}-worker">Worker</label>
      <input id="${coin}-worker" type="text" value="pc" pattern="[A-Za-z0-9_\\-]{1,32}" required>
      <div class="field"><span class="field-label">GPUs auswählen</span>
      <div id="${coin}-devices" class="gpu-selection" role="group" aria-label="GPUs auswählen"></div>
      <small id="${coin}-devices-help" class="muted"></small></div>
      <button id="${coin}-submit" class="button" type="submit">Konfiguration speichern</button>
      <p id="${coin}-save-feedback" class="notice form-feedback" role="status" aria-live="polite" hidden></p>`;
    $('power-form').before(form);
    form.addEventListener('input', () => { form.dataset.dirty = 'true'; });
    $(`${coin}-pool-select`).addEventListener('change', () => { form.dataset.dirty = 'true'; updatePoolChoice(coin); });
    updatePoolChoice(coin);
    form.addEventListener('submit', event => {
      event.preventDefault();
      if (!form.reportValidity()) return;
      const devices = [...gpuCoinSelection[coin]].sort();
      if (!devices.length) return notice('Wähle mindestens eine erkannte GPU.', true);
      const proxyUrl = latest?.proxy?.[coin === 'ravencoin' ? 'ravencoinUrl' : 'ethereumclassicUrl'];
      if (!proxyUrl) return notice('SolarMiner-Proxy-Route fehlt.', true);
      if (!payoutAvailable(coin)) return;
      const standard = usesStandardPayout(coin);
      action(`/api/agent/local/${coin}/configuration`, standard ? `${name} gespeichert: Auszahlung an das SolarMiner-Standardziel.` : `${name}-Konfiguration gespeichert.`, null,
        {poolUrl: standard ? '' : selectedPool(coin), proxyUrl, wallet: standard ? '' : $(`${coin}-wallet`).value.trim(),
         worker: $(`${coin}-worker`).value.trim(), devices: devices.join(',')}, `${coin}-save-feedback`);
    });
  }
}
let consoleState = null;
let randomXOptimization = null, lastOptimizationLoad = 0, optimizationBusy = false;
let nodeAssessment = null;
const gpuKey = gpu => `${gpu.vendor}:${gpu.index}`;
// Keep recommendations explicit and evidence-backed per algorithm/platform. A missing
// platform-specific entry is intentionally not guessed from another miner or coin.
const miningTips = {
  monero: [
    { id: 'huge-pages', status: 'hugePagesConfigured', platforms: ['windows', 'linux'], level: 'Empfohlen', title: 'Huge Pages für RandomX aktivieren', action: 'SolarMiner schaltet die XMRig-Option ein. Unter Windows fragt der Agent per UAC nach der Berechtigung für dein Benutzerkonto; nach einer neuen Anmeldung wird das Recht wirksam.', risk: 'Huge Pages halten RAM während des Minings fest. Dieser Speicher steht anderen Programmen und Windows dann nicht zur Verfügung; bei wenig freiem RAM kann das System langsamer reagieren. Beim Ausschalten entfernt SolarMiner nur ein Windows-Recht, das es selbst hinzugefügt hat.', source: 'https://xmrig.com/docs/miner/hugepages', control: true },
    { id: '1gb-pages', status: 'oneGbPagesActive', platforms: ['linux'], level: 'Optional · Linux', title: '1-GB-Pages für RandomX aktivieren', action: 'SolarMiner schaltet die XMRig-Option ein. XMRig benötigt dafür bis zu 3 GB Speicher pro NUMA-Knoten; die Nutzung hängt zusätzlich von Kernel und verfügbarer Speicherkonfiguration ab.', risk: 'Der Miner kann 1-GB-Pages möglicherweise nicht reservieren und auf normale Huge Pages zurückfallen. Der zusätzlich benötigte Speicher steht dem System und anderen Programmen währenddessen nicht zur Verfügung.', source: 'https://xmrig.com/docs/miner/hugepages', control: true }
  ],
  pearl: [
    { id: 'pearl-gpu-compatible', platforms: ['windows', 'linux'], level: 'Voraussetzung', title: 'Kompatible GPU und Treiber', action: 'Die GPU-, Treiber- und SRBMiner-Kompatibilität hängt von Modell, Version und Betriebssystem ab. Für diese Prüfung gibt es derzeit keine automatische Aktivierungsaktion.', source: 'https://github.com/doktor83/SRBMiner-Multi' },
    { id: 'pearl-miner-no-errors', platforms: ['windows', 'linux'], level: 'Hinweis', title: 'Miner-Ausgabe', action: 'SRBMiner meldet erkannte Geräte und Initialisierungsfehler in seiner Konsole. Der PC-Agent zeigt die Miner-Ausgabe in dieser Ansicht.', source: 'https://github.com/doktor83/SRBMiner-Multi' }
  ],
  ravencoin: [
    { id: 'rvn-gpu-compatible', platforms: ['windows', 'linux'], level: 'Voraussetzung', title: 'KAWPOW-fähige GPU und Treiber', action: 'SRBMiner muss die gewählte GPU für KAWPOW erkennen. Prüfe Geräte und Initialisierungsfehler in der Miner-Konsole; der Agent ändert keine Treiber oder Taktraten.', source: 'https://github.com/doktor83/SRBMiner-Multi' }
  ],
  ethereumclassic: [
    { id: 'etc-gpu-compatible', platforms: ['windows', 'linux'], level: 'Voraussetzung', title: 'ETCHash-fähige GPU und Treiber', action: 'SRBMiner muss die gewählte GPU für ETCHash erkennen. Prüfe Geräte und Initialisierungsfehler in der Miner-Konsole; der Agent ändert keine Treiber oder Taktraten.', source: 'https://github.com/doktor83/SRBMiner-Multi' }
  ]
};
function platformKey(platform = '') {
  const value = platform.toLowerCase();
  return value.includes('win') ? 'windows' : value.includes('linux') ? 'linux' : value.includes('mac') ? 'macos' : 'other';
}
function renderOptimizationChecklist(coin, data) {
  const panel = $('optimization-panel');
  const platform = platformKey(data.platform);
  panel.hidden = !coin || !miningTips[coin.id];
  if (panel.hidden) return;
  set('optimization-platform', `${data.platform || 'Unbekannt'} · ${data.architecture || 'Architektur unbekannt'}`);
  const tips = miningTips[coin.id].filter(tip => tip.platforms.includes(platform));
  const list = $('optimization-checklist'); list.replaceChildren();
  if (!tips.length) {
    list.append(node('p', 'empty', `Für ${data.platform || 'dieses Betriebssystem'} sind noch keine verifizierten Tipps hinterlegt.`));
    return;
  }
  for (const tip of tips) {
    const active = Boolean(randomXOptimization?.[tip.status]);
    const item = node('article', `optimization-item ${active ? 'complete' : ''}`);
    const label = node('div', 'optimization-check');
    const title = node('strong', '', tip.title);
    label.append(title, node('span', 'tag', tip.level));
    if (tip.control) {
      const input = document.createElement('input'); input.type = 'checkbox'; input.checked = active;
      input.disabled = optimizationBusy;
      input.setAttribute('aria-label', `${t(tip.title)} ${t('über SolarMiner aktivieren')}`);
      input.addEventListener('change', () => setRandomXOptimization(tip, input.checked));
      label.prepend(input);
    } else {
      label.prepend(node('span', 'optimization-info', 'i'));
    }
    const description = node('p', 'muted', tip.action);
    const source = node('a', '', 'Dokumentation öffnen ↗'); source.href = tip.source; source.target = '_blank'; source.rel = 'noopener noreferrer';
    item.append(label, description);
    if (tip.risk) item.append(node('p', 'optimization-risk', `${t('Risiko:')} ${t(tip.risk)}`));
    item.append(source); list.append(item);
  }
  const statusMessage = $('optimization-message');
  if (randomXOptimization?.restartRequired) {
    statusMessage.classList.remove('error');
    statusMessage.hidden = false;
    set('optimization-message', 'Neustart erforderlich: Die Einstellung und Windows-Berechtigung sind eingerichtet. Starte Windows neu, damit Huge Pages für den PC-Agent wirksam werden.');
  } else if (randomXOptimization?.error) {
    statusMessage.classList.add('error');
    statusMessage.hidden = false;
    set('optimization-message', `Status konnte nicht geladen werden: ${randomXOptimization.error}`);
  }
}
async function loadRandomXOptimization() {
  lastOptimizationLoad = Date.now();
  try {
    const response = await fetch('/api/agent/local/optimizations/randomx', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    randomXOptimization = await response.json();
  } catch (error) {
    randomXOptimization = { hugePagesActive: false, oneGbPagesActive: false, error: error.message };
  }
  if (latest) renderOptimizationChecklist(latest.coins?.find(c => c.id === selectedView), latest);
}
async function setRandomXOptimization(tip, enabled) {
  optimizationBusy = true;
  if (latest) renderOptimizationChecklist(latest.coins?.find(c => c.id === selectedView), latest);
  try {
    const response = await fetch(`/api/agent/local/optimizations/randomx/${tip.id}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled })
    });
    if (!response.ok) {
      const body = await response.json().catch(() => null);
      throw new Error(body?.message || `HTTP ${response.status}`);
    }
    randomXOptimization = await response.json();
    const enabledStatus = tip.id === 'huge-pages' ? randomXOptimization.hugePagesActive : randomXOptimization.oneGbPagesActive;
    const message = randomXOptimization.restartRequired
      ? 'Neustart erforderlich: Windows hat die Berechtigung eingerichtet. Starte den PC neu; danach wird Huge Pages hier als aktiv angezeigt.'
      : !enabled && platformKey(latest?.platform) === 'windows'
        ? 'Huge Pages sind in XMRig deaktiviert. Windows übernimmt Änderungen an Benutzerrechten bei einer neuen Anmeldung; vorhandene Sitzungen können das alte Recht bis dahin behalten.'
        : enabled && !enabledStatus
          ? 'Einstellung wurde gespeichert, ist auf diesem System aber noch nicht aktiv.'
          : enabled ? 'Funktion wurde aktiviert.' : 'Funktion wurde deaktiviert.';
    $('optimization-message').classList.remove('error');
    $('optimization-message').hidden = false;
    set('optimization-message', message);
  } catch (error) {
    $('optimization-message').classList.add('error');
    $('optimization-message').hidden = false;
    set('optimization-message', `Aktivierung fehlgeschlagen: ${error.message}`);
    await loadRandomXOptimization();
  } finally {
    optimizationBusy = false;
    if (latest) renderOptimizationChecklist(latest.coins?.find(c => c.id === selectedView), latest);
  }
}
const fmt = (value, digits = 1) => new Intl.NumberFormat(i18n.locale, { maximumFractionDigits: digits }).format(value);
function fmtHashrate(value) {
  if (!(value > 0)) return '—';
  const units = ['H/s', 'kH/s', 'MH/s', 'GH/s', 'TH/s', 'PH/s', 'EH/s']; let unit = 0;
  while (value >= 1000 && unit < units.length - 1) { value /= 1000; unit++; }
  return `${fmt(value, value >= 100 ? 0 : 2)} ${units[unit]}`;
}
const set = (id, value) => { $(id).textContent = t(value); };
function notice(message, error = false) { const el = selectedView === 'catalog' ? $('catalog-notice') : $('notice'); el.textContent = t(message); el.classList.toggle('error', error); el.hidden = !message; }
function node(tag, className, value) { const el = document.createElement(tag); if (className) el.className = className; if (value !== undefined) el.textContent = t(value); return el; }
function usesStandardPayout(coin) {
  return $(`${coin}-pool-select`).value === 'solarminer';
}
function payoutDefault(coin) {
  return (latest?.payoutDefaults || []).find(entry => entry.coin === coin);
}
function renderPayoutNote(coin) {
  if (!usesStandardPayout(coin)) { set(`${coin}-payout-note`, 'Eigene Auszahlung: Pool und Wallet gehören zusammen.'); return; }
  const entry = payoutDefault(coin);
  set(`${coin}-payout-note`, entry?.available
    ? `Auszahlung an das SolarMiner-Standardziel (${entry.maskedWallet}). Ohne eigene Wallet geht die gesamte Hashrate dorthin.`
    : 'Kein SolarMiner-Standardziel erreichbar. Eigene Wallet angeben oder Proxy prüfen.');
}
function ensureStandardConsent(coin) {
  const form = $(`${coin}-form`);
  let row = form.querySelector('.standard-consent');
  if (!row) {
    row = document.createElement('label'); row.className = 'standard-consent';
    const input = document.createElement('input'); input.type = 'checkbox'; input.id = `${coin}-standard-consent`;
    const copy = document.createElement('span');
    copy.textContent = 'Ich bestätige: Ohne eigene Wallet geht meine gesamte Mining-Auszahlung an das angezeigte SolarMiner-Standardziel. Die vollständigen Gebühren stehen unten.';
    row.append(input, copy);
    const note = $(`${coin}-payout-note`); note.after(row);
  }
  row.hidden = !usesStandardPayout(coin);
  return row.querySelector('input').checked;
}
function renderPayoutState(data) {
  for (const coin of viewIds) {
    const select = $(`${coin}-pool-select`);
    const configured = coin === 'monero' ? data.moneroConfiguration : coin === 'pearl' ? data.pearlConfiguration : data.gpuCoins?.[coin]?.configuration;
    if (payoutDefault(coin)?.inUse && $(`${coin}-form`).dataset.dirty !== 'true') select.value = 'solarminer';
    // A new user starts with their own payout route. The SolarMiner house route remains an
    // explicit, consented option instead of silently receiving the user's full hashrate.
    else if (!configured && $(`${coin}-form`).dataset.initialized !== 'true') {
      select.value = [...select.options].find(option => option.value !== 'solarminer' && option.value !== 'custom')?.value || 'custom';
      $(`${coin}-form`).dataset.initialized = 'true';
    }
    updatePoolChoice(coin);
  }
}
function payoutAvailable(coin) {
  if (!usesStandardPayout(coin)) return true;
  if (!ensureStandardConsent(coin)) {
    notice('Bestätige zuerst die Auszahlung an das SolarMiner-Standardziel.', true);
    return false;
  }
  if (payoutDefault(coin)?.available) return true;
  notice(`Kein SolarMiner-Standard-Auszahlungsziel für ${coin === 'pearl' ? 'Pearl' : coin === 'monero' ? 'Monero' : coin === 'ravencoin' ? 'Ravencoin' : 'Ethereum Classic'} erreichbar. Bitte eigene Wallet angeben.`, true);
  return false;
}
function updatePoolChoice(coin) {
  const choice = $(`${coin}-pool-select`).value;
  const standard = choice === 'solarminer';
  const custom = choice === 'custom';
  $(`${coin}-pool-custom`).hidden = !custom;
  $(`${coin}-pool`).required = custom;
  const wallet = $(`${coin}-wallet`);
  wallet.required = !standard;
  wallet.disabled = standard;
  if (standard) wallet.value = '';
  renderPayoutNote(coin);
  ensureStandardConsent(coin);
}
function selectSavedPool(coin, url) {
  const picker = $(`${coin}-pool-select`);
  const known = [...picker.options].some(option => option.value === url);
  picker.value = known ? url : 'custom';
  if (!known) $(`${coin}-pool`).value = url;
  updatePoolChoice(coin);
}
function selectedPool(coin) {
  return $(`${coin}-pool-select`).value === 'custom' ? $(`${coin}-pool`).value.trim() : $(`${coin}-pool-select`).value;
}
function hydrateConfiguration(formId, configuration, fields) {
  const form = $(formId);
  if (!configuration || form.dataset.dirty === 'true') return;
  const version = JSON.stringify(configuration);
  if (form.dataset.hydrated === version) return;
  for (const [field, id] of Object.entries(fields)) {
    if (configuration[field] == null) continue;
    if (field === 'poolUrl') selectSavedPool(formId.split('-')[0], configuration[field]);
    else $(id).value = configuration[field];
  }
  form.dataset.hydrated = version;
}
async function addDefenderExclusion(coinId) {
  defenderAction = coinId; defenderMessage = null;
  if (latest) render(latest);
  try {
    const response = await fetch(`/api/agent/local/${encodeURIComponent(coinId)}/defender-exclusion`, { method: 'POST' });
    const result = await response.json().catch(() => null);
    if (!response.ok || !result?.success) throw new Error(result?.message || `HTTP ${response.status}`);
    defenderMessage = { coinId, success: true, text: result.message };
  } catch (error) {
    defenderMessage = { coinId, success: false, text: error.message };
  } finally {
    defenderAction = null;
    await refresh();
  }
}
function renderGpuSelection(data) {
  const gpus = data.gpus || [];
  const version = JSON.stringify({ devices: data.pearlConfiguration?.devices, gpus: gpus.map(g => [g.vendor, g.index, g.model]) });
  if (!gpuSelectionDirty && version !== gpuSelectionVersion) {
    const saved = data.pearlConfiguration?.devices;
    selectedGpuIndices = new Set(saved && saved !== 'all'
      ? saved.split(',').flatMap(value => value.includes(':') ? [value] : gpus.filter(gpu => String(gpu.index) === value).map(gpuKey))
      : gpus.map(gpuKey));
    gpuSelectionVersion = version;
  }
  const list = $('pearl-devices');
  const indices = gpus.map(gpuKey);
  const current = [...list.querySelectorAll('button')].map(button => button.dataset.index);
  if (current.join(',') !== indices.join(',')) {
    list.replaceChildren();
    for (const gpu of gpus) {
      const key = gpuKey(gpu);
      const button = node('button', 'gpu-button', `${gpu.vendor} ${gpu.index} · ${gpu.model || gpu.name || gpu.vendor}`);
      button.type = 'button'; button.dataset.index = key;
      button.addEventListener('click', () => {
        if (selectedGpuIndices.has(key)) selectedGpuIndices.delete(key);
        else selectedGpuIndices.add(key);
        gpuSelectionDirty = true;
        renderGpuSelection(latest);
      });
      list.append(button);
    }
  }
  for (const button of list.querySelectorAll('button')) {
    const selected = selectedGpuIndices.has(button.dataset.index);
    button.classList.toggle('selected', selected);
    button.setAttribute('aria-pressed', String(selected));
  }
  const selectedCount = indices.filter(index => selectedGpuIndices.has(index)).length;
  set('pearl-devices-help', indices.length ? `${selectedCount} von ${indices.length} GPUs ausgewählt.` : 'Keine unterstützte GPU erkannt.');
  $('pearl-submit').disabled = busy || !indices.some(index => selectedGpuIndices.has(index));
}
function renderGpuCoinSelection(coin, data) {
  const config = data.gpuCoins?.[coin]?.configuration;
  const gpus = data.gpus || [];
  const version = JSON.stringify({devices: config?.devices, gpus: gpus.map(g => [g.vendor, g.index, g.model])});
  if (!gpuCoinSelectionDirty[coin] && gpuCoinSelectionVersion[coin] !== version) {
    gpuCoinSelection[coin] = new Set(config?.devices ? config.devices.split(',') : gpus.map(gpuKey));
    gpuCoinSelectionVersion[coin] = version;
  }
  const list = $(`${coin}-devices`);
  const indices = gpus.map(gpuKey);
  if ([...list.querySelectorAll('button')].map(button => button.dataset.index).join(',') !== indices.join(',')) {
    list.replaceChildren();
    for (const gpu of gpus) {
      const key = gpuKey(gpu);
      const button = node('button', 'gpu-button', `${gpu.vendor} ${gpu.index} · ${gpu.model || gpu.vendor}`);
      button.type = 'button'; button.dataset.index = key;
      button.addEventListener('click', () => {
        if (gpuCoinSelection[coin].has(key)) gpuCoinSelection[coin].delete(key);
        else gpuCoinSelection[coin].add(key);
        gpuCoinSelectionDirty[coin] = true;
        renderGpuCoinSelection(coin, latest);
      });
      list.append(button);
    }
  }
  for (const button of list.querySelectorAll('button')) {
    const selected = gpuCoinSelection[coin].has(button.dataset.index);
    button.classList.toggle('selected', selected);
    button.setAttribute('aria-pressed', String(selected));
  }
  const selectedCount = indices.filter(index => gpuCoinSelection[coin].has(index)).length;
  set(`${coin}-devices-help`, indices.length ? `${selectedCount} von ${indices.length} GPUs ausgewählt.` : 'Keine GPU erkannt.');
  $(`${coin}-submit`).disabled = busy || !indices.some(index => gpuCoinSelection[coin].has(index));
  const form = $(`${coin}-form`);
  if (config && form.dataset.dirty !== 'true' && form.dataset.hydrated !== JSON.stringify(config)) {
    selectSavedPool(coin, config.poolUrl);
    $(`${coin}-wallet`).value = config.wallet;
    $(`${coin}-worker`).value = config.worker;
    form.dataset.hydrated = JSON.stringify(config);
  }
}
function showView(view) {
  selectedView = view; location.hash = view === 'catalog' ? '' : view;
  if (latest) render(latest);
  else if (initialCatalog) renderInitialCatalog(initialCatalog);
}
function syncConsoleView() {
  const coin = selectedView === 'pearl' || gpuCoinIds.includes(selectedView)
    ? $('console-gpu').value || selectedView : selectedView === 'monero' ? 'monero' : null;
  if ((consoleState?.coin ?? null) === coin) return;
  consoleState = coin ? { coin, offset: 0, runId: null, decoder: new TextDecoder(), loading: false } : null;
  $('miner-console').replaceChildren();
  $('console-download').hidden = true;
  if (!coin) return;
  set('miner-console-title', coin === 'monero' ? 'XMRig-Konsole' : `SRBMiner-Konsole · ${coin}`);
  set('console-status', 'Lade vollständige Ausgabe …');
  $('console-download').href = `/api/agent/local/console/${coin}/download`;
  pollConsole();
}
async function pollConsole() {
  const state = consoleState;
  if (!state || state.loading || document.hidden) return;
  state.loading = true;
  let more = false;
  try {
    const response = await fetch(`/api/agent/local/console/${state.coin}?offset=${state.offset}`, { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const chunk = await response.json();
    if (consoleState !== state) return;
    if (chunk.runId !== state.runId) {
      state.runId = chunk.runId;
      state.offset = 0;
      state.decoder = new TextDecoder();
      $('miner-console').replaceChildren();
    }
    if (chunk.data) {
      const bytes = Uint8Array.from(atob(chunk.data), character => character.charCodeAt(0));
      const text = state.decoder.decode(bytes, { stream: true });
      const output = $('miner-console');
      const follow = output.scrollTop + output.clientHeight >= output.scrollHeight - 40;
      output.append(document.createTextNode(text));
      if (follow) output.scrollTop = output.scrollHeight;
    }
    state.offset = chunk.nextOffset;
    more = chunk.hasMore;
    $('console-download').hidden = state.offset === 0;
    set('console-status', state.offset ? 'Live · vollständiger Verlauf' : 'Noch keine Miner-Ausgabe');
  } catch (error) {
    if (consoleState === state) set('console-status', `Konsole nicht erreichbar: ${error.message}`);
  } finally {
    state.loading = false;
    // Miner output does not always alter the overview snapshot. Keep tailing the
    // selected log so a quiet status monitor cannot hide fresh console output.
    if (consoleState === state && !document.hidden) setTimeout(pollConsole, more ? 0 : 1000);
  }
}
function renderInstalledRail(coins, data = {}) {
  $('add-miner').classList.toggle('selected', selectedView === 'catalog');
  $('add-miner').setAttribute('aria-pressed', String(selectedView === 'catalog'));
  const rail = $('installed-rail'); rail.replaceChildren();
  for (const coin of coins.filter(c => c.binaryAvailable)) {
    const running = coin.id === 'pearl' ? data.pearl?.running : coin.status === 'MINING';
    const button = node('button', `rail-coin ${selectedView === coin.id ? 'selected' : ''} ${running ? 'running' : ''}`, coin.id === 'monero' ? 'ɱ' : coin.id === 'ravencoin' ? 'RVN' : coin.id === 'ethereumclassic' ? 'ETC' : 'PRL');
    button.setAttribute('aria-pressed', String(selectedView === coin.id));
    button.type = 'button'; button.title = `${coin.name} öffnen${running ? ' · läuft' : ''}`; button.setAttribute('aria-label', `${coin.name} öffnen${running ? ', läuft' : ''}`);
    button.addEventListener('click', () => showView(coin.id)); rail.append(button);
  }
}
function catalogPackages(coins) {
  const packages = new Map();
  for (const coin of coins || []) {
    for (const option of coin.miners || []) {
      const entry = packages.get(option.id) || {id: option.id, name: option.name, options: [], coins: []};
      entry.options.push(option); entry.coins.push(coin); packages.set(option.id, entry);
    }
  }
  return [...packages.values()].map(entry => ({
    ...entry,
    installed: entry.options.some(option => option.installed),
    selectable: entry.options.some(option => option.selectable !== false),
    device: entry.options.some(option => String(option.device).includes('GPU')) ? 'GPU' : 'CPU',
    experimental: entry.options.every(option => option.experimental),
    representative: entry.options.find(option => option.selectable !== false) || entry.options[0]
  }));
}
function packageStatus(pkg, data) {
  const option = pkg.options.find(item => item.downloadStatus === 'DOWNLOADING') || pkg.representative;
  const readiness = pkg.id === 'xmrig' ? data?.monero : data?.pearl;
  return {status: readiness?.downloadStatus || option?.downloadStatus || 'PENDING', detail: readiness?.downloadDetail || option?.downloadDetail,
    progress: readiness?.downloadProgress || 0};
}
function syncBulkInstall(packages) {
  for (const id of [...selectedInstallers]) {
    const pkg = packages.find(item => item.id === id);
    if (!pkg || pkg.installed || !pkg.selectable) selectedInstallers.delete(id);
  }
  const available = packages.filter(pkg => !pkg.installed && pkg.selectable);
  const selected = available.filter(pkg => selectedInstallers.has(pkg.id));
  set('catalog-installed-count', `${packages.filter(pkg => pkg.installed).length} installiert`);
  set('catalog-available-count', `${available.length} installierbar`);
  $('select-installable').disabled = busy || !available.length;
  $('select-installable').textContent = selected.length === available.length && available.length ? 'Auswahl aufheben' : 'Verfügbare auswählen';
  $('install-selected').disabled = busy || !selected.length || !overviewFresh;
  $('install-selected').textContent = selected.length ? `${selected.length} Miner installieren` : 'Auswahl installieren';
}
function renderCatalog(coins, data = null) {
  const packages = catalogPackages(coins);
  const grid = $('catalog-grid'); grid.replaceChildren();
  for (const pkg of packages) {
    if (deviceFilter !== 'all' && pkg.device !== deviceFilter) continue;
    const state = packageStatus(pkg, data), downloading = state.status === 'DOWNLOADING';
    const card = node('article', `catalog-card package-card ${pkg.installed ? 'installed' : ''}`);
    const select = document.createElement('input'); select.type = 'checkbox'; select.className = 'package-select';
    select.checked = selectedInstallers.has(pkg.id); select.disabled = busy || pkg.installed || !pkg.selectable || downloading || !overviewFresh;
    select.setAttribute('aria-label', `${pkg.name} für Installation auswählen`);
    select.addEventListener('change', () => { if (select.checked) selectedInstallers.add(pkg.id); else selectedInstallers.delete(pkg.id); syncBulkInstall(packages); });
    const emblem = node('span', 'package-emblem', pkg.device === 'CPU' ? 'CPU' : 'GPU');
    const identity = node('div', 'package-identity'); identity.append(node('h2', '', pkg.name), node('p', 'muted', `${pkg.device} · ${[...new Set(pkg.options.map(option => option.algorithm))].join(' / ')}`));
    const badge = node('span', `tag ${pkg.installed ? 'ready' : ''}`, pkg.installed ? 'INSTALLIERT' : pkg.experimental ? 'EXPERIMENTELL' : 'VERFÜGBAR');
    const head = node('div', 'package-head'); head.append(select, emblem, identity, badge); card.append(head);
    const coinsRow = node('div', 'package-coins');
    for (const coin of pkg.coins) {
      const selected = coin.selectedMiner?.id === pkg.id;
      coinsRow.append(node('span', `package-coin ${selected ? 'selected' : ''}`, `${coin.ticker || coin.name} · ${coin.algorithm}${selected ? ' · aktiv' : ''}`));
    }
    card.append(coinsRow);
    const fees = [...new Set(pkg.options.map(option => option.developerFeePercent).filter(value => value != null))].sort((a,b) => a-b);
    const feeText = fees.length ? `Dev-Fee ${fees.length === 1 ? fmt(fees[0], 2) : `${fmt(fees[0], 2)}–${fmt(fees.at(-1), 2)}`} %` : 'Dev-Fee unbekannt';
    const advantages = [...new Set(pkg.options.flatMap(option => option.advantages || []))].slice(0, 2);
    card.append(node('p', 'package-meta muted', [feeText, ...advantages].join(' · ')));
    if (state.status === 'BLOCKED_BY_ANTIVIRUS') {
      const help = node('details', 'package-help');
      const summary = node('summary', '', 'Windows-Sicherheit hat den Download blockiert – sicher prüfen'); help.append(summary);
      help.append(node('p', 'muted', 'Prüfe zuerst Erkennungsname und Datei unter Windows-Sicherheit → Viren- & Bedrohungsschutz → Schutzverlauf. Gib nur eine verifizierte Datei frei.'));
      const readiness = pkg.id === 'xmrig' ? data?.monero : data?.pearl;
      const directory = String(readiness?.installDirectory || '');
      if (['localhost', '127.0.0.1', '[::1]'].includes(location.hostname) && /^[A-Za-z]:\\/.test(directory) && !/[\x00-\x1f]/.test(directory)) {
        const request = node('button', 'button subtle', defenderAction === pkg.representative.coin ? 'Windows-Freigabe wird angefordert …' : 'Diesen Installationsordner ausnehmen');
        request.type = 'button'; request.disabled = defenderAction !== null;
        request.addEventListener('click', () => addDefenderExclusion(pkg.representative.coin)); help.append(request);
        if (defenderMessage?.coinId === pkg.representative.coin) help.append(node('p', defenderMessage.success ? 'muted' : 'error', defenderMessage.text));
      }
      card.append(help);
    }
    const footer = node('div', 'package-footer');
    const statusText = pkg.installed ? 'Bereit' : downloading ? `Download ${state.progress} %` : !pkg.selectable ? (pkg.representative?.unavailableReason || 'Noch nicht integriert') : state.status === 'FAILED' ? (state.detail || 'Installation fehlgeschlagen') : 'Nicht installiert';
    footer.append(node('span', `package-state ${pkg.installed ? 'ready' : ''}`, statusText));
    const actions = node('div', 'package-actions');
    if (pkg.installed) {
      for (const coin of pkg.coins) {
        const selected = coin.selectedMiner?.id === pkg.id;
        const open = node('button', `button ${selected ? 'subtle' : ''}`, selected ? (coin.ticker || coin.name) : `${coin.ticker || coin.name} auswählen`);
        open.type = 'button'; open.disabled = busy;
        open.title = selected ? `${coin.name} öffnen` : `${pkg.name} für ${coin.name} auswählen`;
        open.addEventListener('click', () => selected ? showView(coin.id)
          : action(`/api/agent/local/${coin.id}/miner`, `${pkg.name} als Miner für ${coin.name} ausgewählt.`, {minerId: pkg.id}));
        actions.append(open);
      }
    } else if (pkg.selectable) {
      const install = node('button', 'button primary', downloading ? 'Installiert …' : 'Installieren'); install.type = 'button';
      install.disabled = busy || downloading || !overviewFresh; install.addEventListener('click', () => installPackages([pkg])); actions.append(install);
    }
    footer.append(actions); card.append(footer);
    if (downloading) { const bar = node('progress', 'download-progress'); bar.max = 100; bar.value = state.progress; card.append(bar); }
    grid.append(card);
  }
  if (!grid.children.length) grid.append(node('p', 'muted', 'Für diesen Hardware-Filter ist keine Mining-Software verfügbar.'));
  syncBulkInstall(packages);
}
function renderInitialCatalog(coins) {
  renderInstalledRail(coins);
  renderCatalog(coins);
  const selected = coins.find(coin => coin.id === selectedView);
  // Only installation data is known; the full detail view needs fresh operational state.
  $('catalog-view').hidden = false; $('miner-view').hidden = true;
  if ($('notice').dataset.kind !== 'connection') {
    $('catalog-notice').textContent = `${selected ? `${selected.name}: ` : ''}${t('Betriebsdaten werden geladen …')}`;
    $('catalog-notice').hidden = false;
  }
}
async function loadInitialCatalog() {
  try {
    const response = await fetch('/api/agent/local/miner-catalog', { cache: 'no-store' });
    if (!response.ok) return; // The overview remains the fallback and owns connection errors.
    const coins = await response.json();
    if (!latest && Array.isArray(coins)) { initialCatalog = coins; renderInitialCatalog(coins); }
  } catch (_) { /* The overview reports connection failures. */ }
}
function renderWorkspace(data) {
  let onboarding = document.getElementById('onboarding');
  if (!onboarding) {
    onboarding = node('section', 'onboarding'); onboarding.id = 'onboarding';
    onboarding.innerHTML = '<div><p class="kicker">SCHNELLSTART</p><h2>Lokal starten – Node später hinzufügen</h2><p class="muted">Der PC-Agent funktioniert eigenständig. Ein SolarMiner Node ergänzt später Automatisierung, PV-Überschuss und weitere Homelab-Geräte.</p></div><ol class="onboarding-steps"></ol>';
    $('catalog-view').insertBefore(onboarding, $('catalog-notice'));
  }
  const steps = onboarding.querySelector('.onboarding-steps'); steps.replaceChildren();
  const configured = Boolean(data.moneroConfiguration || data.pearlConfiguration || gpuCoinIds.some(coin => data.gpuCoins?.[coin]?.configuration));
  const installedAny = (data.coins || []).some(coin => coin.binaryAvailable);
  onboarding.hidden = installedAny;
  const stepData = [
    [true, 'Hardware ansehen und einen CPU- oder GPU-Miner auswählen.'],
    [installedAny, installedAny ? 'Miner installiert – Pool und eigene Wallet einrichten.' : 'Passenden Miner herunterladen und installieren.'],
    [configured, configured ? 'Eigene Auszahlung ist konfiguriert.' : 'Eigene Wallet eintragen oder das SolarMiner-Standardziel bewusst bestätigen.'],
    [Boolean(nodeAssessment?.connected), nodeAssessment?.connected ? 'SolarMiner Node ist verbunden.' : 'Optional: SolarMiner Node verbinden für Regeln, PV-Überschuss und Automatisierung.']
  ];
  for (const [done, text] of stepData) steps.append(node('li', done ? 'done' : '', text));
  let assessment = document.getElementById('node-assessment');
  if (!assessment) {
    assessment = node('p', 'node-assessment muted'); assessment.id = 'node-assessment';
    onboarding.append(assessment);
  }
  const decision = nodeAssessment?.decision || 'UNKNOWN';
  const label = decision === 'PROFITABLE' ? 'Node: Mining ist aktuell wirtschaftlich freigegeben.'
    : decision === 'NOT_PROFITABLE' ? 'Node: Mining ist aktuell nicht wirtschaftlich freigegeben.'
      : 'Node: Noch keine Wirtschaftlichkeitsbewertung.';
  assessment.textContent = `${t(nodeAssessment?.connected ? 'SolarMiner Node verbunden' : 'SolarMiner Node nicht verbunden')} · ${t(label)}${nodeAssessment?.reason ? ` ${nodeAssessment.reason}` : ''}`;
  const installed = (data.coins || []).filter(c => c.binaryAvailable);
  if (selectedView !== 'catalog' && !installed.some(c => c.id === selectedView)) selectedView = 'catalog';
  $('catalog-view').hidden = selectedView !== 'catalog';
  $('miner-view').hidden = selectedView === 'catalog';
  renderInstalledRail(data.coins || [], data);
  renderCatalog(data.coins || [], data);
  if (pendingInstall) {
    const coin = data.coins?.find(c => c.id === pendingInstall);
    if (coin?.binaryAvailable) { const installedId = pendingInstall; pendingInstall = null; showView(installedId); }
    else if (['FAILED', 'UNSUPPORTED', 'BLOCKED_BY_ANTIVIRUS'].includes((gpuCoinIds.includes(pendingInstall) ? data.pearl : data[pendingInstall])?.downloadStatus)) pendingInstall = null;
  }
  if (selectedView !== 'catalog') {
    const coin = data.coins?.find(c => c.id === selectedView);
    $('setup-title').textContent = `${coin?.name || 'Miner'} einrichten`;
  }
}
async function installMiner(coin) {
  pendingInstall = coin;
  await action(`/api/agent/local/${coin}/download`, `${coin === 'monero' ? 'XMRig' : 'SRBMiner-MULTI'}-Download gestartet.`);
  const readiness = gpuCoinIds.includes(coin) ? latest?.pearl : latest?.[coin];
  if (['FAILED', 'UNSUPPORTED', 'BLOCKED_BY_ANTIVIRUS'].includes(readiness?.downloadStatus)) pendingInstall = null;
}
async function installPackages(packages) {
  if (busy || !overviewFresh || !packages.length) return;
  invalidateMiningViewCache(); busy = true;
  if (latest) render(latest, true);
  const failures = [];
  await Promise.all(packages.map(async pkg => {
    try {
      const option = pkg.representative;
      const response = await fetch(`/api/agent/local/${encodeURIComponent(option.coin)}/miners/${encodeURIComponent(pkg.id)}/download`, {method: 'POST'});
      if (!response.ok || await response.json() !== true) throw new Error(`HTTP ${response.status}`);
      selectedInstallers.delete(pkg.id);
    } catch (error) { failures.push(`${pkg.name}: ${error.message}`); }
  }));
  busy = false;
  $('catalog-notice').hidden = false;
  $('catalog-notice').classList.toggle('error', failures.length > 0);
  $('catalog-notice').textContent = failures.length
    ? `Nicht alle Installationen konnten gestartet werden. ${failures.join(' · ')}`
    : `${packages.length} Installation${packages.length === 1 ? '' : 'en'} gestartet. Du kannst den Fortschritt hier live verfolgen.`;
  invalidateMiningViewCache(); await refresh();
}
async function removeMiner(coin) {
  const name = coin === 'monero' ? 'XMRig' : 'SRBMiner-MULTI';
  if (!window.confirm(t(`${name} wird gestoppt und entfernt. Die gespeicherte Pool-/Wallet-Konfiguration bleibt erhalten. Fortfahren?`))) return;
  await action(`/api/agent/local/${coin}/remove`, `${name} wurde entfernt.`);
}
function coinBlockers(coin, data) {
  const blocked = [];
  const standalone = data.proxy?.mode === 'standalone';
  if (!data.proxy?.reachable) blocked.push('SolarMiner-Proxy nicht erreichbar');
  else if (standalone && data.proxy?.managedStatus !== 'running') blocked.push('Lokaler Proxy läuft nicht');
  const feeReady = coin.id === 'monero' ? data.proxy?.moneroFeeReady : coin.id === 'pearl' ? data.proxy?.pearlFeeReady
    : coin.id === 'ravencoin' ? data.proxy?.ravencoinFeeReady : data.proxy?.ethereumclassicFeeReady;
  if ((standalone || gpuCoinIds.includes(coin.id)) && !feeReady) blocked.push(`${coin.name}-Fee-Ziel nicht geladen`);
  if (!coin.configured) blocked.push('Pool-Konfiguration fehlt');
  if (!coin.binaryAvailable) blocked.push('Miner-Binary fehlt');
  return blocked;
}
function renderCoins(data) {
  const coins = data.coins || [], grid = $('coin-grid'); grid.replaceChildren();
  set('coins-summary', `${coins.filter(c => c.status === 'MINING').length} aktiv · ${coins.length} integriert`);
  for (const coin of coins) {
    const card = node('article', `coin-card${coin.status === 'MINING' || coin.id === 'pearl' && data.pearl?.running ? ' active' : ''}`);
    const top = node('div', 'coin-top');
    top.append(node('span', 'coin-emblem', coin.ticker || coin.id), node('span', 'tag', coin.status === 'MINING' || coin.id === 'pearl' && data.pearl?.running ? 'LÄUFT' : 'VERFÜGBAR'));
    const reasons = coinBlockers(coin, data);
    const coinStatus = coin.id === 'pearl' && data.pearl?.running && !data.pearl?.poolHealthy
      ? 'Verbinde mit Pool' : statusLabels[coin.status] || coin.status;
    card.append(top, node('h3', '', coin.name), node('div', 'coin-meta', `${coin.device} · ${coin.algorithm} · ${coinStatus}`),
      node('div', `coin-reason ${reasons.length ? 'blocked' : 'ready'}`, reasons.length ? reasons.join(' · ') : 'Bereit zum Starten'));
    grid.append(card);
  }
}
function renderGpuProcesses(data) {
  const gpuCoin = selectedView === 'pearl' || gpuCoinIds.includes(selectedView);
  const states = selectedView === 'pearl' ? data.pearl?.gpus || [] : data.gpuCoins?.[selectedView]?.gpus || [];
  $('gpu-processes-panel').hidden = !gpuCoin;
  $('console-gpu-label').hidden = !gpuCoin;
  set('gpu-processes-title', `${data.coins?.find(coin => coin.id === selectedView)?.name || 'GPU-Miner'} pro GPU`);
  const picker = $('console-gpu'), selected = picker.value;
  const keys = [selectedView, ...states.map(gpu => `${selectedView}-${gpu.vendor}-${gpu.index}`)];
  if ([...picker.options].map(option => option.value).join(',') !== keys.join(',')) {
    picker.replaceChildren();
    const all = node('option', '', 'Alle GPUs'); all.value = selectedView; picker.append(all);
    for (const gpu of states) {
      const option = node('option', '', `${gpu.vendor} ${gpu.index} · ${gpu.model}`);
      option.value = `${selectedView}-${gpu.vendor}-${gpu.index}`; picker.append(option);
    }
    picker.value = keys.includes(selected) ? selected : selectedView;
  }
  const list = $('gpu-processes'); list.replaceChildren();
  if (!states.length) { list.append(node('p', 'muted', 'Keine GPU erkannt.')); return; }
  const selectedCoin = data.coins?.find(coin => coin.id === selectedView);
  for (const gpu of states) {
    const card = node('article', `gpu-process${gpu.running ? ' running' : ''}`);
    const title = node('strong', '', `${gpu.vendor} ${gpu.index} · ${gpu.model}`);
    const state = node('span', `tag ${gpu.status === 'ERROR' ? 'blocked' : gpu.poolHealthy ? 'ready' : ''}`,
      !gpu.selected ? 'Nicht ausgewählt' : gpu.manuallyPaused ? 'Manuell pausiert'
        : gpu.running && !gpu.poolHealthy ? 'Verbinde mit Pool' : statusLabels[gpu.status] || gpu.status);
    const header = node('div', 'gpu-process-head'); header.append(title, state);
    const detail = node('p', 'muted', gpu.lastError || gpu.connectionDetail ||
      (gpu.running ? gpu.poolHealthy ? 'Pool verbunden' : 'Verbinde mit Pool' : 'Noch nicht gestartet'));
    const controls = node('div', 'gpu-process-actions');
    const start = node('button', 'button subtle', 'GPU starten'); start.type = 'button';
    start.disabled = busy || !gpu.selected || gpu.running || !selectedCoin || coinBlockers(selectedCoin, data).length > 0;
    start.addEventListener('click', () => action(`/api/agent/local/${selectedView}/gpus/${gpu.vendor}/${gpu.index}/resume`, `${gpu.vendor} ${gpu.index} gestartet.`));
    const stop = node('button', 'button subtle', 'GPU pausieren'); stop.type = 'button';
    stop.disabled = busy || !gpu.running;
    stop.addEventListener('click', () => action(`/api/agent/local/${selectedView}/gpus/${gpu.vendor}/${gpu.index}/pause`, `${gpu.vendor} ${gpu.index} pausiert.`));
    controls.append(start, stop); card.append(header, detail, controls); list.append(card);
  }
}
function renderProxy(proxy) {
  const standalone = proxy?.mode === 'standalone';
  document.body.classList.toggle('standalone', standalone);
  $('proxy-panel').hidden = standalone;
  $('api-docs').hidden = standalone;
  $('monero-help').textContent = standalone
    ? 'Pool-Adresse und Wallet werden für den Mining-Start verwendet.'
    : 'Der Miner verbindet sich über den SolarMiner-Proxy; die Pool-Adresse wird dort als Ziel verwendet.';
  if (standalone) return;
  set('proxy-mode', 'EXTERN');
  set('proxy-description', 'Der Agent nutzt einen SolarMiner-Proxy in deinem Netzwerk.');
  set('proxy-state', proxy?.reachable ? 'Erreichbar' : 'Nicht erreichbar');
  set('proxy-xmr', proxy?.moneroUrl || '—'); set('proxy-pearl', proxy?.pearlUrl || '—');
  set('proxy-ravencoin', proxy?.ravencoinUrl || '—');
  set('proxy-ethereumclassic', proxy?.ethereumclassicUrl || '—');
  set('fee-monero', proxy?.moneroFeeReady ? 'Verfügbar' : 'Nicht verfügbar');
  set('fee-pearl', proxy?.pearlFeeReady ? 'Verfügbar' : 'Nicht verfügbar');
  set('fee-ravencoin', proxy?.ravencoinFeeReady ? 'Verfügbar' : 'Nicht verfügbar');
  set('fee-ethereumclassic', proxy?.ethereumclassicFeeReady ? 'Verfügbar' : 'Nicht verfügbar');
  set('proxy-detail', '');
  $('proxy-form').hidden = false;
  $('proxy-discovery').hidden = false;
  if (document.activeElement !== $('proxy-host')) $('proxy-host').value = proxy?.host || '';
  $('proxy-submit').disabled = busy;
}
function renderFees(data) {
  let section = $('fee-panel');
  if (!section) {
    section = node('section', 'section panel'); section.id = 'fee-panel';
    section.innerHTML = '<div class="section-head"><div><p class="kicker">TRANSPARENTE GEBÜHREN</p><h2>Wo dein Mining-Ertrag hingeht</h2></div><span id="fee-referral-state" class="tag">—</span></div><p class="muted">Alle bekannten Abzüge werden live aufgeschlüsselt. SolarMiner nutzt eine verpflichtende Fee-Route; ist sie nicht erreichbar, startet der betreffende Miner nicht. Pool- und Miner-Gebühren sind zusätzlich ausgewiesen.</p><div id="fee-breakdown"></div><form id="referral-form" class="form-block"><label for="referral-key">Referral-Key</label><div class="input-row"><input id="referral-key" maxlength="64" pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,63}" autocomplete="off"><button class="button subtle" type="submit">Lokal speichern</button></div><p class="muted">Der SolarMiner Node setzt seinen Referral-Key automatisch erneut, sobald er den Agenten steuert.</p></form>';
    $('miner-setup-pane').append(section);
    $('referral-form').addEventListener('submit', event => { event.preventDefault(); if ($('referral-form').reportValidity()) action('/api/agent/local/referral', 'Referral-Key lokal gespeichert. Der Node kann ihn wieder überschreiben.', {key: $('referral-key').value.trim()}); });
  }
  const root = $('fee-breakdown'); root.replaceChildren();
  const fee = (data.fees || []).find(item => item.coin === selectedView) || (data.fees || [])[0];
  set('fee-referral-state', data.referral?.key ? `KEY · ${data.referral.key}` : 'STANDARD · KEIN REFERRER');
  if (document.activeElement !== $('referral-key')) $('referral-key').value = data.referral?.key || '';
  if (!fee) { root.append(node('p', 'muted', 'Gebührenmodell wird geladen …')); return; }
  const knownTotal = fee.parts.filter(part => part.known).reduce((sum, part) => sum + (part.percentage || 0), 0);
  const feeCoin = data.coins?.find(item => item.id === fee.coin);
  const card = node('article', 'fee-card'); card.append(node('strong', '', `${feeCoin?.name || fee.coin} · ${fee.coin === 'monero' ? 'XMRig' : 'SRBMiner-MULTI'} · ${i18n.language === 'en' ? 'known deductions' : 'bekannte Abzüge'} ${fmt(knownTotal, 2)} %`));
  const bar = node('div', 'fee-bar');
  for (const part of fee.parts.filter(part => part.known && part.percentage > 0)) { const segment = node('span', `fee-segment fee-${part.kind.toLowerCase()}`); segment.style.flexGrow = String(part.percentage); segment.title = `${part.label}: ${fmt(part.percentage, 2)} %`; bar.append(segment); }
  const user = node('span', 'fee-segment fee-user'); user.style.flexGrow = String(Math.max(0, 100 - knownTotal)); bar.append(user); card.append(bar);
  for (const part of fee.parts) { const row = node('div', 'detail'); row.append(node('span', '', part.label), node('strong', '', part.known ? `${fmt(part.percentage, 2)} %` : 'unbekannt')); card.append(row, node('small', 'muted', part.source)); }
  const remaining = node('div', 'detail'); remaining.append(node('span', '', 'Voraussichtlich für dich'), node('strong', '', `${fmt(Math.max(0, 100-knownTotal), 2)} %`)); card.append(remaining); root.append(card);
}
function render(data, fresh = overviewFresh) {
  for (const [control, disabled] of cachedControls) control.disabled = disabled;
  cachedControls.clear();
  overviewFresh = fresh;
  latest = data;
  hydrateConfiguration('monero-form', data.moneroConfiguration, {poolUrl: 'monero-pool', wallet: 'monero-wallet', worker: 'monero-worker'});
  hydrateConfiguration('pearl-form', data.pearlConfiguration, {poolUrl: 'pearl-pool', wallet: 'pearl-wallet', worker: 'pearl-worker'});
  for (const coin of gpuCoinIds) renderGpuCoinSelection(coin, data);
  renderPayoutState(data);
  renderGpuSelection(data);
  renderWorkspace(data);
  renderCoins(data); renderProxy(data.proxy); renderGpuProcesses(data); renderFees(data); syncConsoleView();
  const coin = data.coins?.find(c => c.id === selectedView);
  if (coin?.id === 'monero' && Date.now() - lastOptimizationLoad > 5000) loadRandomXOptimization();
  renderOptimizationChecklist(coin, data);
  const workers = (data.stats?.workers || []).filter(worker => worker.currentAlgorithm ===
    (selectedView === 'pearl' ? 'PearlHash' : selectedView === 'ravencoin' ? 'kawpow' : selectedView === 'ethereumclassic' ? 'etchash' : 'RandomX'));
  const activeShareScope = workers.filter(worker => worker.miningStatus === 'MINING');
  const shareWorkers = activeShareScope.filter(worker => worker.acceptedShares != null || worker.rejectedShares != null);
  const acceptedWorkers = shareWorkers.filter(worker => worker.acceptedShares != null);
  const rejectedWorkers = shareWorkers.filter(worker => worker.rejectedShares != null);
  set('detail-shares', shareWorkers.length
    ? `${acceptedWorkers.length === activeShareScope.length ? fmt(acceptedWorkers.reduce((sum, worker) => sum + Number(worker.acceptedShares), 0), 0) : '—'} akzeptiert · ${rejectedWorkers.length === activeShareScope.length ? fmt(rejectedWorkers.reduce((sum, worker) => sum + Number(worker.rejectedShares), 0), 0) : '—'} abgelehnt`
    : 'Nicht von der Miner-API gemeldet');
  const status = coin?.status || 'STOPPED';
  const pearlWaiting = selectedView === 'pearl' && data.pearl?.running && !data.pearl?.poolHealthy;
  set('status', pearlWaiting ? 'Verbinde mit Pool' : statusLabels[status] || status); set('coin-label', coin ? `${coin.name} · ${coin.device}` : '—');
  const currentGpuStates = selectedView === 'pearl' ? data.pearl?.gpus || [] : data.gpuCoins?.[selectedView]?.gpus || [];
  const gpuRunning = currentGpuStates.filter(gpu => gpu.running).length;
  const gpuSelected = currentGpuStates.filter(gpu => gpu.selected).length;
  const cpuRunning = data.coins?.find(c => c.id === 'monero')?.status === 'MINING';
  set('view-note', `CPU: ${cpuRunning ? 'läuft' : 'pausiert'} · GPUs: ${gpuRunning}/${gpuSelected} gestartet. Beide können parallel laufen.`);
  set('miner-details-title', coin?.name || 'Miner');
  set('miner-view-title', coin?.name || 'Mining');
  set('miner-emblem', coin?.ticker || (coin?.id === 'monero' ? 'XMR' : '—'));
  $('miner-view').dataset.coin = coin?.id || '';
  window.dispatchEvent(new CustomEvent('mining-rendered', {detail: data}));
  set('detail-algorithm', coin?.algorithm || '—');
  set('detail-hardware', coin?.device === 'GPU' ? (currentGpuStates.filter(gpu => gpu.selected)
    .map(gpu => gpu.model || `${gpu.vendor} ${gpu.index}`).join(', ') || 'Keine GPU ausgewählt') : 'CPU');
  set('detail-installation', coin?.binaryAvailable ? 'Installiert' : 'Nicht installiert');
  set('detail-configuration', coin?.configured
    ? (payoutDefault(selectedView)?.inUse ? 'Pool aktiv · Auszahlung an SolarMiner-Standard' : 'Pool und Wallet konfiguriert')
    : 'Pool und Wallet fehlen');
  set('detail-connection', gpuCoinIds.includes(selectedView) && (data.gpuCoins?.[selectedView]?.running || status === 'ERROR')
    ? (data.gpuCoins?.[selectedView]?.minerError || currentGpuStates.find(gpu => gpu.running)?.connectionDetail || 'Verbindung wird geprüft')
    : selectedView === 'pearl' && (data.pearl?.running || status === 'ERROR')
    ? (data.pearl?.minerError || data.pearl?.connectionDetail || (data.pearl?.poolHealthy ? 'Pool verbunden' : 'Verbindung wird geprüft'))
    : selectedView === 'monero' && status === 'ERROR' && data.monero?.minerError
      ? data.monero.minerError
      : data.proxy?.reachable ? (data.proxy?.mode === 'standalone' ? 'Lokaler Mining-Dienst bereit' : 'SolarMiner-Proxy erreichbar') : 'Nicht erreichbar');
  const hashValue = workers.reduce((sum, worker) => sum + (worker.terahashPerSecond || 0), 0);
  const hashrate = hashValue > 0;
  set('hashrate', hashrate ? fmtHashrate(hashValue * 1e12) : '—');
  set('hashrate-note', hashrate ? 'Aktueller Miner-Wert' : 'Keine Hashrate gemeldet');
  const earnings = data.earnings?.find(value => value.coin === selectedView);
  set('earnings', earnings?.available ? `${fmt(earnings.coinsPerDay, 6)} ${earnings.ticker}` : '—');
  set('earnings-note', earnings?.available
    ? `≈ ${fmt(earnings.usdPerDay, 2)} USD · brutto${earnings.stale ? ' · Daten veraltet' : ''}`
    : earnings?.unavailableReason || 'Netzwerkdaten werden geladen');
  const powerTarget = workers.reduce((sum, worker) => sum + (worker.powerTargetWatts || 0), 0);
  const maximum = workers.reduce((sum, worker) => sum + (worker.maxPowerTarget || 0), 0);
  set('power-target', powerTarget > 0 ? `${fmt(powerTarget, 0)} W` : '—');
  set('power-range', maximum > 0 ? `Maximal ${fmt(maximum, 0)} W`
    : powerTarget > 0 ? 'Leistungsobergrenze nicht gemeldet' : 'Noch kein Leistungsziel');
  const remainingGpus = currentGpuStates.some(gpu => gpu.selected && !gpu.running);
  $('resume').disabled = busy || !coin || coinBlockers(coin, data).length > 0 || (coin.device === 'GPU' ? !remainingGpus : status === 'MINING');
  $('pause').disabled = busy || (coin?.device === 'GPU' ? !currentGpuStates.some(gpu => gpu.running) : status !== 'MINING');
  const selectedMiner = data.coins?.find(item => item.id === selectedView);
  $('remove-current-miner').hidden = !selectedMiner?.binaryAvailable;
  $('remove-current-miner').disabled = busy || (gpuCoinIds.includes(selectedView) ? data.pearl : data[selectedView])?.downloadStatus === 'DOWNLOADING';
  const pearl = selectedView === 'pearl';
  $('monero-form').hidden = selectedView !== 'monero'; $('pearl-form').hidden = !pearl;
  for (const gpuCoin of gpuCoinIds) $(`${gpuCoin}-form`).hidden = selectedView !== gpuCoin;
  if (pearl) {
    set('pearl-readiness', data.pearl?.minerError || 'SRBMiner nutzt den SolarMiner-Proxy.');
    $('pearl-download').hidden = data.pearl?.binaryAvailable || data.pearl?.downloadStatus === 'DOWNLOADING';
    $('pearl-download').disabled = busy;
  }
  const gpus = (data.gpus || []).filter(gpu => currentGpuStates.some(state => state.selected && state.vendor === gpu.vendor && state.index === gpu.index));
  const min = gpus.reduce((s,g) => s + g.minWatts, 0), max = gpus.reduce((s,g) => s + g.maxWatts, 0);
  $('power-form').hidden = coin?.device !== 'GPU' || !gpus.length;
  $('power-input').min = String(min); $('power-input').max = String(max);
  if (document.activeElement !== $('power-input')) $('power-input').value = powerTarget || Math.round((min + max) / 2);
  set('power-limits', `${fmt(min, 0)}–${fmt(max, 0)} W`);
  $('power-submit').disabled = busy || coin?.device !== 'GPU'; $('monero-submit').disabled = busy;
  $('connection').className = 'badge online'; set('connection', 'Agent verbunden');
  set('updated', `Aktualisiert ${new Date().toLocaleTimeString(i18n.locale)}`);
  $('mining-refresh-state').hidden = fresh;
  if (!fresh) {
    $('connection').className = 'badge'; set('connection', 'Aktualisiere …');
    set('updated', `Letzter Stand ${new Date(overviewSavedAt).toLocaleTimeString(i18n.locale)}`);
    set('mining-refresh-state', 'Letzter bekannter Stand · Aktuelle Daten werden geprüft. Aktionen sind danach verfügbar.');
    for (const control of document.querySelectorAll('#miner-view button:not([data-view]), #miner-view input, #miner-view select, #catalog-grid button')) {
      cachedControls.set(control, control.disabled); control.disabled = true;
    }
  }
}
async function refresh() {
  if (busy || document.hidden) return;
  if (refreshing) { refreshQueued = true; return; }
  refreshing = true;
  const revision = stateRevision;
  try {
    const response = await fetch('/api/agent/local/overview', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const overview = await response.json();
    if (revision !== stateRevision) { refreshQueued = true; return; }
    applyOverview(overview);
  } catch (error) {
    if (revision !== stateRevision) { refreshQueued = true; return; }
    overviewFresh = false;
    if (latest) render(latest, false);
    $('connection').className = 'badge offline'; set('connection', 'Agent nicht erreichbar');
    $('resume').disabled = true; $('pause').disabled = true;
    $('notice').dataset.kind = 'connection'; notice(`Daten konnten nicht geladen werden: ${error.message}`, true);
  } finally {
    refreshing = false;
    if (refreshQueued && !busy && !document.hidden) { refreshQueued = false; queueMicrotask(refresh); }
  }
}
function applyOverview(overview) {
  overviewSavedAt = Date.now();
  render(overview, true);
  window.SolarMinerMiningCache?.write(overview);
  if (initialCatalog) { initialCatalog = null; $('catalog-notice').hidden = true; }
  refreshAssessment();
  if ($('notice').dataset.kind === 'connection') { $('notice').hidden = true; $('catalog-notice').hidden = true; }
  // Pull immediately after a state change as well as through the console tailer.
  pollConsole();
}
function connectEvents() {
  if (eventStream || document.hidden) return;
  const stream = new EventSource('/api/agent/local/events');
  eventStream = stream;
  stream.addEventListener('overview', event => {
    if (stream !== eventStream || busy) return;
    try { applyOverview(JSON.parse(event.data)); }
    catch (_) { /* A reconnect or manual refresh recovers from malformed transport data. */ }
  });
  stream.onerror = () => {
    if (stream !== eventStream) return;
    overviewFresh = false;
    if (latest) render(latest, false);
    $('connection').className = 'badge offline'; set('connection', 'Live-Verbindung wird wiederhergestellt …');
  };
}
function disconnectEvents() {
  if (!eventStream) return;
  eventStream.close(); eventStream = null;
}
async function refreshAssessment() {
  if (assessing) return;
  assessing = true;
  try {
    const response = await fetch('/api/agent/local/node-assessment', { cache: 'no-store', signal: AbortSignal.timeout(5000) });
    if (response.ok) { nodeAssessment = await response.json(); if (latest && !busy) renderWorkspace(latest); }
  } catch (_) { /* Optional Node assessment must not block or mark the local agent offline. */ }
  finally { assessing = false; }
}
async function action(path, success, params, body, feedbackId = null) {
  if (!overviewFresh) return notice('Warte auf aktuelle Agent-Daten, bevor du eine Änderung ausführst.', true);
  if (busy) return;
  invalidateMiningViewCache();
  busy = true; $('resume').disabled = true; $('pause').disabled = true;
  try {
    const url = new URL(path, location.origin);
    for (const [key, value] of Object.entries(params || {})) url.searchParams.set(key, String(value));
    const response = await fetch(url, { method: 'POST', headers: body ? { 'Content-Type': 'application/json' } : {}, body: body ? JSON.stringify(body) : undefined });
    if (!response.ok) {
      const errorBody = await response.json().catch(() => null);
      throw new Error(errorBody?.message || `HTTP ${response.status}`);
    }
    if (await response.json() !== true) throw new Error('Der Agent hat die Konfiguration abgelehnt');
    $('notice').dataset.kind = 'action'; notice(success);
    if (feedbackId) { const feedback = $(feedbackId); feedback.classList.remove('error'); feedback.hidden = false; feedback.textContent = t(success); }
  } catch (error) {
    $('notice').dataset.kind = 'action';
    const message = error.message.startsWith('HTTP ') ? `${error.message}. Bitte Agent-Logs prüfen.` : error.message;
    notice(message, true);
    if (feedbackId) { const feedback = $(feedbackId); feedback.classList.add('error'); feedback.hidden = false; feedback.textContent = t(message); }
  }
  finally { invalidateMiningViewCache(); busy = false; await refresh(); }
}
initGpuCoinForms();
// Keep the daily controls short: status, setup and diagnostics are separate task views.
$('miner-diagnostics-pane').prepend($('optimization-panel'));
document.querySelectorAll('.miner-subnav [data-pane]').forEach(button => button.addEventListener('click', () => {
  document.querySelectorAll('.miner-subnav [data-pane]').forEach(item => item.classList.toggle('selected', item === button));
  document.querySelectorAll('[data-pane-content]').forEach(pane => { pane.hidden = pane.dataset.paneContent !== button.dataset.pane; });
}));
$('refresh').addEventListener('click', refresh);
window.addEventListener('hashchange', () => {
  const view = viewIds.includes(location.hash.slice(1)) ? location.hash.slice(1) : 'catalog';
  if (view !== selectedView) { selectedView = view; if (latest) render(latest); else if (initialCatalog) renderInitialCatalog(initialCatalog); }
});
$('console-bottom').addEventListener('click', () => { $('miner-console').scrollTop = $('miner-console').scrollHeight; });
$('console-gpu').addEventListener('change', syncConsoleView);
['monero-form', 'pearl-form'].forEach(id => $(id).addEventListener('input', () => { $(id).dataset.dirty = 'true'; }));
viewIds.forEach(coin => {
  $(`${coin}-pool-select`).addEventListener('change', () => updatePoolChoice(coin));
  for (const option of $(`${coin}-pool-select`).options) {
    if (option.value.includes('kryptex.network') && !option.dataset.feeLabel) {
      option.dataset.feeLabel = 'true';
      option.textContent += i18n.language === 'en' ? ' · 1% pool fee' : ' · 1 % Poolgebühr';
    }
  }
  updatePoolChoice(coin);
});
$('add-miner').addEventListener('click', () => showView('catalog'));
$('select-installable').addEventListener('click', () => {
  const packages = catalogPackages(latest?.coins || initialCatalog || []).filter(pkg => !pkg.installed && pkg.selectable);
  const selectAll = packages.some(pkg => !selectedInstallers.has(pkg.id));
  selectedInstallers.clear(); if (selectAll) packages.forEach(pkg => selectedInstallers.add(pkg.id));
  if (latest) renderWorkspace(latest); else if (initialCatalog) renderInitialCatalog(initialCatalog);
});
$('install-selected').addEventListener('click', () => {
  const packages = catalogPackages(latest?.coins || initialCatalog || []).filter(pkg => selectedInstallers.has(pkg.id) && !pkg.installed && pkg.selectable);
  installPackages(packages);
});
document.querySelectorAll('.filter-button').forEach(button => button.addEventListener('click', () => {
  deviceFilter = button.dataset.device;
  document.querySelectorAll('.filter-button').forEach(item => item.classList.toggle('selected', item === button));
  if (latest) renderWorkspace(latest);
  else if (initialCatalog) renderInitialCatalog(initialCatalog);
}));
$('resume').addEventListener('click', () => { if (viewIds.includes(selectedView)) action(`/api/agent/local/miners/${selectedView}/resume`, 'Miner gestartet.'); });
$('pause').addEventListener('click', () => { if (viewIds.includes(selectedView)) action(`/api/agent/local/miners/${selectedView}/pause`, 'Miner pausiert.'); });
$('remove-current-miner').addEventListener('click', () => { if (viewIds.includes(selectedView)) removeMiner(selectedView); });
$('pearl-download').addEventListener('click', () => action('/api/agent/local/pearl/download', 'SRBMiner-Download gestartet.'));
$('proxy-form').addEventListener('submit', event => { event.preventDefault(); if ($('proxy-form').reportValidity()) action('/api/agent/local/proxy', 'Proxy gespeichert.', { host: $('proxy-host').value.trim() }); });
$('proxy-discover').addEventListener('click', async () => {
  const button = $('proxy-discover'), status = $('discovery-status'), results = $('proxy-candidates');
  button.disabled = true; button.textContent = 'Suche im lokalen Netzwerk …'; status.textContent = ''; results.replaceChildren();
  try {
    const response = await fetch('/api/agent/local/proxy/discover', { method: 'POST' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const candidates = await response.json();
    if (!candidates.length) {
      status.textContent = 'Kein kompatibler Proxy gefunden. Prüfe, ob beide Geräte im selben LAN sind und UDP 8091 sowie API-Port 8090 erreichbar sind.';
    } else {
      status.textContent = `${candidates.length} Proxy${candidates.length === 1 ? '' : 's'} gefunden`;
      for (const candidate of candidates) {
        const choose = node('button', 'candidate button subtle',
          `${candidate.host} · API ${candidate.apiPort} · Monero ${candidate.moneroPort}${candidate.pearlPort ? ` · Pearl ${candidate.pearlPort}` : ''} — Verbinden`);
        choose.type = 'button';
        choose.addEventListener('click', () => action('/api/agent/local/proxy',
          `Proxy ${candidate.host} gespeichert. Der Miner wurde vorsorglich pausiert.`, { host: candidate.host }));
        results.append(choose);
      }
    }
  } catch (error) {
    status.textContent = `Netzwerksuche fehlgeschlagen: ${error.message}`;
  } finally {
    button.disabled = false; button.textContent = 'Erneut im Netzwerk suchen';
  }
});
$('monero-form').addEventListener('submit', event => { event.preventDefault(); if (!$('monero-form').reportValidity()) return; if (!latest?.proxy?.moneroUrl) return notice('SolarMiner-Proxy für Monero fehlt. Verbinde zuerst den Proxy.', true); if (!payoutAvailable('monero')) return; const standard = usesStandardPayout('monero'); action('/api/agent/local/monero/configuration', standard ? 'Monero gespeichert: Auszahlung an das SolarMiner-Standardziel.' : 'Monero-Konfiguration gespeichert.', null, { poolUrl: standard ? '' : selectedPool('monero'), wallet: standard ? '' : $('monero-wallet').value.trim(), worker: $('monero-worker').value.trim() }, 'monero-save-feedback'); });
$('pearl-form').addEventListener('submit', event => { event.preventDefault(); if (!$('pearl-form').reportValidity()) return; const indices = [...selectedGpuIndices].filter(index => latest?.gpus?.some(gpu => gpuKey(gpu) === index)).sort(); if (!indices.length) return notice('Wähle mindestens eine erkannte GPU.', true); if (!latest?.proxy?.pearlUrl) return notice('SolarMiner-Proxy für Pearl fehlt. Verbinde zuerst den Proxy.', true); if (!payoutAvailable('pearl')) return; const standard = usesStandardPayout('pearl'); action('/api/agent/local/pearl/configuration', standard ? 'Pearl gespeichert: Auszahlung an das SolarMiner-Standardziel.' : 'Pearl-Konfiguration und GPU-Auswahl gespeichert.', null, { poolUrl: standard ? '' : selectedPool('pearl'), proxyUrl: latest.proxy.pearlUrl, wallet: standard ? '' : $('pearl-wallet').value.trim(), worker: $('pearl-worker').value.trim(), devices: indices.join(',') }, 'pearl-save-feedback'); });
$('power-form').addEventListener('submit', event => { event.preventDefault(); if ($('power-form').reportValidity() && ['pearl', ...gpuCoinIds].includes(selectedView)) action(`/api/agent/local/miners/${selectedView}/power-target`, 'GPU-Leistungsziel übernommen.', { powerTarget: $('power-input').value }); });
const cachedOverview = window.SolarMinerMiningCache?.read();
if (cachedOverview) { overviewSavedAt = cachedOverview.savedAt; render(cachedOverview.overview, false); }
loadInitialCatalog(); refresh(); connectEvents();
document.addEventListener('visibilitychange', () => {
  if (document.hidden) disconnectEvents();
  else { refresh(); pollConsole(); connectEvents(); }
});
