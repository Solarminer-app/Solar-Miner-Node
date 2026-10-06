// Pool switching per coin. The proxy stays the miner's target; only the upstream pool changes.
(() => {
  const ui = window.SolarMinerUI;
  const catalog = window.SolarMinerPoolCatalog;
  const $ = id => document.getElementById(id);
  const t = ui.t;
  let overview = null, fresh = false, savedAt = null, busy = false, refreshing = false, queued = false, stream = null;
  let search = '', pending = null;

  function notice(message, error = false) {
    const el = $('notice');
    el.textContent = t(message);
    el.className = `notice${error ? ' error' : ''}`;
    el.hidden = !message;
  }
  function configurationOf(data, coinId) {
    if (coinId === 'monero') return data.moneroConfiguration;
    if (coinId === 'pearl') return data.pearlConfiguration;
    return data.gpuCoins?.[coinId]?.configuration;
  }
  function endpoint(coinId) {
    if (coinId === 'monero') return '/api/agent/local/monero/configuration';
    return `/api/agent/local/${coinId}/configuration`;
  }

  async function applyPool(row, poolUrl) {
    if (!fresh) return notice('Warte auf aktuelle Agent-Daten, bevor du eine Änderung ausführst.', true);
    if (busy) return;
    if (!window.confirm(t(`Pool für ${row.coinName} wechseln? Der Miner dieses Coins wird dabei angehalten und startet mit dem neuen Ziel neu.`))) return;
    const data = overview;
    const configuration = configurationOf(data, row.coin) || {};
    const body = {poolUrl, wallet: configuration.wallet || '', worker: configuration.worker || 'pc'};
    if (row.device === 'GPU') {
      if (!row.proxyUrl) return notice(`SolarMiner-Proxy-Route für ${row.coinName} fehlt.`, true);
      body.proxyUrl = row.proxyUrl;
      body.devices = configuration.devices && configuration.devices !== 'all'
        ? configuration.devices : (data.gpus || []).map(gpu => `${gpu.vendor}:${gpu.index}`).join(',');
    }
    pending = {coin: row.coin, poolUrl};
    busy = true;
    window.SolarMinerMiningCache?.clear();
    try {
      const response = await fetch(endpoint(row.coin), {
        method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)
      });
      if (!response.ok) {
        const failure = await response.json().catch(() => null);
        throw new Error(failure?.message || `HTTP ${response.status}`);
      }
      if (await response.json() !== true) throw new Error('Der Agent hat die Änderung abgelehnt.');
      notice(`${row.coinName}: Pool übernommen. Der Miner startet mit dem neuen Ziel.`);
    } catch (error) {
      notice(`Poolwechsel fehlgeschlagen: ${error.message}`, true);
    } finally {
      pending = null;
      busy = false;
      window.SolarMinerMiningCache?.clear();
      refresh();
    }
  }

  function renderStatusStrip(data) {
    const strip = $('status-strip');
    strip.replaceChildren();
    const add = (label, value, tone, href) => {
      const item = ui.element('span', 'strip-item');
      item.append(ui.element('span', '', `${label}: `));
      const pill = ui.statusPill(null, value);
      if (tone) pill.classList.add(tone);
      item.append(pill);
      if (href) { const link = ui.element('a', '', 'Öffnen'); link.href = href; item.append(link); }
      strip.append(item);
    };
    const proxy = data.proxy || {};
    add('Agent', $('connection').classList.contains('offline') ? 'nicht erreichbar' : 'verbunden',
      $('connection').classList.contains('offline') ? 'tone-bad' : 'tone-ok');
    add('Proxy', !proxy.reachable ? 'getrennt' : proxy.mode === 'standalone' ? 'lokaler Proxy' : 'Proxy im Netzwerk',
      proxy.reachable ? 'tone-ok' : 'tone-bad', '/proxy.html');
    const coins = ui.poolRows(data).filter(row => row.running);
    add('Coins mit Pool', coins.length ? `${coins.length} verbunden` : 'kein Miner läuft', coins.length ? 'tone-ok' : '');
  }

  function renderSummary(data) {
    const rows = ui.poolRows(data);
    const latencies = rows.map(row => row.latency).filter(value => value != null);
    // Per-coin device selections overlap: one GPU can be selected for several coins but can only run in one,
    // so the headline counts hardware once instead of summing selections into a number nothing can reach.
    const cards = data.gpus || [];
    const cpuCoin = (data.coins || []).find(coin => coin.id === 'monero');
    const cpuMining = cpuCoin?.status === 'MINING';
    const devices = cards.length + (cpuCoin ? 1 : 0);
    const running = cards.filter(card => card.running).length + (cpuMining ? 1 : 0);
    const contacted = cards.filter(card => card.running && card.poolHealthy).length + (cpuMining ? 1 : 0);
    const grid = $('pool-summary');
    grid.replaceChildren(
      ui.metric({label: 'Laufende Geräte', value: `${running}/${devices}`,
        detail: `${contacted} mit stabilem Pool-Kontakt`, tone: running ? 'ok' : ''}),
      ui.metric({label: 'Proxy-Ziele', value: ui.number(rows.filter(row => row.proxyUrl).length, 0),
        detail: `Agent-Modus: ${data.proxy?.mode === 'standalone' ? 'lokaler Mining-Dienst' : 'Proxy im Netzwerk'}`}),
      ui.metric({label: 'Ø Latenz', value: latencies.length ? ui.latency(latencies.reduce((sum, value) => sum + value, 0) / latencies.length) : '—',
        detail: 'Gemessen von der Miner-API des laufenden Miners'}),
      ui.metric({label: 'Fee-Ziele geladen', value: `${rows.filter(row => row.feeReady).length}/${rows.length}`,
        detail: 'Ohne Fee-Ziel startet der betreffende Miner nicht', tone: rows.every(row => row.feeReady) ? 'ok' : 'warn'})
    );
  }

  function poolCard(row, entry, data) {
    const card = ui.element('button', `choice${entry.value === row.poolUrl ? ' selected' : ''}`);
    card.type = 'button';
    const current = entry.value === row.poolUrl;
    card.append(ui.element('strong', '', entry.value === 'custom' ? 'Eigene Pool-Adresse' : `Kryptex · ${entry.region}`),
      ui.element('small', 'cell-mono', entry.value));
    const meta = ui.element('div', 'choice-meta');
    if (entry.feePercent != null) meta.append(ui.element('span', 'pill', `Poolgebühr ${entry.feePercent} %`));
    if (current) meta.append(ui.element('span', 'pill tone-ok', 'aktives Ziel'));
    card.append(meta);
    if (!current) {
      card.addEventListener('click', event => {
        event.stopPropagation();
        applyPool(row, entry.value);
      });
    }
    card.disabled = busy || !fresh || !row.reachable;
    return card;
  }

  function renderBoards(data) {
    const boards = $('pool-boards');
    boards.replaceChildren();
    for (const row of ui.poolRows(data)) {
      if (search && ![row.coinName, row.ticker, row.algorithm, row.poolUrl, row.proxyUrl]
        .some(value => String(value || '').toLowerCase().includes(search.toLowerCase()))) continue;
      const panel = ui.element('section', 'panel pool-board');
      const head = ui.element('div', 'panel-head');
      const copy = ui.element('div');
      copy.append(ui.element('p', 'kicker', `${row.device} · ${row.algorithm}`), ui.element('h2', '', row.coinName));
      head.append(copy);
      const state = ui.element('div', 'instance-state');
      state.append(ui.statusPill(row.running ? 'MINING' : row.configured ? 'STOPPED' : 'ERROR',
        row.running ? 'Miner läuft' : row.configured ? 'Bereit' : 'Nicht eingerichtet'));
      const setup = ui.element('a', 'button subtle', 'Worker zuweisen');
      setup.href = '/workers.html';
      state.append(setup);
      head.append(state);
      panel.append(head);

      const facts = ui.element('div', 'pool-facts');
      for (const [label, value, detail] of [
        ['Proxy-Ziel des Miners', row.proxyUrl || '—', row.proxyUrl ? 'Der Miner spricht immer den Proxy an' : 'Keine Proxy-Route gespeichert'],
        ['Aktuelles Pool-Ziel', row.poolUrl || '—', row.detail || (row.poolUrl ? 'Vom Proxy verwendet' : 'Ziel noch nicht gewählt')],
        ['Latenz', ui.latency(row.latency), row.latency != null ? 'Von der Miner-API gemessen' : 'Nur im laufenden Betrieb messbar'],
        ['Difficulty', ui.difficulty(row.difficulty), 'Aktuelle Pool-Difficulty'],
        ['Pool-Kontakt', `${row.connectedWorkers}/${row.selectedWorkers}`, 'Ausgewählte Geräte dieses Coins mit Pool-Kontakt'],
        ['Auszahlung', row.payout?.available ? row.payout.maskedWallet : 'Eigene Wallet',
          row.payout?.inUse ? 'SolarMiner-Standardziel aktiv' : 'Wallet aus der Miner-Konfiguration']
      ]) {
        const fact = ui.element('div', 'pool-fact');
        fact.append(ui.element('span', '', label), ui.element('strong', 'cell-mono', value), ui.element('small', '', detail));
        facts.append(fact);
      }
      panel.append(facts);

      const optionsHead = ui.element('div', 'section-head');
      const optionsCopy = ui.element('div');
      optionsCopy.append(ui.element('p', 'kicker', 'POOL ZWECHSELN'), ui.element('h3', '', 'Verfügbare Ziele'));
      optionsHead.append(optionsCopy);
      panel.append(optionsHead);
      const choices = ui.element('div', 'choice-grid');
      for (const entry of catalog.candidates(row.coin)) choices.append(poolCard(row, entry, data));
      panel.append(choices);

      const custom = ui.element('details', 'custom-pool');
      custom.append(ui.element('summary', '', 'Eigene Pool-Adresse verwenden'));
      const line = ui.element('div', 'input-row');
      const input = document.createElement('input');
      input.type = 'text';
      input.placeholder = 'stratum+tcp://pool.example:3333';
      input.spellcheck = false;
      input.setAttribute('aria-label', t(`${row.coinName} eigene Pool-Adresse`));
      const apply = ui.element('button', 'button subtle', 'Eigenes Ziel übernehmen');
      apply.type = 'button';
      apply.disabled = busy || !fresh || !row.reachable;
      apply.addEventListener('click', () => {
        const value = input.value.trim();
        if (!catalog.validUrl(value)) return notice('Pool-Adresse ungültig. Format: stratum+tcp://host:port oder stratum+ssl://host:port', true);
        applyPool(row, value);
      });
      line.append(input, apply);
      custom.append(ui.element('p', 'muted', 'Wallet und Worker bleiben unverändert. Prüfe, dass die Wallet zum gewählten Pool passt.'), line);
      panel.append(custom);

      if (pending?.coin === row.coin) {
        const busyNote = ui.element('p', 'notice-line warn', 'Poolwechsel läuft …');
        panel.append(busyNote);
      }
      if (!row.reachable) panel.append(ui.element('p', 'notice-line warn', 'Der Proxy ist nicht erreichbar; Poolwechsel ist erst nach der Verbindung möglich.'));
      boards.append(panel);
    }
    if (!boards.children.length) boards.append(ui.element('p', 'muted', 'Kein Coin passt zur Suche.'));
  }

  function render(data, isFresh = fresh) {
    overview = data;
    fresh = isFresh;
    renderStatusStrip(data);
    renderSummary(data);
    renderBoards(data);
    $('connection').className = `badge ${fresh ? 'online' : ''}`;
    $('connection').textContent = t(fresh ? 'Agent verbunden' : 'Aktualisiere …');
    if (!fresh) {
      const box = $('notice');
      box.hidden = false;
      box.className = 'notice';
      box.textContent = t(`Letzter Stand ${new Date(savedAt || Date.now()).toLocaleTimeString(window.SolarMinerI18n.locale)} · Aktionen sind danach verfügbar.`);
    }
  }

  async function refresh() {
    if (refreshing) { queued = true; return; }
    refreshing = true;
    try {
      const response = await fetch('/api/agent/local/overview', {cache: 'no-store'});
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      savedAt = Date.now();
      window.SolarMinerMiningCache?.write(data);
      render(data, true);
      notice('');
    } catch (error) {
      if (overview) render(overview, false);
      $('connection').className = 'badge offline';
      $('connection').textContent = t('Agent nicht erreichbar');
      notice(`Agent-Daten konnten nicht geladen werden: ${error.message}`, true);
    } finally {
      refreshing = false;
      if (queued) { queued = false; queueMicrotask(refresh); }
    }
  }

  function connectEvents() {
    if (stream || document.hidden) return;
    const source = new EventSource('/api/agent/local/events');
    stream = source;
    source.addEventListener('overview', event => {
      if (source !== stream || busy) return;
      try {
        savedAt = Date.now();
        const data = JSON.parse(event.data);
        window.SolarMinerMiningCache?.write(data);
        render(data, true);
      } catch (_) { /* A manual refresh recovers from a malformed transport frame. */ }
    });
    source.onerror = () => { if (source === stream) { source.close(); stream = null; } };
  }

  $('refresh').addEventListener('click', refresh);
  const searchInput = $('pool-search');
  searchInput.addEventListener('input', () => {
    search = searchInput.value.trim();
    if (overview) render(overview);
  });
  const cached = window.SolarMinerMiningCache?.read();
  if (cached) { savedAt = cached.savedAt; render(cached.overview, false); }
  refresh();
  connectEvents();
  setInterval(() => {
    if (document.hidden) return;
    if (!stream) connectEvents();
    if (!fresh || Date.now() - savedAt > (stream ? 4000 : 1500)) refresh();
  }, 2000);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { stream?.close(); stream = null; }
    else { refresh(); connectEvents(); }
  });
})();
