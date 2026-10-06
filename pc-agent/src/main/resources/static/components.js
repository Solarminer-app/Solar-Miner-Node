// Shared view layer for every PC-Agent page: one formatting, one status language, one table.
(() => {
  const i18n = () => window.SolarMinerI18n;
  const t = value => window.SolarMinerI18n?.t(value) ?? value;
  const number = (value, digits = 2) => Number.isFinite(value)
    ? new Intl.NumberFormat(i18n()?.locale || 'de-DE', {maximumFractionDigits: digits}).format(value) : '—';

  const hashUnits = ['H/s', 'kH/s', 'MH/s', 'GH/s', 'TH/s', 'PH/s', 'EH/s'];
  function hashrate(hashesPerSecond) {
    if (!(hashesPerSecond > 0) || !Number.isFinite(hashesPerSecond)) return '—';
    let index = 0;
    while (hashesPerSecond >= 1000 && index < hashUnits.length - 1) { hashesPerSecond /= 1000; index++; }
    return `${number(hashesPerSecond, hashesPerSecond >= 100 ? 0 : 2)} ${hashUnits[index]}`;
  }
  function difficulty(value) {
    if (!(value > 0) || !Number.isFinite(value)) return '—';
    const units = ['', 'k', 'M', 'G', 'T', 'P', 'E'];
    let index = 0;
    while (value >= 1000 && index < units.length - 1) { value /= 1000; index++; }
    return `${number(value, value >= 100 ? 0 : 1)}${units[index]}`;
  }
  const power = watts => Number.isFinite(watts) && watts > 0 ? `${number(watts, 0)} W` : '—';
  const temperature = celsius => Number.isFinite(celsius) && celsius > 0 ? `${number(celsius, 0)} °C` : '—';
  const latency = ms => Number.isFinite(ms) && ms >= 0 ? `${number(ms, 0)} ms` : '—';
  const percent = value => Number.isFinite(value) ? `${number(value, value >= 10 ? 0 : 1)} %` : '—';
  function efficiency(hashesPerSecond, watts) {
    if (!(hashesPerSecond > 0) || !(watts > 0)) return '—';
    let value = watts / hashesPerSecond, index = 0;
    while (value < 1 && index < hashUnits.length - 1) { value *= 1000; index++; }
    return `${number(value, value >= 100 ? 0 : 2)} J/${hashUnits[index].replace('/s', '')}`;
  }
  /**
   * Gross revenue per kilowatt-hour a worker consumes — the only figure that makes coins on
   * different hardware comparable. The worker share is taken from the coin forecast exactly as
   * the dashboard splits it, so per-device values always add up to the coin total.
   */
  function workerUsdPerDay(hashesPerSecond, forecast) {
    if (!(hashesPerSecond > 0) || !forecast?.available || !(forecast.usdPerDay > 0) || !(forecast.hashrateHps > 0)) return null;
    return forecast.usdPerDay * hashesPerSecond / forecast.hashrateHps;
  }
  function revenuePerKwh(usdPerDay, watts) {
    if (!(usdPerDay > 0) || !(watts > 0)) return null;
    return usdPerDay / (24 * watts / 1_000);
  }
  function moneyPerKwh(value) {
    if (!Number.isFinite(value) || value <= 0) return '—';
    const digits = value >= 1 ? 2 : value >= 0.01 ? 3 : 5;
    const money = window.SolarMinerPreferences?.money;
    return money ? `${money(value, 'USD', {maximumFractionDigits: digits})} /kWh` : `${number(value, digits)} USD/kWh`;
  }
  function shares(counters) {
    if (!counters || (counters.accepted == null && counters.rejected == null && counters.stale == null)) return null;
    const total = [counters.accepted, counters.rejected, counters.stale].filter(value => value != null).reduce((sum, value) => sum + value, 0);
    const rejectedShare = counters.rejected != null && total > 0 ? counters.rejected / total * 100 : null;
    return {
      text: [counters.accepted == null ? null : `${number(counters.accepted, 0)} akzeptiert`,
        counters.rejected == null ? null : `${number(counters.rejected, 0)} abgelehnt`,
        counters.stale == null ? null : `${number(counters.stale, 0)} veraltet`].filter(Boolean).join(' · '),
      rejectedShare
    };
  }

  const statusLabels = {MINING: 'Mining aktiv', PAUSED: 'Pausiert', STOPPED: 'Gestoppt', ERROR: 'Fehler'};
  function statusPill(status, override) {
    const pill = element('span', `pill status-${String(status || 'unknown').toLowerCase()}`);
    pill.append(element('i', 'pill-dot'), element('span', '', t(override || statusLabels[status] || 'Unbekannt')));
    return pill;
  }

  function element(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = t(text);
    return node;
  }
  function panel({kicker, title, action, className = '', labelId}) {
    const section = element('section', `panel ${className}`.trim());
    if (labelId) section.setAttribute('aria-labelledby', labelId);
    if (kicker || title || action) {
      const head = element('div', 'panel-head');
      const copy = element('div');
      if (kicker) copy.append(element('p', 'kicker', kicker));
      if (title) { const heading = element('h2', '', title); if (labelId) heading.id = labelId; copy.append(heading); }
      head.append(copy);
      if (action) head.append(action);
      section.append(head);
    }
    return section;
  }
  function metric({label, value, detail, tone = '', action}) {
    const card = element('article', `metric${tone ? ` ${tone}` : ''}`);
    card.append(element('span', 'metric-label', label));
    card.append(element('strong', 'metric-value', value));
    if (detail) card.append(element('small', 'metric-detail', detail));
    if (action) card.append(action);
    return card;
  }

  /** Session-only sparkline. Missing samples break the line instead of drawing them as zero. */
  function sparkline(values, {height = 96, label} = {}) {
    const width = 600, top = 8, bottom = height - 8;
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', `0 0 ${width} ${height}`);
    svg.setAttribute('preserveAspectRatio', 'none');
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', t(label || 'Messverlauf seit Öffnen dieser Seite'));
    const valid = values.filter(Number.isFinite);
    if (!valid.length) return svg;
    const low = Math.min(...valid), high = Math.max(...valid), span = high - low || Math.max(1, Math.abs(high) * .1);
    const step = values.length > 1 ? width / (values.length - 1) : 0;
    let open = false;
    const path = values.map((value, index) => {
      if (!Number.isFinite(value)) { open = false; return ''; }
      const command = `${open ? 'L' : 'M'}${(index * step).toFixed(1)} ${(bottom - (value - low) / span * (bottom - top)).toFixed(1)}`;
      open = true;
      return command;
    }).join(' ');
    const grid = document.createElementNS(svg.namespaceURI, 'path');
    grid.setAttribute('d', `M0 ${top}H${width} M0 ${(top + bottom) / 2}H${width} M0 ${bottom}H${width}`);
    grid.setAttribute('class', 'spark-grid');
    const line = document.createElementNS(svg.namespaceURI, 'path');
    line.setAttribute('d', path); line.setAttribute('class', 'spark-line');
    svg.append(grid, line);
    const last = values.at(-1);
    if (Number.isFinite(last)) {
      const dot = document.createElementNS(svg.namespaceURI, 'circle');
      dot.setAttribute('cx', String(width)); dot.setAttribute('r', '3.5');
      dot.setAttribute('cy', (bottom - (last - low) / span * (bottom - top)).toFixed(1));
      dot.setAttribute('class', 'spark-dot');
      svg.append(dot);
    }
    return svg;
  }

  /**
   * One table implementation for dashboard, workers and pools so sorting, alignment
   * and the "no value" language never diverge between pages.
   */
  function table({columns, rows, empty, sortKey = null, sortDirection = 'desc', rowClass, onRow}) {
    const scroll = element('div', 'table-scroll');
    const table = element('table', 'data-table');
    const head = document.createElement('thead'), headRow = document.createElement('tr');
    for (const column of columns) {
      const cell = document.createElement('th');
      cell.scope = 'col';
      cell.textContent = column.label;
      if (column.align) cell.dataset.align = column.align;
      if (column.width) cell.style.width = column.width;
      if (column.sortable) {
        cell.setAttribute('aria-sort', column.key === sortKey ? (sortDirection === 'asc' ? 'ascending' : 'descending') : 'none');
        const button = element('button', 'table-sort', column.label);
        button.type = 'button';
        button.addEventListener('click', () => column.onSort?.());
        cell.replaceChildren(button);
      }
      headRow.append(cell);
    }
    head.append(headRow);
    const body = document.createElement('tbody');
    if (!rows.length) {
      const row = document.createElement('tr'), cell = document.createElement('td');
      cell.colSpan = columns.length; cell.className = 'table-empty'; cell.textContent = t(empty || 'Keine Einträge.');
      row.append(cell); body.append(row);
    }
    for (const [index, row] of rows.entries()) {
      const tr = document.createElement('tr');
      if (rowClass) tr.className = rowClass(row, index) || '';
      if (onRow) { tr.tabIndex = 0; tr.addEventListener('click', () => onRow(row)); tr.addEventListener('keydown', event => { if (event.key === 'Enter') onRow(row); }); }
      for (const column of columns) {
        const cell = document.createElement('td');
        if (column.align) cell.dataset.align = column.align;
        const value = column.render(row, index);
        if (value == null) cell.textContent = '—';
        else if (value instanceof Node) cell.append(value);
        else cell.textContent = String(value);
        tr.append(cell);
      }
      body.append(tr);
    }
    table.append(head, body);
    scroll.append(table);
    return scroll;
  }

  /** Tabs with roving arrow-key focus; every page uses the same control. */
  function tabs({items, active, onChange, label}) {
    const nav = element('nav', 'tabs');
    nav.setAttribute('aria-label', t(label || 'Bereich'));
    const buttons = new Map();
    for (const item of items) {
      const button = element('button', '', item.label);
      button.type = 'button'; button.dataset.tab = item.key;
      button.setAttribute('aria-controls', `tab-panel-${item.key}`);
      button.addEventListener('click', () => onChange(item.key));
      buttons.set(item.key, button);
      nav.append(button);
    }
    nav.addEventListener('keydown', event => {
      if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
      const keys = items.map(item => item.key);
      const index = keys.findIndex(key => key === active);
      const next = keys[(index + (event.key === 'ArrowRight' ? 1 : keys.length - 1)) % keys.length];
      event.preventDefault();
      onChange(next);
      buttons.get(next).focus();
    });
    const setActive = key => {
      active = key;
      for (const item of items) {
        const button = buttons.get(item.key);
        const selected = item.key === key;
        button.classList.toggle('selected', selected);
        button.setAttribute('aria-current', selected ? 'page' : 'false');
        button.tabIndex = selected ? 0 : -1;
      }
    };
    setActive(active);
    nav.setActive = setActive;
    return nav;
  }

  /** Coin chips plus a search field: the shared way to jump between coins, miners, workers and pools. */
  function scopeBar({coins, active, onCoin, onSearch, placeholder, resultLabel}) {
    const bar = element('div', 'scope-bar');
    const chips = element('div', 'scope-chips');
    chips.setAttribute('role', 'group');
    chips.setAttribute('aria-label', t('Coin wählen'));
    for (const coin of coins) {
      const chip = element('button', `scope-chip${coin.id === active ? ' selected' : ''}`, coin.ticker || coin.name);
      chip.type = 'button';
      chip.dataset.coin = coin.id;
      chip.title = coin.name;
      chip.setAttribute('aria-pressed', String(coin.id === active));
      if (coin.running) chip.classList.add('running');
      chip.addEventListener('click', () => onCoin(coin.id));
      chips.append(chip);
    }
    const search = element('input', 'scope-search');
    search.type = 'search';
    search.placeholder = t(placeholder || 'Worker, Miner oder Pool suchen');
    search.setAttribute('aria-label', t(placeholder || 'Worker, Miner oder Pool suchen'));
    search.addEventListener('input', () => onSearch(search.value.trim()));
    const counter = element('span', 'scope-count', resultLabel || '');
    counter.setAttribute('aria-live', 'polite');
    bar.append(chips, search, counter);
    bar.search = search;
    bar.setCount = value => { counter.textContent = t(value); };
    bar.setActive = (coinId, runningIds = []) => {
      for (const chip of chips.children) {
        chip.classList.toggle('selected', chip.dataset.coin === coinId);
        chip.classList.toggle('running', runningIds.includes(chip.dataset.coin));
        chip.setAttribute('aria-pressed', String(chip.dataset.coin === coinId));
      }
    };
    return bar;
  }
  function installSearchShortcut(bar) {
    document.addEventListener('keydown', event => {
      if (event.key === '/' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName)) {
        event.preventDefault();
        bar.search.focus();
      }
    });
  }

  const gpuKey = gpu => `${gpu.vendor}:${gpu.index}`;
  const sensorKey = value => String(value).toLowerCase().replace(/[^a-z0-9]+/g, '_');

  /**
   * A worker temperature is only real when a sensor reports it. GPU workers never had a
   * temperature in the miner payload, so the host sensor is used and the source is named.
   */
  function temperatureOf(worker, telemetry, gpus) {
    if (Number(worker.temperatureCelsius) > 0) return {value: Number(worker.temperatureCelsius), source: 'Miner'};
    const metrics = telemetry?.metrics || {};
    const candidates = [];
    if (worker.hardwareType === 'CPU') candidates.push(metrics['cpu.temperature']);
    else {
      const gpu = (gpus || telemetry?.gpus || []).find(card => card.deviceId === worker.deviceId);
      if (gpu) {
        if (gpu.vendor === 'NVIDIA') candidates.push(metrics[`gpu.nvidia.${gpu.index}.temperature`]);
        for (const [key, metric] of Object.entries(metrics)) {
          if (!/temperature$/i.test(key) || !key.startsWith('gpu.amd.') && !key.startsWith('hardware.')) continue;
          const model = sensorKey(gpu.model || '');
          if (model.length > 4 && key.includes(model)) candidates.push(metric);
        }
        if (!gpu.model) for (const [key, metric] of Object.entries(metrics))
          if (key.startsWith('gpu.amd.') && /temperature$/i.test(key)) candidates.push(metric);
      }
    }
    const usable = candidates.find(metric => metric?.available && Number.isFinite(metric.value) && metric.value > 0);
    if (usable) return {value: Number(usable.value), source: 'Host-Sensor'};
    return {value: null, source: null};
  }

  /** Normalized worker rows shared by the dashboard and the worker page. */
  function workerRows(overview, telemetry) {
    const gpus = overview.gpus || [];
    return (overview.stats?.workers || []).map(worker => {
      const heat = temperatureOf(worker, telemetry, gpus);
      const watts = Number(worker.approximatedPowerUsageWatts) > 0 ? Number(worker.approximatedPowerUsageWatts) : null;
      const hashes = (Number(worker.terahashPerSecond) || 0) * 1e12;
      const coin = (overview.coins || []).find(entry => entry.algorithm === worker.currentAlgorithm
        && (entry.device === worker.hardwareType || entry.id === 'monero' && worker.hardwareType === 'CPU'));
      const pool = worker.pool || {};
      return {
        id: worker.deviceId || worker.workerDisplayName,
        name: worker.workerDisplayName || 'Worker',
        coin: coin?.id || '', coinName: coin?.name || '—', ticker: coin?.ticker || '',
        algorithm: worker.currentAlgorithm || '—',
        hardwareType: worker.hardwareType || '—', hardwareModel: worker.hardwareModel || '',
        deviceId: worker.deviceId || '',
        status: worker.miningStatus,
        hashrateHps: hashes,
        temperature: heat,
        watts, powerTargetWatts: Number(worker.powerTargetWatts) || null,
        shares: {accepted: worker.acceptedShares, rejected: worker.rejectedShares, stale: pool.staleShares},
        difficulty: pool.difficulty ?? null, bestShare: pool.bestShareDifficulty ?? null, latency: pool.latencyMs ?? null,
        pools: (worker.pools || []).map(entry => entry.poolUrl).filter(Boolean),
        raw: worker
      };
    });
  }

  /** One row per coin describing how its miners currently reach a pool. */
  function poolRows(overview) {
    const pearl = overview.pearl || {};
    const states = coin => coin === 'pearl' ? pearl.gpus || [] : overview.gpuCoins?.[coin]?.gpus || [];
    return (overview.coins || []).map(coin => {
      const gpuStates = states(coin.id);
      const workers = (overview.stats?.workers || []).filter(worker =>
        (coin.id === 'monero' ? worker.hardwareType === 'CPU' : false)
        || (coin.id !== 'monero' && (overview.coins.find(entry => entry.id === coin.id)?.algorithm || '')
          .toLowerCase() === String(worker.currentAlgorithm || '').toLowerCase()));
      const running = coin.id === 'pearl' ? Boolean(pearl.running) : coin.id === 'monero'
        ? coin.status === 'MINING' : Boolean(overview.gpuCoins?.[coin.id]?.running);
      const connected = coin.id === 'monero' ? (running && coin.status === 'MINING' ? 1 : 0)
        : gpuStates.filter(gpu => gpu.running && gpu.poolHealthy).length;
      const selected = coin.id === 'monero' ? 1 : gpuStates.filter(gpu => gpu.selected).length;
      const pool = workers.map(worker => worker.pool).find(entry => entry?.difficulty != null) || {};
      const configuration = coin.id === 'monero' ? overview.moneroConfiguration
        : coin.id === 'pearl' ? overview.pearlConfiguration : overview.gpuCoins?.[coin.id]?.configuration;
      return {
        coin: coin.id, coinName: coin.name, ticker: coin.ticker, algorithm: coin.algorithm, device: coin.device,
        configured: Boolean(coin.configured), experimental: Boolean(coin.experimental),
        proxyUrl: overview.proxy?.[`${coin.id}Url`] || overview.proxy?.moneroUrl && coin.id === 'monero' || '',
        poolUrl: configuration?.poolUrl || '',
        reachable: Boolean(overview.proxy?.reachable),
        feeReady: Boolean(overview.proxy?.[`${coin.id}FeeReady`]),
        running, selectedWorkers: selected, connectedWorkers: connected,
        latency: pool.latencyMs ?? null, difficulty: pool.difficulty ?? null,
        detail: coin.id === 'pearl' ? pearl.connectionDetail : overview.gpuCoins?.[coin.id]?.minerError || '',
        payout: (overview.payoutDefaults || []).find(entry => entry.coin === coin.id) || null
      };
    });
  }

  /** A miner instance is a coin paired with one installed build; several builds per coin are manageable. */
  function instances(overview) {
    const rows = [];
    for (const coin of overview.coins || []) {
      const readiness = coin.id === 'monero' ? overview.monero : coin.id === 'pearl' ? overview.pearl
        : overview.gpuCoins?.[coin.id];
      for (const option of coin.miners || []) {
        rows.push({
          key: `${coin.id}/${option.id}`,
          coin: coin.id, coinName: coin.name, ticker: coin.ticker, algorithm: coin.algorithm, device: coin.device,
          minerId: option.id, minerName: option.name, developerFeePercent: option.developerFeePercent,
          advantages: option.advantages || [], disadvantages: option.disadvantages || [],
          projectUrl: option.projectUrl,
          installed: Boolean(option.installed), selectable: option.selectable !== false,
          unavailableReason: option.unavailableReason || '',
          experimental: Boolean(option.experimental),
          active: coin.selectedMiner?.id === option.id,
          status: coin.status,
          running: coin.id === 'pearl' ? Boolean(overview.pearl?.running) : coin.status === 'MINING',
          configured: Boolean(coin.configured),
          downloadStatus: option.downloadStatus || readiness?.downloadStatus || 'PENDING',
          downloadProgress: option.downloadProgress ?? readiness?.downloadProgress ?? 0,
          downloadDetail: option.downloadDetail || readiness?.downloadDetail || ''
        });
      }
    }
    return rows;
  }

  function coinBlockers(coin, overview) {
    const blocked = [];
    const standalone = overview.proxy?.mode === 'standalone';
    const gpuCoin = ['pearl', 'ravencoin', 'ethereumclassic'].includes(coin.id);
    if (!overview.proxy?.reachable) blocked.push('SolarMiner-Proxy nicht erreichbar');
    else if (standalone && overview.proxy?.managedStatus !== 'running') blocked.push('Lokaler Proxy läuft nicht');
    if ((standalone || gpuCoin) && !overview.proxy?.[`${coin.id}FeeReady`]) blocked.push(`${coin.name}-Fee-Ziel nicht geladen`);
    if (!coin.configured) blocked.push('Pool-Konfiguration fehlt');
    if (!coin.binaryAvailable) blocked.push('Miner-Binary fehlt');
    return blocked;
  }

  /** Miner display names carry a tool prefix and a device suffix; tables need the readable part. */
  function shortWorkerName(row) {
    return String(row.name || '').replace(/^(SRBMiner|XMRig)\s+/i, '').replace(/\s*\((NVIDIA|AMD):[0-9]+\)$/i, '');
  }
  function shareLines(counters) {
    const summary = shares(counters);
    if (!summary) return null;
    const wrap = element('div', 'cell-lines');
    wrap.append(element('strong', '', counters.accepted == null ? '—' : `${number(counters.accepted, 0)} akzeptiert`));
    if (counters.rejected != null) wrap.append(element('span', '', `${number(counters.rejected, 0)} abgelehnt`));
    if (counters.stale != null) wrap.append(element('span', '', `${number(counters.stale, 0)} veraltet`));
    return wrap;
  }

  // A paused miner keeps reporting its devices, which would list one GPU twice under two coins and imply capacity
  // that does not exist. Only a live miner process owns a device, so operation lists use exactly these states.
  const operatingStates = new Set(['MINING', 'ERROR']);
  function operatingRows(rows) { return rows.filter(row => operatingStates.has(row.status)); }

  /** Hardware that runs in no miner has to be named, otherwise a running-only list looks like lost hardware. */
  function idleDeviceNotice(el, overview) {
    const idle = ((overview && overview.gpus) || []).filter(card => !card.running);
    el.hidden = idle.length === 0;
    if (!idle.length) return;
    el.className = 'notice-line warn';
    el.replaceChildren(
      element('span', '', t(idle.length === 1 ? `${idle.length} GPU läuft gerade in keinem Miner.`
        : `${idle.length} GPUs laufen gerade in keinem Miner.`)),
      Object.assign(element('a', 'button subtle', 'Geräte zuteilen →'), {href: '/hardware.html'})
    );
  }

  window.SolarMinerUI = {
    t, number, hashrate, difficulty, power, temperature, latency, percent, efficiency,
    workerUsdPerDay, revenuePerKwh, moneyPerKwh, shares,
    statusLabels, statusPill, element, panel, metric, sparkline, table, tabs, scopeBar, installSearchShortcut,
    temperatureOf, workerRows, poolRows, instances, coinBlockers, gpuKey, sensorKey, shortWorkerName, shareLines,
    operatingRows, idleDeviceNotice
  };
})();
