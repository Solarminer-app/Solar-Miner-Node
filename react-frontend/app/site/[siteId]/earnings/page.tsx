"use client";

import {useParams} from "next/navigation";
import {useEffect, useState} from "react";
import {useSitePreferences} from "../site-preferences-context";

type CoinEstimate = {coin: string; ticker: string; available: boolean; reason: string; coinsPerDay: number; usdPerDay: number; watts: number; hashrateHps: number; updatedAt: string};
type Snapshot = {coins: CoinEstimate[]; complete: boolean; centsPerKwh: number | null; gridCentsPerKwh: number | null; eurPerDay: number | null; kwhPerDay: number | null; eurPerUsd: number | null; updatedAt: string};

export default function EarningsPage() {
    const {siteId} = useParams<{siteId: string}>();
    const {locale} = useSitePreferences();
    const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
    const [error, setError] = useState(false);
    useEffect(() => {
        let active = true;
        const load = async () => {
            try {
                const response = await fetch(`/api/pv-site/${siteId}/dashboard/earnings`, {cache: "no-store"});
                if (!response.ok) throw new Error(String(response.status));
                if (active) {setSnapshot(await response.json() as Snapshot); setError(false);}
            } catch { if (active) setError(true); }
        };
        void load();
        const timer = window.setInterval(() => void load(), 30000);
        return () => {active = false; window.clearInterval(timer);};
    }, [siteId]);
    const de = locale === "de";
    const number = (value: number, digits = 2) => new Intl.NumberFormat(de ? "de-DE" : "en-US", {maximumFractionDigits: digits}).format(value);
    const money = (value: number) => new Intl.NumberFormat(de ? "de-DE" : "en-US", {style: "currency", currency: "EUR"}).format(value);
    const tile = "rounded-2xl border border-white/10 bg-[#151519] p-5";
    return <main className="min-h-screen bg-[#0b0b0d] px-4 py-8 text-white md:px-8">
        <div className="mx-auto max-w-6xl space-y-6">
            <header><p className="text-xs font-semibold uppercase tracking-[0.16em] text-yellow-400">{de ? "Mining-Ökonomie" : "Mining economics"}</p><h1 className="mt-2 text-3xl font-semibold">{de ? "Ertragsprognose" : "Earnings forecast"}</h1><p className="mt-2 max-w-3xl text-sm text-[#a5a5af]">{de ? "Aktuelle Schätzung anhand gemessener Hashrate, Chain-Daten und Marktpreisen. Werte sind Brutto-Prognosen, keine tatsächlichen Pool-Gutschriften." : "Current estimate using measured hashrate, chain data and market prices. Values are gross forecasts, not actual pool rewards."}</p></header>
            {error && <p className="rounded-xl border border-red-500/30 p-4 text-red-300">{de ? "Prognose konnte nicht geladen werden." : "Could not load forecast."}</p>}
            {!snapshot ? <p className="text-[#a5a5af]">{de ? "Lade Prognose…" : "Loading forecast…"}</p> : <>
                <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                    <article className={tile}><p className="text-sm text-[#9b9ba5]">{de ? "Ertrag pro Tag" : "Daily revenue"}</p><strong className="mt-3 block text-2xl">{snapshot.eurPerDay == null ? "—" : money(snapshot.eurPerDay)}</strong></article>
                    <article className={tile}><p className="text-sm text-[#9b9ba5]">{de ? "Ertrag je kWh" : "Revenue per kWh"}</p><strong className="mt-3 block text-2xl">{snapshot.centsPerKwh == null ? "—" : `${number(snapshot.centsPerKwh)} ct`}</strong></article>
                    <article className={tile}><p className="text-sm text-[#9b9ba5]">{de ? "Aktueller Netzstrompreis" : "Current grid tariff"}</p><strong className="mt-3 block text-2xl">{snapshot.gridCentsPerKwh == null ? "—" : `${number(snapshot.gridCentsPerKwh)} ct/kWh`}</strong></article>
                    <article className={tile}><p className="text-sm text-[#9b9ba5]">{de ? "Abstand zum Netzpreis" : "Margin to grid tariff"}</p><strong className={`mt-3 block text-2xl ${snapshot.complete && snapshot.centsPerKwh! > snapshot.gridCentsPerKwh! ? "text-emerald-300" : "text-[#dddde3]"}`}>{snapshot.complete ? `${number(snapshot.centsPerKwh! - snapshot.gridCentsPerKwh!)} ct/kWh` : "—"}</strong></article>
                </div>
                <section className={tile}><h2 className="text-lg font-semibold">{de ? "Alle Coins" : "All coins"}</h2><p className="mt-2 text-sm text-[#9b9ba5]">{de ? "Gesamtverbrauch" : "Total consumption"}: {snapshot.kwhPerDay == null ? "—" : `${number(snapshot.kwhPerDay)} kWh/Tag`}. {de ? "Die Vergleichswerte erscheinen nur bei vollständigen Daten für alle aktiven Miner." : "Comparisons appear only when all active miners have complete data."}</p></section>
                <section className="grid gap-4 md:grid-cols-2">{snapshot.coins.map((coin, index) => <article className={tile} key={`${coin.coin}-${index}`}><div className="flex items-center justify-between"><h2 className="text-xl font-semibold">{coin.ticker === "—" ? coin.coin : coin.ticker}</h2><span className={`text-xs ${coin.available ? "text-emerald-300" : "text-amber-300"}`}>{coin.available ? de ? "Aktuell" : "Current" : de ? "Nicht verfügbar" : "Unavailable"}</span></div>{coin.available ? <div className="mt-5 grid grid-cols-2 gap-4 text-sm"><div><span className="text-[#9999a3]">{de ? "Coins / Tag" : "Coins / day"}</span><strong className="mt-1 block">{number(coin.coinsPerDay, 8)} {coin.ticker}</strong></div><div><span className="text-[#9999a3]">{de ? "Brutto / Tag" : "Gross / day"}</span><strong className="mt-1 block">{snapshot.eurPerUsd == null ? "—" : money(coin.usdPerDay * snapshot.eurPerUsd)}</strong></div><div><span className="text-[#9999a3]">{de ? "Leistung" : "Power"}</span><strong className="mt-1 block">{number(coin.watts)} W</strong></div><div><span className="text-[#9999a3]">{de ? "Ertrag je kWh" : "Revenue per kWh"}</span><strong className="mt-1 block">{snapshot.eurPerUsd == null ? "—" : `${number(coin.usdPerDay * snapshot.eurPerUsd / (coin.watts * 24 / 1000) * 100)} ct/kWh`}</strong></div></div> : <p className="mt-4 text-sm text-[#a5a5af]">{coin.reason || (de ? "Hashrate, Leistung oder Marktdaten fehlen." : "Hashrate, power or market data unavailable.")}</p>}</article>)}</section>
                <p className="text-xs leading-relaxed text-[#85858f]">{de ? "Brutto-Prognose ohne Poolgebühren, Entwicklergebühr, ungültige Shares und Steuern. Stromkosten und PV-Einspeisevergütung sind im Ertrag je kWh nicht abgezogen. Fehlende oder veraltete Daten verhindern die Rentabilitätsregel. Stand:" : "Gross forecast before pool fees, developer fee, rejected shares and taxes. Electricity costs and PV export value are not deducted from revenue per kWh. Missing or stale data disable the profitability rule. Updated:"} {new Date(snapshot.updatedAt).toLocaleString(de ? "de-DE" : "en-US")}</p>
            </>}
        </div>
    </main>;
}
