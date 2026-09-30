(() => {
  const strip = document.getElementById('wallet-strip');
  if (!strip) return;
  const currencies = ['EUR', 'USD', 'CHF'];
  let currency = currencies.includes(localStorage.getItem('solarminer.agent.currency'))
    ? localStorage.getItem('solarminer.agent.currency') : 'EUR';
  let balances = [];
  let prices = {};
  const exchangeRates = {USD: 1};

  const number = value => new Intl.NumberFormat(document.documentElement.lang || 'de', {
    maximumFractionDigits: 8
  }).format(value);

  const fiat = amount => new Intl.NumberFormat(document.documentElement.lang || 'de', {
    style: 'currency', currency, maximumFractionDigits: 2
  }).format(amount);

  function chip(symbol, title, amount, ticker, status, description) {
    const item = document.createElement('div');
    item.className = `wallet-chip${status === 'AVAILABLE' ? '' : ' wallet-chip--loading'}`;
    const icon = document.createElement('span'); icon.setAttribute('aria-hidden', 'true'); icon.textContent = symbol;
    const label = document.createElement('span'); label.className = 'wallet-chip__content';
    const name = document.createElement('small'); name.textContent = title;
    const value = document.createElement('strong');
    value.textContent = status === 'AVAILABLE' && amount !== null
      ? `${number(amount)} ${ticker}` : '—';
    const hint = document.createElement('small');
    const valueInFiat = status === 'AVAILABLE' && amount !== null && prices[ticker] > 0 && exchangeRates[currency] > 0
      ? amount * prices[ticker] * exchangeRates[currency] : null;
    hint.textContent = status === 'NOT_CONFIGURED' ? 'Noch nicht eingerichtet'
      : status === 'UNSUPPORTED_POOL' ? 'Für diesen Pool nicht verfügbar'
      : status === 'UNAVAILABLE' ? 'Momentan nicht erreichbar'
      : valueInFiat === null ? description : `≈ ${fiat(valueInFiat)} · ${description}`;
    label.append(name, value, hint);
    item.append(icon, label);
    return item;
  }

  function render() {
    const byCoin = Object.fromEntries(balances.map(entry => [entry.coin, entry]));
    const xmr = byCoin.xmr || {};
    const prl = byCoin.prl || {};
    strip.replaceChildren();
    const items = document.createElement('div'); items.className = 'wallet-strip__items';
    items.append(
      chip('ɱ', 'Monero · im Pool', xmr.poolBalance, 'XMR', xmr.poolStatus, 'Kryptex-Guthaben vor Auszahlung'),
      chip('◈', 'Pearl · im Pool', prl.poolBalance, 'PRL', prl.poolStatus, 'Kryptex-Guthaben vor Auszahlung'),
      chip('◈', 'Pearl · auf Adresse', prl.onChainBalance, 'PRL', prl.onChainStatus, 'Bereits ausgezahlter Bestand')
    );
    const settings = document.createElement('div'); settings.className = 'wallet-strip__settings';
    const amounts = [
      [xmr.poolBalance, xmr.poolStatus, prices.XMR],
      [prl.poolBalance, prl.poolStatus, prices.PRL],
      [prl.onChainBalance, prl.onChainStatus, prices.PRL]
    ];
    const known = amounts.filter(([amount, status, price]) => status === 'AVAILABLE' && amount !== null && price > 0);
    const total = known.reduce((sum, [amount, , price]) => sum + amount * price * (exchangeRates[currency] || 0), 0);
    const summary = document.createElement('strong'); summary.className = 'wallet-strip__total';
    summary.textContent = `Bekannter Wert ≈ ${known.length && exchangeRates[currency] ? fiat(total) : '—'}`;
    const selector = document.createElement('select'); selector.setAttribute('aria-label', 'Anzeigewährung');
    for (const code of currencies) {
      const option = document.createElement('option'); option.value = code; option.textContent = code;
      selector.append(option);
    }
    selector.value = currency;
    selector.addEventListener('change', () => {
      currency = selector.value;
      localStorage.setItem('solarminer.agent.currency', currency);
      loadExchangeRate().then(render);
    });
    settings.append(summary, selector);
    strip.append(items, settings);
  }

  async function loadExchangeRate() {
    if (exchangeRates[currency]) return;
    try {
      const response = await fetch(`https://api.frankfurter.dev/v2/rate/usd/${currency.toLowerCase()}`);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      if (Number.isFinite(data.rate) && data.rate > 0) exchangeRates[currency] = data.rate;
    } catch { /* Coin balances remain visible without a fiat exchange rate. */ }
  }

  async function load() {
    try {
      const [balancesResult, forecastsResult] = await Promise.allSettled([
        fetch('/api/agent/wallet-balances', {cache: 'no-store'}),
        fetch('/api/agent/earnings', {cache: 'no-store'})
      ]);
      if (balancesResult.status === 'fulfilled' && balancesResult.value.ok) {
        balances = await balancesResult.value.json();
      }
      if (forecastsResult.status === 'fulfilled' && forecastsResult.value.ok) {
        const forecasts = await forecastsResult.value.json();
        prices = Object.fromEntries(forecasts.filter(item => item.priceUsd > 0)
          .map(item => [item.ticker, item.priceUsd]));
      }
      render();
      await loadExchangeRate();
      render();
    } catch {
      render();
    }
  }

  render();
  load();
  document.addEventListener('visibilitychange', () => { if (!document.hidden) load(); });
  window.setInterval(() => { if (!document.hidden) load(); }, 60000);
})();
