const $ = id => document.getElementById(id);
let current;
function notice(message, error = false) { const n = $('notice'); n.hidden = false; n.className = `notice ${error ? 'error' : ''}`; n.textContent = message; }
async function switchMode() {
  try {
    if (current?.mode === 'standalone') {
      const found = await fetch('/api/agent/proxy/discover', {method: 'POST'}); const candidates = found.ok ? await found.json() : [];
      if (!candidates.length) throw new Error('Kein externer SolarMiner-Proxy im Netzwerk gefunden.');
      const host = candidates[0].host;
      const saved = await fetch(`/api/agent/proxy?host=${encodeURIComponent(host)}`, {method: 'POST'});
      if (!saved.ok || !await saved.json()) throw new Error('Der externe Proxy konnte nicht gespeichert werden.');
      const changed = await fetch('/api/agent/proxy/mode?mode=external', {method: 'POST'});
      if (!changed.ok || !await changed.json()) throw new Error('Der externe Proxy konnte nicht aktiviert werden.');
      notice(`Externer Proxy ${host} aktiviert. Alle Miner wurden pausiert.`);
    } else {
      const changed = await fetch('/api/agent/proxy/mode?mode=local', {method: 'POST'});
      if (!changed.ok || !await changed.json()) throw new Error('Der lokale Proxy konnte nicht gestartet werden.');
      notice('Lokaler Proxy aktiviert. Alle Miner wurden pausiert.');
    }
    await refresh();
  } catch (e) { notice(e.message, true); }
}
async function refresh() {
  try {
    const response = await fetch('/api/agent/overview', {cache: 'no-store'}); if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json(); current = data.proxy; const local = current?.mode === 'standalone';
    $('proxy-heading').textContent = local ? 'Lokaler SolarMiner-Proxy' : 'Externer SolarMiner-Proxy';
    $('proxy-mode').textContent = local ? 'LOKAL' : 'EXTERN';
    $('proxy-description').textContent = local ? 'Der im Agent enthaltene Proxy wird verwendet.' : 'Der Agent verwendet einen SolarMiner-Proxy im lokalen Netzwerk.';
    $('proxy-route').textContent = current?.moneroUrl || 'Noch keine Route';
    $('proxy-state').textContent = local ? (current?.managedStatus === 'running' ? 'Lokal aktiv' : 'Lokaler Proxy startet …') : (current?.reachable ? 'Erreichbar' : 'Nicht erreichbar');
    $('proxy-switch').textContent = local ? 'Externen Proxy suchen & wechseln' : 'Lokalen Proxy verwenden'; $('proxy-switch').onclick = switchMode;
    $('connection').className = 'badge online'; $('connection').textContent = 'Agent verbunden'; $('updated').textContent = `Aktualisiert ${new Date().toLocaleTimeString()}`;
  } catch (e) { $('connection').className = 'badge offline'; $('connection').textContent = 'Agent nicht erreichbar'; notice(`Proxy-Daten konnten nicht geladen werden: ${e.message}`, true); }
}
$('refresh').addEventListener('click', refresh); refresh(); setInterval(() => { if (!document.hidden) refresh(); }, 12000);
