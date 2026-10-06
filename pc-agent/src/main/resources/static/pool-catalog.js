// Single source for pool candidates. Previously these were <option> values inside mining.html,
// so the pool list could not be shown, compared or reused anywhere else.
(() => {
  const kryptex = (coin, port, ssl) => [
    {value: `stratum+${ssl ? 'ssl' : 'tcp'}://${coin}.kryptex.network:${port}`, region: 'Global', feePercent: 1},
    {value: `stratum+${ssl ? 'ssl' : 'tcp'}://${coin}-eu.kryptex.network:${port}`, region: 'Europa', feePercent: 1},
    {value: `stratum+${ssl ? 'ssl' : 'tcp'}://${coin}-us.kryptex.network:${port}`, region: 'Nordamerika', feePercent: 1}
  ];
  const catalog = {
    monero: kryptex('xmr', 7029, false),
    pearl: kryptex('prl', 8048, true),
    ravencoin: kryptex('rvn', 7031, false),
    ethereumclassic: kryptex('etc', 7033, false),
    decred: [
      {value: 'stratum+tcp://dcr.suprnova.cc:9333', region: 'Europa', feePercent: null},
      {value: 'stratum+tcp://stratum-us.suprnova.cc:9333', region: 'Nordamerika', feePercent: null},
      {value: 'stratum+tcp://stratum-apac.suprnova.cc:9333', region: 'Asien', feePercent: null}
    ]
  };
  const standard = {value: 'solarminer', region: 'SolarMiner', feePercent: null};
  const custom = {value: 'custom', region: 'Eigene Adresse', feePercent: null};

  window.SolarMinerPoolCatalog = {
    /** SolarMiner first is wrong here: a new user should start on their own payout route. */
    for(coinId) { return [standard, ...(catalog[coinId] || []), custom]; },
    candidates(coinId) { return catalog[coinId] || []; },
    find(coinId, value) {
      if (!value) return null;
      return this.for(coinId).find(entry => entry.value === value)
        || {value, region: 'Eigener/noch nicht katalogisierter Pool', feePercent: null};
    },
    feeLabel(entry) {
      if (entry.value === 'solarminer') return 'SolarMiner-Standardziel · ohne eigene Auszahlung';
      if (entry.value === 'custom') return 'Eigene Pool-Adresse';
      return `${entry.region}${entry.feePercent != null ? ` · ${entry.feePercent} % Poolgebühr` : ''}`;
    },
    validUrl(value) { return /^stratum\+(tcp|ssl):\/\/[A-Za-z0-9._-]+:[0-9]{1,5}$/.test(String(value || '').trim()); }
  };
})();
