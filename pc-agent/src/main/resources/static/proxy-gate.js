// Blocks the whole dashboard until the downloaded Stratum proxy is running. The agent ships no
// proxy build, so every start first resolves and downloads the published release JAR.
const gate = document.createElement('div');
gate.id = 'proxy-gate';
gate.className = 'gate';
gate.setAttribute('role', 'status');
gate.setAttribute('aria-live', 'polite');
const card = document.createElement('section');
card.className = 'gate-card';
const kicker = document.createElement('p');
kicker.className = 'kicker';
kicker.textContent = 'MINING-PROXY';
const title = document.createElement('h2');
const copy = document.createElement('p');
const bar = document.createElement('div');
bar.className = 'gate-progress';
bar.setAttribute('hidden', '');
const barFill = document.createElement('span');
bar.append(barFill);
const detail = document.createElement('p');
detail.className = 'muted';
const retry = document.createElement('button');
retry.type = 'button';
retry.className = 'button primary';
retry.textContent = 'Erneut versuchen';
retry.setAttribute('hidden', '');
card.append(kicker, title, copy, bar, detail, retry);
gate.append(card);
document.body.append(gate);

const t = key => window.SolarMinerI18n ? window.SolarMinerI18n.t(key) : key;
let retrying = false, closed = false;

function close() {
  if (closed) return;
  closed = true;
  gate.remove();
}

function show(state, percent, version, message) {
  const downloading = state === 'downloading';
  title.textContent = t(state === 'failed' ? 'Mining-Proxy nicht bereit' : 'Mining-Proxy wird vorbereitet');
  copy.textContent = t(state === 'checking' ? 'Der PC-Agent sucht die neueste Proxy-Version auf GitHub.'
    : downloading ? 'Der SolarMiner-Stratum-Proxy wird heruntergeladen.'
      : state === 'starting' ? 'Der lokale Mining-Proxy wird gestartet.'
        : state === 'running' ? 'Der lokale Mining-Proxy wird gestartet.'
          : 'Der SolarMiner-Stratum-Proxy konnte nicht geladen oder gestartet werden.');
  bar.hidden = !downloading;
  if (downloading) barFill.style.width = Math.max(2, Math.min(100, percent)) + '%';
  detail.textContent = (version ? 'Version ' + version + ' · ' : '') + (message || t('Ohne Proxy kann dieser PC nicht minen.'));
  retry.hidden = state !== 'failed' || retrying;
}

async function check() {
  if (closed) return;
  try {
    const response = await fetch('/api/agent/local/proxy-gate', { cache: 'no-store' });
    if (!response.ok) throw new Error('HTTP ' + response.status);
    const gateState = await response.json();
    if (gateState.ready) { close(); return; }
    show(gateState.state, gateState.percent, gateState.version, gateState.detail);
  } catch (error) {
    show('failed', 0, '', t('Der PC-Agent ist nicht erreichbar.'));
  }
}

retry.addEventListener('click', async () => {
  if (retrying) return;
  retrying = true;
  retry.disabled = true;
  retry.textContent = t('Erneuter Versuch läuft …');
  try {
    const response = await fetch('/api/agent/local/proxy-gate/retry', { method: 'POST' });
    if (!response.ok) throw new Error('HTTP ' + response.status);
  } catch (error) {
    detail.textContent = t('Erneuter Versuch fehlgeschlagen:') + ' ' + error.message;
  } finally {
    retrying = false;
    retry.disabled = false;
    retry.textContent = t('Erneut versuchen');
  }
  await check();
});

check();
setInterval(check, 1200);
