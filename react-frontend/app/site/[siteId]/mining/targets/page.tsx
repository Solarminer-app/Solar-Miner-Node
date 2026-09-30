'use client';

import {useCallback, useEffect, useMemo, useState} from 'react';
import Link from 'next/link';
import {useParams} from 'next/navigation';
import {ArrowLeft, ArrowRight, CheckCircle2, CircleAlert, Coins, Cpu, Plus, RefreshCw, Server, Trash2} from 'lucide-react';
import type {MiningPageDto} from '../../../../types';
import {useSitePreferences} from '../../site-preferences-context';

type Coin = {key: string; symbol: string; algorithm: string; automaticAssignment: boolean};
type Target = {id: string; coin: string; payoutCoin: string; payoutAddress: string | null; algorithm: string; name: string; stratumUrl: string; workerPrefix: string; priority: number; enabled: boolean};
type Pool = {id: string; source: 'ACCOUNT' | 'TARGET'; coin: string; balanceCoin: string; name: string; stratumUrl: string; balance: number | null; balanceUpdatedAt: string | null; poolWorkerCount: number | null; assignedMiners: Array<{id: string; name: string; status: string}>; suggestedWorkerPrefix: string | null};
type Assignment = {minerId: string; coin: string; algorithm: string; status: 'ASSIGNED' | 'PENDING' | 'DIFFERENT' | 'MANUAL' | 'UNCONFIGURED' | 'UNAVAILABLE'; targetId: string | null; targetName: string | null; currentPool: string | null};
type WatchedWallet = {id: string; label: string; coin: string; address: string};
type Form = Omit<Target, 'id'> & {id?: string};
const catalog: Coin[] = [{key: 'bitcoin', symbol: 'BTC', algorithm: 'SHA256', automaticAssignment: true}, {key: 'monero', symbol: 'XMR', algorithm: 'RandomX', automaticAssignment: true}, {key: 'pearl', symbol: 'PRL', algorithm: 'PearlHash', automaticAssignment: true}];
const emptyForm: Form = {coin: 'bitcoin', payoutCoin: 'bitcoin', payoutAddress: null, algorithm: 'SHA256', name: '', stratumUrl: 'stratum+tcp://', workerPrefix: '', priority: 1, enabled: true};
const field = 'w-full rounded-xl border border-white/10 bg-[#17171c] px-3 py-2.5 text-sm text-white outline-none focus:border-yellow-400/60';

export default function MiningTargetsPage() {
    const {siteId} = useParams<{siteId: string}>();
    const {locale} = useSitePreferences();
    const de = locale === 'de';
    const base = `/api/pv-site/${siteId}/mining`;
    const [targets, setTargets] = useState<Target[]>([]);
    const [assignments, setAssignments] = useState<Assignment[]>([]);
    const [coins, setCoins] = useState<Coin[]>(catalog);
    const [selectedCoin, setSelectedCoin] = useState('bitcoin');
    const [pools, setPools] = useState<Pool[]>([]);
    const [wallets, setWallets] = useState<WatchedWallet[]>([]);
    const [mining, setMining] = useState<MiningPageDto | null>(null);
    const [form, setForm] = useState<Form>(emptyForm);
    const [token, setToken] = useState('');
    const [poolType, setPoolType] = useState<'BRAIINS' | 'KRYPTEX'>('BRAIINS');
    const [poolAddress, setPoolAddress] = useState('');
    const [showForm, setShowForm] = useState(false);
    const [showPoolForm, setShowPoolForm] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [message, setMessage] = useState<string | null>(null);

    const load = useCallback(async () => {
        try {
            const responses = await Promise.all([fetch(`${base}/targets`), fetch(`${base}/pools/overview`), fetch(base), fetch(`${base}/coins`), fetch(`${base}/targets/assignments`), fetch(`/api/pv-site/${siteId}/watched-wallets`)]);
            if (responses.some(response => !response.ok)) throw new Error(de ? 'Daten konnten nicht geladen werden.' : 'Could not load data.');
            const [nextTargets, nextPools, nextMining, nextCoins, nextAssignments, nextWallets] = await Promise.all(responses.map(response => response.json()));
            setTargets(nextTargets);
            setAssignments(nextAssignments);
            setCoins(nextCoins);
            setPools(nextPools);
            setMining(nextMining);
            setWallets(nextWallets);
            setError(null);
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : String(reason));
        }
    }, [base, de]);

    useEffect(() => { void load(); }, [load]);

    const eligible = useMemo(() => mining?.connectedMiners.filter(miner => selectedCoin === 'bitcoin'
        ? miner.os === 'BRAIINS' || miner.os === 'ANTMINER_STOCK_OS' : miner.os === 'AGENT') ?? [], [mining, selectedCoin]);
    const visibleTargets = targets.filter(target => target.coin === selectedCoin);
    const currentCoin = coins.find(coin => coin.key === selectedCoin) ?? catalog[0];
    const assigned = useMemo(() => eligible.filter(miner => assignments.some(assignment => assignment.minerId === miner.id
        && assignment.coin === selectedCoin && assignment.status === 'ASSIGNED')), [eligible, assignments, selectedCoin]);
    const pending = eligible.length - assigned.length;

    async function mutate(url: string, init: RequestInit, success: string) {
        setBusy(true); setError(null); setMessage(null);
        try {
            const response = await fetch(url, init);
            if (!response.ok) throw new Error((await response.text()) || `HTTP ${response.status}`);
            await load();
            setMessage(success);
            return true;
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : String(reason));
            return false;
        } finally { setBusy(false); }
    }

    async function saveTarget() {
        const ok = await mutate(`${base}/targets`, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(form)}, de ? 'Ziel gespeichert und Zuordnung angestoßen.' : 'Target saved and assignment started.');
        if (ok) { setShowForm(false); setForm(emptyForm); }
    }

    async function connectPool() {
        if (poolType === 'KRYPTEX') {
            const stratumUrl = selectedCoin === 'monero' ? 'stratum+tcp://xmr.kryptex.network:7029' : 'stratum+ssl://prl.kryptex.network:8048';
            const target = {...emptyForm, coin: selectedCoin, payoutCoin: selectedCoin, payoutAddress: poolAddress, algorithm: currentCoin.algorithm, name: `Kryptex ${currentCoin.symbol} · Direct wallet`, stratumUrl, workerPrefix: 'solarminer', priority: targets.filter(entry => entry.coin === selectedCoin).length + 1};
            const ok = await mutate(`${base}/targets`, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(target)}, de ? 'Kryptex-Wallet-Ziel gespeichert.' : 'Kryptex wallet target saved.');
            if (ok) { setPoolAddress(''); setShowPoolForm(false); }
            return;
        }
        const ok = await mutate(`${base}/pools`, {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({type: 'BRAIINS', accessToken: token})}, de ? 'Braiins-Konto verbunden.' : 'Braiins account connected.');
        if (ok) { setToken(''); setShowPoolForm(false); }
    }

    function usePoolAsTarget(pool: Pool) {
        setSelectedCoin(pool.coin);
        setForm({...emptyForm, coin: pool.coin, payoutCoin: pool.coin, name: pool.name, stratumUrl: pool.stratumUrl,
            workerPrefix: pool.suggestedWorkerPrefix ?? '', priority: targets.filter(target => target.coin === pool.coin).length + 1});
        setShowForm(true);
    }

    return <main className="min-h-screen bg-[#0b0b0d] px-4 py-6 text-white md:px-8">
        <div className="mx-auto max-w-[1320px] space-y-6">
            <header className="flex flex-wrap items-start justify-between gap-4">
                <div><Link className="inline-flex items-center gap-1 text-xs text-[#92929c] hover:text-white" href={`/site/${siteId}/mining`}><ArrowLeft size={14}/>{de ? 'Mining-Übersicht' : 'Mining overview'}</Link>
                    <p className="mt-5 text-xs font-semibold uppercase tracking-[.18em] text-yellow-400">{de ? 'Konfiguration' : 'Configuration'} · {mining?.siteName ?? ''}</p>
                    <h1 className="mt-1 text-3xl font-bold">{de ? 'Mining-Ziele' : 'Mining targets'}</h1>
                    <p className="mt-2 max-w-2xl text-sm leading-6 text-[#a2a2ad]">{de ? 'Pools und Auszahlungsziele je Coin verwalten. Bitcoin bleibt die Standardansicht.' : 'Manage pools and payout targets by coin. Bitcoin remains the default view.'}</p></div>
                <button className="inline-flex items-center gap-2 rounded-xl border border-white/10 px-4 py-2 text-sm hover:bg-white/5" onClick={() => void load()} type="button"><RefreshCw size={16}/>{de ? 'Aktualisieren' : 'Refresh'}</button>
            </header>

            {error && <div className="rounded-xl border border-red-400/30 bg-red-400/10 p-4 text-sm text-red-200" role="alert">{error}</div>}
            {message && <div className="rounded-xl border border-emerald-400/30 bg-emerald-400/10 p-4 text-sm text-emerald-200" role="status">{message}</div>}

            <nav className="flex flex-wrap gap-2" aria-label={de ? 'Mining-Coin' : 'Mining coin'}>{coins.map(coin => <button key={coin.key} type="button" aria-current={selectedCoin === coin.key ? 'page' : undefined} className={`rounded-xl border px-4 py-2 text-sm font-semibold ${selectedCoin === coin.key ? 'border-yellow-400/60 bg-yellow-400/10 text-yellow-300' : 'border-white/10 text-[#92929c] hover:text-white'}`} onClick={() => setSelectedCoin(coin.key)}>{coin.symbol} <span className="font-normal text-xs">· {coin.algorithm}</span></button>)}</nav>

            <section className="grid gap-3 md:grid-cols-3" aria-label={de ? 'Überblick' : 'Overview'}>
                <Summary icon={<Coins size={20}/>} label={de ? `Pools · ${currentCoin.symbol}` : `Pools · ${currentCoin.symbol}`} value={String(pools.filter(pool => pool.coin === selectedCoin).length)}/>
                <Summary icon={<Server size={20}/>} label={de ? `Aktive Ziele · ${currentCoin.symbol}` : `Active targets · ${currentCoin.symbol}`} value={String(visibleTargets.filter(target => target.enabled).length)}/>
                <Summary icon={<Cpu size={20}/>} label={selectedCoin === 'bitcoin' ? (de ? 'ASICs zugeordnet / offen' : 'ASICs assigned / pending') : (de ? 'Agenten zugeordnet / offen' : 'Agents assigned / pending')} value={`${assigned.length} / ${pending}`}/>
            </section>

            <div className="grid gap-6 xl:grid-cols-[minmax(0,1.5fr)_minmax(320px,1fr)]">
                <div className="space-y-6">
                    <section className="rounded-2xl border border-white/[.08] bg-[#121216] p-5">
                        <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-lg font-semibold">{de ? 'Zielreihenfolge' : 'Target order'} · {currentCoin.symbol} / {currentCoin.algorithm}</h2><p className="mt-1 text-xs text-[#91919d]">{selectedCoin === 'bitcoin' ? (de ? 'Kleinere Priorität wird zuerst versucht. Neue ASICs werden automatisch konfiguriert.' : 'Lower priority numbers are tried first. New ASICs are configured automatically.') : (de ? 'Die Node überträgt das Ziel an die passenden Agent-Worker. Die GPU-Auswahl im Agent bleibt erhalten.' : 'The node sends the target to the matching agent workers. GPU selection is preserved.')}</p></div>
                            <button className="inline-flex items-center gap-2 rounded-lg bg-yellow-400 px-3 py-2 text-xs font-semibold text-black" onClick={() => { setForm({...emptyForm, coin: currentCoin.key, payoutCoin: currentCoin.key, algorithm: currentCoin.algorithm, priority: visibleTargets.length + 1}); setShowForm(true); }} type="button"><Plus size={15}/>{de ? 'Ziel hinzufügen' : 'Add target'}</button></div>
                        <div className="mt-5 space-y-3">{visibleTargets.map((target) => <article className="rounded-xl border border-white/[.08] bg-[#1a1a20] p-4" key={target.id}>
                            <div className="flex items-start gap-3"><span className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-yellow-400/10 font-mono text-sm font-bold text-yellow-300">{target.priority}</span><div className="min-w-0 flex-1"><div className="flex flex-wrap items-center gap-2"><strong className="text-sm">{target.name}</strong><span className={`rounded-full px-2 py-0.5 text-[10px] ${target.enabled ? 'bg-emerald-400/10 text-emerald-300' : 'bg-white/10 text-[#8d8d98]'}`}>{target.enabled ? (visibleTargets.find(entry => entry.enabled)?.id === target.id ? (de ? 'Primär' : 'Primary') : (de ? 'Ersatz' : 'Fallback')) : (de ? 'Pausiert' : 'Paused')}</span></div><p className="mt-1 truncate font-mono text-xs text-[#9b9ba6]" title={target.stratumUrl}>{target.stratumUrl}</p><p className="mt-1 text-xs text-[#777783]">{currentCoin.automaticAssignment ? (de ? 'Worker-Präfix' : 'Worker prefix') : 'Worker'}: {target.workerPrefix}</p>{target.payoutAddress && <p className="mt-1 truncate font-mono text-xs text-[#777783]" title={target.payoutAddress}>{de ? 'Auszahlung' : 'Payout'} ({currentCoin.symbol}): {target.payoutAddress}</p>}</div></div>
                            <div className="mt-4 flex items-center justify-between border-t border-white/[.06] pt-3 text-xs text-[#8d8d98]"><span>{assignments.filter(assignment => assignment.targetId === target.id && assignment.status === 'ASSIGNED').length} {de ? 'Miner zugeordnet' : 'miners assigned'}</span><div className="flex gap-2"><button className="rounded-lg border border-white/10 px-3 py-1.5 text-white hover:bg-white/5" onClick={() => { setForm(target); setShowForm(true); }} type="button">{de ? 'Bearbeiten' : 'Edit'}</button><button aria-label={`${de ? 'Löschen' : 'Delete'}: ${target.name}`} className="rounded-lg border border-red-400/20 px-2 text-red-300 hover:bg-red-400/10" disabled={busy} onClick={() => { if (window.confirm(de ? `Ziel „${target.name}“ löschen?` : `Delete target “${target.name}”?`)) void mutate(`${base}/targets/${target.id}`, {method: 'DELETE'}, de ? 'Ziel gelöscht.' : 'Target deleted.'); }} type="button"><Trash2 size={14}/></button></div></div>
                        </article>)}{visibleTargets.length === 0 && <div className="rounded-xl border border-dashed border-white/10 p-8 text-center text-sm text-[#888894]">{de ? `Noch kein ${currentCoin.symbol}-Ziel hinterlegt.` : `No ${currentCoin.symbol} target configured yet.`}</div>}</div>
                        {visibleTargets.length > 0 && <button className="mt-4 rounded-lg border border-yellow-400/30 px-3 py-2 text-xs text-yellow-300 hover:bg-yellow-400/10 disabled:opacity-40" disabled={busy} onClick={() => void mutate(`${base}/targets/apply?coin=${selectedCoin}`, {method: 'POST'}, de ? 'Zuordnung erneut angestoßen.' : 'Assignment retried.')} type="button">{de ? 'Ziele erneut anwenden' : 'Reapply targets'}</button>}
                        <p className="mt-3 text-xs leading-5 text-[#777783]">{de ? 'Ein Ersatz-Ziel wird versucht, wenn das Konfigurieren des vorherigen Ziels fehlschlägt. Laufende Pool-Ausfälle werden derzeit nicht automatisch erkannt.' : 'A fallback is tried if configuring the preceding target fails. Failures during ongoing mining are not detected automatically yet.'}</p>
                    </section>

                    <section className="rounded-2xl border border-white/[.08] bg-[#121216] p-5"><h2 className="text-lg font-semibold">{de ? 'Miner-Zuordnung' : 'Miner assignment'} · {currentCoin.symbol}</h2><p className="mt-1 text-xs text-[#91919d]">{de ? 'Vom Gerät gemeldete Konfiguration und Worker je Algorithmus.' : 'Configuration and workers reported by each device, grouped by algorithm.'}</p>
                        <div className="mt-4 divide-y divide-white/[.06]">{eligible.map(miner => { const assignment = assignments.find(entry => entry.minerId === miner.id && entry.coin === selectedCoin); const workers = selectedCoin === 'bitcoin' ? [] : (miner.algorithmWorkers ?? []).filter(worker => worker.algorithm === currentCoin.algorithm); return <div className="py-3" key={miner.id}>
                            <div className="flex flex-wrap items-center gap-3"><div className="min-w-0 flex-1"><Link className="text-sm font-semibold hover:text-yellow-300" href={`/site/${siteId}/mining/miners/${miner.id}`}>{miner.name || miner.model}</Link><p className="text-xs text-[#85858f]">{miner.model} · {miner.ipAddress}</p></div><span className={`text-xs ${assignment?.status === 'ASSIGNED' ? 'text-emerald-300' : assignment?.status === 'UNAVAILABLE' ? 'text-red-300' : 'text-amber-300'}`}>{assignmentLabel(assignment?.status, de)}</span><span className="min-w-[130px] text-right text-xs text-[#b9b9c2]">{assignment?.targetName ?? assignment?.currentPool ?? (de ? 'Kein Ziel' : 'No target')}</span></div>
                            {workers.length > 0 && <div className="mt-2 space-y-1 pl-3">{workers.map((worker, index) => <div className="flex items-center justify-between gap-2 rounded-lg bg-black/20 px-2 py-1.5 text-xs" key={`${worker.name}:${index}`}><span className="truncate text-[#bdbdc6]">{worker.name}</span><span className="shrink-0 text-[#9999a4]">{worker.status} · {formatWorkerHashrate(worker.hashrateThs)}</span></div>)}</div>}
                        </div>; })}{eligible.length === 0 && <p className="py-7 text-center text-sm text-[#888894]">{de ? `Kein passender ${currentCoin.algorithm}-Miner verbunden.` : `No compatible ${currentCoin.algorithm} miner connected.`}</p>}</div>
                        <p className="mt-3 flex items-start gap-2 text-xs leading-5 text-amber-200/80"><CircleAlert size={15} className="mt-0.5 shrink-0"/>{de ? 'Zugeordnet bedeutet: Konfiguration vom Agent gemeldet. Eine aktive Pool-Verbindung oder Gutschrift zeigt erst der Live-Status beziehungsweise Pool.' : 'Assigned means the agent reports the configuration. Check live status or the pool for a connection or credited shares.'}</p></section>
                </div>

                <aside className="space-y-6"><section className="rounded-2xl border border-white/[.08] bg-[#121216] p-5"><div className="flex items-start justify-between gap-3"><div><h2 className="text-lg font-semibold">{de ? 'Pool-Konten' : 'Pool accounts'} · {currentCoin.symbol}</h2><p className="mt-1 text-xs text-[#91919d]">{de ? 'Kontostand und Pool-Worker aus verbundenen Anbieter-APIs.' : 'Account balance and workers from connected provider APIs.'}</p></div><button className="rounded-lg border border-white/10 p-2 hover:bg-white/5" onClick={() => { setPoolType(currentCoin.key === 'bitcoin' ? 'BRAIINS' : 'KRYPTEX'); setShowPoolForm(true); }} type="button" aria-label={de ? 'Pool verbinden' : 'Connect pool'}><Plus size={17}/></button></div>
                    <div className="mt-4 space-y-3">{pools.filter(pool => pool.coin === selectedCoin).map(pool => <div className="rounded-xl border border-white/[.08] bg-[#1a1a20] p-4" key={pool.id}>
                        <div className="flex items-center justify-between gap-2"><strong className="text-sm">{pool.name}</strong><CheckCircle2 size={16} className="text-emerald-300"/></div>
                        <p className="mt-1 truncate text-xs text-[#85858f]">{pool.stratumUrl}</p>
                        <div className="mt-4 flex items-end justify-between"><div><span className="text-[11px] text-[#777783]">{de ? 'Pool-Guthaben' : 'Pool balance'}</span><strong className="block text-xl">{pool.balance == null ? '—' : `${pool.balance.toFixed(8)} ${pool.balanceCoin}`}</strong></div><span className="text-right text-xs text-[#9999a4]">{pool.assignedMiners.length} {de ? 'zugeordnet' : 'assigned'}{pool.source === 'ACCOUNT' && <><br/>{pool.poolWorkerCount ?? '—'} Worker</>}</span></div>
                        <p className="mt-1 text-[11px] text-[#777783]">{pool.balanceUpdatedAt ? `${de ? 'Stand' : 'Updated'}: ${new Date(pool.balanceUpdatedAt).toLocaleString(locale)}` : (de ? 'Kein verfügbarer Pool-Saldo' : 'No available pool balance')}</p>
                        {pool.assignedMiners.length > 0 && <details className="mt-3 rounded-lg bg-black/20 p-2.5 text-xs"><summary className="cursor-pointer text-[#c7c7d0]">{de ? 'Zugeordnete Miner anzeigen' : 'Show assigned miners'}</summary><div className="mt-2 space-y-2">{pool.assignedMiners.map(miner => <Link className="flex items-center justify-between gap-2 hover:text-yellow-300" href={`/site/${siteId}/mining/miners/${miner.id}`} key={miner.id}><span className="truncate">{miner.name}</span><span className={miner.status === 'MINING' ? 'text-emerald-300' : 'text-[#888894]'}>{miner.status}</span></Link>)}</div></details>}
                        {pool.source === 'ACCOUNT' && <div className="mt-3 flex flex-wrap items-center gap-3"><button className="rounded-lg border border-yellow-400/30 px-2.5 py-1.5 text-xs text-yellow-300 hover:bg-yellow-400/10" onClick={() => usePoolAsTarget(pool)} type="button">{de ? 'Als Ziel verwenden' : 'Use as target'}</button><button className="text-xs text-red-300 hover:underline" disabled={busy} onClick={() => { if (window.confirm(de ? `Pool-Konto „${pool.name}“ trennen?` : `Disconnect pool account “${pool.name}”?`)) void mutate(`${base}/pools/${pool.id}`, {method: 'DELETE'}, de ? 'Pool-Konto getrennt.' : 'Pool account disconnected.'); }} type="button">{de ? 'Konto trennen' : 'Disconnect account'}</button></div>}
                    </div>)}{pools.filter(pool => pool.coin === selectedCoin).length === 0 && <p className="rounded-xl border border-dashed border-white/10 p-5 text-sm text-[#888894]">{de ? `Für ${currentCoin.symbol} ist keine Pool-API verbunden. Zielregeln können mit eigenen Stratum-Daten gespeichert werden; Pool-Guthaben ist hier nicht verfügbar.` : `No pool API is connected for ${currentCoin.symbol}. You can save Stratum targets, but pool balance is unavailable here.`}</p>}</div>
                    <p className="mt-3 text-xs text-[#777783]">{de ? 'Saldo fehlt, wenn die Anbieter-API nicht erreichbar ist.' : 'The balance is unavailable when the provider API cannot be reached.'}</p></section>
                    <Link className="flex items-center justify-between rounded-2xl border border-white/[.08] bg-[#121216] p-5 hover:border-yellow-400/30" href={`/site/${siteId}/finance/wallets`}><span><strong className="block text-sm">{de ? 'Guthaben & Wallets' : 'Balances & wallets'}</strong><span className="mt-1 block text-xs text-[#888894]">{de ? 'BTC, XMR und PRL-Adressen beobachten; Lightning bleibt BTC.' : 'Watch BTC, XMR and PRL addresses; Lightning remains BTC.'}</span></span><ArrowRight size={17} className="text-yellow-300"/></Link></aside>
            </div>
        </div>

        {showForm && <div className="fixed inset-0 z-50 grid place-items-center bg-black/75 p-4" role="presentation"><div className="w-full max-w-lg rounded-2xl border border-white/10 bg-[#18181e] p-6" role="dialog" aria-modal="true" aria-label={de ? 'Mining-Ziel bearbeiten' : 'Edit mining target'}>
            <h2 className="text-lg font-semibold">{form.id ? (de ? 'Ziel bearbeiten' : 'Edit target') : (de ? 'Mining-Ziel hinzufügen' : 'Add mining target')} · {coins.find(coin => coin.key === form.coin)?.symbol}</h2>
            <p className="mt-1 text-xs text-[#92929c]">{form.coin === 'bitcoin' ? (de ? 'SHA-256-ASICs werden automatisch zugeordnet.' : 'SHA-256 ASICs are assigned automatically.') : (de ? 'Die Node überträgt dieses Ziel automatisch an passende PC-Agent-Worker.' : 'The node automatically sends this target to matching PC agent workers.')}</p>
            <div className="mt-5 grid gap-4"><Field label={de ? 'Name' : 'Name'} value={form.name} onChange={value => setForm({...form, name: value})}/><Field label="Stratum URL" value={form.stratumUrl} onChange={value => setForm({...form, stratumUrl: value})}/>
                {form.coin !== 'bitcoin' && <PayoutAddressField wallets={wallets.filter(wallet => wallet.coin === form.coin)} form={form} setForm={setForm} de={de} symbol={coins.find(coin => coin.key === form.coin)?.symbol ?? form.coin.toUpperCase()}/>} 
                <Field label={form.coin === 'bitcoin' ? (de ? 'Worker-Präfix (z. B. konto.)' : 'Worker prefix (e.g. account.)') : (de ? 'Worker-Präfix (Miner-Kennung wird ergänzt)' : 'Worker prefix (miner ID is appended)')} value={form.workerPrefix} onChange={value => setForm({...form, workerPrefix: value})}/>
                <label className="text-xs text-[#b8b8c0]">{de ? 'Priorität (1 zuerst)' : 'Priority (1 first)'}<input className={`mt-1.5 ${field}`} min={1} max={99} type="number" value={form.priority} onChange={event => setForm({...form, priority: Number(event.target.value)})}/></label><label className="flex items-center gap-2 text-sm"><input checked={form.enabled} className="accent-yellow-400" onChange={event => setForm({...form, enabled: event.target.checked})} type="checkbox"/>{de ? 'Ziel aktiv' : 'Target enabled'}</label>
            </div><p className="mt-4 text-xs text-amber-200/80">{form.coin === 'bitcoin' ? (de ? `Speichern wendet die Zielreihenfolge auf ${eligible.length} verbundene ASIC-Miner an.` : `Saving applies the target order to ${eligible.length} connected ASIC miners.`) : (de ? `Speichern überträgt das Ziel an ${eligible.length} verbundene PC-Agenten. Laufende ${form.algorithm}-Worker können dabei neu konfiguriert werden.` : `Saving sends the target to ${eligible.length} connected PC agents. Running ${form.algorithm} workers may be reconfigured.`)}</p>
            <div className="mt-6 flex justify-end gap-2"><button className="rounded-lg px-4 py-2 text-sm text-[#a2a2ad]" onClick={() => setShowForm(false)} type="button">{de ? 'Abbrechen' : 'Cancel'}</button><button className="rounded-lg bg-yellow-400 px-4 py-2 text-sm font-semibold text-black disabled:opacity-40" disabled={busy || !form.name.trim() || !form.workerPrefix.trim() || !form.stratumUrl.trim() || (form.coin !== 'bitcoin' && !form.payoutAddress?.trim())} onClick={() => void saveTarget()} type="button">{de ? 'Speichern' : 'Save'}</button></div>
        </div></div>}
        {showPoolForm && <div className="fixed inset-0 z-50 grid place-items-center bg-black/75 p-4" role="presentation"><div className="w-full max-w-md rounded-2xl border border-white/10 bg-[#18181e] p-6" role="dialog" aria-modal="true" aria-label={de ? 'Pool verbinden' : 'Connect pool'}>{poolType === 'BRAIINS' ? <><h2 className="text-lg font-semibold">{de ? 'Braiins-Konto verbinden' : 'Connect Braiins account'}</h2><p className="mt-2 text-xs leading-5 text-[#92929c]">{de ? 'Der API-Token dient zum Lesen von Guthaben und Workern.' : 'The API token reads balances and workers.'}</p><div className="mt-5"><label className="text-xs text-[#b8b8c0]">API Token<input className={`mt-1.5 ${field}`} type="password" autoComplete="off" value={token} onChange={event => setToken(event.target.value)}/></label></div></> : <><h2 className="text-lg font-semibold">{de ? `Kryptex direkt auf ${currentCoin.symbol}-Wallet` : `Kryptex direct ${currentCoin.symbol} wallet`}</h2><p className="mt-2 text-xs leading-5 text-[#92929c]">{de ? 'Ohne Kryptex-Konto minen die Geräte direkt auf deine Wallet-Adresse. Der Pool-Saldo und die Erträge werden über diese Adresse abgefragt.' : 'Without a Kryptex account, devices mine directly to your wallet address. Pool balance and rewards are queried through this address.'}</p><div className="mt-5"><PayoutAddressField wallets={wallets.filter(wallet => wallet.coin === selectedCoin)} form={{...emptyForm, payoutAddress: poolAddress}} setForm={next => setPoolAddress(next.payoutAddress ?? '')} de={de} symbol={currentCoin.symbol}/></div></>}<div className="mt-6 flex justify-end gap-2"><button className="rounded-lg px-4 py-2 text-sm text-[#a2a2ad]" onClick={() => setShowPoolForm(false)} type="button">{de ? 'Abbrechen' : 'Cancel'}</button><button className="rounded-lg bg-yellow-400 px-4 py-2 text-sm font-semibold text-black disabled:opacity-40" disabled={busy || (poolType === 'BRAIINS' ? !token.trim() : !poolAddress.trim())} onClick={() => void connectPool()} type="button">{poolType === 'BRAIINS' ? (de ? 'Verbinden' : 'Connect') : (de ? 'Ziel anlegen' : 'Create target')}</button></div></div></div>}
    </main>;
}

function Summary({icon, label, value}: {icon: React.ReactNode; label: string; value: string}) { return <div className="flex items-center gap-3 rounded-2xl border border-white/[.08] bg-[#141418] p-4"><span className="rounded-xl bg-yellow-400/10 p-2.5 text-yellow-300">{icon}</span><span><span className="block text-xs text-[#92929c]">{label}</span><strong className="mt-1 block text-xl">{value}</strong></span></div>; }
function Field({label, value, onChange}: {label: string; value: string; onChange: (value: string) => void}) { return <label className="text-xs text-[#b8b8c0]">{label}<input className={`mt-1.5 ${field}`} value={value} onChange={event => onChange(event.target.value)}/></label>; }

function PayoutAddressField({wallets, form, setForm, de, symbol}: {wallets: WatchedWallet[]; form: Form; setForm: (form: Form) => void; de: boolean; symbol: string}) {
    const current = form.payoutAddress ?? '';
    const isSaved = wallets.some(wallet => wallet.address === current);
    return <div className="space-y-2"><label className="block text-xs text-[#b8b8c0]">{de ? `Gespeicherte ${symbol}-Adresse` : `Saved ${symbol} address`}<select className={`mt-1.5 ${field}`} value={isSaved ? current : '__custom__'} onChange={event => setForm({...form, payoutAddress: event.target.value === '__custom__' ? '' : event.target.value})}><option value="__custom__">{de ? 'Andere Adresse eingeben …' : 'Enter another address …'}</option>{wallets.map(wallet => <option key={wallet.id} value={wallet.address}>{wallet.label} · {wallet.address}</option>)}</select></label>{(!isSaved || wallets.length === 0) && <Field label={`${de ? 'Auszahlungsadresse' : 'Payout address'} (${symbol})`} value={current} onChange={value => setForm({...form, payoutAddress: value})}/>} {wallets.length === 0 && <p className="text-[11px] text-[#777783]">{de ? 'Noch keine passende Adresse gespeichert. Du kannst sie hier direkt eingeben.' : 'No matching saved address yet. You can enter one here.'}</p>}</div>;
}

function assignmentLabel(status: Assignment['status'] | undefined, de: boolean) {
    const labels: Record<Assignment['status'], [string, string]> = {
        ASSIGNED: ['Ziel übertragen', 'Target applied'], PENDING: ['Ausstehend', 'Pending'],
        DIFFERENT: ['Agent-Ziel weicht ab', 'Agent target differs'], MANUAL: ['Lokal konfiguriert', 'Locally configured'],
        UNCONFIGURED: ['Nicht konfiguriert', 'Not configured'], UNAVAILABLE: ['Agent nicht erreichbar', 'Agent unavailable']
    };
    return status ? labels[status][de ? 0 : 1] : (de ? 'Status unbekannt' : 'Status unknown');
}

function formatWorkerHashrate(terahashesPerSecond: number) {
    let value = Math.max(0, terahashesPerSecond) * 1e12;
    const units = ['H/s', 'kH/s', 'MH/s', 'GH/s', 'TH/s'];
    let index = 0;
    while (value >= 1000 && index < units.length - 1) { value /= 1000; index++; }
    return `${value.toLocaleString(undefined, {maximumFractionDigits: 2})} ${units[index]}`;
}
