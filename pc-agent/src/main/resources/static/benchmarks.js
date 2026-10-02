const $ = id => document.getElementById(id);
const make = (tag, cls, value) => {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (value != null) e.textContent = value;
    return e;
};
let timer;
let lastResults = '';
let polling = false;
let lastRenderRunning = null;
let lastUploadStatus = '';

function notice(text, error = false) {
    const e = $('notice');
    e.hidden = false;
    e.className = `notice${error ? ' error' : ''}`;
    e.textContent = text;
}

function fmtRate(v) {
    return new Intl.NumberFormat(undefined, {maximumSignificantDigits: 4}).format(v) + ' H/s';
}

async function comparison(row) {
    const params = new URLSearchParams({
        hardwareType: row.hardwareType,
        hardwareModel: row.hardwareModel,
        algorithm: row.algorithm
    });
    const response = await fetch(`/api/agent/benchmarks/match?${params}`, {cache: 'no-store'});
    if (!response.ok) return null;
    return response.json();
}

async function renderResults(results, running = false, phase = '') {
    const root = $('results');
    root.replaceChildren();
    if (!results?.length) {
        root.append(make('p', 'empty', running
            ? `${phase || 'Benchmark läuft'} · Warte auf die ersten gültigen Hashrate-Messpunkte …`
            : 'Keine gültigen Mining-Messwerte erfasst. Prüfe, ob Miner laufen und Hashrate liefern.'));
        return;
    }
    for (const row of results) {
        const card = make('article', 'worker-card');
        const info = make('div', '');
        info.append(make('strong', '', `${row.hardwareModel} · ${row.algorithm}`), make('p', 'muted', `${row.hardwareType} · ${row.observations} Messpunkte`));
        info.append(make('p', '', `Hashrate: ${fmtRate(row.hashrateHs)} · Leistung: ${row.powerWatts > 0 ? Math.round(row.powerWatts) + ' W' : 'nicht verfügbar'} · Effizienz: ${row.hashesPerWatt > 0 ? row.hashesPerWatt.toPrecision(3) + ' H/s/W' : '—'}`));
        const peer = running ? null : await comparison(row);
        if (peer) {
            const delta = peer.medianHashrateHs > 0 ? (row.hashrateHs / peer.medianHashrateHs - 1) * 100 : null;
            info.append(make('p', 'muted', `Vergleich (${peer.sampleCount} Geräte): Median ${fmtRate(peer.medianHashrateHs)}${peer.medianPowerWatts ? ` · ${Math.round(peer.medianPowerWatts)} W` : ''}${delta == null ? '' : ` · ${Math.abs(delta).toFixed(1)} % ${delta >= 0 ? 'über' : 'unter'} dem Median`}`));
        } else info.append(make('p', 'muted', running
            ? 'Messwerte werden laufend aktualisiert; der Vergleich erscheint nach Abschluss.'
            : 'Noch kein veröffentlichter Vergleich für diese Hardware und diesen Algorithmus.'));
        card.append(info);
        root.append(card);
    }
}

async function poll() {
    if (polling) return;
    polling = true;
    try {
        const response = await fetch('/api/agent/benchmarks', {cache: 'no-store'});
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const state = await response.json();
        $('connection').className = 'badge online';
        $('connection').textContent = 'Agent verbunden';
        $('run-state').textContent = state.phase || 'Bereit';
        $('cancel').hidden = !state.running;
        $('progress-wrap').hidden = !state.running;
        $('run-live').disabled = $('run-installed').disabled = state.running;
        if (state.running) {
            $('upload-status').hidden = true;
            $('retry-upload').hidden = true;
        }
        if (state.running) {
            $('phase').textContent = `${state.phase} · Schritt ${state.phaseIndex}/${state.phaseCount}`;
            const count = state.phase.match(/·\s*(\d+)\/(\d+)\s*Messpunkte/);
            if (count) {
                $('progress').max = Number(count[2]);
                $('progress').value = Number(count[1]);
            } else {
                $('progress').removeAttribute('value');
            }
        }
        const signature = JSON.stringify(state.results || []);
        if (signature !== lastResults || state.running !== lastRenderRunning || (state.running && !state.results?.length)) {
            lastResults = signature;
            lastRenderRunning = state.running;
            await renderResults(state.results, state.running, state.phase);
        } else if (!state.running && state.phase !== 'Idle' && !state.results?.length) {
            $('results').replaceChildren(make('p', 'empty', state.phase));
            lastResults = signature;
        }
        if (!state.running) await pollUploadStatus();
    } catch (e) {
        $('connection').className = 'badge offline';
        $('connection').textContent = 'Agent nicht erreichbar';
    } finally {
        polling = false;
    }
}

async function pollUploadStatus() {
    try {
        const response = await fetch('/api/agent/benchmarks/sharing/upload-status', {cache: 'no-store'});
        if (!response.ok) return;
        const statuses = await response.json();
        const signature = JSON.stringify(statuses);
        if (signature === lastUploadStatus) return;
        lastUploadStatus = signature;
        const manual = statuses.manual;
        const periodic = statuses.periodic;
        showUploadStatus('upload-status', manual, 'Benchmark-Upload');
        showUploadStatus('periodic-upload-status', periodic, 'Regelmäßiger Upload');
        $('retry-upload').hidden = manual.status !== 'FAILED';
    } catch (e) {
        // The benchmark result remains visible even if the local status request fails.
    }
}

function showUploadStatus(id, status, label) {
    const element = $(id);
    element.hidden = !status || status.status === 'IDLE';
    if (element.hidden) return;
    element.className = `notice${status.status === 'FAILED' ? ' error' : ''}`;
    element.textContent = `${label}: ${status.message}${status.sampleCount ? ` (${status.sampleCount} Geräte)` : ''}`;
}

async function start(mode) {
    const response = await fetch('/api/agent/benchmarks', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({mode})
    });
    if (!response.ok) {
        const body = await response.text();
        return notice(body || 'Benchmark konnte nicht gestartet werden.', true);
    }
    $('results').replaceChildren(make('p', 'empty', 'Messlauf gestartet …'));
    await poll();
}

$('run-live').addEventListener('click', () => start('LIVE'));
$('run-installed').addEventListener('click', () => start('INSTALLED'));
$('retry-upload').addEventListener('click', async () => {
    const button = $('retry-upload');
    button.disabled = true;
    try {
        const response = await fetch('/api/agent/benchmarks/sharing/retry-manual', {method: 'POST'});
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        lastUploadStatus = '';
        await pollUploadStatus();
    } catch (e) {
        notice('Der Upload konnte nicht erneut gestartet werden.', true);
    } finally {
        button.disabled = false;
    }
});
$('cancel').addEventListener('click', async () => {
    await fetch('/api/agent/benchmarks/cancel', {method: 'POST'});
    await poll();
});
$('refresh').addEventListener('click', poll);
$('sharing').addEventListener('change', async () => {
    const input = $('sharing');
    input.disabled = true;
    const response = await fetch('/api/agent/benchmarks/sharing', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({enabled: input.checked})
    });
    if (!response.ok) {
        input.checked = !input.checked;
        notice('Die Freigabe konnte nicht gespeichert werden.', true);
    } else $('sharing-note').textContent = input.checked ? 'Freigabe gespeichert. Aktive Miner werden regelmäßig übertragen.' : 'Freigabe deaktiviert. Der Widerruf wird beim nächsten Versandintervall übermittelt.';
    input.disabled = false;
});
fetch('/api/agent/benchmarks/sharing').then(r => r.json()).then(v => {
    $('sharing').checked = Boolean(v)
}).catch(() => {
});
poll();
timer = setInterval(poll, 1000);
