const HASHRATE_UNITS = ['H/s', 'kH/s', 'MH/s', 'GH/s', 'TH/s', 'PH/s'];

/** Formats a rate using the closest SI hash-rate unit (for example, 0.8 TH/s becomes 800 GH/s). */
export function formatHashrateThs(terahashesPerSecond: number, locale: 'de' | 'en' = 'en'): string {
    if (!Number.isFinite(terahashesPerSecond) || terahashesPerSecond < 0) return '—';

    let value = terahashesPerSecond * 1e12;
    let unitIndex = 0;
    while (value >= 1000 && unitIndex < HASHRATE_UNITS.length - 1) {
        value /= 1000;
        unitIndex++;
    }

    return `${new Intl.NumberFormat(locale === 'de' ? 'de-DE' : 'en-US', {maximumFractionDigits: 2}).format(value)} ${HASHRATE_UNITS[unitIndex]}`;
}
