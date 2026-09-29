const gate = document.getElementById('lhm-gate');
const retryButton = document.getElementById('gate-retry');
let retrying = false;
async function checkSensorAccess() {
  try {
    const response = await fetch('/api/agent/telemetry', { cache: 'no-store' });
    if (!response.ok) return;
    const data = await response.json();
    const windows = (data.platform || '').toLowerCase().includes('windows');
    const ready = !windows || data.sensorServiceStatus === 'available';
    gate.hidden = ready;
    if (windows && !ready) {
      const starting = data.sensorServiceStatus === 'starting';
      document.getElementById('gate-copy').textContent = starting
        ? 'Bitte bestätige die Windows-Sicherheitsabfrage. Der PC-Agent wartet auf die Administratorfreigabe.'
        : 'LibreHardwareMonitor läuft nicht. Windows benötigt eine Administratorfreigabe, um den Hardware-Monitor neu zu starten.';
      document.getElementById('gate-detail').textContent = data.sensorServiceDetail || '';
      retryButton.disabled = retrying || starting;
      retryButton.textContent = starting ? 'Warte auf Windows-Freigabe …' : 'LibreHardwareMonitor neu starten';
    }
  } catch (_) { }
}
retryButton.addEventListener('click', async () => {
  if (retrying) return;
  retrying = true;
  retryButton.disabled = true;
  retryButton.textContent = 'Windows-Freigabe wird angefordert …';
  try {
    const response = await fetch('/api/agent/telemetry/restart', { method: 'POST' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    await response.json();
  } catch (error) {
    document.getElementById('gate-detail').textContent = `Neustart konnte nicht angefordert werden: ${error.message}`;
  } finally {
    retrying = false;
    await checkSensorAccess();
  }
});
checkSensorAccess(); setInterval(checkSensorAccess, 1500);
