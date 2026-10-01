const $ = id => document.getElementById(id);
const i18n = window.SolarMinerI18n;
const fmt = value => new Intl.NumberFormat(i18n.locale, {maximumFractionDigits: 0}).format(value);
const node = (tag, className, value) => { const e = document.createElement(tag); if (className) e.className = className; if (value !== undefined) e.textContent = value; return e; };
let refreshing = false;
function notice(message, error = false) { const box = $('notice'); box.hidden = false; box.className = `notice${error ? ' error' : ''}`; box.textContent = message; }
function rangeControl(gpu, enabled) {
  const control = node('div', 'dual-range');
  const track = node('div', 'dual-range-track'); const selected = node('div', 'dual-range-selected'); track.append(selected);
  const min = document.createElement('input'); const max = document.createElement('input');
  for (const input of [min, max]) { input.type = 'range'; input.min = String(gpu.driverMinPowerLimitWatts); input.max = String(gpu.driverMaxPowerLimitWatts); input.step = '1'; input.className = 'dual-range-input'; input.disabled = !enabled; }
  min.value = String(gpu.userMinPowerLimitWatts); max.value = String(gpu.userMaxPowerLimitWatts);
  min.setAttribute('aria-label', `${gpu.model}: minimale SolarMiner-Leistung`);
  max.setAttribute('aria-label', `${gpu.model}: maximale SolarMiner-Leistung`);
  const values = node('div', 'dual-range-values');
  const draw = () => {
    if (+min.value > +max.value) { if (document.activeElement === min) max.value = min.value; else min.value = max.value; }
    const low = 100 * (+min.value - +min.min) / (+min.max - +min.min);
    const high = 100 * (+max.value - +max.min) / (+max.max - +max.min);
    selected.style.left = `${low}%`; selected.style.right = `${100 - high}%`;
    values.replaceChildren(node('span', '', `Min. ${fmt(min.value)} W`), node('strong', '', `${fmt(min.value)}–${fmt(max.value)} W`), node('span', '', `Max. ${fmt(max.value)} W`));
  };
  let pending;
  const save = () => { clearTimeout(pending); pending = setTimeout(async () => {
    const response = await fetch(`/api/agent/power-control/gpus/${encodeURIComponent(gpu.deviceId)}/limits`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({minimumWatts:+min.value, maximumWatts:+max.value})});
    if (!response.ok) return notice('Die Treiber haben diese Grenzen abgelehnt. Die vorherigen sicheren Werte bleiben aktiv.', true);
    notice(`${gpu.model}: Schutzkorridor gespeichert.`); await refresh();
  }, 350); };
  for (const input of [min, max]) input.addEventListener('input', () => { draw(); save(); });
  draw(); control.append(track, min, max, values); return control;
}
function renderSettings(data) {
  const list = $('control-settings'); list.replaceChildren();
  const settings = [
    ['dynamicPowerScalingEnabled', 'Dynamische Regelung', 'PV-Wattziele verteilen'],
    ['externalControlEnabled', 'Node-Steuerung', 'Externe Befehle zulassen']
  ];
  for (const [key, title, detail] of settings) {
    const row = node('label', 'control-setting'); const input = document.createElement('input'); input.type = 'checkbox'; input.checked = Boolean(data[key]);
    const copy = node('span', ''); copy.append(node('strong', '', title), node('small', '', detail)); const toggle = node('span', 'toggle'); row.append(input, copy, toggle);
    input.addEventListener('change', async () => {
      const value = {dynamicPowerScalingEnabled: key === 'dynamicPowerScalingEnabled' ? input.checked : Boolean(data.dynamicPowerScalingEnabled), externalControlEnabled: key === 'externalControlEnabled' ? input.checked : Boolean(data.externalControlEnabled), workerExternalControl: data.workerExternalControl || {}};
      const response = await fetch('/api/agent/power-control/settings', {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(value)});
      if (!response.ok) { input.checked = !input.checked; return notice('Die lokale Einstellung konnte nicht gespeichert werden.', true); }
      notice('Lokale Steuerfreigabe sofort aktualisiert.'); await refresh();
    });
    list.append(row);
  }
  const workerToggle = (id, title, detail, enabled) => {
    const row = node('label', 'control-setting'); const input = document.createElement('input'); input.type = 'checkbox'; input.checked = enabled;
    const copy = node('span', ''); copy.append(node('strong', '', title), node('small', '', detail)); row.append(input, copy, node('span', 'toggle'));
    input.addEventListener('change', async () => {
      const response = await fetch(`/api/agent/power-control/workers/${encodeURIComponent(id)}/external-control?enabled=${input.checked}`, {method:'POST'});
      if (!response.ok) { input.checked = !input.checked; return notice('Die Worker-Freigabe konnte nicht gespeichert werden.', true); }
      notice(`${title}: externe Steuerung ${input.checked ? 'freigegeben' : 'deaktiviert'}.`); await refresh();
    });
    list.append(row);
  };
  workerToggle('cpu', 'CPU-Worker', 'Monero / RandomX für den Node sichtbar machen', data.workerExternalControl?.cpu !== false);
  for (const gpu of data.gpus || []) workerToggle(gpu.deviceId, `GPU-Worker · ${gpu.model}`, `${gpu.vendor} ${gpu.index} für externe Steuerung freigeben`, gpu.externalControlEnabled !== false);
}
function render(data) {
  renderSettings(data);
  const list = $('gpu-limits'); list.replaceChildren();
  for (const gpu of data.gpus || []) {
    const card = node('article', 'hardware-gpu-card');
    const head = node('div', 'hardware-gpu-head'); head.append(node('span', 'device-kind', gpu.vendor), node('h2', '', gpu.model), node('span', 'hardware-index', `GPU ${gpu.index}`));
    card.append(head, node('p', 'hardware-id', gpu.deviceId));
    if (!gpu.supportsDynamicPowerScaling) {
      card.append(node('p', 'muted', gpu.regulationError || 'Für dieses Gerät ist nur Start/Stopp verfügbar.')); list.append(card); continue;
    }
    const stats = node('div', 'hardware-gpu-stats');
    stats.append(node('span', '', `Treiber ${fmt(gpu.driverMinPowerLimitWatts)}–${fmt(gpu.driverMaxPowerLimitWatts)} W`), node('span', '', `Limit ${gpu.currentPowerLimitWatts ?? '–'} W`), node('span', '', `Aufnahme ${gpu.currentUsageWatts == null ? '–' : fmt(gpu.currentUsageWatts) + ' W'}`));
    card.append(stats);
    card.append(rangeControl(gpu, data.dynamicPowerScalingEnabled));
    if (!data.dynamicPowerScalingEnabled) card.append(node('p', 'muted', 'Dynamische Leistungsregelung ist lokal deaktiviert.')); list.append(card);
  }
  if (!list.children.length) list.append(node('p', 'empty', 'Keine GPU vom Treiber erkannt.'));
}
async function refresh() {
  if (refreshing) return; refreshing = true;
  try { const response = await fetch('/api/agent/power-control', {cache:'no-store'}); if (!response.ok) throw new Error(`HTTP ${response.status}`); render(await response.json()); $('connection').className = 'badge online'; $('connection').textContent = i18n.t('Agent verbunden'); $('updated').textContent = i18n.t(`Aktualisiert ${new Date().toLocaleTimeString(i18n.locale)}`); }
  catch (error) { $('connection').className = 'badge offline'; $('connection').textContent = 'Agent nicht erreichbar'; notice(`Hardwaredaten konnten nicht geladen werden: ${error.message}`, true); }
  finally { refreshing = false; }
}
$('refresh').addEventListener('click', refresh); refresh(); setInterval(() => { if (!document.hidden) refresh(); }, 5000);
