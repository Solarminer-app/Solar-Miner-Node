// Mining workspace: one miner instance = one coin paired with one installed build.
// The page is rendered from the overview snapshot; every control maps to one documented endpoint.
const $ = id => document.getElementById(id);
const ui = window.SolarMinerUI;
const poolCatalog = window.SolarMinerPoolCatalog;
const i18n = window.SolarMinerI18n;
const t = i18n.t;

const gpuCoinIds = ['ravencoin', 'ethereumclassic', 'decred', 'quantus'];
const coinIds = ['monero', 'pearl', ...gpuCoinIds];
const fmt = (value, digits = 1) => new Intl.NumberFormat(i18n.locale, {maximumFractionDigits: digits}).format(value);

let latest = null, overviewFresh = false, overviewSavedAt = null;
let telemetry = null, telemetryLoading = false;
let busy = false, refreshing = false, refreshQueued = false, stateRevision = 0;
let eventStream = null, initialCatalog = null, nodeAssessment = null, assessing = false;
let pendingInstall = null, defenderAction = null, defenderMessage = null;
let consoleState = null, randomXOptimization = null, lastOptimizationLoad = 0, optimizationBusy = false;
let deviceFilter = 'all', search = '';
const selectedInstallers = new Set();
const cachedControls = new Map();
const configForms = new Map();
const formMeta = new Map();
let scope = null;
let selectedCoin = coinIds.includes(location.hash.slice(1)) ? location.hash.slice(1) : null;
let selectedBuild = null;
let activeTab = 'status';

function invalidateMiningViewCache() {
    stateRevision++;
    window.SolarMinerMiningCache?.clear();
}

function notice(message, error = false) {
    const el = $('notice');
    el.textContent = t(message);
    el.className = `notice${error ? ' error' : ''}`;
    el.hidden = !message;
}

function el(tag, className, text) {
    return ui.element(tag, className, text);
}

function coin(data, id = selectedCoin) {
    return (data.coins || []).find(item => item.id === id);
}

function readiness(data, id = selectedCoin) {
    if (id === 'monero') return data.monero || {};
    if (id === 'pearl') return data.pearl || {};
    return data.gpuCoins?.[id] || {};
}

function gpuStates(data, id = selectedCoin) {
    if (id === 'pearl') return data.pearl?.gpus || [];
    if (gpuCoinIds.includes(id)) return data.gpuCoins?.[id]?.gpus || [];
    return [];
}

/** Workers of this coin only, normalized through the shared row model; the dashboard shows all of them. */
function coinWorkers(data, id = selectedCoin) {
    return ui.workerRows(data, telemetry).filter(row => row.coin === id);
}

function running(data, id = selectedCoin) {
    if (id === 'pearl') return Boolean(data.pearl?.running);
    return coin(data, id)?.status === 'MINING';
}

function connectionState(data, id = selectedCoin) {
    const state = readiness(data, id);
    const states = gpuStates(data, id);
    if (gpuCoinIds.includes(id) && (state.running || coin(data, id)?.status === 'ERROR'))
        return state.minerError || states.find(gpu => gpu.running)?.connectionDetail || 'Verbindung wird geprüft';
    if (id === 'pearl' && (state.running || coin(data, id)?.status === 'ERROR'))
        return state.minerError || state.connectionDetail || (state.poolHealthy ? 'Pool verbunden' : 'Verbindung wird geprüft');
    if (id === 'monero' && coin(data, id)?.status === 'ERROR' && data.monero?.minerError) return data.monero.minerError;
    if (!data.proxy?.reachable) return 'SolarMiner-Proxy nicht erreichbar';
    return data.proxy.mode === 'standalone' ? 'Lokaler Mining-Dienst bereit' : 'SolarMiner-Proxy erreichbar';
}

// ---------------------------------------------------------------- actions

async function action(path, success, params, body, feedbackId = null) {
    if (!overviewFresh) return notice('Warte auf aktuelle Agent-Daten, bevor du eine Änderung ausführst.', true);
    if (busy) return;
    invalidateMiningViewCache();
    busy = true;
    if (latest) render(latest, true);
    try {
        const url = new URL(path, location.origin);
        for (const [key, value] of Object.entries(params || {})) url.searchParams.set(key, String(value));
        const response = await fetch(url, {
            method: 'POST', headers: body ? {'Content-Type': 'application/json'} : {},
            body: body ? JSON.stringify(body) : undefined
        });
        if (!response.ok) {
            const errorBody = await response.json().catch(() => null);
            throw new Error(errorBody?.message || `HTTP ${response.status}`);
        }
        if (await response.json() !== true) throw new Error('Der Agent hat die Änderung abgelehnt.');
        notice(success);
        if (feedbackId) {
            const feedback = $(feedbackId);
            feedback.classList.remove('error');
            feedback.hidden = false;
            feedback.textContent = t(success);
        }
    } catch (error) {
        const message = error.message.startsWith('HTTP ') ? `${error.message}. Bitte Agent-Logs prüfen.` : error.message;
        notice(message, true);
        if (feedbackId) {
            const feedback = $(feedbackId);
            feedback.classList.add('error');
            feedback.hidden = false;
            feedback.textContent = t(message);
        }
    } finally {
        invalidateMiningViewCache();
        busy = false;
        await refresh();
    }
}

async function refresh() {
    if (busy || document.hidden) return;
    if (refreshing) {
        refreshQueued = true;
        return;
    }
    refreshing = true;
    const revision = stateRevision;
    try {
        const response = await fetch('/api/agent/local/overview', {cache: 'no-store'});
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const overview = await response.json();
        if (revision !== stateRevision) {
            refreshQueued = true;
            return;
        }
        applyOverview(overview);
    } catch (error) {
        if (revision !== stateRevision) {
            refreshQueued = true;
            return;
        }
        overviewFresh = false;
        if (latest) render(latest, false);
        $('connection').className = 'badge offline';
        $('connection').textContent = t('Agent nicht erreichbar');
        notice(`Daten konnten nicht geladen werden: ${error.message}`, true);
    } finally {
        refreshing = false;
        if (refreshQueued && !busy && !document.hidden) {
            refreshQueued = false;
            queueMicrotask(refresh);
        }
    }
}

function applyOverview(overview) {
    overviewSavedAt = Date.now();
    render(overview, true);
    window.SolarMinerMiningCache?.write(overview);
    if (initialCatalog) {
        initialCatalog = null;
    }
    refreshAssessment();
    pollConsole();
}

function connectEvents() {
    if (eventStream || document.hidden) return;
    const stream = new EventSource('/api/agent/local/events');
    eventStream = stream;
    stream.addEventListener('overview', event => {
        if (stream !== eventStream || busy) return;
        try {
            applyOverview(JSON.parse(event.data));
        } catch (_) { /* A reconnect or manual refresh recovers from malformed transport data. */
        }
    });
    stream.onerror = () => {
        if (stream !== eventStream) return;
        // A blocked or dropped stream must not freeze the page: fall back to polling and let a
        // failed request be the only thing that marks the snapshot as stale.
        disconnectEvents();
    };
}

function disconnectEvents() {
    if (!eventStream) return;
    eventStream.close();
    eventStream = null;
}

async function refreshAssessment() {
    if (assessing) return;
    assessing = true;
    try {
        const response = await fetch('/api/agent/local/node-assessment', {
            cache: 'no-store',
            signal: AbortSignal.timeout(5000)
        });
        if (response.ok) {
            nodeAssessment = await response.json();
            if (latest && !busy) render(latest);
        }
    } catch (_) { /* An optional Node assessment must not mark the local agent offline. */
    } finally {
        assessing = false;
    }
}

/** Host sensors fill the temperature gap the miner payload leaves open on the device cards. */
async function loadTelemetry() {
    if (telemetryLoading) return;
    telemetryLoading = true;
    try {
        const response = await fetch('/api/agent/local/telemetry', {
            cache: 'no-store',
            signal: AbortSignal.timeout(5000)
        });
        telemetry = response.ok ? await response.json() : null;
        if (latest && !busy && activeTab === 'devices') render(latest);
    } catch (_) { /* Sensor availability varies per platform; the cards show "—" instead. */
    } finally {
        telemetryLoading = false;
    }
}

// ---------------------------------------------------------------- view switching

function showView(view) {
    if (view === 'library') {
        location.hash = 'library';
    } else {
        selectedCoin = view;
        selectedBuild = null;
        location.hash = view;
    }
    if (latest) render(latest);
    else if (initialCatalog) renderInstances(initialCatalog);
}

function openBuild(coinId, minerId) {
    selectedCoin = coinId;
    selectedBuild = minerId;
    activeTab = 'status';
    location.hash = coinId;
    if (latest) render(latest);
}

window.SolarMinerMining = {
    get overview() {
        return latest;
    },
    openCoin: coinId => showView(coinIds.includes(coinId) ? coinId : 'library'),
    openBuild
};

// ---------------------------------------------------------------- instance list

function buildsFor(data, coinId) {
    const target = coin(data, coinId);
    return (target?.miners || []).map(option => ({
        coin: coinId,
        coinName: target.name, ticker: target.ticker, algorithm: target.algorithm, device: target.device,
        minerId: option.id, minerName: option.name, developerFeePercent: option.developerFeePercent,
        advantages: option.advantages || [], disadvantages: option.disadvantages || [], projectUrl: option.projectUrl,
        installed: Boolean(option.installed), selectable: option.selectable !== false,
        unavailableReason: option.unavailableReason || '', experimental: Boolean(option.experimental),
        active: target.selectedMiner?.id === option.id,
        configured: Boolean(target.configured),
        downloadStatus: option.downloadStatus || readiness(data, coinId).downloadStatus || 'PENDING',
        downloadProgress: option.downloadProgress ?? readiness(data, coinId).downloadProgress ?? 0,
        downloadDetail: option.downloadDetail || readiness(data, coinId).downloadDetail || ''
    }));
}

function renderInstances(data) {
    const coins = data.coins || [];
    if (!selectedCoin) selectedCoin = coins.find(c => c.binaryAvailable)?.id || coins[0]?.id || 'monero';
    if (selectedBuild && !buildsFor(data, selectedCoin).some(b => b.minerId === selectedBuild)) selectedBuild = null;

    const list = $('instance-list');
    const builds = buildsFor(data, selectedCoin).sort((a, b) => Number(b.installed) - Number(a.installed));
    const term = search.toLowerCase();
    const visible = builds.filter(row => !term
        || `${row.minerName} ${row.coinName} ${row.algorithm} ${row.device}`.toLowerCase().includes(term));
    list.replaceChildren();
    if (!visible.length) {
        list.append(el('p', 'muted', builds.length ? 'Kein Miner passt zur Suche.' : 'Für diesen Coin ist keine Mining-Software im Katalog.'));
    }
    for (const build of visible) {
        const selected = selectedBuild == null ? build.active : build.minerId === selectedBuild;
        const row = el('button', `instance-row${selected ? ' selected' : ''}`);
        row.type = 'button';
        row.setAttribute('aria-pressed', String(selected));
        const emblem = el('span', 'instance-emblem', build.device === 'CPU' ? 'CPU' : (build.ticker || 'GPU'));
        const copy = el('div', 'instance-copy');
        copy.append(el('strong', '', build.minerName));
        copy.append(el('small', '', `${build.coinName} · ${build.algorithm}${build.developerFeePercent != null ? ` · Dev-Fee ${fmt(build.developerFeePercent, 2)} %` : ''}`));
        const state = el('div', 'instance-state');
        if (build.active && running(data, build.coin)) state.append(ui.statusPill('MINING'));
        else if (build.active) state.append(ui.statusPill(coin(data, build.coin)?.status || 'STOPPED'));
        if (build.installed) {
            if (build.active) state.append(el('span', 'pill tone-ok', 'Aktiver Build'));
        } else if (build.downloadStatus === 'DOWNLOADING') {
            state.append(el('span', 'pill tone-warn', `Installation ${build.downloadProgress} %`));
        } else if (build.selectable) {
            state.append(el('span', 'pill', build.experimental ? 'Experimentell · installierbar' : 'Installierbar'));
        } else {
            state.append(el('span', 'pill', 'Nicht verfügbar'));
        }
        row.append(emblem, copy, state);
        row.addEventListener('click', () => {
            selectedBuild = build.minerId;
            activeTab = build.installed ? 'status' : 'setup';
            render(latest || data);
        });
        list.append(row);
    }
    scope?.setCount(`${builds.filter(row => row.installed).length} installiert · ${builds.length} im Katalog`);
}

// ---------------------------------------------------------------- instance detail

function detailBuild(data) {
    const builds = buildsFor(data, selectedCoin);
    return builds.find(row => row.minerId === selectedBuild) || builds.find(row => row.active) || builds[0] || null;
}

function renderDetail(data) {
    const root = $('instance-detail');
    const target = coin(data, selectedCoin);
    const build = detailBuild(data);
    root.replaceChildren();
    if (!target) {
        root.append(el('p', 'muted', 'Kein Coin ausgewählt. Öffne die Miner-Bibliothek.'));
        return;
    }
    const blockers = ui.coinBlockers(target, data);
    const isRunning = running(data, selectedCoin);
    const states = gpuStates(data, selectedCoin);
    const workers = coinWorkers(data, selectedCoin);

    const head = el('div', 'detail-head');
    const identity = el('div', 'detail-identity');
    identity.append(el('p', 'kicker', `${target.name} · ${target.device} · ${target.algorithm}`));
    identity.append(el('h2', '', build?.minerName || target.name));
    const sub = el('p', 'muted');
    sub.append(el('span', '', build ? (build.installed ? 'Installiert' : build.selectable ? 'Installierbar' : build.unavailableReason || 'Nicht verfügbar') : '—'));
    if (build && build.active) sub.append(el('span', 'dot-sep', '·'), el('span', '', 'Aktiver Build für diesen Coin'));
    if (build?.experimental) sub.append(el('span', 'dot-sep', '·'), el('span', 'warn-text', 'Experimentelle Mining-Route'));
    identity.append(sub);
    head.append(identity);

    const actions = el('div', 'detail-actions');
    const start = el('button', 'button primary', 'Starten');
    start.type = 'button';
    const stop = el('button', 'button subtle', 'Pausieren');
    stop.type = 'button';
    const remaining = states.some(gpu => gpu.selected && !gpu.running);
    start.disabled = busy || !overviewFresh || !build?.installed || blockers.length > 0
        || (target.device === 'GPU' ? !remaining : isRunning);
    stop.disabled = busy || !overviewFresh || (target.device === 'GPU' ? !states.some(gpu => gpu.running) : !isRunning);
    start.addEventListener('click', () => action(`/api/agent/local/miners/${selectedCoin}/resume`, 'Miner gestartet.'));
    stop.addEventListener('click', () => action(`/api/agent/local/miners/${selectedCoin}/pause`, 'Miner pausiert.'));
    actions.append(start, stop);
    if (build && build.installed && !build.active) {
        const choose = el('button', 'button subtle', 'Als aktiven Build wählen');
        choose.type = 'button';
        choose.disabled = busy || !overviewFresh;
        choose.addEventListener('click', () => action(`/api/agent/local/${selectedCoin}/miner`, `${build.minerName} ist der aktive Build für ${target.name}.`, {minerId: build.minerId}));
        actions.append(choose);
    }
    if (build && !build.installed && build.selectable) {
        const install = el('button', 'button primary', build.downloadStatus === 'DOWNLOADING' ? 'Installiert …' : 'Diesen Miner installieren');
        install.type = 'button';
        install.disabled = busy || !overviewFresh || build.downloadStatus === 'DOWNLOADING';
        install.addEventListener('click', () => {
            pendingInstall = {coin: selectedCoin, minerId: build.minerId};
            installBuild(selectedCoin, build.minerId);
        });
        actions.append(install);
    }
    head.append(actions);
    root.append(head);

    const strip = el('div', 'detail-strip');
    strip.append(ui.statusPill(target.status, selectedCoin === 'pearl' && data.pearl?.running && !data.pearl?.poolHealthy ? 'Verbinde mit Pool' : null));
    strip.append(el('span', 'strip-sep'));
    strip.append(el('span', 'strip-item', connectionState(data, selectedCoin)));
    if (target.device === 'GPU') {
        strip.append(el('span', 'strip-sep'));
        strip.append(el('span', 'strip-item', `${states.filter(gpu => gpu.running).length}/${states.filter(gpu => gpu.selected).length || states.length} GPUs aktiv`));
    }
    if (blockers.length) {
        strip.append(el('span', 'strip-sep'));
        strip.append(el('span', 'strip-item warn-text', blockers.join(' · ')));
    }
    root.append(strip);

    if (!build) {
        root.append(el('p', 'muted', 'Dieser Coin hat noch keinen Miner im Katalog.'));
        return;
    }
    if (!build.installed) {
        const hint = el('div', 'notice-line warn');
        hint.append(el('span', '', build.downloadStatus === 'DOWNLOADING'
            ? `Installation läuft (${build.downloadProgress} %).${build.downloadDetail ? ` ${build.downloadDetail}` : ''}`
            : 'Dieser Miner ist noch nicht installiert. Nach der Installation kannst du Pool, Wallet und Geräte konfigurieren.'));
        root.append(hint);
        if (build.downloadStatus === 'BLOCKED_BY_ANTIVIRUS') root.append(defenderHelp(selectedCoin));
    }

    const tabs = ui.tabs({
        items: [
            {key: 'status', label: 'Status'},
            {key: 'devices', label: 'Geräte'},
            {key: 'setup', label: 'Konfiguration'},
            {key: 'advanced', label: 'Erweitert'},
            {key: 'console', label: 'Konsole'}
        ],
        active: activeTab,
        onChange: key => {
            activeTab = key;
            if (key === 'devices' && !telemetry) loadTelemetry();
            render(latest || data);
        },
        label: 'Miner-Bereiche'
    });
    root.append(tabs);

    const panel = el('div', 'tab-panel');
    panel.id = `tab-panel-${activeTab}`;
    panel.setAttribute('role', 'tabpanel');
    // Cached form and console nodes are looked up by id, so the panel must be in the document first.
    root.append(panel);
    if (activeTab === 'status') renderStatusPanel(panel, data, target, build);
    if (activeTab === 'devices') renderDevicePanel(panel, data, target, build);
    if (activeTab === 'setup') configForm(panel, data, target, build);
    if (activeTab === 'advanced') renderAdvancedPanel(panel, data, target, build);
    if (activeTab === 'console') renderConsolePanel(panel, data, target);
}

function renderStatusPanel(panel, data, target, build) {
    const blockers = ui.coinBlockers(target, data);
    const workers = coinWorkers(data, selectedCoin);
    const hashrate = workers.reduce((sum, row) => sum + row.hashrateHps, 0);
    const watts = workers.reduce((sum, row) => sum + (row.watts || 0), 0);
    const targets = workers.reduce((sum, row) => sum + (row.powerTargetWatts || 0), 0);
    const accepted = workers.filter(row => row.shares.accepted != null);
    const rejected = workers.filter(row => row.shares.rejected != null);
    const pool = workers.find(row => row.difficulty != null) || {};
    const earnings = (data.earnings || []).find(entry => entry.coin === selectedCoin);
    const grid = el('div', 'kpi-strip');
    grid.append(ui.metric({
        label: 'Hashrate', value: ui.hashrate(hashrate),
        detail: hashrate > 0 ? `${workers.length} Worker · ${target.algorithm}` : 'Keine Hashrate gemeldet',
        tone: hashrate > 0 ? 'ok' : ''
    }));
    grid.append(ui.metric({
        label: 'Leistung', value: watts > 0 ? ui.power(watts) : '—',
        detail: targets > 0 ? `Leistungsziel ${ui.power(targets)}` : 'Gemessene Worker-Leistung'
    }));
    grid.append(ui.metric({
        label: 'Effizienz', value: ui.efficiency(hashrate, watts) || '—',
        detail: 'Hashrate pro Watt gemessener Workerleistung'
    }));
    grid.append(ui.metric({
        label: 'Shares', value: accepted.length === workers.length && workers.length
            ? ui.number(accepted.reduce((sum, row) => sum + Number(row.shares.accepted), 0), 0) : '—',
        detail: rejected.length === workers.length && workers.length
            ? `${ui.number(rejected.reduce((sum, row) => sum + Number(row.shares.rejected), 0), 0)} abgelehnt`
            : 'Nicht von der Miner-API gemeldet'
    }));
    grid.append(ui.metric({
        label: 'Difficulty', value: ui.difficulty(pool.difficulty),
        detail: pool.latency != null ? `Latenz ${ui.latency(pool.latency)}` : 'Pool-Difficulty wird nicht gemeldet'
    }));
    grid.append(ui.metric({
        label: 'Ertrag pro Tag',
        value: earnings?.available ? `${fmt(earnings.coinsPerDay, 6)} ${earnings.ticker}` : '—',
        detail: earnings?.available ? `≈ ${window.SolarMinerPreferences.moneyFromUsd(earnings.usdPerDay)} · brutto${earnings.stale ? ' · Daten veraltet' : ''}`
            : earnings?.unavailableReason || 'Netzwerkdaten werden geladen'
    }));
    const perKwh = ui.revenuePerKwh(earnings?.usdPerDay, watts);
    grid.append(ui.metric({
        label: 'Ertrag pro kWh', value: ui.moneyPerKwh(perKwh),
        detail: perKwh != null ? `brutto · bei gemessener Workerleistung${earnings.stale ? ' · Daten veraltet' : ''}`
            : running(data, selectedCoin) ? 'Hashrate oder gemessene Watt fehlen' : 'Nur bei laufendem Miner'
    }));
    panel.append(grid);

    const note = el('div', `notice-line${blockers.length ? ' warn' : running(data, selectedCoin) ? ' ok' : ''}`);
    note.append(el('span', '', blockers.length ? `Start verhindert: ${blockers.join(' · ')}`
        : running(data, selectedCoin) ? `Miner läuft. ${connectionState(data, selectedCoin)}.`
            : 'Der Miner läuft nicht. Starten übernimmt die gespeicherte Konfiguration.'));
    panel.append(note);

    const quality = el('div', 'quality-line');
    if (pool.bestShare != null) quality.append(el('span', 'strip-item', `Beste Share-Difficulty ${ui.difficulty(pool.bestShare)}`));
    if (pool.stale != null) quality.append(el('span', 'strip-item', `${ui.number(pool.stale, 0)} veraltete Shares`));
    if (pool.latency != null) quality.append(el('span', 'strip-item', `Pool-Latenz ${ui.latency(pool.latency)}`));
    if (quality.children.length) panel.append(quality);
}

function deviceMetrics(row) {
    const metrics = el('div', 'device-metrics');
    for (const [label, value, detail] of [
        ['Hashrate', ui.hashrate(row?.hashrateHps || 0), ''],
        ['Temperatur', row?.temperature?.value != null ? ui.temperature(row.temperature.value) : '—', row?.temperature?.source || ''],
        ['Leistung', ui.power(row?.watts), ''],
        ['Ertrag pro kWh', ui.moneyPerKwh(ui.revenuePerKwh(ui.workerUsdPerDay(row?.hashrateHps || 0, earnings), row?.watts)), '']
    ]) {
        const box = el('div');
        box.append(el('span', '', label), el('strong', '', value));
        if (detail) box.append(el('small', '', detail));
        metrics.append(box);
    }
    return metrics;
}

function renderDevicePanel(panel, data, target, build) {
    const workers = coinWorkers(data, selectedCoin);
    const earnings = (data.earnings || []).find(entry => entry.coin === selectedCoin);
    const grid = el('div', 'device-grid');
    if (target.device !== 'GPU') {
        const card = el('article', `device-card${running(data, selectedCoin) ? ' running' : ''}`);
        const head = el('div', 'device-head');
        head.append(el('strong', '', `CPU · ${data.platform || 'Unbekannt'}`));
        head.append(ui.statusPill(target.status));
        card.append(head, deviceMetrics(workers[0], earnings));
        card.append(el('p', 'muted', 'Monero läuft auf der CPU. Temperaturen stammen aus der lokalen Sensor-API, der Miner selbst meldet keine.'));
        const actions = el('div', 'device-actions');
        const start = el('button', 'button subtle', 'CPU starten');
        start.type = 'button';
        start.disabled = busy || !overviewFresh || target.status === 'MINING' || ui.coinBlockers(target, data).length > 0;
        start.addEventListener('click', () => action('/api/agent/local/miners/monero/resume', 'Monero gestartet.'));
        const stop = el('button', 'button subtle', 'CPU pausieren');
        stop.type = 'button';
        stop.disabled = busy || !overviewFresh || target.status !== 'MINING';
        stop.addEventListener('click', () => action('/api/agent/local/miners/monero/pause', 'Monero pausiert.'));
        actions.append(start, stop);
        card.append(actions);
        grid.append(card);
        panel.append(grid);
        return;
    }
    const states = gpuStates(data, selectedCoin);
    if (!states.length) {
        panel.append(el('p', 'muted', 'Keine GPU erkannt. Prüfe Treiber und die Seite Leistungsgrenzen.'));
        return;
    }
    for (const gpu of states) {
        // GpuState carries vendor+index; only overview.gpus links them to the worker deviceId.
        const limits = (data.gpus || []).find(item => item.vendor === gpu.vendor && item.index === gpu.index);
        const worker = workers.find(row => row.deviceId === limits?.deviceId);
        const card = el('article', `device-card${gpu.running ? ' running' : ''}${gpu.status === 'ERROR' ? ' error' : ''}`);
        const head = el('div', 'device-head');
        head.append(el('strong', '', `${gpu.vendor} ${gpu.index} · ${gpu.model || 'GPU'}`));
        head.append(ui.statusPill(gpu.status, !gpu.selected ? 'Nicht ausgewählt'
            : gpu.manuallyPaused ? 'Manuell pausiert'
                : gpu.running && !gpu.poolHealthy ? 'Verbinde mit Pool' : null));
        card.append(head, deviceMetrics(worker, earnings));
        if (gpu.lastError) card.append(el('p', 'device-error', gpu.lastError));
        else if (gpu.connectionDetail) card.append(el('p', gpu.running && gpu.poolHealthy ? 'device-detail' : 'device-error', gpu.connectionDetail));
        if (limits?.regulationError) card.append(el('p', 'device-error', limits.regulationError));
        const actions = el('div', 'device-actions');
        const start = el('button', 'button subtle', 'GPU starten');
        start.type = 'button';
        start.disabled = busy || !overviewFresh || !gpu.selected || gpu.running || ui.coinBlockers(target, data).length > 0;
        start.addEventListener('click', () => action(`/api/agent/local/${selectedCoin}/gpus/${gpu.vendor}/${gpu.index}/resume`, `${gpu.vendor} ${gpu.index} gestartet.`));
        const stop = el('button', 'button subtle', 'GPU pausieren');
        stop.type = 'button';
        stop.disabled = busy || !overviewFresh || !gpu.running;
        stop.addEventListener('click', () => action(`/api/agent/local/${selectedCoin}/gpus/${gpu.vendor}/${gpu.index}/pause`, `${gpu.vendor} ${gpu.index} pausiert.`));
        const log = el('button', 'button subtle', 'Log dieser GPU');
        log.type = 'button';
        log.addEventListener('click', () => {
            consoleTarget = `${selectedCoin}-${gpu.vendor}-${gpu.index}`;
            activeTab = 'console';
            render(latest || data);
        });
        actions.append(start, stop, log);
        card.append(actions);
        grid.append(card);
    }
    panel.append(grid, powerForm(data, target));
}

function powerForm(data, target) {
    const states = gpuStates(data, selectedCoin).filter(gpu => gpu.selected);
    const limits = (data.gpus || []).filter(gpu => states.some(state => state.vendor === gpu.vendor && state.index === gpu.index));
    const form = el('form', 'power-form');
    const min = limits.reduce((sum, gpu) => sum + (gpu.minWatts || 0), 0);
    const max = limits.reduce((sum, gpu) => sum + (gpu.maxWatts || 0), 0);
    const current = coinWorkers(data, selectedCoin).reduce((sum, worker) => sum + (Number(worker.powerTargetWatts) || 0), 0);
    const head = el('div', 'section-head');
    const headCopy = el('div');
    headCopy.append(el('p', 'kicker', 'GPU-Leistung'), el('h3', '', 'Leistungsziel'));
    head.append(headCopy);
    form.append(head);
    form.append(el('p', 'muted', `Erlaubter Bereich ${fmt(min, 0)}–${fmt(max, 0)} W. Der Wert gilt für alle gestarteten GPUs dieses Coins zusammen.`));
    const row = el('div', 'input-row');
    const input = document.createElement('input');
    input.type = 'number';
    input.id = 'power-input';
    input.min = String(min);
    input.max = String(max);
    input.step = '1';
    input.value = String(current > 0 ? Math.round(current) : Math.round((min + max) / 2));
    input.setAttribute('aria-label', t('Leistungsziel in Watt'));
    const submit = el('button', 'button subtle', 'Leistungsziel übernehmen');
    submit.type = 'submit';
    submit.disabled = busy || !overviewFresh || !limits.length;
    row.append(input, submit);
    const feedback = el('p', 'notice form-feedback');
    feedback.id = 'power-feedback';
    feedback.hidden = true;
    form.append(row, feedback);
    form.addEventListener('submit', event => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        action(`/api/agent/local/miners/${selectedCoin}/power-target`, 'GPU-Leistungsziel übernommen.', {powerTarget: input.value}, null, 'power-feedback');
    });
    return form;
}

function defenderHelp(coinId) {
    const help = el('details', 'package-help');
    help.append(el('summary', '', 'Windows-Sicherheit hat den Download blockiert – sicher prüfen'));
    help.append(el('p', 'muted', 'Prüfe zuerst Erkennungsname und Datei unter Windows-Sicherheit → Viren- & Bedrohungsschutz → Schutzverlauf. Gib nur eine verifizierte Datei frei.'));
    const directory = String(readiness(latest, coinId).installDirectory || '');
    if (['localhost', '127.0.0.1', '[::1]'].includes(location.hostname) && /^[A-Za-z]:\\/.test(directory) && !/[\x00-\x1f]/.test(directory)) {
        const request = el('button', 'button subtle', defenderAction === coinId ? 'Windows-Freigabe wird angefordert …' : 'Diesen Installationsordner ausnehmen');
        request.type = 'button';
        request.disabled = defenderAction !== null;
        request.addEventListener('click', () => addDefenderExclusion(coinId));
        help.append(request);
        if (defenderMessage?.coinId === coinId) help.append(el('p', defenderMessage.success ? 'muted' : 'error', defenderMessage.text));
    }
    return help;
}

async function addDefenderExclusion(coinId) {
    defenderAction = coinId;
    defenderMessage = null;
    if (latest) render(latest);
    try {
        const response = await fetch(`/api/agent/local/${encodeURIComponent(coinId)}/defender-exclusion`, {method: 'POST'});
        const result = await response.json().catch(() => null);
        if (!response.ok || !result?.success) throw new Error(result?.message || `HTTP ${response.status}`);
        defenderMessage = {coinId, success: true, text: result.message};
    } catch (error) {
        defenderMessage = {coinId, success: false, text: error.message};
    } finally {
        defenderAction = null;
        await refresh();
    }
}

// ---------------------------------------------------------------- configuration wizard

function meta(coinId) {
    if (!formMeta.has(coinId)) formMeta.set(coinId, {
        dirty: false, initialized: false, hydrated: '', pool: 'solarminer',
        devices: new Set(), devicesDirty: false, devicesVersion: ''
    });
    return formMeta.get(coinId);
}

function configurationOf(data, coinId) {
    if (coinId === 'monero') return data.moneroConfiguration;
    if (coinId === 'pearl') return data.pearlConfiguration;
    return data.gpuCoins?.[coinId]?.configuration;
}

function configForm(panel, data, target, build) {
    const coinId = target.id;
    const state = meta(coinId);
    const configuration = configurationOf(data, coinId);
    const cached = configForms.get(coinId);
    if (cached && cached.dataset.build === (build?.minerId || '')) {
        panel.append(cached);
        hydrateConfig(cached, data, target, configuration);
        return;
    }
    const form = el('form', 'config-form');
    form.id = `${coinId}-config`;
    form.dataset.build = build?.minerId || '';
    const steps = el('div', 'steps');
    const stepPool = el('div', 'step');
    stepPool.append(el('b', '', '1'), el('span', '', 'Pool & Auszahlung'));
    const stepWallet = el('div', 'step');
    stepWallet.append(el('b', '', '2'), el('span', '', 'Wallet & Worker'));
    const stepDevices = el('div', 'step');
    stepDevices.append(el('b', '', '3'), el('span', '', 'Geräte'));
    steps.append(stepPool, stepWallet, stepDevices);
    if (target.device !== 'GPU') stepDevices.classList.add('is-muted');
    form.append(steps);

    const poolHead = el('div', 'section-head');
    const poolHeadCopy = el('div');
    poolHeadCopy.append(el('p', 'kicker', 'SCHRITT 1'), el('h3', '', 'Mining-Pool'));
    poolHead.append(poolHeadCopy);
    form.append(poolHead);
    const choices = el('div', 'choice-grid');
    choices.id = `${coinId}-pool-choices`;
    choices.setAttribute('role', 'group');
    choices.setAttribute('aria-label', t('Mining-Pool wählen'));
    form.append(choices);

    const payoutNote = el('p', 'payout-note muted');
    payoutNote.id = `${coinId}-payout-note`;
    form.append(payoutNote);
    const consent = el('label', 'standard-consent');
    const consentInput = document.createElement('input');
    consentInput.type = 'checkbox';
    consentInput.id = `${coinId}-standard-consent`;
    consent.append(consentInput, el('span', '', 'Ich bestätige: Ohne eigene Wallet geht meine gesamte Mining-Auszahlung an das angezeigte SolarMiner-Standardziel. Die vollständigen Gebühren stehen unter Erweitert.'));
    consent.hidden = true;
    form.append(consent);

    const walletHead = el('div', 'section-head');
    const walletHeadCopy = el('div');
    walletHeadCopy.append(el('p', 'kicker', 'SCHRITT 2'), el('h3', '', 'Auszahlung & Worker'));
    walletHead.append(walletHeadCopy);
    form.append(walletHead);
    const wallet = document.createElement('input');
    wallet.type = 'text';
    wallet.id = `${coinId}-wallet`;
    wallet.autocomplete = 'off';
    wallet.spellcheck = false;
    wallet.placeholder = target.ticker ? `${target.ticker}-Wallet (z. B. 4… oder 0x…)` : 'Wallet';
    wallet.setAttribute('aria-label', t(`${target.name} Wallet`));
    const walletField = el('div', 'field');
    walletField.append(el('span', 'field-label', `${target.ticker} Wallet`), wallet);
    const worker = document.createElement('input');
    worker.type = 'text';
    worker.id = `${coinId}-worker`;
    worker.value = 'pc';
    worker.required = true;
    worker.pattern = '[A-Za-z0-9_\\-]{1,32}';
    worker.autocomplete = 'off';
    worker.setAttribute('aria-label', t('Worker-Name'));
    const workerField = el('div', 'field');
    workerField.append(el('span', 'field-label', 'Worker-Name'), worker,
        el('small', 'muted', 'Bezeichnung für diesen PC im Pool, maximal 32 Zeichen aus Buchstaben, Zahlen, _ und -.'));
    const fieldGrid = el('div', 'field-grid');
    fieldGrid.append(walletField, workerField);
    form.append(fieldGrid);

    const deviceHead = el('div', 'section-head');
    const deviceHeadCopy = el('div');
    deviceHeadCopy.append(el('p', 'kicker', 'SCHRITT 3'), el('h3', '', target.device === 'GPU' ? 'GPUs auswählen' : 'Gerät'));
    deviceHead.append(deviceHeadCopy);
    form.append(deviceHead);
    const devices = el('div', 'gpu-selection');
    devices.id = `${coinId}-devices`;
    devices.setAttribute('role', 'group');
    devices.setAttribute('aria-label', t('GPUs auswählen'));
    const devicesHelp = el('small', 'muted');
    devicesHelp.id = `${coinId}-devices-help`;
    form.append(devices, devicesHelp);

    const submit = el('button', 'button primary', 'Konfiguration speichern');
    submit.type = 'submit';
    const feedback = el('p', 'notice form-feedback');
    feedback.id = `${coinId}-save-feedback`;
    feedback.hidden = true;
    form.append(submit, feedback);
    form.addEventListener('input', () => {
        state.dirty = true;
    });
    form.addEventListener('submit', event => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        saveConfiguration(coinId);
    });
    configForms.set(coinId, form);
    panel.append(form);
    hydrateConfig(form, data, target, configuration);
}

function hydrateConfig(form, data, target, configuration) {
    const coinId = target.id;
    const state = meta(coinId);
    const entries = poolCatalog.for(coinId);
    const grid = $(`${coinId}-pool-choices`);
    const version = entries.map(entry => entry.value).join('|');
    if (grid.dataset.version !== version) {
        grid.dataset.version = version;
        grid.replaceChildren();
        for (const entry of entries) {
            const card = el('button', 'choice');
            card.type = 'button';
            card.dataset.pool = entry.value;
            const title = entry.value === 'solarminer' ? 'SolarMiner-Standardziel'
                : entry.value === 'custom' ? 'Eigene Pool-Adresse'
                    : `Kryptex · ${entry.region}`;
            card.append(el('strong', '', title), el('small', '', entry.value === 'solarminer' ? poolCatalog.feeLabel(entry)
                : entry.value === 'custom' ? 'stratum+tcp://host:port oder stratum+ssl://host:port' : entry.value));
            const metaRow = el('div', 'choice-meta');
            if (entry.feePercent != null) metaRow.append(el('span', 'pill', `Poolgebühr ${entry.feePercent} %`));
            if (entry.value === 'solarminer') metaRow.append(el('span', 'pill tone-warn', 'keine eigene Auszahlung'));
            if (entry.value === 'custom') metaRow.append(el('span', 'pill', 'erweiterte Option'));
            card.append(metaRow);
            card.addEventListener('click', () => {
                state.pool = entry.value;
                state.dirty = true;
                hydrateConfig(form, data, target, configurationOf(data, coinId));
            });
            grid.append(card);
        }
    }
    if (!state.initialized && !configuration) {
        state.pool = poolCatalog.candidates(coinId)[0]?.value || 'custom';
        state.initialized = true;
    }
    if (configuration && !state.dirty) {
        const known = poolCatalog.find(coinId, configuration.poolUrl);
        state.pool = known?.value || 'custom';
        if (state.pool === 'custom') {
            const custom = $(`${coinId}-pool-custom`);
            if (custom && document.activeElement !== custom) custom.value = configuration.poolUrl || '';
        }
        const wallet = $(`${coinId}-wallet`);
        if (wallet && document.activeElement !== wallet) wallet.value = configuration.wallet || '';
        const worker = $(`${coinId}-worker`);
        if (worker && document.activeElement !== worker) worker.value = configuration.worker || 'pc';
    }
    for (const card of grid.children) {
        const selected = card.dataset.pool === state.pool;
        card.classList.toggle('selected', selected);
        card.setAttribute('aria-pressed', String(selected));
    }
    const standard = state.pool === 'solarminer';
    const customChoice = state.pool === 'custom';
    let customField = $(`${coinId}-pool-custom`);
    if (customChoice && !customField) {
        customField = document.createElement('input');
        customField.type = 'text';
        customField.id = `${coinId}-pool-custom`;
        customField.spellcheck = false;
        customField.placeholder = 'stratum+tcp://pool.example:3333';
        customField.setAttribute('aria-label', t('Eigene Pool-Adresse'));
        const field = el('div', 'field');
        field.append(el('span', 'field-label', 'Eigene Pool-Adresse'), customField,
            el('small', 'muted', 'Format: stratum+tcp://host:port oder stratum+ssl://host:port'));
        grid.after(field);
        customField.addEventListener('input', () => {
            meta(coinId).dirty = true;
        });
    }
    if (customField) customField.required = customChoice;
    const wallet = $(`${coinId}-wallet`);
    wallet.required = !standard;
    wallet.disabled = standard;
    if (standard && document.activeElement !== wallet) wallet.value = '';
    const payout = (data.payoutDefaults || []).find(entry => entry.coin === coinId);
    const note = $(`${coinId}-payout-note`);
    note.textContent = t(standard
        ? payout?.available
            ? `Auszahlung an das SolarMiner-Standardziel (${payout.maskedWallet}). Ohne eigene Wallet geht die gesamte Hashrate dorthin.`
            : 'Kein SolarMiner-Standardziel erreichbar. Eigene Wallet angeben oder Proxy auf der Seite Verbindung prüfen.'
        : 'Eigene Auszahlung: Pool und Wallet müssen zueinander passen.');
    const consent = $(`${coinId}-standard-consent`).closest('label');
    consent.hidden = !standard;
    const steps = form.querySelectorAll('.step');
    steps[0].classList.toggle('done', Boolean(configuration));
    steps[0].classList.toggle('current', !configuration);
    steps[1].classList.toggle('done', standard ? false : Boolean(configuration?.wallet));
    steps[2].classList.toggle('done', target.device !== 'GPU' ? Boolean(configuration) : false);

    const gpus = data.gpus || [];
    const gpuVersion = JSON.stringify({
        devices: configuration?.devices,
        gpus: gpus.map(g => [g.vendor, g.index, g.model])
    });
    if (target.device === 'GPU' && !state.devicesDirty && state.devicesVersion !== gpuVersion) {
        state.devices = new Set(configuration?.devices && configuration.devices !== 'all'
            ? configuration.devices.split(',').map(value => value.trim()).filter(Boolean)
            : gpus.map(gpu => `${gpu.vendor}:${gpu.index}`));
        state.devicesVersion = gpuVersion;
    }
    const list = $(`${coinId}-devices`);
    const keys = gpus.map(gpu => `${gpu.vendor}:${gpu.index}`);
    if ([...list.children].map(button => button.dataset.key).join(',') !== keys.join(',')) {
        list.replaceChildren();
        if (!gpus.length) list.append(el('p', 'muted', 'Keine unterstützte GPU erkannt.'));
        for (const gpu of gpus) {
            const key = `${gpu.vendor}:${gpu.index}`;
            const button = el('button', 'gpu-button', `${gpu.vendor} ${gpu.index} · ${gpu.model || gpu.vendor}`);
            button.type = 'button';
            button.dataset.key = key;
            button.addEventListener('click', () => {
                if (state.devices.has(key)) state.devices.delete(key); else state.devices.add(key);
                state.devicesDirty = true;
                hydrateConfig(form, data, target, configurationOf(data, coinId));
            });
            list.append(button);
        }
    }
    for (const button of list.children) {
        const selected = state.devices.has(button.dataset.key);
        button.classList.toggle('selected', selected);
        button.setAttribute('aria-pressed', String(selected));
    }
    const selectedCount = keys.filter(key => state.devices.has(key)).length;
    $(`${coinId}-devices-help`).textContent = t(target.device === 'GPU'
        ? (keys.length ? `${selectedCount} von ${keys.length} GPUs ausgewählt.` : 'Keine GPU erkannt.')
        : 'Monero nutzt die CPU; ein Gerät ist immer aktiv.');
    const submit = form.querySelector('button[type=submit]');
    submit.disabled = busy || !overviewFresh || (target.device === 'GPU' && !keys.some(key => state.devices.has(key)));
    form.dataset.hydrated = JSON.stringify(configuration || {});
}

function selectedPoolUrl(coinId) {
    const state = meta(coinId);
    if (state.pool === 'custom') return $(`${coinId}-pool-custom`).value.trim();
    return state.pool;
}

function saveConfiguration(coinId) {
    const data = latest;
    const target = coin(data, coinId);
    const state = meta(coinId);
    if (!target) return;
    const standard = state.pool === 'solarminer';
    if (standard && !$(`${coinId}-standard-consent`).checked) {
        return notice('Bestätige zuerst die Auszahlung an das SolarMiner-Standardziel.', true);
    }
    if (standard && !(data.payoutDefaults || []).find(entry => entry.coin === coinId)?.available) {
        return notice(`Kein SolarMiner-Standard-Auszahlungsziel für ${target.name} erreichbar. Bitte eigene Wallet angeben.`, true);
    }
    const poolUrl = selectedPoolUrl(coinId);
    if (!standard && !poolCatalog.validUrl(poolUrl)) return notice('Pool-Adresse ungültig. Format: stratum+tcp://host:port oder stratum+ssl://host:port', true);
    const body = {
        poolUrl: standard ? '' : poolUrl,
        wallet: standard ? '' : $(`${coinId}-wallet`).value.trim(),
        worker: $(`${coinId}-worker`).value.trim()
    };
    if (target.device === 'GPU') {
        const devices = [...state.devices].sort();
        if (!devices.length) return notice('Wähle mindestens eine erkannte GPU.', true);
        const proxyUrl = data.proxy?.[coinId === 'ravencoin' ? 'ravencoinUrl' : coinId === 'ethereumclassic' ? 'ethereumclassicUrl' : coinId === 'decred' ? 'decredUrl' : coinId === 'quantus' ? 'quantusUrl' : 'pearlUrl'];
        if (!proxyUrl) return notice(`SolarMiner-Proxy-Route für ${target.name} fehlt.`, true);
        body.proxyUrl = proxyUrl;
        body.devices = devices.join(',');
    } else if (!data.proxy?.moneroUrl) {
        return notice('SolarMiner-Proxy für Monero fehlt. Verbinde zuerst den Proxy.', true);
    }
    action(`/api/agent/local/${coinId}/configuration`,
        standard ? `${target.name} gespeichert: Auszahlung an das SolarMiner-Standardziel.` : `${target.name}-Konfiguration gespeichert.`,
        null, body, `${coinId}-save-feedback`);
}

// ---------------------------------------------------------------- advanced

function renderAdvancedPanel(panel, data, target, build) {
    panel.append(feeCard(data, target));
    panel.append(referralForm(data));
    if (target.id === 'monero') panel.append(optimizationCard(data));
    const removal = el('details', 'danger-details');
    removal.append(el('summary', '', 'Miner-Installation entfernen'));
    removal.append(el('p', 'muted', target.device === 'GPU'
        ? 'Entfernt die gemeinsame SRBMiner-Installation. Alle GPU-Miner (Pearl, Ravencoin, Ethereum Classic) werden vorher gestoppt; gespeicherte Pool- und Wallet-Daten bleiben erhalten.'
        : 'Entfernt XMRig. Der Miner wird vorher pausiert; gespeicherte Pool- und Wallet-Daten bleiben erhalten.'));
    const remove = el('button', 'button subtle', 'Installation entfernen');
    remove.type = 'button';
    remove.disabled = busy || !overviewFresh || readiness(data, target.id).downloadStatus === 'DOWNLOADING';
    remove.addEventListener('click', () => removeMiner(target.id));
    removal.append(remove);
    panel.append(removal);
    if (build?.projectUrl) {
        const link = el('a', '', 'Projektseite des Miners ↗');
        link.href = build.projectUrl;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        const row = el('p', 'muted');
        row.append(link);
        panel.append(row);
    }
}

function feeCard(data, target) {
    const card = el('section', 'panel fee-card');
    const head = el('div', 'section-head');
    const headCopy = el('div');
    headCopy.append(el('p', 'kicker', 'GEBÜHREN'), el('h3', '', 'Wo dein Ertrag hingeht'));
    head.append(headCopy);
    card.append(head);
    const fee = (data.fees || []).find(item => item.coin === target.id) || (data.fees || [])[0];
    if (!fee) {
        card.append(el('p', 'muted', 'Gebührenmodell wird geladen …'));
        return card;
    }
    const knownTotal = fee.parts.filter(part => part.known).reduce((sum, part) => sum + (part.percentage || 0), 0);
    card.append(el('p', 'muted', 'Alle bekannten Abzüge werden live aufgeschlüsselt. SolarMiner nutzt eine verpflichtende Fee-Route; ist sie nicht erreichbar, startet der Miner nicht.'));
    const bar = el('div', 'fee-bar');
    for (const part of fee.parts.filter(part => part.known && part.percentage > 0)) {
        const segment = el('span', `fee-segment fee-${String(part.kind).toLowerCase()}`);
        segment.style.flexGrow = String(part.percentage);
        segment.title = `${part.label}: ${fmt(part.percentage, 2)} %`;
        bar.append(segment);
    }
    const user = el('span', 'fee-segment fee-user');
    user.style.flexGrow = String(Math.max(0, 100 - knownTotal));
    bar.append(user);
    card.append(bar);
    const rows = el('div', 'fee-rows');
    for (const part of fee.parts) {
        const row = el('div', 'detail');
        row.append(el('span', '', part.label), el('strong', '', part.known ? `${fmt(part.percentage, 2)} %` : 'unbekannt'));
        rows.append(row, el('small', 'muted', part.source));
    }
    const remaining = el('div', 'detail');
    remaining.append(el('span', '', 'Voraussichtlich für dich'), el('strong', '', `${fmt(Math.max(0, 100 - knownTotal), 2)} %`));
    rows.append(remaining);
    card.append(rows);
    return card;
}

function referralForm(data) {
    const form = el('form', 'form-block');
    form.append(el('h3', '', 'Referral-Key'));
    const row = el('div', 'input-row');
    const input = document.createElement('input');
    input.id = 'referral-key';
    input.maxLength = 64;
    input.pattern = '[A-Za-z0-9][A-Za-z0-9_-]{0,63}';
    input.autocomplete = 'off';
    input.value = data.referral?.key || '';
    input.setAttribute('aria-label', t('Referral-Key'));
    const submit = el('button', 'button subtle', 'Lokal speichern');
    submit.type = 'submit';
    submit.disabled = busy || !overviewFresh;
    row.append(input, submit);
    form.append(row);
    form.append(el('p', 'muted', data.referral?.key ? `Gespeichert: ${data.referral.key}` : 'Kein Referrer gesetzt.')
        , el('small', 'muted', 'Der SolarMiner Node setzt seinen Referral-Key automatisch erneut, sobald er den Agenten steuert.'));
    form.addEventListener('submit', event => {
        event.preventDefault();
        if (!form.reportValidity()) return;
        action('/api/agent/local/referral', 'Referral-Key lokal gespeichert. Der Node kann ihn wieder überschreiben.', {key: input.value.trim()});
    });
    return form;
}

const optimizationTips = {
    'huge-pages': {
        status: 'hugePagesConfigured', platforms: ['windows', 'linux'], level: 'Empfohlen',
        title: 'Huge Pages für RandomX aktivieren',
        action: 'SolarMiner schaltet die XMRig-Option ein. Unter Windows fragt der Agent per UAC nach der Berechtigung für dein Benutzerkonto; nach einer neuen Anmeldung wird das Recht wirksam.',
        risk: 'Huge Pages halten RAM während des Minings fest. Dieser Speicher steht anderen Programmen und Windows dann nicht zur Verfügung; bei wenig freiem RAM kann das System langsamer reagieren. Beim Ausschalten entfernt SolarMiner nur ein Windows-Recht, das es selbst hinzugefügt hat.',
        source: 'https://xmrig.com/docs/miner/hugepages', control: true
    },
    '1gb-pages': {
        status: 'oneGbPagesActive', platforms: ['linux'], level: 'Optional · Linux',
        title: '1-GB-Pages für RandomX aktivieren',
        action: 'SolarMiner schaltet die XMRig-Option ein. XMRig benötigt dafür bis zu 3 GB Speicher pro NUMA-Knoten; die Nutzung hängt zusätzlich von Kernel und verfügbarer Speicherkonfiguration ab.',
        risk: 'Der Miner kann 1-GB-Pages möglicherweise nicht reservieren und auf normale Huge Pages zurückfallen. Der zusätzlich benötigte Speicher steht dem System und anderen Programmen währenddessen nicht zur Verfügung.',
        source: 'https://xmrig.com/docs/miner/hugepages', control: true
    }
};

function platformKey(platform = '') {
    const value = String(platform).toLowerCase();
    return value.includes('win') ? 'windows' : value.includes('linux') ? 'linux' : value.includes('mac') ? 'macos' : 'other';
}

function optimizationCard(data) {
    const card = el('section', 'panel optimization-card');
    const head = el('div', 'section-head');
    const headCopy = el('div');
    headCopy.append(el('p', 'kicker', 'OPTIMIERUNG'), el('h3', '', 'RandomX auf diesem System'));
    head.append(headCopy);
    card.append(head);
    card.append(el('p', 'muted', `${data.platform || 'Unbekannt'} · ${data.architecture || 'Architektur unbekannt'}`));
    const platform = platformKey(data.platform);
    const tips = Object.entries(optimizationTips).filter(([, tip]) => tip.platforms.includes(platform));
    if (!tips.length) {
        card.append(el('p', 'muted', `Für ${data.platform || 'dieses Betriebssystem'} sind noch keine verifizierten Tipps hinterlegt.`));
        return card;
    }
    const list = el('div', 'optimization-list');
    for (const [id, tip] of tips) {
        const active = Boolean(randomXOptimization?.[tip.status]);
        const item = el('article', `optimization-item${active ? ' complete' : ''}`);
        const label = el('div', 'optimization-check');
        if (tip.control) {
            const input = document.createElement('input');
            input.type = 'checkbox';
            input.checked = active;
            input.disabled = optimizationBusy || !overviewFresh;
            input.setAttribute('aria-label', `${t(tip.title)} ${t('über SolarMiner aktivieren')}`);
            input.addEventListener('change', () => setRandomXOptimization(id, tip, input.checked));
            label.append(input);
        } else label.append(el('span', 'optimization-info', 'i'));
        label.append(el('strong', '', tip.title), el('span', 'tag', tip.level));
        item.append(label, el('p', 'muted', tip.action));
        if (tip.risk) item.append(el('p', 'optimization-risk', `Risiko: ${tip.risk}`));
        const source = el('a', '', 'Dokumentation öffnen ↗');
        source.href = tip.source;
        source.target = '_blank';
        source.rel = 'noopener noreferrer';
        item.append(source);
        list.append(item);
    }
    card.append(list);
    const message = el('p', 'notice');
    message.id = 'optimization-message';
    message.hidden = true;
    card.append(message);
    if (randomXOptimization?.restartRequired) {
        message.hidden = false;
        message.textContent = t('Neustart erforderlich: Die Einstellung und Windows-Berechtigung sind eingerichtet. Starte Windows neu, damit Huge Pages wirksam werden.');
    } else if (randomXOptimization?.error) {
        message.classList.add('error');
        message.hidden = false;
        message.textContent = t(`Status konnte nicht geladen werden: ${randomXOptimization.error}`);
    }
    return card;
}

async function loadRandomXOptimization() {
    lastOptimizationLoad = Date.now();
    try {
        const response = await fetch('/api/agent/local/optimizations/randomx', {cache: 'no-store'});
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        randomXOptimization = await response.json();
    } catch (error) {
        randomXOptimization = {hugePagesActive: false, oneGbPagesActive: false, error: error.message};
    }
    if (latest && activeTab === 'advanced') render(latest);
}

async function setRandomXOptimization(id, tip, enabled) {
    optimizationBusy = true;
    if (latest) render(latest);
    try {
        const response = await fetch(`/api/agent/local/optimizations/randomx/${id}`, {
            method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({enabled})
        });
        if (!response.ok) {
            const body = await response.json().catch(() => null);
            throw new Error(body?.message || `HTTP ${response.status}`);
        }
        randomXOptimization = await response.json();
        const active = id === 'huge-pages' ? randomXOptimization.hugePagesActive : randomXOptimization.oneGbPagesActive;
        const message = randomXOptimization.restartRequired
            ? 'Neustart erforderlich: Windows hat die Berechtigung eingerichtet. Starte den PC neu; danach wird Huge Pages hier als aktiv angezeigt.'
            : !enabled && platformKey(latest?.platform) === 'windows'
                ? 'Huge Pages sind in XMRig deaktiviert. Windows übernimmt Änderungen an Benutzerrechten bei einer neuen Anmeldung; vorhandene Sitzungen können das alte Recht bis dahin behalten.'
                : enabled && !active ? 'Einstellung wurde gespeichert, ist auf diesem System aber noch nicht aktiv.'
                    : enabled ? 'Funktion wurde aktiviert.' : 'Funktion wurde deaktiviert.';
        await loadRandomXOptimization();
        const box = $('optimization-message');
        if (box) {
            box.classList.remove('error');
            box.hidden = false;
            box.textContent = t(message);
        }
    } catch (error) {
        await loadRandomXOptimization();
        const box = $('optimization-message');
        if (box) {
            box.classList.add('error');
            box.hidden = false;
            box.textContent = t(`Aktivierung fehlgeschlagen: ${error.message}`);
        }
    } finally {
        optimizationBusy = false;
        if (latest) render(latest);
    }
}

async function removeMiner(coinId) {
    const name = coinId === 'monero' ? 'XMRig' : 'SRBMiner-MULTI';
    if (!window.confirm(t(`${name} wird gestoppt und entfernt. Die gespeicherte Pool-/Wallet-Konfiguration bleibt erhalten. Fortfahren?`))) return;
    await action(`/api/agent/local/${coinId}/remove`, `${name} wurde entfernt.`);
}

// ---------------------------------------------------------------- console

let consoleTarget = '', consoleOutput = null;

function consoleKeyFor(data) {
    if (selectedCoin === 'monero') return 'monero';
    if (consoleTarget && consoleTarget.startsWith(`${selectedCoin}-`)) return consoleTarget;
    return selectedCoin;
}

function renderConsolePanel(panel, data) {
    const key = consoleKeyFor(data);
    const head = el('div', 'section-head');
    const headCopy = el('div');
    headCopy.append(el('p', 'kicker', 'DIAGNOSE'), el('h3', '', 'Miner-Konsole'));
    head.append(headCopy);
    const picker = document.createElement('select');
    picker.className = 'metric-select';
    picker.id = 'console-target';
    picker.setAttribute('aria-label', t('Miner-Log wählen'));
    const options = selectedCoin === 'monero' ? [] : [{value: selectedCoin, label: 'Alle GPUs zusammen'}];
    for (const gpu of gpuStates(data, selectedCoin)) options.push({
        value: `${selectedCoin}-${gpu.vendor}-${gpu.index}`,
        label: `${gpu.vendor} ${gpu.index} · ${gpu.model}`
    });
    for (const option of options) {
        const item = el('option', '', option.label);
        item.value = option.value;
        picker.append(item);
    }
    if (options.length) {
        picker.value = options.some(option => option.value === key) ? key : selectedCoin;
        picker.addEventListener('change', () => {
            consoleTarget = picker.value;
            consoleState = null;
            consoleOutput = null;
            render(latest || data);
        });
    } else picker.hidden = true;
    head.append(picker);
    panel.append(head);
    const status = el('p', 'muted console-status');
    status.id = 'console-status';
    status.textContent = t('Lade vollständige Ausgabe …');
    const download = el('a', 'button subtle', 'Vollständiges Log herunterladen');
    download.id = 'console-download';
    download.hidden = true;
    download.href = `/api/agent/local/console/${key}/download`;
    const follow = el('button', 'button subtle', 'Ans Ende springen');
    follow.type = 'button';
    const tools = el('div', 'console-tools');
    tools.append(status, download, follow);
    // The output element survives re-renders so a live log is not rebuilt on every snapshot.
    if (!consoleOutput || consoleState?.key !== key) {
        consoleOutput = el('pre', 'miner-console');
        consoleOutput.id = 'miner-console';
        consoleState = {key, offset: 0, runId: null, decoder: new TextDecoder(), loading: false};
    }
    follow.addEventListener('click', () => {
        consoleOutput.scrollTop = consoleOutput.scrollHeight;
    });
    panel.append(tools, consoleOutput);
    pollConsole();
}

async function pollConsole() {
    const output = consoleOutput;
    if (!output || !document.body.contains(output)) return;
    const state = consoleState;
    if (!state || state.loading || document.hidden) return;
    state.loading = true;
    let more = false;
    try {
        const response = await fetch(`/api/agent/local/console/${state.key}?offset=${state.offset}`, {cache: 'no-store'});
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const chunk = await response.json();
        if (consoleState !== state) return;
        if (chunk.runId !== state.runId) {
            state.runId = chunk.runId;
            state.offset = 0;
            state.decoder = new TextDecoder();
            output.replaceChildren();
        }
        if (chunk.data) {
            const bytes = Uint8Array.from(atob(chunk.data), character => character.charCodeAt(0));
            const text = state.decoder.decode(bytes, {stream: true});
            const follow = output.scrollTop + output.clientHeight >= output.scrollHeight - 40;
            output.append(document.createTextNode(text));
            if (follow) output.scrollTop = output.scrollHeight;
        }
        state.offset = chunk.nextOffset;
        more = chunk.hasMore;
        const download = $('console-download');
        if (download) {
            download.hidden = state.offset === 0;
            download.href = `/api/agent/local/console/${state.key}/download`;
        }
        const status = $('console-status');
        if (status) status.textContent = t(state.offset ? 'Live · vollständiger Verlauf' : 'Noch keine Miner-Ausgabe');
    } catch (error) {
        const status = $('console-status');
        if (consoleState === state && status) status.textContent = t(`Konsole nicht erreichbar: ${error.message}`);
    } finally {
        state.loading = false;
        // Miner output does not always change the overview snapshot, so keep tailing the log.
        if (consoleState === state && !document.hidden) setTimeout(pollConsole, more ? 0 : 1000);
    }
}

// ---------------------------------------------------------------- library

function catalogPackages(coins) {
    const packages = new Map();
    for (const coin of coins || []) {
        for (const option of coin.miners || []) {
            const entry = packages.get(option.id) || {id: option.id, name: option.name, options: [], coins: []};
            entry.options.push(option);
            entry.coins.push(coin);
            packages.set(option.id, entry);
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
    return {
        status: readiness?.downloadStatus || option?.downloadStatus || 'PENDING',
        detail: readiness?.downloadDetail || option?.downloadDetail,
        progress: readiness?.downloadProgress || option?.downloadProgress || 0
    };
}

function syncBulkInstall(packages) {
    for (const id of [...selectedInstallers]) {
        const pkg = packages.find(item => item.id === id);
        if (!pkg || pkg.installed || !pkg.selectable) selectedInstallers.delete(id);
    }
    const available = packages.filter(pkg => !pkg.installed && pkg.selectable);
    const selected = available.filter(pkg => selectedInstallers.has(pkg.id));
    $('catalog-installed-count').textContent = t(`${packages.filter(pkg => pkg.installed).length} installiert`);
    $('catalog-available-count').textContent = t(`${available.length} installierbar`);
    $('select-installable').disabled = busy || !available.length;
    $('select-installable').textContent = t(selected.length === available.length && available.length ? 'Auswahl aufheben' : 'Verfügbare auswählen');
    $('install-selected').disabled = busy || !selected.length || !overviewFresh;
    $('install-selected').textContent = t(selected.length ? `${selected.length} Miner installieren` : 'Auswahl installieren');
}

function renderLibrary(data) {
    const packages = catalogPackages(data.coins || initialCatalog || []);
    const grid = $('catalog-grid');
    grid.replaceChildren();
    for (const pkg of packages) {
        if (deviceFilter !== 'all' && pkg.device !== deviceFilter) continue;
        const state = packageStatus(pkg, data);
        const downloading = state.status === 'DOWNLOADING';
        const card = el('article', `catalog-card package-card${pkg.installed ? ' installed' : ''}`);
        const select = document.createElement('input');
        select.type = 'checkbox';
        select.className = 'package-select';
        select.checked = selectedInstallers.has(pkg.id);
        select.disabled = busy || pkg.installed || !pkg.selectable || downloading || !overviewFresh;
        select.setAttribute('aria-label', `${t(pkg.name)} ${t('für Installation auswählen')}`);
        select.addEventListener('change', () => {
            if (select.checked) selectedInstallers.add(pkg.id); else selectedInstallers.delete(pkg.id);
            syncBulkInstall(packages);
        });
        const emblem = el('span', 'package-emblem', pkg.device);
        const identity = el('div', 'package-identity');
        identity.append(el('h2', '', pkg.name),
            el('p', 'muted', `${pkg.device} · ${[...new Set(pkg.options.map(option => option.algorithm))].join(' / ')}`));
        const badge = el('span', `tag${pkg.installed ? ' ready' : ''}`, pkg.installed ? 'INSTALLIERT' : pkg.experimental ? 'EXPERIMENTELL' : 'VERFÜGBAR');
        const head = el('div', 'package-head');
        head.append(select, emblem, identity, badge);
        card.append(head);
        const coinsRow = el('div', 'package-coins');
        for (const coin of pkg.coins) {
            const selected = coin.selectedMiner?.id === pkg.id;
            coinsRow.append(el('span', `package-coin${selected ? ' selected' : ''}`, `${coin.ticker || coin.name} · ${coin.algorithm}${selected ? ' · aktiv' : ''}`));
        }
        card.append(coinsRow);
        const fees = [...new Set(pkg.options.map(option => option.developerFeePercent).filter(value => value != null))].sort((a, b) => a - b);
        const feeText = fees.length ? `Dev-Fee ${fees.length === 1 ? fmt(fees[0], 2) : `${fmt(fees[0], 2)}–${fmt(fees.at(-1), 2)}`} %` : 'Dev-Fee unbekannt';
        const advantages = [...new Set(pkg.options.flatMap(option => option.advantages || []))].slice(0, 2);
        const drawbacks = [...new Set(pkg.options.flatMap(option => option.disadvantages || []))].slice(0, 1);
        card.append(el('p', 'package-meta muted', [feeText, ...advantages, ...drawbacks].join(' · ')));
        if (state.status === 'BLOCKED_BY_ANTIVIRUS') card.append(defenderHelp(pkg.representative.coin));
        const footer = el('div', 'package-footer');
        footer.append(el('span', `package-state${pkg.installed ? ' ready' : ''}`,
            pkg.installed ? 'Bereit' : downloading ? `Download ${state.progress} %`
                : !pkg.selectable ? (pkg.representative?.unavailableReason || 'Noch nicht integriert')
                    : state.status === 'FAILED' ? (state.detail || 'Installation fehlgeschlagen') : 'Nicht installiert'));
        const actions = el('div', 'package-actions');
        if (pkg.installed) {
            for (const coin of pkg.coins) {
                const selected = coin.selectedMiner?.id === pkg.id;
                const open = el('button', `button${selected ? ' subtle' : ''}`, selected ? (coin.ticker || coin.name) : `${coin.ticker || coin.name} auswählen`);
                open.type = 'button';
                open.disabled = busy || !overviewFresh;
                open.title = selected ? `${t(coin.name)} öffnen` : `${t(pkg.name)} für ${t(coin.name)} auswählen`;
                open.addEventListener('click', () => selected ? openBuild(coin.id, pkg.id)
                    : action(`/api/agent/local/${coin.id}/miner`, `${pkg.name} als Miner für ${coin.name} ausgewählt.`, {minerId: pkg.id}));
                actions.append(open);
            }
        } else if (pkg.selectable) {
            const install = el('button', 'button primary', downloading ? 'Installiert …' : 'Installieren');
            install.type = 'button';
            install.disabled = busy || downloading || !overviewFresh;
            install.addEventListener('click', () => installPackages([pkg]));
            actions.append(install);
        }
        footer.append(actions);
        card.append(footer);
        if (downloading) {
            const bar = document.createElement('progress');
            bar.className = 'download-progress';
            bar.max = 100;
            bar.value = state.progress;
            card.append(bar);
        }
        grid.append(card);
    }
    if (!grid.children.length) grid.append(el('p', 'muted', 'Für diesen Hardware-Filter ist keine Mining-Software verfügbar.'));
    syncBulkInstall(packages);
}

async function installBuild(coinId, minerId) {
    if (busy || !overviewFresh) return;
    invalidateMiningViewCache();
    busy = true;
    if (latest) render(latest, true);
    try {
        const response = await fetch(`/api/agent/local/${encodeURIComponent(coinId)}/miners/${encodeURIComponent(minerId)}/download`, {method: 'POST'});
        if (!response.ok || await response.json() !== true) throw new Error(`HTTP ${response.status}`);
        notice('Installation gestartet. Der Fortschritt erscheint in der Miner-Liste.');
    } catch (error) {
        notice(`Installation nicht gestartet: ${error.message}`, true);
    } finally {
        invalidateMiningViewCache();
        busy = false;
        await refresh();
    }
}

async function installPackages(packages) {
    if (busy || !overviewFresh || !packages.length) return;
    invalidateMiningViewCache();
    busy = true;
    if (latest) render(latest, true);
    const failures = [];
    await Promise.all(packages.map(async pkg => {
        try {
            const option = pkg.representative;
            const response = await fetch(`/api/agent/local/${encodeURIComponent(option.coin)}/miners/${pkg.id}/download`, {method: 'POST'});
            if (!response.ok || await response.json() !== true) throw new Error(`HTTP ${response.status}`);
            selectedInstallers.delete(pkg.id);
        } catch (error) {
            failures.push(`${pkg.name}: ${error.message}`);
        }
    }));
    busy = false;
    notice(failures.length
        ? `Nicht alle Installationen konnten gestartet werden. ${failures.join(' · ')}`
        : `${packages.length} Installation${packages.length === 1 ? '' : 'en'} gestartet. Du kannst den Fortschritt live verfolgen.`, failures.length > 0);
    invalidateMiningViewCache();
    await refresh();
}

async function loadInitialCatalog() {
    try {
        const response = await fetch('/api/agent/local/miner-catalog', {cache: 'no-store'});
        if (!response.ok) return;
        const coins = await response.json();
        if (!latest && Array.isArray(coins)) {
            initialCatalog = coins;
            renderInstances(coins);
            renderLibrary({coins});
        }
    } catch (_) { /* The overview reports connection failures. */
    }
}

// ---------------------------------------------------------------- render

function libraryOpen() {
    return location.hash === '#library';
}

function render(data, fresh = overviewFresh) {
    for (const [control, disabled] of cachedControls) control.disabled = disabled;
    cachedControls.clear();
    overviewFresh = fresh;
    latest = data;
    if (pendingInstall) {
        const build = buildsFor(data, pendingInstall.coin).find(row => row.minerId === pendingInstall.minerId);
        if (build?.installed) {
            const target = pendingInstall;
            pendingInstall = null;
            selectedBuild = target.minerId;
            selectedCoin = target.coin;
        } else if (['FAILED', 'UNSUPPORTED', 'BLOCKED_BY_ANTIVIRUS'].includes(build?.downloadStatus)) pendingInstall = null;
    }
    $('api-docs').hidden = data.proxy?.mode !== 'standalone';
    document.body.classList.toggle('standalone', data.proxy?.mode === 'standalone');
    $('instance-view').hidden = libraryOpen();
    $('library-view').hidden = !libraryOpen();
    $('open-library').classList.toggle('selected', libraryOpen());
    $('open-library').setAttribute('aria-pressed', String(libraryOpen()));
    $('add-miner').disabled = busy;
    if (!scope) {
        scope = ui.scopeBar({
            coins: (data.coins || []).map(item => ({
                id: item.id,
                name: item.name,
                ticker: item.ticker,
                running: running(data, item.id)
            })),
            active: selectedCoin,
            onCoin: coinId => showView(coinId),
            onSearch: value => {
                search = value;
                if (latest) render(latest);
            },
            placeholder: 'Miner oder Coin suchen',
            resultLabel: ''
        });
        $('scope-bar').replaceChildren(scope);
        ui.installSearchShortcut(scope);
    } else {
        scope.setActive(selectedCoin, (data.coins || []).filter(item => running(data, item.id)).map(item => item.id));
    }
    if (libraryOpen()) renderLibrary(data);
    else {
        renderInstances(data);
        renderDetail(data);
    }
    if (selectedCoin === 'monero' && activeTab === 'advanced' && Date.now() - lastOptimizationLoad > 5000) loadRandomXOptimization();
    $('connection').className = `badge ${fresh ? 'online' : ''}`;
    $('connection').textContent = t(fresh ? 'Agent verbunden' : 'Aktualisiere …');
    $('mining-refresh-state').hidden = fresh;
    if (!fresh) {
        $('mining-refresh-state').textContent = t(`Letzter Stand ${new Date(overviewSavedAt || Date.now()).toLocaleTimeString(i18n.locale)} · Aktionen sind danach verfügbar.`);
        for (const control of document.querySelectorAll('#instance-detail button, #instance-detail input, #instance-detail select, #catalog-grid button, #catalog-grid input')) {
            cachedControls.set(control, control.disabled);
            control.disabled = true;
        }
    }
    window.dispatchEvent(new CustomEvent('mining-rendered', {detail: data}));
}

// ---------------------------------------------------------------- wiring

$('refresh').addEventListener('click', refresh);
$('add-miner').addEventListener('click', () => showView('library'));
$('open-library').addEventListener('click', () => showView('library'));
$('select-installable').addEventListener('click', () => {
    const packages = catalogPackages(latest?.coins || initialCatalog || []).filter(pkg => !pkg.installed && pkg.selectable);
    const selectAll = packages.some(pkg => !selectedInstallers.has(pkg.id));
    selectedInstallers.clear();
    if (selectAll) packages.forEach(pkg => selectedInstallers.add(pkg.id));
    renderLibrary(latest || {coins: initialCatalog || []});
});
$('install-selected').addEventListener('click', () => {
    const packages = catalogPackages(latest?.coins || initialCatalog || []).filter(pkg => selectedInstallers.has(pkg.id) && !pkg.installed && pkg.selectable);
    installPackages(packages);
});
document.querySelectorAll('.hardware-filter .filter-button').forEach(button => button.addEventListener('click', () => {
    deviceFilter = button.dataset.device;
    document.querySelectorAll('.hardware-filter .filter-button').forEach(item => item.classList.toggle('selected', item === button));
    renderLibrary(latest || {coins: initialCatalog || []});
}));
window.addEventListener('hashchange', () => {
    const view = location.hash.slice(1);
    if (view === 'library') {
        if (latest) render(latest); else if (initialCatalog) {
            renderInstances(initialCatalog);
            renderLibrary({coins: initialCatalog});
        }
    } else if (coinIds.includes(view) && view !== selectedCoin) showView(view);
});
const cachedOverview = window.SolarMinerMiningCache?.read();
if (cachedOverview) {
    overviewSavedAt = cachedOverview.savedAt;
    render(cachedOverview.overview, false);
}
loadInitialCatalog();
refresh();
connectEvents();
// The SSE stream is the primary source; polling keeps the page live when the stream is blocked.
setInterval(() => {
    if (document.hidden) return;
    if (!eventStream) connectEvents();
    if (!overviewFresh || Date.now() - overviewSavedAt > (eventStream ? 4000 : 1500)) refresh();
}, 2000);
setInterval(() => {
    if (!document.hidden && activeTab === 'devices') loadTelemetry();
}, 5000);
document.addEventListener('visibilitychange', () => {
    if (document.hidden) disconnectEvents();
    else {
        refresh();
        pollConsole();
        connectEvents();
    }
});
