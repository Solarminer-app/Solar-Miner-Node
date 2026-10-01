const $ = id => document.getElementById(id);
const i18n = window.SolarMinerI18n;
const t = i18n.t;
const statusLabels = { MINING: 'Mining aktiv', PAUSED: 'Pausiert', STOPPED: 'Gestoppt', ERROR: 'Fehler' };
let latest = null, busy = false, deviceFilter = 'all';
let selectedView = ['monero', 'pearl'].includes(location.hash.slice(1)) ? location.hash.slice(1) : 'catalog';
let pendingInstall = null;
let selectedGpuIndices = new Set(), gpuSelectionDirty = false, gpuSelectionVersion = null;
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
    const response = await fetch('/api/agent/optimizations/randomx', { cache: 'no-store' });
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
    const response = await fetch(`/api/agent/optimizations/randomx/${tip.id}`, {
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
  for (const coin of ['monero', 'pearl']) {
    const select = $(`${coin}-pool-select`);
    const configured = coin === 'monero' ? data.moneroConfiguration : data.pearlConfiguration;
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
  notice(`Kein SolarMiner-Standard-Auszahlungsziel für ${coin === 'pearl' ? 'Pearl' : 'Monero'} erreichbar. Bitte eigene Wallet angeben.`, true);
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
  set('pearl-devices-help', indices.length ? 'Wähle mindestens eine GPU.' : 'Keine unterstützte GPU erkannt.');
  $('pearl-submit').disabled = busy || !indices.some(index => selectedGpuIndices.has(index));
}
function showView(view) {
  selectedView = view; location.hash = view === 'catalog' ? '' : view;
  if (latest) render(latest);
}
function syncConsoleView() {
  const coin = selectedView === 'pearl' ? $('console-gpu').value || 'pearl'
    : selectedView === 'monero' ? 'monero' : null;
  if ((consoleState?.coin ?? null) === coin) return;
  consoleState = coin ? { coin, offset: 0, decoder: new TextDecoder(), loading: false } : null;
  $('miner-console').replaceChildren();
  $('console-download').hidden = true;
  if (!coin) return;
  set('miner-console-title', coin === 'monero' ? 'XMRig-Konsole' : coin === 'pearl' ? 'SRBMiner-Konsole · alle GPUs' : `SRBMiner-Konsole · ${coin.replace('pearl-', '').replace('-', ' ')}`);
  set('console-status', 'Lade vollständige Ausgabe …');
  $('console-download').href = `/api/agent/console/${coin}/download`;
  pollConsole();
}
async function pollConsole() {
  const state = consoleState;
  if (!state || state.loading || document.hidden) return;
  state.loading = true;
  let more = false;
  try {
    const response = await fetch(`/api/agent/console/${state.coin}?offset=${state.offset}`, { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const chunk = await response.json();
    if (consoleState !== state) return;
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
    if (more && consoleState === state) setTimeout(pollConsole, 0);
  }
}
function renderWorkspace(data) {
  let onboarding = document.getElementById('onboarding');
  if (!onboarding) {
    onboarding = node('section', 'onboarding'); onboarding.id = 'onboarding';
    onboarding.innerHTML = '<div><p class="kicker">SCHNELLSTART</p><h2>Lokal starten – Node später hinzufügen</h2><p class="muted">Der PC-Agent funktioniert eigenständig. Ein SolarMiner Node ergänzt später Automatisierung, PV-Überschuss und weitere Homelab-Geräte.</p></div><ol class="onboarding-steps"></ol>';
    $('catalog-view').insertBefore(onboarding, $('catalog-notice'));
  }
  const steps = onboarding.querySelector('.onboarding-steps'); steps.replaceChildren();
  const configured = Boolean(data.moneroConfiguration || data.pearlConfiguration);
  const installedAny = (data.coins || []).some(coin => coin.binaryAvailable);
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
  const rail = $('installed-rail'); rail.replaceChildren();
  for (const coin of installed) {
    const running = coin.id === 'pearl' ? data.pearl?.running : coin.status === 'MINING';
    const button = node('button', `rail-coin ${selectedView === coin.id ? 'selected' : ''} ${running ? 'running' : ''}`, coin.id === 'monero' ? 'ɱ' : '◈');
    button.type = 'button'; button.title = `${coin.name} öffnen${running ? ' · läuft' : ''}`; button.setAttribute('aria-label', `${coin.name} öffnen${running ? ', läuft' : ''}`);
    button.addEventListener('click', () => showView(coin.id)); rail.append(button);
  }
  const grid = $('catalog-grid'); grid.replaceChildren();
  for (const coin of data.coins || []) {
    if (deviceFilter !== 'all' && coin.device !== deviceFilter) continue;
    const card = node('article', 'catalog-card');
    const icon = node('span', `coin-emblem ${coin.id}`, coin.id === 'monero' ? 'ɱ' : '◈');
    const badge = node('span', 'tag', coin.experimental ? 'EXPERIMENTELL' : coin.device);
    const header = node('div', 'coin-top'); header.append(icon, badge);
    const title = node('h2', '', coin.name); const meta = node('p', 'muted', `${coin.algorithm} · ${coin.device} Mining · ${coin.id === 'monero' ? 'XMRig' : 'SRBMiner-MULTI'}`);
    const readiness = data[coin.id] || {}; const status = readiness.downloadStatus || 'PENDING';
    const state = node('p', 'catalog-state', coin.binaryAvailable ? `${coin.id === 'pearl' && data.pearl?.running ? 'Läuft auf GPU(s)' : coin.status === 'MINING' ? 'Mining aktiv' : 'Installiert'}` : status === 'DOWNLOADING' ? `Download ${readiness.downloadProgress || 0} %` : status === 'FAILED' || status === 'UNSUPPORTED' ? readiness.downloadDetail || 'Installation fehlgeschlagen' : 'Noch nicht installiert');
    card.append(header, title, meta, state);
    if (status === 'DOWNLOADING') {
      const bar = node('progress', 'download-progress'); bar.max = 100; bar.value = readiness.downloadProgress || 0;
      bar.setAttribute('aria-label', `${coin.name} Downloadfortschritt`); card.append(bar);
    }
    const button = node('button', `button ${coin.binaryAvailable ? '' : 'primary'}`, coin.binaryAvailable ? 'Miner öffnen' : status === 'DOWNLOADING' ? 'Wird installiert …' : status === 'FAILED' ? 'Erneut versuchen' : 'Herunterladen & installieren');
    button.type = 'button'; button.disabled = busy || status === 'DOWNLOADING';
    button.addEventListener('click', () => {
      if (coin.binaryAvailable) showView(coin.id);
      else installMiner(coin.id);
    }); card.append(button); grid.append(card);
  }
  if (!grid.children.length) grid.append(node('p', 'muted', 'Für diesen Hardware-Filter ist kein Miner verfügbar.'));
  if (pendingInstall) {
    const coin = data.coins?.find(c => c.id === pendingInstall);
    if (coin?.binaryAvailable) { const installedId = pendingInstall; pendingInstall = null; showView(installedId); }
    else if (['FAILED', 'UNSUPPORTED'].includes(data[pendingInstall]?.downloadStatus)) pendingInstall = null;
  }
  if (selectedView !== 'catalog') {
    const coin = data.coins?.find(c => c.id === selectedView);
    $('setup-title').textContent = `${coin?.name || 'Miner'} einrichten`;
  }
}
async function installMiner(coin) {
  pendingInstall = coin;
  await action(`/api/agent/${coin}/download`, `${coin === 'monero' ? 'XMRig' : 'SRBMiner-MULTI'}-Download gestartet.`);
  const readiness = latest?.[coin];
  if (readiness?.downloadStatus === 'FAILED' || readiness?.downloadStatus === 'UNSUPPORTED') pendingInstall = null;
}
function coinBlockers(coin, data) {
  const blocked = [];
  const standalone = data.proxy?.mode === 'standalone';
  if (!data.proxy?.reachable) blocked.push('SolarMiner-Proxy nicht erreichbar');
  else if (standalone && data.proxy?.managedStatus !== 'running') blocked.push('Lokaler Proxy läuft nicht');
  const feeReady = coin.id === 'monero' ? data.proxy?.moneroFeeReady : data.proxy?.pearlFeeReady;
  if (standalone && !feeReady) blocked.push(coin.id === 'pearl' ? 'Pearl-Fee-Ziel nicht geladen' : 'Monero-Fee-Ziel nicht geladen');
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
  const pearl = selectedView === 'pearl', states = data.pearl?.gpus || [];
  $('gpu-processes-panel').hidden = !pearl;
  $('console-gpu-label').hidden = !pearl;
  const picker = $('console-gpu'), selected = picker.value;
  const keys = ['pearl', ...states.map(gpu => `pearl-${gpu.vendor}-${gpu.index}`)];
  if ([...picker.options].map(option => option.value).join(',') !== keys.join(',')) {
    picker.replaceChildren();
    const all = node('option', '', 'Alle GPUs'); all.value = 'pearl'; picker.append(all);
    for (const gpu of states) {
      const option = node('option', '', `${gpu.vendor} ${gpu.index} · ${gpu.model}`);
      option.value = `pearl-${gpu.vendor}-${gpu.index}`; picker.append(option);
    }
    picker.value = keys.includes(selected) ? selected : 'pearl';
  }
  const list = $('gpu-processes'); list.replaceChildren();
  if (!states.length) { list.append(node('p', 'muted', 'Keine GPU erkannt.')); return; }
  const pearlCoin = data.coins?.find(coin => coin.id === 'pearl');
  for (const gpu of states) {
    const card = node('article', `gpu-process${gpu.running ? ' running' : ''}`);
    const title = node('strong', '', `${gpu.vendor} ${gpu.index} · ${gpu.model}`);
    const state = node('span', `tag ${gpu.status === 'ERROR' ? 'blocked' : gpu.poolHealthy ? 'ready' : ''}`,
      !gpu.selected ? 'Nicht ausgewählt' : gpu.manuallyPaused ? 'Manuell pausiert'
        : gpu.running && !gpu.poolHealthy ? 'Verbinde mit Pool' : statusLabels[gpu.status] || gpu.status);
    const header = node('div', 'gpu-process-head'); header.append(title, state);
    const detail = node('p', 'muted', gpu.lastError || gpu.connectionDetail || 'Noch nicht gestartet');
    const controls = node('div', 'gpu-process-actions');
    const start = node('button', 'button subtle', 'GPU starten'); start.type = 'button';
    start.disabled = busy || !gpu.selected || gpu.running || !pearlCoin || coinBlockers(pearlCoin, data).length > 0;
    start.addEventListener('click', () => action(`/api/agent/pearl/gpus/${gpu.vendor}/${gpu.index}/resume`, `${gpu.vendor} ${gpu.index} gestartet.`));
    const stop = node('button', 'button subtle', 'GPU pausieren'); stop.type = 'button';
    stop.disabled = busy || !gpu.running;
    stop.addEventListener('click', () => action(`/api/agent/pearl/gpus/${gpu.vendor}/${gpu.index}/pause`, `${gpu.vendor} ${gpu.index} pausiert.`));
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
  set('fee-monero', proxy?.moneroFeeReady ? 'Verfügbar' : 'Nicht verfügbar');
  set('fee-pearl', proxy?.pearlFeeReady ? 'Verfügbar' : 'Nicht verfügbar');
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
    $('optimization-panel').before(section);
    $('referral-form').addEventListener('submit', event => { event.preventDefault(); if ($('referral-form').reportValidity()) action('/api/agent/referral', 'Referral-Key lokal gespeichert. Der Node kann ihn wieder überschreiben.', {key: $('referral-key').value.trim()}); });
  }
  const root = $('fee-breakdown'); root.replaceChildren();
  const fee = (data.fees || []).find(item => item.coin === selectedView) || (data.fees || [])[0];
  set('fee-referral-state', data.referral?.key ? `KEY · ${data.referral.key}` : 'STANDARD · KEIN REFERRER');
  if (document.activeElement !== $('referral-key')) $('referral-key').value = data.referral?.key || '';
  if (!fee) { root.append(node('p', 'muted', 'Gebührenmodell wird geladen …')); return; }
  const knownTotal = fee.parts.filter(part => part.known).reduce((sum, part) => sum + (part.percentage || 0), 0);
  const card = node('article', 'fee-card'); card.append(node('strong', '', `${fee.coin === 'monero' ? 'Monero · XMRig' : 'Pearl · SRBMiner-MULTI'} · ${i18n.language === 'en' ? 'known deductions' : 'bekannte Abzüge'} ${fmt(knownTotal, 2)} %`));
  const bar = node('div', 'fee-bar');
  for (const part of fee.parts.filter(part => part.known && part.percentage > 0)) { const segment = node('span', `fee-segment fee-${part.kind.toLowerCase()}`); segment.style.flexGrow = String(part.percentage); segment.title = `${part.label}: ${fmt(part.percentage, 2)} %`; bar.append(segment); }
  const user = node('span', 'fee-segment fee-user'); user.style.flexGrow = String(Math.max(0, 100 - knownTotal)); bar.append(user); card.append(bar);
  for (const part of fee.parts) { const row = node('div', 'detail'); row.append(node('span', '', part.label), node('strong', '', part.known ? `${fmt(part.percentage, 2)} %` : 'unbekannt')); card.append(row, node('small', 'muted', part.source)); }
  const remaining = node('div', 'detail'); remaining.append(node('span', '', 'Voraussichtlich für dich'), node('strong', '', `${fmt(Math.max(0, 100-knownTotal), 2)} %`)); card.append(remaining); root.append(card);
}
function render(data) {
  latest = data;
  hydrateConfiguration('monero-form', data.moneroConfiguration, {poolUrl: 'monero-pool', wallet: 'monero-wallet', worker: 'monero-worker'});
  hydrateConfiguration('pearl-form', data.pearlConfiguration, {poolUrl: 'pearl-pool', wallet: 'pearl-wallet', worker: 'pearl-worker'});
  renderPayoutState(data);
  renderGpuSelection(data);
  renderWorkspace(data);
  renderCoins(data); renderProxy(data.proxy); renderGpuProcesses(data); renderFees(data); syncConsoleView();
  const coin = data.coins?.find(c => c.id === selectedView);
  if (coin?.id === 'monero' && Date.now() - lastOptimizationLoad > 5000) loadRandomXOptimization();
  renderOptimizationChecklist(coin, data);
  const workers = (data.stats?.workers || []).filter(worker => worker.currentAlgorithm === (selectedView === 'pearl' ? 'PearlHash' : 'RandomX'));
  const status = coin?.status || 'STOPPED';
  const pearlWaiting = selectedView === 'pearl' && data.pearl?.running && !data.pearl?.poolHealthy;
  set('status', pearlWaiting ? 'Verbinde mit Pool' : statusLabels[status] || status); set('coin-label', coin ? `${coin.name} · ${coin.device}` : '—');
  const gpuRunning = (data.pearl?.gpus || []).filter(gpu => gpu.running).length;
  const gpuSelected = (data.pearl?.gpus || []).filter(gpu => gpu.selected).length;
  const cpuRunning = data.coins?.find(c => c.id === 'monero')?.status === 'MINING';
  set('view-note', `CPU: ${cpuRunning ? 'läuft' : 'pausiert'} · GPUs: ${gpuRunning}/${gpuSelected} gestartet. Beide können parallel laufen.`);
  set('miner-details-title', coin?.name || 'Miner');
  set('detail-algorithm', coin?.algorithm || '—');
  set('detail-hardware', coin?.device === 'GPU' ? ((data.pearl?.gpus || []).filter(gpu => gpu.selected)
    .map(gpu => gpu.model || `${gpu.vendor} ${gpu.index}`).join(', ') || 'Keine GPU ausgewählt') : 'CPU');
  set('detail-installation', coin?.binaryAvailable ? 'Installiert' : 'Nicht installiert');
  set('detail-configuration', coin?.configured
    ? (payoutDefault(selectedView)?.inUse ? 'Pool aktiv · Auszahlung an SolarMiner-Standard' : 'Pool und Wallet konfiguriert')
    : 'Pool und Wallet fehlen');
  set('detail-connection', selectedView === 'pearl' && (data.pearl?.running || status === 'ERROR')
    ? (data.pearl?.minerError || data.pearl?.connectionDetail || 'Verbindung wird geprüft')
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
  set('power-range', maximum > 0 ? `Maximal ${fmt(maximum, 0)} W` : 'Noch kein Leistungsziel');
  const remainingGpus = (data.pearl?.gpus || []).some(gpu => gpu.selected && !gpu.running);
  $('resume').disabled = busy || !coin || coinBlockers(coin, data).length > 0 || (selectedView === 'pearl' ? !remainingGpus : status === 'MINING');
  $('pause').disabled = busy || (selectedView === 'pearl' ? !data.pearl?.running : status !== 'MINING');
  const pearl = selectedView === 'pearl'; $('monero-form').hidden = pearl; $('pearl-form').hidden = !pearl;
  if (pearl) {
    set('pearl-readiness', data.pearl?.minerError || 'SRBMiner nutzt den SolarMiner-Proxy.');
    $('pearl-download').hidden = data.pearl?.binaryAvailable || data.pearl?.downloadStatus === 'DOWNLOADING';
    $('pearl-download').disabled = busy;
  }
  const gpus = (data.gpus || []).filter(gpu => data.pearl?.gpus?.some(state => state.selected && state.vendor === gpu.vendor && state.index === gpu.index));
  const min = gpus.reduce((s,g) => s + g.minWatts, 0), max = gpus.reduce((s,g) => s + g.maxWatts, 0);
  $('power-form').hidden = !pearl || !gpus.length;
  $('power-input').min = String(min); $('power-input').max = String(max);
  if (document.activeElement !== $('power-input')) $('power-input').value = powerTarget || Math.round((min + max) / 2);
  set('power-limits', `${fmt(min, 0)}–${fmt(max, 0)} W`);
  $('power-submit').disabled = busy || !pearl; $('monero-submit').disabled = busy;
  $('connection').className = 'badge online'; set('connection', 'Agent verbunden');
  set('updated', `Aktualisiert ${new Date().toLocaleTimeString(i18n.locale)}`);
}
async function refresh() {
  if (busy || document.hidden) return;
  try {
    const response = await fetch('/api/agent/overview', { cache: 'no-store' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const overview = await response.json();
    const assessmentResponse = await fetch('/api/agent/node-assessment', { cache: 'no-store' });
    if (assessmentResponse.ok) nodeAssessment = await assessmentResponse.json();
    render(overview);
    if ($('notice').dataset.kind === 'connection') { $('notice').hidden = true; $('catalog-notice').hidden = true; }
  } catch (error) {
    $('connection').className = 'badge offline'; set('connection', 'Agent nicht erreichbar');
    $('resume').disabled = true; $('pause').disabled = true;
    $('notice').dataset.kind = 'connection'; notice(`Daten konnten nicht geladen werden: ${error.message}`, true);
  }
}
async function action(path, success, params, body, feedbackId = null) {
  if (busy) return;
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
  finally { busy = false; await refresh(); }
}
$('refresh').addEventListener('click', refresh);
window.addEventListener('hashchange', () => {
  const view = ['monero', 'pearl'].includes(location.hash.slice(1)) ? location.hash.slice(1) : 'catalog';
  if (view !== selectedView) { selectedView = view; if (latest) render(latest); }
});
$('console-bottom').addEventListener('click', () => { $('miner-console').scrollTop = $('miner-console').scrollHeight; });
$('console-gpu').addEventListener('change', syncConsoleView);
['monero-form', 'pearl-form'].forEach(id => $(id).addEventListener('input', () => { $(id).dataset.dirty = 'true'; }));
['monero', 'pearl'].forEach(coin => {
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
document.querySelectorAll('.filter-button').forEach(button => button.addEventListener('click', () => {
  deviceFilter = button.dataset.device;
  document.querySelectorAll('.filter-button').forEach(item => item.classList.toggle('selected', item === button));
  if (latest) renderWorkspace(latest);
}));
$('resume').addEventListener('click', () => { if (['monero', 'pearl'].includes(selectedView)) action(`/api/agent/miners/${selectedView}/resume`, 'Miner gestartet.'); });
$('pause').addEventListener('click', () => { if (['monero', 'pearl'].includes(selectedView)) action(`/api/agent/miners/${selectedView}/pause`, 'Miner pausiert.'); });
$('pearl-download').addEventListener('click', () => action('/api/agent/pearl/download', 'SRBMiner-Download gestartet.'));
$('proxy-form').addEventListener('submit', event => { event.preventDefault(); if ($('proxy-form').reportValidity()) action('/api/agent/proxy', 'Proxy gespeichert.', { host: $('proxy-host').value.trim() }); });
$('proxy-discover').addEventListener('click', async () => {
  const button = $('proxy-discover'), status = $('discovery-status'), results = $('proxy-candidates');
  button.disabled = true; button.textContent = 'Suche im lokalen Netzwerk …'; status.textContent = ''; results.replaceChildren();
  try {
    const response = await fetch('/api/agent/proxy/discover', { method: 'POST' });
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
        choose.addEventListener('click', () => action('/api/agent/proxy',
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
$('monero-form').addEventListener('submit', event => { event.preventDefault(); if (!$('monero-form').reportValidity()) return; if (!latest?.proxy?.moneroUrl) return notice('SolarMiner-Proxy für Monero fehlt. Verbinde zuerst den Proxy.', true); if (!payoutAvailable('monero')) return; const standard = usesStandardPayout('monero'); action('/api/agent/monero/configuration', standard ? 'Monero gespeichert: Auszahlung an das SolarMiner-Standardziel.' : 'Monero-Konfiguration gespeichert.', null, { poolUrl: standard ? '' : selectedPool('monero'), wallet: standard ? '' : $('monero-wallet').value.trim(), worker: $('monero-worker').value.trim() }, 'monero-save-feedback'); });
$('pearl-form').addEventListener('submit', event => { event.preventDefault(); if (!$('pearl-form').reportValidity()) return; const indices = [...selectedGpuIndices].filter(index => latest?.gpus?.some(gpu => gpuKey(gpu) === index)).sort(); if (!indices.length) return notice('Wähle mindestens eine erkannte GPU.', true); if (!latest?.proxy?.pearlUrl) return notice('SolarMiner-Proxy für Pearl fehlt. Verbinde zuerst den Proxy.', true); if (!payoutAvailable('pearl')) return; const standard = usesStandardPayout('pearl'); action('/api/agent/pearl/configuration', standard ? 'Pearl gespeichert: Auszahlung an das SolarMiner-Standardziel.' : 'Pearl-Konfiguration und GPU-Auswahl gespeichert.', null, { poolUrl: standard ? '' : selectedPool('pearl'), proxyUrl: latest.proxy.pearlUrl, wallet: standard ? '' : $('pearl-wallet').value.trim(), worker: $('pearl-worker').value.trim(), devices: indices.join(',') }, 'pearl-save-feedback'); });
$('power-form').addEventListener('submit', event => { event.preventDefault(); if ($('power-form').reportValidity()) action('/api/agent/miners/pearl/power-target', 'GPU-Leistungsziel übernommen.', { powerTarget: $('power-input').value }); });
let lastPoll = Date.now();
refresh(); setInterval(pollConsole, 2000); setInterval(() => {
  const downloading = ['monero', 'pearl'].some(id => latest?.[id]?.downloadStatus === 'DOWNLOADING');
  if (downloading || Date.now() - lastPoll > 3000) { lastPoll = Date.now(); refresh(); }
}, 1000); document.addEventListener('visibilitychange', () => { if (!document.hidden) { refresh(); pollConsole(); } });
