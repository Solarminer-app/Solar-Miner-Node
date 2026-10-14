"use client";

import {useParams} from "next/navigation";
import {useEffect, useState} from "react";
import {useSitePreferences} from "../site-preferences-context";

type CoinEstimate = {coin: string; ticker: string; available: boolean; reason: string; coinsPerDay: number; usdPerDay: number; watts: number; hashrateHps: number; updatedAt: string; sources?: string[]; diagnostics?: string[]; grossCoinsPerDay: number; grossUsdPerDay: number; knownFeePercent: number | null; estimateKind: 'NET_KNOWN_FEES' | 'GROSS' | 'UNAVAILABLE'};
type PoolActual = {coin: string; ticker: string; from: string; to: string; amount: number; averagePerDay: number; usdValue: number | null; routes: number; dataPoints: number; complete: boolean; source: string};
type Snapshot = {coins: CoinEstimate[]; actualPoolCredits: PoolActual[]; complete: boolean; centsPerKwh: number | null; gridCentsPerKwh: number | null; eurPerDay: number | null; kwhPerDay: number | null; eurPerUsd: number | null; updatedAt: string; diagnostics?: string[]; currencyCode: string; currencySymbol: string; currencyPerKwh: number | null; gridCurrencyPerKwh: number | null; currencyPerDay: number | null; currencyPerUsd: number | null};

export default function EarningsPage() {
    const {siteId} = useParams<{siteId: string}>();
    const {locale, currency} = useSitePreferences();
    const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
    const [error, setError] = useState<string | null>(null);
    useEffect(() => {
        let active = true;
        const load = async () => {
            try {
                const query = new URLSearchParams({locale, currency});
                const response = await fetch(`/api/pv-site/${siteId}/dashboard/earnings?${query}`, {cache: "no-store"});
                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                if (active) {setSnapshot(await response.json() as Snapshot); setError(null);}
            } catch (cause) { if (active) setError(cause instanceof Error ? cause.message : "Network error"); }
        };
        void load();
        const timer = window.setInterval(() => void load(), 30000);
        return () => {active = false; window.clearInterval(timer);};
    }, [currency, locale, siteId]);
    const de = locale === "de";
    const number = (value: number, digits = 2) => new Intl.NumberFormat(de ? "de-DE" : "en-US", {maximumFractionDigits: digits}).format(value);
    const money = (value: number) => new Intl.NumberFormat(de ? "de-DE" : "en-US", {style: "currency", currency: snapshot?.currencyCode ?? currency}).format(value);
    const moneyPerKwh = (value: number) => `${new Intl.NumberFormat(de ? "de-DE" : "en-US", {style: "currency", currency: snapshot?.currencyCode ?? currency, maximumFractionDigits: 4}).format(value)}/kWh`;
    const usd = (value: number) => new Intl.NumberFormat(de ? "de-DE" : "en-US", {style: "currency", currency: "USD"}).format(value);
    const tile = "rounded-2xl border border-white/10 bg-[#151519] p-5";
    return <main className="min-h-screen bg-[#0b0b0d] px-4 py-8 text-white md:px-8">
        <div className="mx-auto max-w-6xl space-y-6">
            <header><p className="text-xs font-semibold uppercase tracking-[0.16em] text-yellow-400">{de ? "Mining-Ökonomie" : "Mining economics"}</p><h1 className="mt-2 text-3xl font-semibold">{de ? "Ertragsprognose" : "Earnings forecast"}</h1><p className="mt-2 max-w-3xl text-sm text-[#a5a5af]">{de ? "Prognose aus gemessener Hashrate, Chain- und Marktdaten; PC-Agent-Werte werden erst nach vollständig bekannten Gebühren als verfügbar gewertet. Tatsächliche Pool-Gutschriften stehen getrennt darunter." : "Forecast from measured hashrate, chain and market data; PC agent values are available only after all known fees are resolved. Actual pool credits are shown separately below."}</p></header>
            {error && <p className="rounded-xl border border-red-500/30 p-4 text-red-300">{de ? "Datenabruf fehlgeschlagen" : "Data request failed"}: {error}</p>}
            {!snapshot ? <p className="text-[#a5a5af]">{de ? "Lade Prognose…" : "Loading forecast…"}</p> : <>
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                    <article className={tile}><p className="text-sm text-[#9b9ba5]">{de ? "Ertrag pro Tag" : "Daily revenue"}</p><strong className="mt-3 block text-2xl">{snapshot.currencyPerDay == null ? "—" : money(snapshot.currencyPerDay)}</strong></article>
                    <article className={tile}><p className="text-sm text-[#9b9ba5]">{de ? "Ertrag je kWh" : "Revenue per kWh"}</p><strong className="mt-3 block text-2xl">{snapshot.currencyPerKwh == null ? "—" : moneyPerKwh(snapshot.currencyPerKwh)}</strong></article>
                    <article className={tile}><p className="text-sm text-[#9b9ba5]">{de ? "Aktueller Netzstrompreis" : "Current grid tariff"}</p><strong className="mt-3 block text-2xl">{snapshot.gridCurrencyPerKwh == null ? "—" : moneyPerKwh(snapshot.gridCurrencyPerKwh)}</strong></article>
                    <article className={tile}><p className="text-sm text-[#9b9ba5]">{de ? "Abstand zum Netzpreis" : "Margin to grid tariff"}</p><strong className={`mt-3 block text-2xl ${snapshot.complete && snapshot.currencyPerKwh! > snapshot.gridCurrencyPerKwh! ? "text-emerald-300" : "text-[#dddde3]"}`}>{snapshot.complete ? moneyPerKwh(snapshot.currencyPerKwh! - snapshot.gridCurrencyPerKwh!) : "—"}</strong></article>
                </div>
                <section className={tile}><h2 className="text-lg font-semibold">{de ? "Alle Coins" : "All coins"}</h2><p className="mt-2 text-sm text-[#9b9ba5]">{de ? "Gesamtverbrauch" : "Total consumption"}: {snapshot.kwhPerDay == null ? "—" : `${number(snapshot.kwhPerDay)} kWh/Tag`}. {de ? "Die Vergleichswerte erscheinen nur bei vollständigen Daten für alle aktiven Miner." : "Comparisons appear only when all active miners have complete data."}</p></section>
                <section className={tile}><div className="flex flex-wrap items-end justify-between gap-2"><div><h2 className="text-lg font-semibold">{de ? 'Tatsächliche Pool-Gutschriften' : 'Actual pool credits'}</h2><p className="mt-1 text-sm text-[#9b9ba5]">{de ? 'Sieben abgeschlossene UTC-Tage aus unterstützten Pool-APIs; Saldo und Auszahlung sind davon getrennt.' : 'Seven completed UTC days from supported pool APIs; balance and payouts are separate.'}</p></div><span className="text-xs text-[#777783]">Kryptex Reward Chart</span></div><div className="mt-4 grid gap-3 md:grid-cols-2">{snapshot.actualPoolCredits?.map(actual => <article className="rounded-xl border border-white/10 bg-black/20 p-4" key={actual.coin}><div className="flex items-center justify-between"><strong>{actual.ticker}</strong><span className={`text-xs ${actual.complete ? 'text-emerald-300' : 'text-amber-300'}`}>{actual.complete ? (de ? 'vollständig' : 'complete') : (de ? 'unvollständig' : 'incomplete')}</span></div><strong className="mt-3 block text-xl">{number(actual.amount, 8)} {actual.ticker}</strong><p className="mt-1 text-xs text-[#9999a4]">Ø {number(actual.averagePerDay, 8)} {actual.ticker}/{de ? 'Tag' : 'day'} · {actual.from} – {actual.to}</p>{actual.usdValue != null && <p className="mt-1 text-xs text-[#9999a4]">{de ? 'Historischer USD-Wert' : 'Historical USD value'}: {usd(actual.usdValue)}</p>}<p className="mt-1 text-xs text-[#777783]">{actual.routes} {de ? 'Ziel(e)' : 'route(s)'} · {actual.dataPoints} {de ? 'Tageswerte' : 'daily points'}</p></article>)}{!snapshot.actualPoolCredits?.length && <p className="text-sm text-[#777783]">{de ? 'Für XMR/PRL ist noch kein unterstütztes Pool-Ziel mit Ertragshistorie hinterlegt.' : 'No supported XMR/PRL pool target with reward history is configured yet.'}</p>}</div></section>
                {snapshot.diagnostics?.length ? <section className={`${tile} border-amber-400/30`}><h2 className="text-lg font-semibold text-amber-200">{de ? "Probleme bei der Datengrundlage" : "Data basis issues"}</h2><ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-amber-100">{snapshot.diagnostics.map((diagnostic, index) => <li key={`${diagnostic}-${index}`}>{diagnosticText(diagnostic, de)}</li>)}</ul></section> : null}
                <section className="grid gap-4 md:grid-cols-2">{snapshot.coins.map((coin, index) => <article className={tile} key={`${coin.coin}-${index}`}><div className="flex items-center justify-between"><h2 className="text-xl font-semibold">{coin.ticker === "—" ? coin.coin : coin.ticker}</h2><span className={`text-xs ${coin.available ? "text-emerald-300" : "text-amber-300"}`}>{coin.available ? de ? "Aktuell" : "Current" : de ? "Nicht verfügbar" : "Unavailable"}</span></div>{coin.available ? <div className="mt-5 grid grid-cols-2 gap-4 text-sm"><div><span className="text-[#9999a3]">{coin.estimateKind === 'NET_KNOWN_FEES' ? (de ? 'Netto-Coins / Tag' : 'Net coins / day') : (de ? 'Brutto-Coins / Tag' : 'Gross coins / day')}</span><strong className="mt-1 block">{number(coin.coinsPerDay, 8)} {coin.ticker}</strong></div><div><span className="text-[#9999a3]">{coin.estimateKind === 'NET_KNOWN_FEES' ? (de ? 'Nach bekannten Gebühren / Tag' : 'After known fees / day') : (de ? 'Brutto / Tag' : 'Gross / day')}</span><strong className="mt-1 block">{snapshot.currencyPerUsd == null ? "—" : money(coin.usdPerDay * snapshot.currencyPerUsd)}</strong>{coin.knownFeePercent != null && <span className="mt-1 block text-xs text-[#777783]">− {number(coin.knownFeePercent)} %</span>}</div><div><span className="text-[#9999a3]">{de ? "Leistung" : "Power"}</span><strong className="mt-1 block">{number(coin.watts)} W</strong></div><div><span className="text-[#9999a3]">{de ? "Ertrag je kWh" : "Revenue per kWh"}</span><strong className="mt-1 block">{snapshot.currencyPerUsd == null || coin.watts <= 0 ? "—" : moneyPerKwh(coin.usdPerDay * snapshot.currencyPerUsd / (coin.watts * 24 / 1000))}</strong></div></div> : <p className="mt-4 text-sm text-[#a5a5af]">{coin.reason ? diagnosticText(coin.reason, de) : (de ? "Hashrate, Leistung oder Marktdaten fehlen." : "Hashrate, power or market data unavailable.")}</p>}<details className="mt-4 border-t border-white/10 pt-3 text-xs text-[#a5a5af]"><summary className="cursor-pointer">{de ? "Datenquellen und Diagnose" : "Sources and diagnostics"}</summary>{coin.diagnostics?.length ? <ul className="mt-2 list-disc space-y-1 pl-5 text-amber-100">{coin.diagnostics.map((diagnostic, diagnosticIndex) => <li key={`${diagnostic}-${diagnosticIndex}`}>{diagnosticText(diagnostic, de)}</li>)}</ul> : <p className="mt-2">{de ? "Keine Datenfehler gemeldet." : "No data errors reported."}</p>}{coin.sources?.length ? <p className="mt-2">{de ? "Quellen" : "Sources"}: {coin.sources.join(", ")}</p> : null}{coin.updatedAt ? <p className="mt-1">{de ? "Letzter Coin-Datenstand" : "Last coin data update"}: {new Date(coin.updatedAt).toLocaleString(de ? "de-DE" : "en-US")}</p> : null}</details></article>)}</section>
                <p className="text-xs leading-relaxed text-[#85858f]">{de ? "PC-Agent-Prognosen ziehen vollständig bekannte Pool-, Miner- und SolarMiner-Gebühren ab. Abgelehnte oder veraltete Shares, Steuern, Stromkosten und PV-Einspeisevergütung sind nicht abgezogen. BTC bleibt eine Brutto-Prognose. Fehlende oder veraltete Daten verhindern die Rentabilitätsregel. Stand:" : "PC agent forecasts deduct fully resolved pool, miner, and SolarMiner fees. Rejected or stale shares, taxes, electricity costs, and PV export value are not deducted. BTC remains a gross forecast. Missing or stale data disable the profitability rule. Updated:"} {new Date(snapshot.updatedAt).toLocaleString(de ? "de-DE" : "en-US")}</p>
            </>}
        </div>
    </main>;
}

function diagnosticText(value: string, de: boolean) {
    if (de) return value;
    const exact: Record<string, string> = {
        'USD/EUR-Wechselkurs fehlt oder ist veraltet': 'USD/EUR exchange rate is missing or stale',
        'Stromtarif fehlt oder ist ungültig': 'Electricity tariff is missing or invalid',
        'Keine positive Leistung aus verfügbaren Miner-Prognosen': 'No positive power reading from available miner forecasts',
        'PC-Agent-Daten sind als veraltet markiert': 'PC agent data is marked as stale',
        'PC-Agent-Prognose fehlt oder ist älter als 20 Minuten': 'PC agent forecast is missing or older than 20 minutes',
        'Gemessene Miner-Leistung fehlt oder ist 0 W': 'Measured miner power is missing or 0 W',
        'Prognostizierte Coins pro Tag fehlen oder sind 0': 'Forecast coins per day are missing or zero',
        'PC-Agent konnte Pool-, Miner- und SolarMiner-Gebühren nicht vollständig auflösen': 'PC agent could not fully resolve pool, miner, and SolarMiner fees',
        'Keine Prognosequelle für diesen Miner': 'No forecast source is available for this miner',
        'Keine Coin-Prognose vorhanden': 'No coin forecast is available',
        'Coin ist im Node nicht registriert': 'Coin is not registered in the node'
    };
    if (exact[value]) return exact[value];
    if (value.includes('/USD-Kurs fehlt im Currency Service')) return value.replace('/USD-Kurs fehlt im Currency Service', '/USD price is missing from Currency Service');
    if (value.includes('/USD-Kurs fehlt')) return value.replace('/USD-Kurs fehlt in den PC-Agent-Daten', '/USD price is missing from PC agent data');
    if (value.startsWith('PC-Agent-Abruf fehlgeschlagen')) return value.replace('PC-Agent-Abruf fehlgeschlagen', 'PC agent request failed');
    return value;
}
