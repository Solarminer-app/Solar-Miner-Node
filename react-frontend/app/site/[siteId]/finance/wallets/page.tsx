'use client';

import {useCallback, useEffect, useState} from 'react';
import Link from 'next/link';
import {useParams} from 'next/navigation';
import {ArrowLeft, ArrowRight, Coins, Plus, RefreshCw, Trash2, Wallet, Zap} from 'lucide-react';
import {useSitePreferences} from '../../site-preferences-context';

type WatchedWallet = {id: string; label: string; coin: string; address: string; confirmedBalance: number | null; unconfirmedBalance: number | null; fetchedAt: string | null; balanceStatus: string};
type Pool = {id: string; coin: string; balanceCoin: string; name: string; stratumUrl: string; balance: number | null};
type Coin = {key: string; symbol: string; algorithm: string; automaticAssignment: boolean};
const fallbackCoins: Coin[] = [{key: 'bitcoin', symbol: 'BTC', algorithm: 'SHA256', automaticAssignment: true}, {key: 'monero', symbol: 'XMR', algorithm: 'RandomX', automaticAssignment: false}, {key: 'pearl', symbol: 'PRL', algorithm: 'PearlHash', automaticAssignment: false}];
const input = 'w-full rounded-xl border border-white/10 bg-[#17171c] px-3 py-2.5 text-sm text-white outline-none focus:border-yellow-400/60';
const btc = (sat: number | null) => sat == null ? '—' : `${(sat / 100_000_000).toFixed(8)} BTC`;

export default function WalletOverviewPage() {
    const {siteId} = useParams<{siteId: string}>();
    const {locale} = useSitePreferences();
    const de = locale === 'de';
    const [wallets, setWallets] = useState<WatchedWallet[]>([]);
    const [pools, setPools] = useState<Pool[]>([]);
    const [coins, setCoins] = useState<Coin[]>(fallbackCoins);
    const [selectedCoin, setSelectedCoin] = useState('bitcoin');
    const [lightning, setLightning] = useState<number | null>(null);
    const [label, setLabel] = useState('');
    const [address, setAddress] = useState('');
    const [showAdd, setShowAdd] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const base = `/api/pv-site/${siteId}`;
    const coin = coins.find(entry => entry.key === selectedCoin) ?? fallbackCoins[0];

    const load = useCallback(async () => {
        try {
            const results = await Promise.all([
                fetch(`${base}/watched-wallets`), fetch(`${base}/mining/pools/overview`),
                fetch(`/api/lightning-wallet?currency=EUR&locale=${locale}`), fetch(`${base}/mining/coins`)
            ]);
            if (!results[0].ok || !results[1].ok || !results[3].ok) throw new Error(de ? 'Guthaben konnten nicht geladen werden.' : 'Could not load balances.');
            const [nextWallets, nextPools, nextCoins] = await Promise.all([results[0].json(), results[1].json(), results[3].json()]);
            setWallets(nextWallets); setPools(nextPools); setCoins(nextCoins);
            setLightning(results[2].ok ? ((await results[2].json()).balanceSat ?? null) : null);
            setError(null);
        } catch (reason) { setError(reason instanceof Error ? reason.message : String(reason)); }
    }, [base, de, locale]);

    useEffect(() => { void load(); }, [load]);

    async function add() {
        setBusy(true); setError(null);
        try {
            const response = await fetch(`${base}/watched-wallets`, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({label, coin: selectedCoin, address})});
            if (!response.ok) throw new Error(await response.text());
            setLabel(''); setAddress(''); setShowAdd(false); await load(); window.dispatchEvent(new Event('solarminer:balances-changed'));
        } catch (reason) { setError(reason instanceof Error ? reason.message : String(reason)); }
        finally { setBusy(false); }
    }

    async function remove(wallet: WatchedWallet) {
        if (!window.confirm(de ? `Adresse „${wallet.label}“ entfernen?` : `Remove address “${wallet.label}”?`)) return;
        setBusy(true); setError(null);
        try {
            const response = await fetch(`${base}/watched-wallets/${wallet.id}`, {method: 'DELETE'});
            if (!response.ok) throw new Error(await response.text());
            await load(); window.dispatchEvent(new Event('solarminer:balances-changed'));
        } catch (reason) { setError(reason instanceof Error ? reason.message : String(reason)); }
        finally { setBusy(false); }
    }

    const visibleWallets = wallets.filter(wallet => wallet.coin === selectedCoin);
    const visiblePools = pools.filter(pool => pool.coin === selectedCoin);
    const balance = (amount: number | null, symbol = coin.symbol) => amount == null ? '—' : `${amount.toFixed(8)} ${symbol}`;

    return <main className="min-h-screen bg-[#0b0b0d] px-4 py-6 text-white md:px-8"><div className="mx-auto max-w-[1200px] space-y-6">
        <header className="flex flex-wrap items-start justify-between gap-4"><div><Link className="inline-flex items-center gap-1 text-xs text-[#92929c] hover:text-white" href={`/site/${siteId}/finance`}><ArrowLeft size={14}/>{de ? 'Finanzen' : 'Finance'}</Link><p className="mt-5 text-xs font-semibold uppercase tracking-[.18em] text-yellow-400">{de ? 'Beobachten' : 'Monitor'}</p><h1 className="mt-1 text-3xl font-bold">{de ? 'Guthaben & Wallets' : 'Balances & wallets'}</h1><p className="mt-2 max-w-2xl text-sm leading-6 text-[#a2a2ad]">{de ? 'Pool-Konten, öffentliche Adressen und die Bitcoin-Lightning-Wallet getrennt anzeigen. Keine Summen über verschiedene Coins.' : 'View pool accounts, public addresses and the Bitcoin Lightning wallet separately. Balances in different coins are not added together.'}</p></div><button className="inline-flex items-center gap-2 rounded-xl border border-white/10 px-4 py-2 text-sm hover:bg-white/5" onClick={() => void load()} type="button"><RefreshCw size={16}/>{de ? 'Aktualisieren' : 'Refresh'}</button></header>
        {error && <div className="rounded-xl border border-red-400/30 bg-red-400/10 p-4 text-sm text-red-200" role="alert">{error}</div>}
        <nav className="flex flex-wrap gap-2" aria-label={de ? 'Wallet-Coin' : 'Wallet coin'}>{coins.map(entry => <button key={entry.key} type="button" aria-current={selectedCoin === entry.key ? 'page' : undefined} className={`rounded-xl border px-4 py-2 text-sm font-semibold ${selectedCoin === entry.key ? 'border-yellow-400/60 bg-yellow-400/10 text-yellow-300' : 'border-white/10 text-[#92929c] hover:text-white'}`} onClick={() => setSelectedCoin(entry.key)}>{entry.symbol}</button>)}</nav>
        <div className="grid gap-5 lg:grid-cols-3">
            <section className="rounded-2xl border border-white/[.08] bg-[#141418] p-5"><div className="flex items-center gap-2 text-yellow-300"><Coins size={19}/><h2 className="font-semibold text-white">{de ? 'Bei Mining-Pools' : 'At mining pools'} · {coin.symbol}</h2></div><p className="mt-2 text-xs leading-5 text-[#92929c]">{de ? 'Nur bestätigte Anbieter-API-Daten. Ein gespeichertes Mining-Ziel ist kein Guthaben.' : 'Provider API data only. A saved mining target is not a balance.'}</p><div className="mt-5 space-y-3">{visiblePools.map(pool => <div className="border-t border-white/[.07] pt-3" key={pool.id}><span className="block text-xs text-[#92929c]">{pool.name}</span><strong className="mt-1 block text-lg">{balance(pool.balance, pool.balanceCoin)}</strong></div>)}{visiblePools.length === 0 && <p className="text-sm text-[#73737e]">{de ? 'Keine Pool-API für diesen Coin verbunden.' : 'No pool API connected for this coin.'}</p>}</div><Link className="mt-5 inline-flex items-center gap-1 text-xs text-yellow-300 hover:underline" href={`/site/${siteId}/mining/targets`}>{de ? 'Mining-Ziele verwalten' : 'Manage mining targets'}<ArrowRight size={13}/></Link></section>
            <section className="rounded-2xl border border-white/[.08] bg-[#141418] p-5 lg:col-span-2"><div className="flex items-start justify-between gap-3"><div><div className="flex items-center gap-2 text-sky-300"><Wallet size={19}/><h2 className="font-semibold text-white">{de ? 'Öffentliche Adressen' : 'Public addresses'} · {coin.symbol}</h2></div><p className="mt-2 text-xs leading-5 text-[#92929c]">{de ? 'Nur beobachten. Die Node kann von diesen Adressen keine Coins ausgeben.' : 'Watch only. The node cannot spend funds from these addresses.'}</p></div><button className="rounded-lg border border-white/10 p-2 hover:bg-white/5" onClick={() => setShowAdd(true)} type="button" aria-label={de ? 'Adresse hinzufügen' : 'Add address'}><Plus size={17}/></button></div>
                <div className="mt-4 divide-y divide-white/[.07]">{visibleWallets.map(wallet => <div className="flex items-start gap-3 py-3" key={wallet.id}><div className="min-w-0 flex-1"><strong className="text-sm">{wallet.label}</strong><p className="mt-1 truncate font-mono text-xs text-[#777783]" title={wallet.address}>{wallet.address}</p><p className="mt-1 text-[11px] text-[#777783]">{wallet.balanceStatus === 'NOT_SUPPORTED' ? (de ? 'Monero-Saldo per öffentlicher Adresse nicht abrufbar' : 'Monero balance cannot be read from a public address') : wallet.fetchedAt ? `${de ? 'Stand' : 'Updated'}: ${new Date(wallet.fetchedAt).toLocaleString(locale)}` : (de ? 'Aktuell nicht verfügbar' : 'Currently unavailable')}</p></div><div className="text-right"><strong className="block text-sm">{balance(wallet.confirmedBalance)}</strong><span className="text-xs text-[#85858f]">{de ? 'bestätigt' : 'confirmed'}</span>{wallet.unconfirmedBalance != null && wallet.unconfirmedBalance !== 0 && <span className="block text-xs text-amber-300">{wallet.unconfirmedBalance > 0 ? '+' : ''}{balance(wallet.unconfirmedBalance)} {de ? 'unbestätigt' : 'unconfirmed'}</span>}</div><button aria-label={`${de ? 'Entfernen' : 'Remove'}: ${wallet.label}`} className="p-1 text-red-300 hover:bg-red-400/10" disabled={busy} onClick={() => void remove(wallet)} type="button"><Trash2 size={15}/></button></div>)}{visibleWallets.length === 0 && <p className="py-6 text-sm text-[#73737e]">{de ? 'Noch keine Adresse für diesen Coin hinterlegt.' : 'No address watched for this coin yet.'}</p>}</div><p className="mt-4 text-xs leading-5 text-[#777783]">{selectedCoin === 'bitcoin' ? (de ? 'BTC: Einzeladresse über mempool.space; kein vollständiges Wallet.' : 'BTC: one address via mempool.space, not a whole wallet.') : selectedCoin === 'pearl' ? (de ? 'PRL: öffentlicher Chain-Saldo über pearlchain.live.' : 'PRL: public on-chain balance via pearlchain.live.') : (de ? 'XMR: Wegen der privaten Chain ist der Saldo mit einer öffentlichen Adresse allein nicht sichtbar.' : 'XMR: the private chain does not expose a balance from a public address alone.')}</p></section>
        </div>
        {selectedCoin === 'bitcoin' && <section className="flex flex-wrap items-center gap-4 rounded-2xl border border-white/[.08] bg-[#141418] p-5"><span className="rounded-xl bg-yellow-400/10 p-3 text-yellow-300"><Zap size={22}/></span><div className="min-w-[180px] flex-1"><h2 className="font-semibold">{de ? 'Node-Lightning-Wallet' : 'Node Lightning wallet'}</h2><p className="mt-1 text-xs text-[#92929c]">{de ? 'Eigenständiges BTC-Guthaben der Node.' : 'Separate BTC balance of the node.'}</p></div><strong className="text-xl">{btc(lightning)}</strong><Link className="inline-flex items-center gap-1 text-xs text-yellow-300 hover:underline" href="/lightning-wallet">{de ? 'Wallet öffnen' : 'Open wallet'}<ArrowRight size={13}/></Link></section>}
    </div>
        {showAdd && <div className="fixed inset-0 z-50 grid place-items-center bg-black/75 p-4" role="presentation"><div className="w-full max-w-md rounded-2xl border border-white/10 bg-[#18181e] p-6" role="dialog" aria-modal="true" aria-label={de ? 'Adresse hinzufügen' : 'Add address'}><h2 className="text-lg font-semibold">{de ? 'Adresse beobachten' : 'Watch address'} · {coin.symbol}</h2><p className="mt-2 text-xs text-[#92929c]">{de ? 'Nur eine öffentliche Adresse eingeben. Niemals Seed oder privaten Schlüssel.' : 'Enter a public address only. Never enter a seed or private key.'}</p><div className="mt-5 space-y-4"><label className="block text-xs text-[#b8b8c0]">{de ? 'Bezeichnung' : 'Label'}<input className={`mt-1.5 ${input}`} value={label} onChange={event => setLabel(event.target.value)}/></label><label className="block text-xs text-[#b8b8c0]">{coin.symbol}-{de ? 'Adresse' : 'address'}<input className={`mt-1.5 ${input}`} value={address} onChange={event => setAddress(event.target.value)}/></label></div><div className="mt-6 flex justify-end gap-2"><button className="rounded-lg px-4 py-2 text-sm text-[#a2a2ad]" onClick={() => setShowAdd(false)} type="button">{de ? 'Abbrechen' : 'Cancel'}</button><button className="rounded-lg bg-yellow-400 px-4 py-2 text-sm font-semibold text-black disabled:opacity-40" disabled={busy || !label.trim() || !address.trim()} onClick={() => void add()} type="button">{de ? 'Hinzufügen' : 'Add'}</button></div></div></div>}
    </main>;
}
