(() => {
  const strip = document.getElementById('wallet-strip');
  if (!strip) return;
  const preferences = window.SolarMinerPreferences;
  let balances = [];
  let prices = {};
  let wallets = [];

  const number = value => new Intl.NumberFormat(preferences.locale, {
    maximumFractionDigits: 8
  }).format(value);

  function chip(symbol, title, amount, ticker, status, description, address) {
    const item = document.createElement('div');
    item.className = `wallet-chip${status === 'AVAILABLE' ? '' : ' wallet-chip--loading'}`;
    const icon = document.createElement('span'); icon.setAttribute('aria-hidden', 'true'); icon.textContent = symbol;
    const label = document.createElement('span'); label.className = 'wallet-chip__content';
    const name = document.createElement('small'); name.textContent = title;
    const value = document.createElement('strong');
    value.textContent = status === 'AVAILABLE' && amount !== null
      ? `${number(amount)} ${ticker}` : 'Wallet hinterlegt';
    const hint = document.createElement('small');
    const valueInUsd = status === 'AVAILABLE' && amount !== null && prices[ticker] > 0 ? amount * prices[ticker] : null;
    hint.textContent = status === 'NOT_CONFIGURED' ? 'Noch nicht eingerichtet'
      : status === 'UNSUPPORTED_POOL' ? 'Für diesen Pool nicht verfügbar'
      : status === 'UNAVAILABLE' ? 'Momentan nicht erreichbar'
      : status === 'NOT_SUPPORTED' ? `${address} · ${prices[ticker] > 0 ? `1 ${ticker} ≈ ${preferences.moneyFromUsd(prices[ticker])} · ` : ''}Pool-Guthaben nicht verfügbar`
      : valueInUsd === null ? description : `≈ ${preferences.moneyFromUsd(valueInUsd)} · ${description}`;
    label.append(name, value, hint);
    item.append(icon, label);
    return item;
  }

  function render() {
    const byCoin = Object.fromEntries(balances.map(entry => [entry.coin, entry]));
    strip.replaceChildren();
    const items = document.createElement('div'); items.className = 'wallet-strip__items';
    for (const wallet of wallets) {
      const balance = byCoin[wallet.coin] || {};
      const supported = wallet.coin === 'monero' || wallet.coin === 'pearl';
      const status = supported ? balance.poolStatus || 'UNAVAILABLE' : 'NOT_SUPPORTED';
      const amount = supported ? balance.poolBalance : null;
      const shortAddress = wallet.address.length > 12
        ? `${wallet.address.slice(0, 6)}…${wallet.address.slice(-4)}` : wallet.address;
      items.append(chip(wallet.symbol, `${wallet.name} · im Pool`, amount, wallet.ticker,
        status, 'Pool-Guthaben vor Auszahlung', shortAddress));
      if (wallet.coin === 'pearl') items.append(chip(wallet.symbol, 'Pearl · auf Adresse', balance.onChainBalance,
        'PRL', balance.onChainStatus || 'UNAVAILABLE', 'Bereits ausgezahlter Bestand', shortAddress));
    }
    if (!wallets.length) {
      const link = document.createElement('a'); link.href = '/wallets.html';
      link.className = 'wallet-chip'; link.textContent = 'Noch keine Wallet hinterlegt · Wallets öffnen';
      items.append(link);
    }
    const settings = document.createElement('div'); settings.className = 'wallet-strip__settings';
    const amounts = wallets.flatMap(wallet => {
      const balance = byCoin[wallet.coin] || {};
      return wallet.coin === 'pearl'
        ? [[balance.poolBalance, balance.poolStatus, prices.PRL], [balance.onChainBalance, balance.onChainStatus, prices.PRL]]
        : [[balance.poolBalance, balance.poolStatus, prices[wallet.ticker]]];
    });
    const known = amounts.filter(([amount, status, price]) => status === 'AVAILABLE' && amount !== null && price > 0);
    const totalUsd = known.reduce((sum, [amount, , price]) => sum + amount * price, 0);
    const summary = document.createElement('strong'); summary.className = 'wallet-strip__total';
    summary.textContent = `Bekannter Wert ≈ ${known.length ? preferences.moneyFromUsd(totalUsd) : '—'}`;
    const btc = document.createElement('span'); btc.className = 'wallet-strip__total';
    btc.textContent = prices.BTC > 0 ? `BTC ${preferences.moneyFromUsd(prices.BTC)}` : 'BTC-Preis —';
    const selector = document.createElement('select'); selector.setAttribute('aria-label', 'Anzeigewährung');
    for (const code of preferences.currencies) {
      const option = document.createElement('option'); option.value = code; option.textContent = code;
      selector.append(option);
    }
    selector.value = preferences.currency;
    selector.addEventListener('change', () => {
      preferences.setCurrency(selector.value);
    });
    settings.append(btc, summary, selector);
    strip.append(items, settings);
  }

  async function load() {
    try {
      const [balancesResult, pricesResult, overviewResult] = await Promise.allSettled([
        fetch('/api/agent/local/wallet-balances', {cache: 'no-store'}),
        fetch('/api/agent/local/market-prices', {cache: 'no-store'}),
        fetch('/api/agent/local/overview', {cache: 'no-store'})
      ]);
      if (balancesResult.status === 'fulfilled' && balancesResult.value.ok) {
        const received = await balancesResult.value.json();
        if (Array.isArray(received)) balances = received;
      }
      if (pricesResult.status === 'fulfilled' && pricesResult.value.ok) {
        const received = await pricesResult.value.json();
        prices = received && typeof received === 'object' && !Array.isArray(received) ? received : {};
      }
      if (overviewResult.status === 'fulfilled' && overviewResult.value.ok) {
        const overview = await overviewResult.value.json();
        wallets = (overview.coins || []).map(coin => {
          const configuration = coin.id === 'monero' ? overview.moneroConfiguration
            : coin.id === 'pearl' ? overview.pearlConfiguration : overview.gpuCoins?.[coin.id]?.configuration;
          return configuration?.wallet ? {coin: coin.id, name: coin.name, ticker: coin.ticker,
            address: configuration.wallet, symbol: coin.ticker.slice(0, 1)} : null;
        }).filter(Boolean);
      }
      render();
    } catch {
      render();
    }
  }

  render();
  load();
  document.addEventListener('solarminer:preferences-changed', render);
  window.addEventListener('solarminer:wallets-changed', load);
  document.addEventListener('visibilitychange', () => { if (!document.hidden) load(); });
  window.setInterval(() => { if (!document.hidden) load(); }, 60000);
})();
