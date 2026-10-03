(() => {
  const number = value => new Intl.NumberFormat(window.SolarMinerI18n.locale,
    {maximumSignificantDigits: 4, notation: 'standard'}).format(value);
  const hashUnits = ['H', 'kH', 'MH', 'GH', 'TH', 'PH', 'EH'];
  window.SolarMinerMeasurements = {
    hashrate(value) {
      if (!(value > 0) || !Number.isFinite(value)) return '—';
      let index = 0;
      while (value >= 1000 && index < hashUnits.length - 1) { value /= 1000; index++; }
      return `${number(value)} ${hashUnits[index]}/s`;
    },
    efficiency(hashrateHs, powerWatts) {
      if (!(hashrateHs > 0) || !(powerWatts > 0) || !Number.isFinite(hashrateHs) || !Number.isFinite(powerWatts)) return '—';
      let value = powerWatts / hashrateHs, index = 0;
      if (!Number.isFinite(value)) return '—';
      while (value < 1 && index < hashUnits.length - 1) { value *= 1000; index++; }
      return `${number(value)} J/${hashUnits[index]}`;
    }
  };
})();
