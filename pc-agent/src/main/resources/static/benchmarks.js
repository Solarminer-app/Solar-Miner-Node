const $ = id => document.getElementById(id);
const make = (tag, cls, value) => {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (value != null) e.textContent = value;
    return e;
};
let timer;
let lastResults = '';

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

async function renderResults(results) {
    const root = $('results');
    root.replaceChildren();
    if (!results?.length) {
        root.append(make('p', 'empty', 'Keine gültigen Mining-Messwerte erfasst. Prüfe, ob Miner laufen und Hashrate liefern.'));
        return;
    }
    for (const row of results) {
        const card = make('article', 'worker-card');
        const info = make('div', '');
        info.append(make('strong', '', `${row.hardwareModel} · ${row.algorithm}`), make('p', 'muted', `${row.hardwareType} · ${row.observations} Messpunkte`));
        info.append(make('p', '', `Hashrate: ${fmtRate(row.hashrateHs)} · Leistung: ${row.powerWatts > 0 ? Math.round(row.powerWatts) + ' W' : 'nicht verfügbar'} · Effizienz: ${row.hashesPerWatt > 0 ? row.hashesPerWatt.toPrecision(3) + ' H/s/W' : '—'}`));
        const peer = await comparison(row);
        if (peer) {
            const delta = peer.medianHashrateHs > 0 ? (row.hashrateHs / peer.medianHashrateHs - 1) * 100 : null;
            info.append(make('p', 'muted', `Vergleich (${peer.sampleCount} Geräte): Median ${fmtRate(peer.medianHashrateHs)}${peer.medianPowerWatts ? ` · ${Math.round(peer.medianPowerWatts)} W` : ''}${delta == null ? '' : ` · ${Math.abs(delta).toFixed(1)} % ${delta >= 0 ? 'über' : 'unter'} dem Median`}`));
        } else info.append(make('p', 'muted', 'Noch kein veröffentlichter Vergleich für diese Hardware und diesen Algorithmus.'));
        card.append(info);
        root.append(card);
    }
}

async function poll() {
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
            $('phase').textContent = `${state.phase} · Schritt ${state.phaseIndex}/${state.phaseCount}`;
            $('remaining').textContent = `${state.secondsRemaining ?? 0} s verbleibend`;
            const total = Math.max(1, (new Date(state.totalEndsAt) - new Date(state.startedAt)) / 1000);
            $('progress').value = Math.min(100, 100 * (1 - (state.secondsRemaining ?? 0) / total));
        }
        const signature = JSON.stringify(state.results || []);
        if (state.results?.length && signature !== lastResults) {
            lastResults = signature;
            await renderResults(state.results);
        }
    } catch (e) {
        $('connection').className = 'badge offline';
        $('connection').textContent = 'Agent nicht erreichbar';
    }
}

async function start(mode) {
    const response = await fetch('/api/agent/benchmarks', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({mode, seconds: Number($('duration').value)})
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
