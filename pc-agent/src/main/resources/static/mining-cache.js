// A short-lived view snapshot for this origin and tab; never an authorization to run miners.
(() => {
  const key = 'solarminer.mining-view.v1', maxAge = 5 * 60 * 1000;
  const valid = value => value && Array.isArray(value.coins) && Array.isArray(value.gpus) &&
    value.stats && Array.isArray(value.stats.workers) && value.proxy;
  window.SolarMinerMiningCache = {
    read() {
      try {
        const snapshot = JSON.parse(sessionStorage.getItem(key));
        const age = Date.now() - snapshot?.savedAt;
        if (!(age >= 0 && age < maxAge) || !valid(snapshot?.overview)) return null;
        return snapshot;
      } catch (_) { return null; }
    },
    write(overview) {
      if (!valid(overview)) return;
      try { sessionStorage.setItem(key, JSON.stringify({savedAt: Date.now(), overview})); } catch (_) {}
    },
    clear() { try { sessionStorage.removeItem(key); } catch (_) {} }
  };
})();
