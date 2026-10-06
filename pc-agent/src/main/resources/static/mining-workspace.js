// Local view state is independent of the persistent Node mining profile.
(() => {
  const byId = id => document.getElementById(id);
  const labels = {none: 'Nur lokal / Benchmarks', monero: 'Monero · XMR', pearl: 'Pearl · PRL', ravencoin: 'Ravencoin · RVN', ethereumclassic: 'Ethereum Classic · ETC', decred: 'Decred · DCR', quantus: 'Quantus · QTC'};
  let overview, settings, power, saving = false, loading = false, initialProfile = true;
  const element = (tag, cls, text) => {
    const el = document.createElement(tag); el.className = cls || '';
    if (text !== undefined) el.textContent = text;
    return el;
  };
  const message = (text, error = false) => {
    const box = byId('automation-feedback'); box.hidden = false;
    box.className = `notice${error ? ' error' : ''}`; box.textContent = text;
  };
  async function request(url, body) {
    if (!overviewFresh) throw new Error('Warte auf aktuelle Agent-Daten, bevor du eine Änderung ausführst.');
    invalidateMiningViewCache();
    try {
      const response = await fetch(url, {method: 'POST', headers: {'Content-Type': 'application/json'}, ...(body ? {body: JSON.stringify(body)} : {})});
      if (!response.ok) {
        const result = await response.json().catch(() => ({}));
        throw new Error(result.message || result.detail || `Speichern fehlgeschlagen (${response.status})`);
      }
      return await response.json();
    } finally { invalidateMiningViewCache(); refresh(); }
  }
  function renderProfile() {
    if (!settings || !power || !overview) return;
    if (!overviewFresh) {
      byId('automation-enabled').disabled = true;
      byId('automation-devices').querySelectorAll('select').forEach(select => select.disabled = true);
      return;
    }
    if (initialProfile) {
      initialProfile = false;
      if (sessionStorage.getItem('mining-profile-open') == null && settings.workerCoins != null && !Object.keys(settings.workerCoins).length)
        byId('automation-editor').open = true;
      if (location.hash === '#profile') requestAnimationFrame(() => byId('node-profile').scrollIntoView({block: 'start'}));
    }
    const enabled = byId('automation-enabled'); enabled.checked = settings.externalControlEnabled; enabled.disabled = saving;
    const root = byId('automation-devices');
    // Polling must not replace an open select or steal keyboard focus.
    if (root.contains(document.activeElement)) return;
    root.replaceChildren(); root.setAttribute('aria-busy', 'false');
    const devices = [{deviceId: 'cpu', model: 'CPU', kind: 'CPU'}, ...(power.gpus || []).map(g => ({...g, kind: 'GPU'}))];
    let included = 0;
    for (const device of devices) {
      const coin = settings.workerCoins?.[device.deviceId] || (settings.workerCoins == null
        ? (device.kind === 'CPU' ? 'monero' : 'pearl') : device.kind === 'GPU' ? settings.workerCoins['*'] || 'none' : 'none');
      const permitted = settings.workerExternalControl?.[device.deviceId] !== false;
      if (coin !== 'none' && permitted) included++;
      const card = element('article', `automation-device${coin === 'none' || !permitted ? ' local-only' : ''}`);
      const head = element('div', 'automation-device-head');
      head.append(element('span', 'tag', device.kind), element('strong', '', device.model));
      const label = element('label', '', 'Standard für den Node');
      const select = element('select'); select.id = `node-coin-${device.deviceId}`; label.htmlFor = select.id;
      select.setAttribute('aria-label', `${device.model}: Standard-Coin für den Node`); select.disabled = saving;
      for (const key of device.kind === 'CPU' ? ['none', 'monero'] : ['none', 'pearl', 'ravencoin', 'ethereumclassic', 'decred', 'quantus']) {
        const option = element('option', '', labels[key]); option.value = key;
        const catalog = overview.coins?.find(c => c.id === key);
        if (key !== 'none' && key !== coin && (!catalog?.binaryAvailable || !catalog?.configured || catalog?.experimental)) option.disabled = true;
        select.append(option);
      }
      select.value = coin;
      const catalog = overview.coins?.find(c => c.id === coin);
      const states = coin === 'pearl' ? overview.pearl?.gpus : overview.gpuCoins?.[coin]?.gpus;
      const selected = device.kind === 'CPU' || states?.some(g => g.vendor === device.vendor && g.index === device.index && g.selected);
      let detail = coin === 'none' ? 'Bleibt lokal. Für den Node: 0 W.'
        : !permitted ? 'Gerätefreigabe ist in Hardware deaktiviert.'
        : !catalog?.binaryAvailable ? 'Miner zuerst über + installieren.'
        : !catalog?.configured || !selected ? 'Einrichtung fehlt: Pool, Wallet und dieses Gerät auswählen.'
        : catalog?.experimental ? 'Vorbereitet. Mining-Route noch nicht freigegeben.'
        : settings.externalControlEnabled ? 'Im Node-Profil enthalten.' : 'Gespeichert. Node-Steuerung ist ausgeschaltet.';
      const info = element('small', '', detail);
      select.addEventListener('change', async () => {
        saving = true; select.disabled = true; enabled.disabled = true;
        try {
          settings = await request(`/api/agent/local/power-control/workers/${encodeURIComponent(device.deviceId)}/coin?coin=${encodeURIComponent(select.value)}`);
          select.blur(); message(`${device.model}: ${labels[select.value]} gespeichert. Der vorherige Worker wurde angehalten; ein Start erfolgt erst durch einen neuen Befehl.`);
        } catch (error) { select.value = coin; message(error.message, true); }
        finally { saving = false; await loadProfile(); renderProfile(); }
      });
      card.append(head, label, select, info);
      if (coin !== 'none') {
        const link = element('a', '', 'Miner einrichten →'); link.href = `#${coin}`;
        link.addEventListener('click', event => {
          event.preventDefault();
          window.SolarMinerMining?.openCoin(catalog?.binaryAvailable ? coin : 'library');
          byId('instance-view').scrollIntoView({behavior: 'smooth', block: 'start'});
        }); card.append(link);
      }
      root.append(card);
    }
    byId('automation-summary').textContent = `${included} von ${devices.length} Geräten im Node-Profil · ${settings.externalControlEnabled ? 'Steuerung erlaubt' : 'Steuerung ausgeschaltet'}`;
  }
  async function loadProfile() {
    if (loading || saving) return; loading = true;
    try {
      const responses = await Promise.all(['/api/agent/local/power-control/settings', '/api/agent/local/power-control'].map(url => fetch(url, {cache: 'no-store'})));
      if (responses.some(r => !r.ok)) throw new Error('Node-Profil konnte nicht geladen werden.');
      [settings, power] = await Promise.all(responses.map(r => r.json())); renderProfile();
    } catch (error) { message(error.message, true); byId('automation-enabled').disabled = true; }
    finally { loading = false; }
  }
  byId('automation-enabled').addEventListener('change', async event => {
    saving = true; event.target.disabled = true;
    try {
      settings = await request('/api/agent/local/power-control/settings', {...settings, externalControlEnabled: event.target.checked});
      message(settings.externalControlEnabled ? 'Node-Steuerung erlaubt. Nur Geräte aus diesem Profil werden automatisch verwendet.' : 'Node-Steuerung ausgeschaltet. Laufende Miner kannst du weiterhin lokal pausieren.');
    } catch (error) { message(error.message, true); }
    finally { saving = false; renderProfile(); }
  });

  const editor = byId('automation-editor');
  byId('open-node-profile').addEventListener('click', () => {
    editor.open = true;
    sessionStorage.setItem('mining-profile-open', 'true');
    byId('node-profile').scrollIntoView({behavior: 'smooth', block: 'start'});
    editor.querySelector('summary').focus({preventScroll: true});
  });
  editor.open = location.hash === '#profile' || sessionStorage.getItem('mining-profile-open') === 'true';
  editor.querySelector('summary').addEventListener('click', () => setTimeout(() => sessionStorage.setItem('mining-profile-open', String(editor.open)), 0));
  window.addEventListener('mining-rendered', event => { overview = event.detail; renderProfile(); });
  // The first overview may have arrived before this deferred script.
  if (typeof latest !== 'undefined' && latest) overview = latest;
  loadProfile();
  setInterval(() => { if (!document.hidden) loadProfile(); }, 5000);
})();
