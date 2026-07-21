'use client';

import Link from 'next/link';
import {useParams} from 'next/navigation';
import {useCallback, useEffect, useMemo, useState} from 'react';
import {Activity, ArrowLeft, BrainCircuit, Cpu, Gauge, Hash, Network, RefreshCw, Save, Server, Thermometer, Zap} from 'lucide-react';
import {CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis} from 'recharts';

import de from '../../../../../locales/de.json';
import en from '../../../../../locales/en.json';
import type {MinerDetailsPageDto} from '../../../../../types';
import {useSitePreferences} from '../../../site-preferences-context';

const translations = {de, en};
const API_BASE_URL = '/api/pv-site';

export default function MinerDetailsPage() {
    const {siteId, minerId} = useParams<{siteId: string; minerId: string}>();
    const {locale, isHydrated} = useSitePreferences();
    const t = translations[locale] as Record<string, string>;
    const tr = useCallback((key: string, fallback: string) => t[key] ?? fallback, [t]);
    const [hours, setHours] = useState(24);
    const [data, setData] = useState<MinerDetailsPageDto | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const response = await fetch(`${API_BASE_URL}/${siteId}/mining/miners/${minerId}?hours=${hours}`, {cache: 'no-store'});
            if (!response.ok) throw new Error(String(response.status));
            setData(await response.json() as MinerDetailsPageDto);
            setError(null);
        } catch (reason) {
            console.error('Failed to load miner details', reason);
            setError(tr('miner.details.error', 'Miner-Daten konnten nicht geladen werden.'));
        } finally { setLoading(false); }
    }, [hours, minerId, siteId, tr]);

    useEffect(() => {
        if (!isHydrated) return;
        const timeout = window.setTimeout(() => void load(), 0);
        const interval = window.setInterval(() => void load(), 30_000);
        return () => {
            window.clearTimeout(timeout);
            window.clearInterval(interval);
        };
    }, [isHydrated, load]);

    const history = useMemo(() => data?.history.map(point => ({
        ...point,
        time: new Intl.DateTimeFormat(locale === 'de' ? 'de-DE' : 'en-US', hours <= 24 ? {hour: '2-digit', minute: '2-digit'} : {day: '2-digit', month: '2-digit', hour: '2-digit'}).format(new Date(point.timestamp)),
    })) ?? [], [data, hours, locale]);

    if (!data && loading) return <main className="grid min-h-[60vh] place-items-center text-[#9999a3]">{tr('miner.details.loading', 'Miner-Dashboard wird geladen...')}</main>;
    if (!data) return <main className="grid min-h-[60vh] place-items-center text-red-300">{error}</main>;
    const live = data.live;
    const summary = data.historySummary;

    return <main className="min-h-screen bg-[#0b0b0d] px-4 py-6 text-white md:px-8"><div className="mx-auto max-w-[1450px] space-y-6">
        <header className="flex flex-col gap-4 rounded-2xl border border-white/[0.07] bg-[#141418] p-5 lg:flex-row lg:items-center lg:justify-between"><div className="flex items-start gap-4"><Link className="rounded-lg border border-white/10 p-2 text-[#aaaab4] hover:bg-white/5 hover:text-white" href={`/site/${siteId}/mining`}><ArrowLeft size={19}/></Link><span className="rounded-xl bg-yellow-400/10 p-3 text-yellow-300"><Cpu size={24}/></span><div><p className="text-xs font-semibold uppercase tracking-[0.16em] text-yellow-400">{tr('miner.details.eyebrow', 'Miner-Analyse')} · {data.name || data.model}</p><div className="mt-1 flex flex-wrap items-center gap-3"><h1 className="text-2xl font-semibold">{tr('miner.details.title', 'Miner-Dashboard')}</h1><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${data.status === 'MINING' ? 'bg-emerald-500/15 text-emerald-300' : 'bg-orange-500/15 text-orange-300'}`}>{data.status}</span></div><p className="mt-1 text-sm text-[#92929c]">{tr('miner.details.subtitle', 'Leistung, Effizienz, Temperatur und Verlauf dieses Miners.')} {data.ipAddress} · {data.model} · {data.operatingSystem}</p><p className="mt-1 text-xs text-[#74747e]">{data.clusterName || tr('miner.details.unassigned', 'Keinem Cluster zugeordnet')}</p></div></div><div className="flex items-center gap-2"><select className="rounded-lg border border-white/10 bg-[#101014] px-3 py-2 text-sm" onChange={event => setHours(Number(event.target.value))} value={hours}><option value={24}>{tr('miner.details.range.24h', '24 Stunden')}</option><option value={168}>{tr('miner.details.range.7d', '7 Tage')}</option><option value={720}>{tr('miner.details.range.30d', '30 Tage')}</option></select><button className="rounded-lg border border-white/10 p-2.5 text-[#aaaab4] hover:bg-white/5" onClick={() => void load()} type="button"><RefreshCw className={loading ? 'animate-spin' : ''} size={17}/></button></div></header>
        {error && <p className="rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-300">{error}</p>}

        <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5"><Kpi icon={<Hash/>} label={tr('miner.details.hashrate', 'Hashrate')} value={`${live.hashrateThs.toFixed(2)} TH/s`} tone="yellow"/><Kpi icon={<Zap/>} label={tr('miner.details.power', 'Leistung')} value={`${live.powerUsageWatts} W`} tone="blue"/><Kpi icon={<Gauge/>} label={tr('miner.details.target', 'Power Target')} value={`${live.powerTargetWatts} W`} tone="violet"/><Kpi icon={<Thermometer/>} label={tr('miner.details.temperature', 'Temperatur')} value={`${live.temperatureCelsius.toFixed(1)} °C`} tone="orange"/><Kpi icon={<Activity/>} label={tr('miner.details.efficiency', 'Effizienz')} value={`${live.efficiencyJTh.toFixed(1)} J/TH`} tone="emerald"/></section>

        <EfficiencyStrategyPanel data={data} hours={hours} minerId={minerId} onSaved={setData} siteId={siteId} tr={tr}/>

        <section className="grid gap-4 xl:grid-cols-2"><ChartCard title={tr('miner.details.chart.power', 'Leistung und Ziel')}><ResponsiveContainer height="100%" width="100%"><LineChart data={history}><CartesianGrid stroke="#292932" strokeDasharray="3 3"/><XAxis dataKey="time" minTickGap={30} stroke="#777783" tick={{fontSize: 11}}/><YAxis stroke="#777783" tick={{fontSize: 11}} unit=" W"/><Tooltip contentStyle={{background: '#18181d', border: '1px solid #34343d'}}/><Legend/><Line dataKey="powerUsageWatts" dot={false} name={tr('miner.details.power', 'Leistung')} stroke="#60a5fa" strokeWidth={2}/><Line dataKey="powerTargetWatts" dot={false} name={tr('miner.details.target', 'Ziel')} stroke="#c084fc"/></LineChart></ResponsiveContainer></ChartCard><ChartCard title={tr('miner.details.chart.hashrate', 'Hashrate und Temperatur')}><ResponsiveContainer height="100%" width="100%"><LineChart data={history}><CartesianGrid stroke="#292932" strokeDasharray="3 3"/><XAxis dataKey="time" minTickGap={30} stroke="#777783" tick={{fontSize: 11}}/><YAxis yAxisId="hash" stroke="#777783" tick={{fontSize: 11}}/><YAxis orientation="right" yAxisId="temp" stroke="#777783" tick={{fontSize: 11}}/><Tooltip contentStyle={{background: '#18181d', border: '1px solid #34343d'}}/><Legend/><Line dataKey="hashrateThs" dot={false} name={tr('miner.details.hashrate', 'Hashrate')} stroke="#facc15" strokeWidth={2} yAxisId="hash"/><Line dataKey="temperatureCelsius" dot={false} name={tr('miner.details.temperature', 'Temperatur')} stroke="#fb923c" yAxisId="temp"/></LineChart></ResponsiveContainer></ChartCard></section>

        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-5"><Summary label={tr('miner.details.average_hashrate', 'Ø Hashrate')} value={`${summary.averageHashrateThs.toFixed(2)} TH/s`}/><Summary label={tr('miner.details.average_power', 'Ø Leistung')} value={`${summary.averagePowerWatts.toFixed(0)} W`}/><Summary label={tr('miner.details.average_efficiency', 'Ø Effizienz')} value={`${summary.averageEfficiencyJTh.toFixed(1)} J/TH`}/><Summary label={tr('miner.details.max_temperature', 'Max. Temperatur')} value={`${summary.maximumTemperatureCelsius.toFixed(1)} °C`}/><Summary label={tr('miner.details.energy', 'Energie im Zeitraum')} value={`${summary.estimatedEnergyKwh.toFixed(2)} kWh`}/></section>

        <section className="grid gap-5 xl:grid-cols-3"><InfoPanel icon={<Gauge/>} title={tr('miner.details.hardware', 'Hardware & Controller')}><Info label={tr('miner.details.hardware_range', 'Hardwarebereich')} value={`${data.hardware.hardwareMinPowerWatts}–${data.hardware.hardwareMaxPowerWatts} W`}/><Info label={tr('miner.details.config_range', 'Konfigurierter Bereich')} value={`${data.hardware.configuredMinPowerWatts}–${data.hardware.configuredMaxPowerWatts} W`}/><Info label={tr('miner.details.default_power', 'Standardleistung')} value={`${data.hardware.hardwareDefaultPowerWatts} W`}/><Info label={tr('miner.details.step', 'Schrittweite')} value={data.hardware.powerStepWatts == null ? 'Default' : `${data.hardware.powerStepWatts} W`}/><Info label={tr('miner.details.locks', 'Run / Idle / Power Lock')} value={`${data.hardware.minimumRunMinutes ?? '–'} / ${data.hardware.minimumIdleMinutes ?? '–'} / ${data.hardware.powerChangeLockMinutes ?? '–'} min`}/></InfoPanel><InfoPanel icon={<Network/>} title={tr('miner.details.pools', 'Pools')}><Info label={tr('miner.details.configured_pool', 'Controller-Ziel')} value={data.configuredPool || '–'}/>{data.pools.map((pool, index) => <div className="rounded-lg border border-white/[0.06] bg-black/20 p-3" key={`${pool.url}-${index}`}><strong className="block truncate text-sm">{pool.url}</strong><span className="text-xs text-[#898994]">{pool.username || '–'}</span></div>)}{data.pools.length === 0 && <p className="text-sm text-[#85858f]">{tr('miner.details.no_pools', 'Keine Pooldaten gemeldet.')}</p>}</InfoPanel><InfoPanel icon={<Server/>} title={tr('miner.details.identity', 'Identität')}><Info label="UID" value={data.uid || '–'}/><Info label="MAC" value={data.macAddress || '–'}/><Info label={tr('miner.details.model', 'Modell')} value={data.model || '–'}/><Info label={tr('miner.details.datapoints', 'Datenpunkte')} value={String(summary.dataPoints)}/></InfoPanel></section>

        <section className="overflow-hidden rounded-2xl border border-white/[0.07] bg-[#141418]"><div className="border-b border-white/[0.07] p-5"><h2 className="font-semibold">{tr('miner.details.workers', 'Worker')}</h2></div>{data.workers.length ? <div className="overflow-x-auto"><table className="w-full min-w-[700px] text-left text-sm"><thead className="text-xs text-[#8e8e99]"><tr><th className="px-5 py-3">{tr('miner.details.worker', 'Worker')}</th><th>{tr('miner.details.algorithm', 'Algorithmus')}</th><th>{tr('miner.details.status', 'Status')}</th><th>{tr('miner.details.hashrate', 'Hashrate')}</th><th>{tr('miner.details.temperature', 'Temperatur')}</th><th>{tr('miner.details.power', 'Leistung')}</th></tr></thead><tbody className="divide-y divide-white/[0.05]">{data.workers.map((worker, index) => <tr key={`${worker.name}-${index}`}><td className="px-5 py-3 font-medium">{worker.name}</td><td>{worker.algorithm}</td><td>{worker.status}</td><td>{worker.hashrateThs.toFixed(2)} TH/s</td><td>{worker.temperatureCelsius.toFixed(1)} °C</td><td>{worker.powerUsageWatts} W</td></tr>)}</tbody></table></div> : <p className="p-8 text-sm text-[#85858f]">{tr('miner.details.no_workers', 'Dieser Miner meldet keine separaten Worker.')}</p>}</section>
    </div></main>;
}

function EfficiencyStrategyPanel({data, hours, minerId, onSaved, siteId, tr}: {
    data: MinerDetailsPageDto;
    hours: number;
    minerId: string;
    onSaved: (data: MinerDetailsPageDto) => void;
    siteId: string;
    tr: (key: string, fallback: string) => string;
}) {
    const strategy = data.efficiencyStrategy;
    const [priority, setPriority] = useState(strategy.dispatchPriority?.toString() ?? '');
    const [nominal, setNominal] = useState(strategy.nominalEfficiencyJTh?.toString() ?? '');
    const [dirty, setDirty] = useState(false);
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState<string | null>(null);

    const save = async () => {
        setSaving(true);
        setMessage(null);
        try {
            const response = await fetch(`${API_BASE_URL}/${siteId}/mining/miners/${minerId}/efficiency-settings?hours=${hours}`, {
                method: 'PATCH',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    dispatchPriority: priority.trim() === '' ? null : Number(priority),
                    nominalEfficiencyJTh: nominal.trim() === '' ? null : Number(nominal),
                }),
            });
            if (!response.ok) throw new Error(String(response.status));
            const updated = await response.json() as MinerDetailsPageDto;
            onSaved(updated);
            setDirty(false);
            setMessage(tr('miner.details.strategy.saved', 'Einstellungen gespeichert.'));
        } catch (reason) {
            console.error('Failed to save miner efficiency settings', reason);
            setMessage(tr('miner.details.strategy.save_error', 'Einstellungen konnten nicht gespeichert werden.'));
        } finally {
            setSaving(false);
        }
    };

    const sourceLabel = strategy.effectiveSource === 'LEARNED'
        ? tr('miner.details.strategy.source.learned', 'Gelerntes Profil')
        : strategy.effectiveSource === 'NOMINAL'
            ? tr('miner.details.strategy.source.nominal', 'Herstellerwert')
            : tr('miner.details.strategy.source.live', 'Live-Fallback');

    return <section className="grid gap-5 rounded-2xl border border-emerald-400/15 bg-[#141418] p-5 xl:grid-cols-[0.9fr_1.4fr]">
        <div>
            <h2 className="flex items-center gap-2 font-semibold text-emerald-300"><BrainCircuit size={20}/>{tr('miner.details.strategy.title', 'Efficiency-First Strategie')}</h2>
            <p className="mt-2 text-sm leading-6 text-[#9999a3]">{tr('miner.details.strategy.description', 'Optional priorisieren oder einen Herstellerwert als Startpunkt setzen. Leere Felder verwenden weiterhin die automatische Logik.')}</p>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <label className="text-sm text-[#aaaab4]">{tr('miner.details.strategy.priority', 'Manuelle Priorität')}<input className="mt-1 w-full rounded-lg border border-white/10 bg-[#0e0e12] px-3 py-2 text-white outline-none focus:border-emerald-400/50" min={1} max={10000} onChange={event => { setPriority(event.target.value); setDirty(true); }} placeholder={tr('miner.details.strategy.automatic', 'Automatisch')} type="number" value={priority}/><span className="mt-1 block text-xs text-[#73737d]">{tr('miner.details.strategy.priority_hint', '1 wird zuerst versorgt. Manuell gesetzte Miner stehen vor automatischen Minern.')}</span></label>
                <label className="text-sm text-[#aaaab4]">{tr('miner.details.strategy.nominal', 'Hersteller-Effizienz')}<div className="relative mt-1"><input className="w-full rounded-lg border border-white/10 bg-[#0e0e12] px-3 py-2 pr-14 text-white outline-none focus:border-emerald-400/50" min={5} max={200} onChange={event => { setNominal(event.target.value); setDirty(true); }} placeholder={tr('miner.details.strategy.automatic', 'Automatisch')} step="0.1" type="number" value={nominal}/><span className="absolute right-3 top-2.5 text-xs text-[#777781]">J/TH</span></div><span className="mt-1 block text-xs text-[#73737d]">{tr('miner.details.strategy.nominal_hint', 'Dient nur als Startwert, bis genügend Lerndaten vorhanden sind.')}</span></label>
            </div>
            <div className="mt-4 flex items-center gap-3"><button className="inline-flex items-center gap-2 rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-[#07120e] disabled:cursor-not-allowed disabled:opacity-40" disabled={!dirty || saving} onClick={() => void save()} type="button"><Save size={16}/>{saving ? tr('miner.details.strategy.saving', 'Speichert…') : tr('miner.details.strategy.save', 'Speichern')}</button>{message && <span className="text-xs text-[#aaaab4]">{message}</span>}</div>
        </div>
        <div>
            <div className="grid gap-3 sm:grid-cols-3"><Summary label={tr('miner.details.strategy.active_source', 'Aktive Grundlage')} value={sourceLabel}/><Summary label={tr('miner.details.strategy.active_value', 'Controller-Effizienz')} value={strategy.effectiveEfficiencyJTh == null ? '–' : `${strategy.effectiveEfficiencyJTh.toFixed(1)} J/TH`}/><Summary label={tr('miner.details.strategy.profile_count', 'Gelernte Bereiche')} value={String(strategy.learnedProfiles.filter(profile => profile.controllerReady).length)}/></div>
            <div className="mt-4 overflow-x-auto rounded-xl border border-white/[0.06]"><table className="w-full min-w-[560px] text-left text-sm"><thead className="bg-black/20 text-xs text-[#8e8e99]"><tr><th className="px-4 py-3">{tr('miner.details.strategy.power_target', 'Power Target')}</th><th>{tr('miner.details.efficiency', 'Effizienz')}</th><th>{tr('miner.details.strategy.samples', 'Messwerte')}</th><th>{tr('miner.details.temperature', 'Temperatur')}</th><th>{tr('miner.details.status', 'Status')}</th></tr></thead><tbody className="divide-y divide-white/[0.05]">{strategy.learnedProfiles.map(profile => <tr key={profile.powerTargetBucketWatts}><td className="px-4 py-3 font-medium">{profile.powerTargetBucketWatts} W</td><td>{profile.learnedEfficiencyJTh.toFixed(1)} J/TH</td><td>{profile.sampleCount}</td><td>{profile.averageTemperatureCelsius == null ? '–' : `${profile.averageTemperatureCelsius.toFixed(1)} °C`}</td><td><span className={profile.controllerReady ? 'text-emerald-300' : 'text-amber-300'}>{profile.controllerReady ? tr('miner.details.strategy.ready', 'Aktiv') : tr('miner.details.strategy.learning', 'Lernt')}</span></td></tr>)}</tbody></table>{strategy.learnedProfiles.length === 0 && <p className="p-5 text-sm text-[#85858f]">{tr('miner.details.strategy.empty', 'Noch keine stabilen Betriebspunkte gelernt. Ein Power Target muss dafür mindestens fünf Minuten unverändert laufen.')}</p>}</div>
            <p className="mt-2 text-xs text-[#6f6f79]">{tr('miner.details.strategy.stability', 'Lerndaten werden alle fünf Minuten robust geglättet. Kleine Änderungen lösen keine neue Miner-Reihenfolge aus.')} {strategy.effectivePowerTargetBucketWatts != null ? ` ${strategy.effectivePowerTargetBucketWatts} W · ${strategy.effectiveSampleCount} Samples` : ''}</p>
        </div>
    </section>;
}

const tones: Record<string, string> = {yellow: 'text-yellow-300 bg-yellow-400/10', blue: 'text-blue-300 bg-blue-400/10', violet: 'text-violet-300 bg-violet-400/10', orange: 'text-orange-300 bg-orange-400/10', emerald: 'text-emerald-300 bg-emerald-400/10'};
function Kpi({icon, label, value, tone}: {icon: React.ReactNode; label: string; value: string; tone: string}) { return <article className="rounded-2xl border border-white/[0.07] bg-[#141418] p-4"><span className={`inline-grid rounded-lg p-2 ${tones[tone]}`}>{icon}</span><span className="mt-3 block text-xs text-[#92929c]">{label}</span><strong className="mt-1 block text-xl">{value}</strong></article>; }
function ChartCard({title, children}: {title: string; children: React.ReactNode}) { return <article className="rounded-2xl border border-white/[0.07] bg-[#141418] p-5"><h2 className="mb-4 font-semibold">{title}</h2><div className="h-[330px]">{children}</div></article>; }
function Summary({label, value}: {label: string; value: string}) { return <article className="rounded-xl border border-white/[0.07] bg-[#141418] p-4"><span className="text-xs text-[#8e8e99]">{label}</span><strong className="mt-1 block">{value}</strong></article>; }
function InfoPanel({icon, title, children}: {icon: React.ReactNode; title: string; children: React.ReactNode}) { return <article className="space-y-3 rounded-2xl border border-white/[0.07] bg-[#141418] p-5"><h2 className="flex items-center gap-2 font-semibold text-yellow-300">{icon}{title}</h2>{children}</article>; }
function Info({label, value}: {label: string; value: string}) { return <div className="flex items-center justify-between gap-4 border-b border-white/[0.05] py-2 text-sm last:border-0"><span className="text-[#8e8e99]">{label}</span><span className="text-right text-[#e8e8ec]">{value}</span></div>; }
