// Dashboard: readiness first, then measurement, then money. One aggregate, pushed over SSE.
(() => {
  const ui = window.SolarMinerUI;
  const $ = id => document.getElementById(id);
  const t = ui.t;
  const preferences = window.SolarMinerPreferences;
  const history = {hashrate: [], power: [], temperature: [], efficiency: []};
  const charts = {
    hashrate: {title: 'Hashrate', className: '', unit: value => ui.hashrate(value)},
    power: {title: 'Leistungsaufnahme', className: 'power', unit: value => ui.power(value)},
    temperature: {title: 'Höchste Temperatur', className: 'heat', unit: value => ui.temperature(value)},
    efficiency: {title: 'Effizienz', className: 'efficiency', unit: value => ui.efficiency(value, 1)}
  };
  let overview, telemetry, settings, assessment, energy, energyError = null;
  let selectedAlgorithm = '', sort = {key: 'hashrateHps', direction: 'desc'}, workerFilter = '';
  let stream = null, refreshing = false, queued = false, savedAt = null;

  const algorithms = () => [...new Set((overview?.stats?.workers || []).map(worker => worker.currentAlgorithm).filter(Boolean))];

  function renderStatusStrip(data) {
    const strip = $('status-strip');
    strip.replaceChildren();
    const add = (label, value, tone, href) => {
      const item = document.createElement('span');
      item.className = 'strip-item';
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
    add('Mining-Verbindung', !proxy.reachable ? 'getrennt' : proxy.mode === 'standalone' ? 'lokaler Proxy' : 'Proxy im Netzwerk',
      proxy.reachable ? 'tone-ok' : 'tone-bad', '/proxy.html');
    add('Node', assessment?.connected ? 'verbunden' : assessment?.connected === false ? 'nicht verbunden' : 'unbekannt',
      assessment?.connected ? 'tone-ok' : '', '/workers.html');
    const workers = (data.stats?.workers || []);
    const errors = workers.filter(worker => worker.miningStatus === 'ERROR').length;
    if (errors) add('Meldungen', `${errors} Worker mit Fehler`, 'tone-bad', '/workers.html');
  }

  function renderAlerts(data) {
    const list = $('alerts');
    list.replaceChildren();
    const installed = (data.coins || []).filter(coin => coin.binaryAvailable && !coin.experimental);
    const unconfigured = installed.find(coin => !coin.configured);
    const workers = data.stats?.workers || [];
    const errors = workers.filter(worker => worker.miningStatus === 'ERROR');
    const enabledIds = settings ? ['cpu', ...(data.gpus || []).map(gpu => gpu.deviceId)]
      .filter(id => settings.workerExternalControl?.[id] !== false
        && (settings.workerCoins == null || (settings.workerCoins[id]
          || (id === 'cpu' ? 'none' : settings.workerCoins['*'] || 'none')) !== 'none')) : [];
    const items = [];
    if (!installed.length) items.push(['warn', 'Noch kein Miner installiert', 'Wähle in der Miner-Bibliothek einen passenden Build und installiere ihn.', '/mining.html', 'Miner installieren →']);
    else if (unconfigured) items.push(['warn', 'Einrichtung unvollständig', `${unconfigured.name}: Pool, Auszahlung oder Geräteauswahl fehlen.`, '/workers.html', 'Worker einrichten →']);
    if (!data.proxy?.reachable) items.push(['bad', 'Mining-Verbindung getrennt', 'Der Proxy ist nicht erreichbar; ohne ihn starten die Miner nicht.', '/proxy.html', 'Verbindung prüfen →']);
    if (errors.length) items.push(['bad', `${errors.length} Worker mit Fehler`, errors.map(worker => worker.workerDisplayName).join(', '), '/workers.html', 'Worker prüfen →']);
    if (settings?.externalControlEnabled && !enabledIds.length) items.push(['warn', 'Node-Steuerung erlaubt, aber kein Gerät freigegeben', 'Ordne im Worker-Bereich mindestens ein Gerät zu, damit der Node automatisch regeln darf.', '/workers.html', 'Worker öffnen →']);
    if (!items.length) items.push(['ok', 'Alles im erwarteten Bereich', 'Keine offene Aktion. Die Messwerte unten zeigen den laufenden Betrieb.', null, null]);
    for (const [tone, title, description, href, action] of items) {
      const row = ui.element('div', `notice-line ${tone}`);
      const copy = ui.element('div');
      copy.append(ui.element('strong', '', title), ui.element('div', '', description));
      row.append(copy);
      if (href) { const link = ui.element('a', '', action); link.href = href; row.append(link); }
      list.append(row);
    }
  }

  function renderKpis(data) {
    const workers = ui.workerRows(data, telemetry);
    const scoped = selectedAlgorithm ? workers.filter(worker => worker.algorithm === selectedAlgorithm) : workers;
    const active = scoped.filter(worker => worker.status === 'MINING');
    const hashes = scoped.reduce((sum, worker) => sum + worker.hashrateHps, 0);
    const workerWatts = scoped.reduce((sum, worker) => sum + (worker.watts || 0), 0);
    const systemPower = telemetry?.metrics?.['system.total_power'];
    const heats = scoped.map(worker => worker.temperature.value).filter(value => Number.isFinite(value));
    const accepted = scoped.filter(worker => worker.shares.accepted != null);
    const rejected = scoped.filter(worker => worker.shares.rejected != null);
    const stale = scoped.filter(worker => worker.shares.stale != null);
    const shareSummary = ui.shares({
      accepted: accepted.length === scoped.length && accepted.length ? accepted.reduce((sum, w) => sum + w.shares.accepted, 0) : null,
      rejected: rejected.length === scoped.length && rejected.length ? rejected.reduce((sum, w) => sum + w.shares.rejected, 0) : null,
      stale: stale.length === scoped.length && stale.length ? stale.reduce((sum, w) => sum + w.shares.stale, 0) : null
    });
    const difficulties = scoped.map(worker => worker.difficulty).filter(value => Number.isFinite(value));
    const latencies = scoped.map(worker => worker.latency).filter(value => Number.isFinite(value));
    const network = (data.earnings || []).find(entry => entry.available && entry.difficulty);

    const strip = $('kpi-strip');
    strip.replaceChildren(
      ui.metric({label: 'Hashrate', value: ui.hashrate(hashes), detail: selectedAlgorithm
        ? `${selectedAlgorithm} · ${active.length} aktive Worker` : 'Alle Algorithmen · nicht vergleichbar summieren',
        tone: hashes > 0 ? 'ok' : ''}),
      ui.metric({label: 'Leistung', value: systemPower?.available ? ui.power(systemPower.value) : workerWatts ? ui.power(workerWatts) : '—',
        detail: systemPower?.available ? `Hostsensoren · Workeranteil ${ui.power(workerWatts)}`
          : workerWatts ? `nur gemessene Workerleistung` : 'Kein Leistungssensor verfügbar'}),
      ui.metric({label: 'Effizienz', value: ui.efficiency(hashes, workerWatts),
        detail: hashes > 0 && workerWatts > 0 ? 'Hashrate pro gemessener Workerleistung' : 'Hashrate oder Leistung fehlt'}),
      ui.metric({label: 'Worker', value: `${active.length}/${scoped.length}`,
        detail: scoped.some(worker => worker.status === 'ERROR') ? 'Mindestens ein Worker meldet einen Fehler' : 'Aktiv / gemeldet',
        tone: scoped.some(worker => worker.status === 'ERROR') ? 'bad' : ''}),
      ui.metric({label: 'Shares', value: shareSummary && accepted.length ? ui.number(accepted.reduce((sum, w) => sum + w.shares.accepted, 0), 0) : '—',
        detail: shareSummary ? `${shareSummary.text}${shareSummary.rejectedShare != null ? ` · ${ui.percent(shareSummary.rejectedShare)} abgelehnt` : ''}` : 'Von der Miner-API nicht gemeldet',
        tone: shareSummary?.rejectedShare > 5 ? 'warn' : ''}),
      ui.metric({label: 'Difficulty', value: difficulties.length ? ui.difficulty(Math.max(...difficulties)) : network?.difficulty ? ui.difficulty(network.difficulty) : '—',
        detail: difficulties.length ? `Job-Difficulty · Latenz ${latencies.length ? ui.latency(Math.round(latencies.reduce((a, b) => a + b, 0) / latencies.length)) : '—'}`
          : network?.difficulty ? 'Netzwerk-Difficulty als Kontext' : 'Keine Difficulty gemeldet'})
    );

    const forecastGrid = $('earnings-grid');
    const relevant = (data.coins || []).filter(coin => !coin.experimental
      && (coin.configured || (data.earnings || []).some(entry => entry.coin === coin.id && entry.available)));
    forecastGrid.replaceChildren(...relevant.map(coin => {
      const entry = (data.earnings || []).find(value => value.coin === coin.id);
      const card = ui.element('article', 'earning-card');
      const head = ui.element('div', 'coin-top');
      head.append(ui.element('strong', '', `${coin.name} (${coin.ticker})`),
        ui.element('span', `tag ${entry?.available ? 'ready' : ''}`, entry?.stale ? 'DATEN VERALTET' : entry?.available ? 'LIVE-SCHÄTZUNG' : 'NOCH NICHT VERFÜGBAR'));
      card.append(head,
        ui.element('strong', 'earning-value', entry?.available ? `${ui.number(entry.coinsPerDay, 6)} ${coin.ticker}` : '—'),
        ui.element('span', 'earning-fiat', entry?.available ? `≈ ${preferences.moneyFromUsd(entry.usdPerDay)} / Tag` : entry?.unavailableReason || 'Marktdaten werden geladen'),
        ui.element('small', '', `${ui.hashrate(entry?.hashrateHps)} · Netzwerk ${ui.difficulty(entry?.networkHashrateHps)} · Kurs ${entry?.priceUsd > 0 ? preferences.moneyFromUsd(entry.priceUsd) : '—'}`));
      return card;
    }));
    if (!forecastGrid.children.length) forecastGrid.textContent = t('Ertragsprognosen erscheinen, sobald ein Miner eingerichtet ist und Hashrate liefert.');
  }

  function renderLiveWorkers(data) {
    const grid = $('live-worker-grid');
    const rows = ui.operatingRows(ui.workerRows(data, telemetry));
    const poolTargets = Object.fromEntries(ui.poolRows(data).map(row => [row.coin, row.poolUrl]));
    const coinHashrates = Object.fromEntries((data.coins || []).map(coin => [coin.id,
      rows.filter(row => row.coin === coin.id).reduce((sum, row) => sum + row.hashrateHps, 0)]));
    grid.replaceChildren(...rows.map(row => {
      const card = ui.element('article', `live-worker-card ${row.status === 'ERROR' ? 'error' : ''}`);
      const head = ui.element('div', 'live-worker-head');
      const identity = ui.element('div');
      identity.append(ui.element('strong', '', ui.shortWorkerName(row)), ui.element('span', '', `${row.coinName} · ${row.algorithm}`));
      head.append(identity, ui.statusPill(row.status)); card.append(head);
      const forecast = (data.earnings || []).find(item => item.coin === row.coin && item.available);
      const ratio = forecast && coinHashrates[row.coin] > 0 ? row.hashrateHps / coinHashrates[row.coin] : 0;
      const usdPerDay = forecast ? forecast.usdPerDay * ratio : null;
      const metrics = ui.element('div', 'live-worker-metrics');
      for (const [label, value] of [
        ['Hashrate', ui.hashrate(row.hashrateHps)], ['Leistung', row.watts ? ui.power(row.watts) : '—'],
        ['Temperatur', row.temperature.value != null ? ui.temperature(row.temperature.value) : '—'],
        ['Ertrag', usdPerDay != null ? `≈ ${preferences.moneyFromUsd(usdPerDay)}/Tag` : '—'],
        ['Ertrag pro kWh', ui.moneyPerKwh(ui.revenuePerKwh(usdPerDay, row.watts))]]) {
        const item = ui.element('div'); item.append(ui.element('span', '', label), ui.element('strong', '', value)); metrics.append(item);
      }
      card.append(metrics);
      const targetPool = poolTargets[row.coin] || row.pools?.[0];
      const pool = ui.element('p', 'live-worker-pool', targetPool ? `${targetPool} · Latenz ${ui.latency(row.latency)}` : 'Kein Pool gemeldet');
      const session = energy?.activeSessions?.find(item => item.deviceId === row.deviceId);
      if (session) pool.append(ui.element('span', '', ` · Session ${(Number(session.wattHours) / 1000).toLocaleString(window.SolarMinerI18n.locale, {maximumFractionDigits: 3})} kWh`));
      const link = ui.element('a', '', 'Worker öffnen →'); link.href = '/workers.html'; card.append(pool, link); return card;
    }));
    if (!rows.length) grid.append(ui.element('div', 'notice-line warn', 'Kein Worker läuft gerade. Richte Hardware im Bereich Worker ein oder starte eine vorhandene Zuweisung.'));
  }

  function renderEnergy() {
    const root = $('energy-kpis');
    $('energy-settings').disabled = !energy;
    if (!energy) {
      root.replaceChildren(ui.element('div', energyError ? 'notice-line warn' : 'skeleton',
        energyError ? 'Energiedaten konnten nicht geladen werden.' : undefined));
      $('energy-note').textContent = energyError ? `${t('Energie-API:')} ${t(energyError)}` : '';
      return;
    }
    const activeWh = (energy.activeSessions || []).reduce((sum, session) => sum + Number(session.wattHours || 0), 0);
    const activeSeconds = (energy.activeSessions || []).reduce((sum, session) => Math.max(sum, Number(session.runtimeSeconds || 0)), 0);
    const money = value => preferences.money(value, energy.settings.currency);
    const tariff = preferences.convert(energy.settings.pricePerKwh, energy.settings.currency);
    const tariffCurrency = tariff == null ? energy.settings.currency : preferences.currency;
    const duration = seconds => `${Math.floor(seconds / 3600)} h ${Math.floor(seconds % 3600 / 60)} min`;
    root.replaceChildren(
      ui.metric({label: 'Laufende Sessions', value: `${(activeWh / 1000).toLocaleString(window.SolarMinerI18n.locale, {maximumFractionDigits: 3})} kWh`, detail: `${energy.activeSessions.length} Worker · ${duration(activeSeconds)}`, tone: energy.activeSessions.length ? 'ok' : ''}),
      ui.metric({label: 'Heute', value: `${Number(energy.today.kilowattHours).toLocaleString(window.SolarMinerI18n.locale, {maximumFractionDigits: 3})} kWh`, detail: `${money(energy.today.cost)} Stromkosten`}),
      ui.metric({label: '7 Tage', value: `${Number(energy.last7Days.kilowattHours).toLocaleString(window.SolarMinerI18n.locale, {maximumFractionDigits: 2})} kWh`, detail: `${money(energy.last7Days.cost)} Stromkosten`}),
      ui.metric({label: 'Messabdeckung', value: `${Math.round(energy.today.measurementCoverage * 100)} %`, detail: 'Fehlende Sensorintervalle werden nicht geschätzt'})
    );
    $('energy-note').textContent = t(`Komponentenverbrauch aus verfügbaren CPU-Package- und GPU-Board-Sensoren · Tarif ${Number(tariff == null ? energy.settings.pricePerKwh : tariff).toLocaleString(preferences.locale, {maximumFractionDigits: 4})} ${tariffCurrency}/kWh. Netzteil- und übrige Systemverluste können fehlen.`);
  }

  async function editEnergySettings() {
    if (!energy) return;
    const converted = preferences.convert(energy.settings.pricePerKwh, energy.settings.currency);
    const editCurrency = converted == null ? energy.settings.currency : preferences.currency;
    const value = prompt(t(`Strompreis in ${editCurrency} pro kWh`), converted == null ? energy.settings.pricePerKwh : converted);
    if (value == null) return;
    const price = Number(String(value).replace(',', '.'));
    if (!Number.isFinite(price) || price < 0) return alert(t('Bitte gib einen gültigen positiven Strompreis ein.'));
    const response = await fetch('/api/agent/local/energy/settings', {method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({pricePerKwh: price, currency: editCurrency})});
    if (!response.ok) return alert(t('Stromtarif konnte nicht gespeichert werden.'));
    energy.settings = await response.json(); refresh();
  }

  function track(value) {
    for (const key of Object.keys(history)) {
      history[key].push(Number.isFinite(value[key]) ? value[key] : NaN);
      if (history[key].length > 60) history[key].shift();
    }
  }

  function renderCharts(data) {
    const workers = ui.workerRows(data, telemetry);
    const scoped = selectedAlgorithm ? workers.filter(worker => worker.algorithm === selectedAlgorithm) : workers;
    const hashes = scoped.reduce((sum, worker) => sum + worker.hashrateHps, 0);
    const watts = scoped.reduce((sum, worker) => sum + (worker.watts || 0), 0);
    const systemPower = telemetry?.metrics?.['system.total_power'];
    const heats = scoped.map(worker => worker.temperature.value).filter(Number.isFinite);
    const sample = {
      hashrate: hashes > 0 ? hashes : NaN,
      power: systemPower?.available ? systemPower.value : watts || NaN,
      temperature: heats.length ? Math.max(...heats) : NaN,
      efficiency: hashes > 0 && watts > 0 ? watts / hashes : NaN
    };
    track(sample);
    const grid = $('charts');
    grid.replaceChildren(...Object.entries(charts).map(([key, chart]) => {
      const card = ui.element('article', `chart-card ${chart.className}`.trim());
      const head = document.createElement('header');
      head.append(ui.element('span', '', chart.title));
      const value = history[key].filter(Number.isFinite).at(-1);
      head.append(ui.element('strong', '', key === 'efficiency'
        ? (Number.isFinite(value) ? ui.efficiency(1, value) : '—') : chart.unit(value)));
      card.append(head, ui.sparkline(history[key], {label: `${chart.title} · Verlauf seit Seitenaufruf`}));
      card.append(ui.element('small', '', key === 'hashrate' && selectedAlgorithm
        ? `${selectedAlgorithm} · Verlauf seit Seitenaufruf` : 'Verlauf seit Seitenaufruf'));
      return card;
    }));
  }

  function renderPools(data) {
    const list = $('pool-list');
    const rows = ui.poolRows(data).filter(row => row.configured || row.running || row.selectedWorkers > 0);
    if (!rows.length) { list.replaceChildren(ui.element('p', 'muted', 'Noch kein Mining-Pool eingerichtet.')); return; }
    list.replaceChildren(...rows.map(row => {
      const card = ui.element('div', 'pool-row');
      const identity = ui.element('div', 'pool-identity');
      identity.append(ui.element('strong', '', `${row.ticker} · ${row.coinName}`), ui.element('small', '', `${row.algorithm} · ${row.device}`));
      const target = ui.element('div', 'pool-target');
      const catalog = window.SolarMinerPoolCatalog?.find(row.coin, row.poolUrl);
      target.append(ui.element('strong', '', row.poolUrl || 'Kein Pool gespeichert'),
        ui.element('span', 'cell-sub', catalog ? window.SolarMinerPoolCatalog.feeLabel(catalog) : ''));
      const connection = ui.element('div', 'pool-fact');
      connection.append(ui.element('span', '', 'Verbindung'));
      const connected = row.running && row.connectedWorkers > 0;
      connection.append(ui.element('strong', '', `${row.connectedWorkers}/${row.selectedWorkers} verbunden`));
      const latency = ui.element('div', 'pool-fact');
      latency.append(ui.element('span', '', 'Latenz'), ui.element('strong', '', ui.latency(row.latency)));
      const diff = ui.element('div', 'pool-fact');
      diff.append(ui.element('span', '', 'Difficulty'), ui.element('strong', '', ui.difficulty(row.difficulty)));
      const actions = ui.element('div', 'pool-actions');
      actions.append(ui.statusPill(connected ? 'MINING' : row.running ? 'ERROR' : 'STOPPED',
        connected ? 'Pool verbunden' : row.running ? 'Verbinde mit Pool' : 'Gestoppt'));
      if (!row.feeReady && (data.proxy?.mode === 'standalone' || ['pearl', 'ravencoin', 'ethereumclassic'].includes(row.coin)))
        actions.append(ui.element('span', 'pill tone-warn', 'Fee-Ziel fehlt'));
      const link = ui.element('a', 'button subtle', 'Wallet & Pool bearbeiten');
      link.href = '/wallets.html';
      actions.append(link);
      card.append(identity, target, connection, latency, diff, actions);
      return card;
    }));
  }

  const workerColumns = [
    {label: 'Worker', key: 'name', sortable: true, render: row => {
      const wrap = document.createElement('div');
      wrap.append(ui.element('strong', 'cell-truncate', ui.shortWorkerName(row)));
      wrap.append(ui.element('span', 'cell-sub', row.deviceId || row.hardwareModel));
      return wrap;
    }},
    {label: 'Coin · Miner', key: 'coinName', sortable: true, render: row => row.coinName},
    {label: 'Algorithmus', key: 'algorithm', sortable: true, render: row => row.algorithm},
    {label: 'Status', key: 'status', sortable: true, render: row => ui.statusPill(row.status)},
    {label: 'Hashrate', key: 'hashrateHps', sortable: true, align: 'number', render: row => ui.hashrate(row.hashrateHps)},
    {label: 'Temperatur', key: 'temperatureValue', sortable: true, align: 'number', render: row => {
      if (!Number.isFinite(row.temperature.value)) return '—';
      const wrap = document.createElement('div');
      wrap.append(ui.element('strong', '', ui.temperature(row.temperature.value)));
      wrap.append(ui.element('span', 'cell-sub', row.temperature.source));
      return wrap;
    }},
    {label: 'Leistung', key: 'watts', sortable: true, align: 'number', render: row => ui.power(row.watts)},
    {label: 'Effizienz', key: 'efficiencyValue', sortable: true, align: 'number', render: row => ui.efficiency(row.hashrateHps, row.watts)},
    {label: 'Shares', key: 'acceptedShares', sortable: true, align: 'number', render: row => ui.shareLines(row.shares)},
    {label: 'Difficulty', key: 'difficulty', sortable: true, align: 'number', render: row => ui.difficulty(row.difficulty)}
  ];

  function renderWorkers(data) {
    let rows = ui.operatingRows(ui.workerRows(data, telemetry));
    rows = rows.map(row => ({...row, temperatureValue: row.temperature.value ?? null,
      efficiencyValue: row.hashrateHps > 0 && row.watts > 0 ? row.watts / row.hashrateHps : null,
      acceptedShares: row.shares.accepted ?? null}));
    if (workerFilter) {
      const needle = workerFilter.toLowerCase();
      rows = rows.filter(row => [row.name, row.coinName, row.algorithm, row.hardwareModel, row.deviceId]
        .some(value => String(value || '').toLowerCase().includes(needle)));
    }
    const direction = sort.direction === 'asc' ? 1 : -1;
    rows.sort((a, b) => {
      const left = a[sort.key], right = b[sort.key];
      if (left == null && right == null) return 0;
      if (left == null) return 1;
      if (right == null) return -1;
      return (typeof left === 'number' && typeof right === 'number' ? left - right : String(left).localeCompare(String(right))) * direction;
    });
    $('worker-table').replaceChildren(ui.table({
      columns: workerColumns.map(column => ({...column, onSort: () => {
        sort = column.key === sort.key ? {key: column.key, direction: sort.direction === 'asc' ? 'desc' : 'asc'} : {key: column.key, direction: 'desc'};
        renderWorkers(overview);
      }})),
      rows, sortKey: sort.key, sortDirection: sort.direction,
      empty: 'Kein Worker läuft gerade. Zuweisungen und angehaltene Geräte findest du im Bereich Worker.',
      onRow: () => { location.href = '/workers.html'; }
    }));
  }

  function renderAlgorithmPicker() {
    const picker = $('kpi-algorithm');
    const options = algorithms();
    if (!options.includes(selectedAlgorithm)) selectedAlgorithm = options[0] || '';
    const values = [...picker.options].map(option => option.value);
    if (values.join(',') !== options.join(',')) {
      picker.replaceChildren();
      for (const algorithm of options) {
        const option = document.createElement('option');
        option.value = algorithm; option.textContent = algorithm;
        picker.append(option);
      }
      if (!options.length) { const empty = document.createElement('option'); empty.textContent = t('Noch keine Worker'); empty.value = ''; picker.append(empty); }
    }
    picker.value = selectedAlgorithm;
    picker.disabled = !options.length;
  }

  function render(data, fresh = true) {
    overview = data;
    renderStatusStrip(data);
    renderAlerts(data);
    renderLiveWorkers(data);
    renderEnergy();
    $('connection').className = `badge ${fresh ? 'online' : ''}`;
    $('connection').textContent = t(fresh ? 'Agent verbunden' : 'Aktualisiere …');
    $('updated').textContent = t(`Aktualisiert ${new Date(savedAt || Date.now()).toLocaleTimeString(window.SolarMinerI18n.locale)}`);
    $('notice').hidden = fresh;
    if (!fresh) $('notice').textContent = t('Letzter bekannter Stand · aktuelle Daten werden geprüft.');
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
      render(data);
      const optional = await Promise.allSettled(['/api/agent/local/power-control/settings', '/api/agent/local/node-assessment', '/api/agent/local/telemetry', '/api/agent/local/energy']
        .map(url => fetch(url, {cache: 'no-store', signal: AbortSignal.timeout(5000)})
          .then(answer => {
            if (!answer.ok) throw new Error(`HTTP ${answer.status}`);
            return answer.json();
          })));
      settings = optional[0].status === 'fulfilled' ? optional[0].value : null;
      assessment = optional[1].status === 'fulfilled' ? optional[1].value : null;
      telemetry = optional[2].status === 'fulfilled' ? optional[2].value : null;
      energy = optional[3].status === 'fulfilled' ? optional[3].value : null;
      energyError = optional[3].status === 'rejected'
        ? (optional[3].reason?.message === 'HTTP 404' ? 'HTTP 404 · PC-Agent neu starten oder aktualisieren.'
          : optional[3].reason?.name === 'TimeoutError' ? 'Zeitüberschreitung'
            : optional[3].reason?.message || 'Verbindung fehlgeschlagen') : null;
      render(data);
      $('connection').className = 'badge online';
      $('notice').hidden = true;
    } catch (error) {
      $('connection').className = 'badge offline';
      $('connection').textContent = t('Agent nicht erreichbar');
      const notice = $('notice');
      notice.hidden = false;
      notice.classList.add('error');
      notice.textContent = t(`Agent-Daten konnten nicht geladen werden: ${error.message}`);
      if (overview) renderStatusStrip(overview);
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
      if (source !== stream) return;
      try { savedAt = Date.now(); const data = JSON.parse(event.data); window.SolarMinerMiningCache?.write(data); render(data); }
      catch (_) { /* A manual refresh recovers from a malformed transport frame. */ }
    });
    source.onerror = () => {
      if (source !== stream) return;
      $('connection').className = 'badge';
      $('connection').textContent = t('Live-Verbindung wird wiederhergestellt …');
    };
  }

  $('refresh').addEventListener('click', refresh);
  $('energy-settings').addEventListener('click', editEnergySettings);
  document.addEventListener('solarminer:preferences-changed', () => { if (overview) render(overview); });
  const cached = window.SolarMinerMiningCache?.read();
  if (cached) { savedAt = cached.savedAt; render(cached.overview, false); }
  refresh();
  connectEvents();
  setInterval(() => { if (!document.hidden) refresh(); }, 5000);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { stream?.close(); stream = null; }
    else { refresh(); connectEvents(); }
  });
})();
