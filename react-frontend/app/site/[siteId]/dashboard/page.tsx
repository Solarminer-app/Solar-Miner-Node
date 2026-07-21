'use client';

import {useCallback, useEffect, useMemo, useState} from 'react';
import {useParams, useRouter} from 'next/navigation';
import {
    Activity,
    AlertTriangle,
    ArrowDownToLine,
    ArrowRight,
    ArrowUpFromLine,
    BatteryCharging,
    Bot,
    CheckCircle2,
    CircleDollarSign,
    Clock3,
    Cpu,
    Gauge,
    Grid3X3,
    Home,
    Leaf,
    PlugZap,
    RefreshCw,
    Server,
    ShieldAlert,
    Sun,
    TriangleAlert,
    Zap,
} from 'lucide-react';
import {CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis} from 'recharts';

import de from '../../../locales/de.json';
import en from '../../../locales/en.json';
import type {
    DailyEnergySummaryDto,
    DashboardChartsDto,
    DashboardInitDto,
    DashboardPoolDto as PoolDto,
    LiveDashboardUpdateDto,
    LiveEnergyDto,
    MinerDashboardItemDTO,
    MiningOverviewDto,
    SeriesPointDto,
} from '../../../types';
import {useSitePreferences} from '../site-preferences-context';

const translations = {de, en};
const API_BASE_URL = '/api/pv-site';
const CHART_REFRESH_INTERVAL_MS = 5 * 60 * 1000;
const LIVE_CHART_WINDOW_POINTS = 24 * 60;

type ChartTab = 'live' | 'history' | 'controller';

export default function DashboardPage() {
    const params = useParams();
    const router = useRouter();
    const siteId = params.siteId as string;
    const {locale, currency, timeZone, isHydrated} = useSitePreferences();
    const t = translations[locale] as Record<string, string>;
    const numberLocale = locale === 'de' ? 'de-DE' : 'en-US';

    const [initData, setInitData] = useState<DashboardInitDto | null>(null);
    const [liveData, setLiveData] = useState<LiveDashboardUpdateDto | null>(null);
    const [charts, setCharts] = useState<DashboardChartsDto | null>(null);
    const [chartTab, setChartTab] = useState<ChartTab>('live');
    const [selectedCluster, setSelectedCluster] = useState('Standard');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [profilesReady, setProfilesReady] = useState(false);

    useEffect(() => {
        if (!siteId) return;
        const controller = new AbortController();
        fetch(`${API_BASE_URL}/${siteId}/profile-compatibility`, {signal: controller.signal, cache: 'no-store'})
            .then((response) => response.ok ? response.json() as Promise<{compatible: boolean}> : Promise.reject())
            .then((result) => {
                if (result.compatible) setProfilesReady(true);
                else router.replace(`/site/${siteId}/repair-profiles`);
            })
            .catch(() => {
                if (!controller.signal.aborted) setError(t['profileCheckError']);
            });
        return () => controller.abort();
    }, [router, siteId, t]);

    const loadInitialization = useCallback(async (signal?: AbortSignal) => {
        const response = await fetch(`${API_BASE_URL}/${siteId}/dashboard/init`, {signal, cache: 'no-store'});
        if (!response.ok) throw new Error(t['dashboard.error.missing']);
        const data = await response.json() as DashboardInitDto;
        if (!signal?.aborted) setInitData(data);
    }, [siteId, t]);

    const loadLiveUpdates = useCallback(async (signal?: AbortSignal) => {
        const query = new URLSearchParams({locale, currency, timeZone});
        const response = await fetch(`${API_BASE_URL}/${siteId}/dashboard/live?${query}`, {signal, cache: 'no-store'});
        if (!response.ok) throw new Error(t['dashboard.error.missing']);
        const data = await response.json() as LiveDashboardUpdateDto;
        if (!signal?.aborted) {
            setLiveData(data);
            setCharts((current) => appendLiveEnergySample(current, data.energy));
            setError(null);
            setLoading(false);
        }
    }, [currency, locale, siteId, t, timeZone]);

    const loadCharts = useCallback(async (signal?: AbortSignal) => {
        const query = new URLSearchParams({timeZone, cluster: selectedCluster});
        const response = await fetch(`${API_BASE_URL}/${siteId}/dashboard/charts?${query}`, {signal, cache: 'no-store'});
        if (!response.ok) return;
        const data = await response.json() as DashboardChartsDto;
        if (!signal?.aborted) {
            setCharts(data);
            if (data.controller.clusterName) setSelectedCluster(data.controller.clusterName);
        }
    }, [selectedCluster, siteId, timeZone]);

    useEffect(() => {
        if (!siteId || !profilesReady) return;
        const controller = new AbortController();
        const refresh = () => void loadInitialization(controller.signal).catch((reason) => {
            if (!controller.signal.aborted) setError(reason instanceof Error ? reason.message : t['dashboard.error.missing']);
        });
        const timeout = window.setTimeout(refresh, 0);
        const interval = window.setInterval(refresh, 15_000);
        return () => {
            window.clearTimeout(timeout);
            window.clearInterval(interval);
            controller.abort();
        };
    }, [loadInitialization, profilesReady, siteId, t]);

    useEffect(() => {
        if (!siteId || !isHydrated || !profilesReady) return;
        const controller = new AbortController();
        const refresh = () => void loadLiveUpdates(controller.signal).catch((reason) => {
            if (!controller.signal.aborted) {
                setError(reason instanceof Error ? reason.message : t['dashboard.error.missing']);
                setLoading(false);
            }
        });
        const timeout = window.setTimeout(refresh, 0);
        const interval = window.setInterval(refresh, 3_000);
        return () => {
            window.clearTimeout(timeout);
            window.clearInterval(interval);
            controller.abort();
        };
    }, [isHydrated, loadLiveUpdates, profilesReady, siteId, t]);

    useEffect(() => {
        if (!siteId || !isHydrated || !profilesReady) return;
        const controller = new AbortController();
        const refresh = () => void loadCharts(controller.signal).catch(() => undefined);
        const timeout = window.setTimeout(refresh, 0);
        const interval = window.setInterval(refresh, CHART_REFRESH_INTERVAL_MS);
        return () => {
            window.clearTimeout(timeout);
            window.clearInterval(interval);
            controller.abort();
        };
    }, [isHydrated, loadCharts, profilesReady, siteId]);

    const liveChartData = useMemo(() => {
        if (!charts) return [];
        const points = new Map<number, Record<string, number>>();
        const merge = (key: string, values: SeriesPointDto[]) => values.forEach(({timestamp, value}) => {
            points.set(timestamp, {...points.get(timestamp), timestamp, [key]: value});
        });
        merge('pvPower', charts.live.pvPower);
        merge('gridImport', charts.live.gridImport);
        merge('gridExport', charts.live.gridExport);
        merge('consumption', charts.live.consumption);
        merge('minerConsumption', charts.live.minerConsumption);
        return Array.from(points.values()).sort((left, right) => left.timestamp - right.timestamp);
    }, [charts]);

    if (loading && (!initData || !liveData)) {
        return <main className="grid min-h-[70vh] w-full min-w-0 place-items-center text-[#a1a1aa]"><span className="flex items-center gap-3"><RefreshCw className="animate-spin text-yellow-300"/>{t['dashboard.loading']}</span></main>;
    }
    if (!initData || !liveData) {
        return <main className="grid min-h-[70vh] w-full min-w-0 place-items-center text-red-300"><span className="flex items-center gap-3"><AlertTriangle/>{error ?? t['dashboard.error.missing']}</span></main>;
    }

    const {energy, day, mining, dataQuality} = liveData;
    const format = (value: number, digits = 1) => new Intl.NumberFormat(numberLocale, {maximumFractionDigits: digits}).format(Number.isFinite(value) ? value : 0);
    const kw = (value: number) => `${format(value, 2)} kW`;
    const kwh = (value: number) => `${format(value, 2)} kWh`;
    const money = (value: number) => `${format(value, 2)} ${day.currencySymbol}`;
    const percent = (value: number) => `${format(value, 0)} %`;
    const sourceOnline = dataQuality.sourceStatus === 'ONLINE';
    const problems = buildProblems(initData, liveData, t);
    const batteryLevel = Math.max(0, Math.min(100, energy.batterySocPercent));
    const batteryFlowState = energy.batteryPowerKw > 0.01
        ? 'charging'
        : energy.batteryPowerKw < -0.01
            ? 'discharging'
            : 'idle';
    const displayedChartData: Array<Record<string, string | number | null | undefined>> = chartTab === 'live'
        ? liveChartData
        : chartTab === 'history'
            ? (charts?.pvHistory ?? []).map((point) => ({...point}))
            : (charts?.controller.points ?? []).map((point) => ({...point}));

    return (
        <main className="min-h-screen w-full min-w-0 max-w-full overflow-x-clip bg-[#0b0b0e] px-3 py-4 text-white sm:px-5 lg:px-7">
            <div className="mx-auto w-full min-w-0 max-w-[1700px] space-y-4">
                <header className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-white/[0.07] bg-[#131318] px-4 py-3 sm:px-5">
                    <div>
                        <p className="text-[10px] font-bold uppercase tracking-[0.2em] text-yellow-300">{t['dashboard.eyebrow']} · {initData.siteName}</p>
                        <h1 className="mt-1 text-xl font-bold sm:text-2xl">{t['dashboard.header.title']}</h1>
                        <p className="mt-1 text-sm text-[#777781]">{t['dashboard.header.subtitle']}</p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                        <span className={`inline-flex items-center gap-2 rounded-lg px-3 py-2 text-xs font-semibold ${sourceOnline ? 'bg-emerald-400/10 text-emerald-300' : ['STALE', 'WAITING'].includes(dataQuality.sourceStatus) ? 'bg-amber-400/10 text-amber-300' : 'bg-red-400/10 text-red-300'}`}>
                            {sourceOnline ? <CheckCircle2 size={14}/> : <TriangleAlert size={14}/>} {t[`dashboard.source.${dataQuality.sourceStatus.toLowerCase()}`]}
                        </span>
                        <span className="inline-flex items-center gap-2 rounded-lg bg-white/[0.04] px-3 py-2 text-xs text-[#9898a2]"><Clock3 size={14}/>{freshnessLabel(dataQuality.ageSeconds, t)}</span>
                    </div>
                </header>

                {error ? <div className="flex items-center gap-2 rounded-xl border border-red-400/20 bg-red-400/[0.06] px-4 py-3 text-sm text-red-200"><AlertTriangle size={16}/>{error}</div> : null}
                {problems.length ? <section className="rounded-2xl border border-amber-400/20 bg-amber-400/[0.05] p-3"><div className="mb-2 flex items-center gap-2 text-sm font-semibold text-amber-200"><ShieldAlert size={16}/>{t['dashboard.attention.title']}<span className="rounded-full bg-amber-300/10 px-2 py-0.5 text-[10px]">{problems.length}</span></div><div className="grid gap-1.5 lg:grid-cols-2">{problems.map((problem) => <div className="flex items-start gap-2 rounded-lg bg-black/10 px-3 py-2 text-xs text-amber-100/80" key={problem}><TriangleAlert className="mt-0.5 shrink-0" size={13}/>{problem}</div>)}</div></section> : null}
                {liveData.lockStatusDtos.length ? <section className="rounded-2xl border border-sky-400/15 bg-sky-400/[0.04] p-3"><div className="mb-2 flex flex-wrap items-center justify-between gap-2"><div className="flex items-center gap-2 text-sm font-semibold text-sky-200"><Clock3 size={16}/>{t['dashboard.locks.title']}<span className="rounded-full bg-sky-300/10 px-2 py-0.5 text-[10px]">{liveData.lockStatusDtos.length}</span></div><span className="text-[10px] text-[#707a87]">{t['dashboard.locks.description']}</span></div><div className="grid gap-1.5 lg:grid-cols-2">{liveData.lockStatusDtos.map((lock) => <div className="flex flex-wrap items-center gap-2 rounded-lg bg-black/10 px-3 py-2 text-xs" key={`${lock.ipAddress}-${lock.minerName}`}><span className="min-w-0 flex-1"><strong className="block truncate text-sky-100">{lock.minerName}</strong><span className="text-[10px] text-[#6f7884]">{lock.ipAddress}</span></span>{lock.stateLockRemainingSeconds > 0 ? <span className="rounded-md bg-white/[0.05] px-2 py-1 text-[#aab5c2]">{t['dashboard.locks.state']} · {formatLockDuration(lock.stateLockRemainingSeconds)}</span> : null}{lock.powerLockRemainingSeconds > 0 ? <span className="rounded-md bg-white/[0.05] px-2 py-1 text-[#aab5c2]">{t['dashboard.locks.power']} · {formatLockDuration(lock.powerLockRemainingSeconds)}</span> : null}<span className="rounded-md bg-sky-400/10 px-2 py-1 font-semibold text-sky-200">{lock.expectedPowerWatts.toFixed(0)} W</span></div>)}</div></section> : null}

                <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                    <MetricCard icon={<Sun size={18}/>} label={t['dashboard.today.production']} value={kwh(day.productionKwh)} detail={`${kwh(day.selfConsumedKwh)} ${t['dashboard.today.used_locally']}`} tone="yellow"/>
                    <MetricCard icon={<Leaf size={18}/>} label={t['dashboard.today.autarky']} value={percent(day.autarkyPercent)} detail={`${percent(day.selfConsumptionPercent)} ${t['dashboard.today.self_consumption']}`} tone="green"/>
                    <MetricCard icon={<Gauge size={18}/>} label={t['dashboard.mining.efficiency']} value={mining.efficiencyJPerTh > 0 ? `${format(mining.efficiencyJPerTh, 1)} J/TH` : '—'} detail={`${format(mining.totalHashrateThs, 2)} TH/s`} tone="cyan"/>
                    <MetricCard icon={<CircleDollarSign size={18}/>} label={t['dashboard.today.mining_result']} value={money(day.miningNetResult)} detail={`${format(day.minedSats, 0)} sats`} tone={day.miningNetResult >= 0 ? 'green' : 'red'}/>
                </section>

                <section className="grid gap-4 xl:grid-cols-12">
                    <EnergyFlow energy={energy} kw={kw} t={t}/>
                    <div className="space-y-4 xl:col-span-4">
                        <article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4">
                            <div className="flex items-center justify-between"><div className="flex items-center gap-2"><BatteryCharging className={`text-emerald-300 ${batteryFlowState !== 'idle' ? 'battery-status-icon' : ''}`} size={18}/><h2 className="text-sm font-semibold">{t['dashboard.battery.title']}</h2></div><span className="text-2xl font-bold">{percent(batteryLevel)}</span></div>
                            <div className={`battery-level-track mt-4 h-2 overflow-hidden rounded-full bg-white/[0.06] ${batteryFlowState !== 'idle' ? 'battery-level-track--active' : ''}`}>
                                <div
                                    className={`battery-level-fill h-full rounded-full ${batteryFlowState === 'charging' ? 'battery-level-fill--charging' : batteryFlowState === 'discharging' ? 'battery-level-fill--discharging' : batteryLevel < 20 ? 'bg-red-400' : batteryLevel < 40 ? 'bg-amber-300' : 'bg-emerald-400'}`}
                                    style={{width: `${batteryLevel}%`}}
                                />
                            </div>
                            <div className="mt-4 grid grid-cols-2 gap-2 text-xs"><SmallStat label={t['dashboard.battery.state']} value={t[`dashboard.battery.${energy.batteryState.toLowerCase()}`]}/><SmallStat label={t['dashboard.battery.power']} value={kw(Math.abs(energy.batteryPowerKw))}/><SmallStat label={t['dashboard.battery.capacity']} value={energy.batteryCapacityKwh > 0 ? kwh(energy.batteryCapacityKwh) : '—'}/><SmallStat label={t['dashboard.battery.runtime']} value={energy.estimatedBatteryRuntimeHours == null ? '—' : `${format(energy.estimatedBatteryRuntimeHours, 1)} h`}/></div>
                        </article>

                        <article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4">
                            <div className="flex items-center justify-between"><div className="flex items-center gap-2"><Bot className="text-violet-300" size={18}/><h2 className="text-sm font-semibold">{t['dashboard.controller.title']}</h2></div><span className={`rounded-lg px-2 py-1 text-[10px] font-bold uppercase ${mining.controllerRunning ? 'bg-emerald-400/10 text-emerald-300' : 'bg-white/[0.05] text-[#888892]'}`}>{t[mining.controllerRunning ? 'dashboard.chart.running' : 'dashboard.chart.stopped']}</span></div>
                            <div className="mt-4 grid grid-cols-2 gap-2"><SmallStat label={t['dashboard.controller.mode']} value={mining.controllerMode || '—'}/><SmallStat label={t['dashboard.controller.cluster']} value={mining.clusterName || '—'}/><SmallStat label={t['dashboard.controller.actual']} value={`${format(mining.actualPowerWatts, 0)} W`}/><SmallStat label={t['dashboard.controller.target']} value={`${format(mining.targetPowerWatts, 0)} W`}/></div>
                            <p className="mt-3 rounded-lg bg-white/[0.025] px-3 py-2 text-xs leading-5 text-[#8f8f99]">{mining.lastControllerAction || t['dashboard.controller.no_action']}</p>
                        </article>
                    </div>
                </section>

                <DaySummary day={day} kwh={kwh} money={money} percent={percent} format={format} t={t}/>
                <MiningSourceCard mining={mining} format={format} t={t}/>

                <article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4 sm:p-5">
                    <div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-sm font-semibold">{t['dashboard.charts.title']}</h2><p className="mt-1 text-xs text-[#73737d]">{t['dashboard.charts.subtitle']}</p></div>{chartTab === 'controller' && charts?.clusterNames.length ? <select className="rounded-lg border border-white/10 bg-[#0d0d11] px-3 py-2 text-xs outline-none" onChange={(event) => setSelectedCluster(event.target.value)} value={selectedCluster}>{charts.clusterNames.map((name) => <option key={name}>{name}</option>)}</select> : null}</div>
                    <div className="mt-4 flex gap-1 rounded-lg bg-black/20 p-1">{(['live', 'history', 'controller'] as ChartTab[]).map((tab) => <button className={`rounded-md px-3 py-1.5 text-xs font-semibold transition ${chartTab === tab ? 'bg-white/[0.09] text-white' : 'text-[#73737d] hover:text-white'}`} key={tab} onClick={() => setChartTab(tab)}>{t[`dashboard.tab.${tab === 'controller' ? 'automation' : tab}`]}</button>)}</div>
                    <div className="mt-4 h-[330px] w-full min-w-0 overflow-hidden">{displayedChartData.length ? <ResponsiveContainer height="100%" width="100%"><LineChart data={displayedChartData}><CartesianGrid stroke="#26262d" strokeDasharray="3 3"/><XAxis dataKey="timestamp" stroke="#696973" tickFormatter={(value) => new Date(value).toLocaleTimeString(numberLocale, {hour: '2-digit', minute: '2-digit'})}/><YAxis stroke="#696973"/><Tooltip contentStyle={{background: '#16161b', border: '1px solid #303038', borderRadius: 8}} labelFormatter={(value) => new Date(Number(value)).toLocaleString(numberLocale)}/><Legend/>{chartTab === 'live' ? <><Line dataKey="pvPower" dot={false} name={t['dashboard.chart.pv_power']} stroke="#facc15" strokeWidth={2}/><Line dataKey="consumption" dot={false} name={t['dashboard.chart.consumption']} stroke="#a78bfa"/><Line dataKey="minerConsumption" dot={false} name={t['dashboard.chart.miner_consumption']} stroke="#fb923c"/><Line dataKey="gridImport" dot={false} name={t['dashboard.chart.import']} stroke="#f87171"/><Line dataKey="gridExport" dot={false} name={t['dashboard.chart.export']} stroke="#34d399"/></> : chartTab === 'history' ? <Line dataKey="value" dot={false} name={t['dashboard.chart.pv_power_history']} stroke="#facc15" strokeWidth={2}/> : <><Line dataKey="targetPowerWatts" dot={false} name={t['dashboard.chart.target_power']} stroke="#60a5fa" strokeWidth={2}/><Line dataKey="allocatedPowerWatts" dot={false} name={t['dashboard.chart.allocated_power']} stroke="#c084fc" strokeWidth={2}/></>}</LineChart></ResponsiveContainer> : <div className="grid h-full place-items-center text-sm text-[#666670]">{t['dashboard.chart.no_data']}</div>}</div>
                </article>

                <section className="grid gap-4 xl:grid-cols-2">
                    <InventoryCard miners={initData.miners} t={t}/>
                    <PoolCard pools={initData.pools} t={t}/>
                </section>
            </div>
        </main>
    );
}

function EnergyFlow({energy, kw, t}: {energy: LiveEnergyDto; kw: (value: number) => string; t: Record<string, string>}) {
    const batteryDischarge = Math.max(0, -energy.batteryPowerKw);
    const batteryCharge = Math.max(0, energy.batteryPowerKw);
    const hasIncomingEnergy = energy.pvPowerKw > 0.01 || energy.gridImportKw > 0.01 || batteryDischarge > 0.01;
    const hasOutgoingEnergy = energy.householdPowerKw > 0.01 || energy.minerPowerKw > 0.01 || energy.gridExportKw > 0.01 || batteryCharge > 0.01;

    return (
        <article className="overflow-hidden rounded-2xl border border-white/[0.07] bg-[#131318] p-4 sm:p-5 xl:col-span-8">
            <div><h2 className="text-sm font-semibold">{t['dashboard.flow.title']}</h2><p className="mt-1 text-xs text-[#73737d]">{t['dashboard.flow.subtitle']}</p></div>
            <div className="mt-5 grid items-stretch gap-3 lg:grid-cols-[1fr_2.25rem_1.15fr_2.25rem_1fr]">
                <div className="space-y-2">
                    <FlowNode active={energy.pvPowerKw > 0.01} icon={<Sun/>} label={t['dashboard.flow.pv']} value={kw(energy.pvPowerKw)} tone="yellow"/>
                    <FlowNode active={energy.gridImportKw > 0.01} icon={<ArrowDownToLine/>} label={t['dashboard.flow.grid_import']} value={kw(energy.gridImportKw)} tone="red"/>
                    <FlowNode active={batteryDischarge > 0.01} icon={<BatteryCharging/>} label={t['dashboard.flow.battery_discharge']} value={kw(batteryDischarge)} tone="cyan"/>
                </div>
                <FlowConnector active={hasIncomingEnergy}/>
                <div className={`energy-flow-hub grid place-items-center rounded-xl border border-violet-400/20 bg-gradient-to-br from-violet-400/10 to-transparent p-5 text-center ${hasIncomingEnergy || hasOutgoingEnergy ? 'energy-flow-hub--active' : ''}`}>
                    <Zap className="text-violet-300" size={28}/><p className="mt-2 text-xs text-[#8c8c96]">{t['dashboard.flow.distribution']}</p><strong className="mt-1 text-2xl">{kw(energy.totalLoadKw)}</strong>
                </div>
                <FlowConnector active={hasOutgoingEnergy}/>
                <div className="space-y-2">
                    <FlowNode active={energy.householdPowerKw > 0.01} icon={<Home/>} label={t['dashboard.flow.household']} value={kw(energy.householdPowerKw)} tone="violet"/>
                    <FlowNode active={energy.minerPowerKw > 0.01} icon={<Cpu/>} label={t['dashboard.flow.mining']} value={kw(energy.minerPowerKw)} tone="orange"/>
                    <FlowNode active={energy.gridExportKw > 0.01} icon={<ArrowUpFromLine/>} label={t['dashboard.flow.grid_export']} value={kw(energy.gridExportKw)} tone="green"/>
                    <FlowNode active={batteryCharge > 0.01} icon={<BatteryCharging/>} label={t['dashboard.flow.battery_charge']} value={kw(batteryCharge)} tone="green"/>
                </div>
            </div>
        </article>
    );
}

function FlowConnector({active}: {active: boolean}) {
    return <div aria-hidden="true" className={`energy-flow-connector hidden items-center lg:flex ${active ? 'energy-flow-connector--active' : ''}`}><ArrowRight size={18}/></div>;
}

function FlowNode({active, icon, label, value, tone}: {active: boolean; icon: React.ReactNode; label: string; value: string; tone: string}) {
    const colors: Record<string, string> = {yellow: 'text-yellow-300 bg-yellow-300/10', red: 'text-red-300 bg-red-300/10', cyan: 'text-cyan-300 bg-cyan-300/10', violet: 'text-violet-300 bg-violet-300/10', orange: 'text-orange-300 bg-orange-300/10', green: 'text-emerald-300 bg-emerald-300/10'};
    return <div className={`energy-flow-node flex items-center gap-3 overflow-hidden rounded-xl border border-white/[0.06] bg-[#0e0e12] p-3 ${active ? `energy-flow-node--active energy-flow-node--${tone}` : ''}`}><span className={`energy-flow-node-icon grid h-8 w-8 shrink-0 place-items-center rounded-lg [&_svg]:h-4 [&_svg]:w-4 ${colors[tone]}`}>{icon}</span><span className="min-w-0 flex-1"><span className="block truncate text-[11px] text-[#777781]">{label}</span><strong className="text-sm">{value}</strong></span></div>;
}

function MetricCard({icon, label, value, detail, tone}: {icon: React.ReactNode; label: string; value: string; detail: string; tone: string}) {
    const colors: Record<string, string> = {yellow: 'text-yellow-300 bg-yellow-300/10', green: 'text-emerald-300 bg-emerald-300/10', cyan: 'text-cyan-300 bg-cyan-300/10', red: 'text-red-300 bg-red-300/10'};
    return <article className="flex items-center gap-3 rounded-2xl border border-white/[0.07] bg-[#131318] p-4"><span className={`grid h-9 w-9 place-items-center rounded-lg ${colors[tone]}`}>{icon}</span><span className="min-w-0"><span className="block text-[11px] text-[#777781]">{label}</span><strong className="block truncate text-xl">{value}</strong><span className="block truncate text-[11px] text-[#666670]">{detail}</span></span></article>;
}

function SmallStat({label, value}: {label: string; value: string}) {
    return <div className="rounded-lg bg-white/[0.03] px-3 py-2"><span className="block text-[10px] text-[#696973]">{label}</span><strong className="mt-0.5 block truncate text-xs">{value}</strong></div>;
}

function DaySummary({day, kwh, money, percent, format, t}: {day: DailyEnergySummaryDto; kwh: (value: number) => string; money: (value: number) => string; percent: (value: number) => string; format: (value: number, digits?: number) => string; t: Record<string, string>}) {
    const groups = [
        {icon: <Zap/>, title: t['dashboard.day.energy'], rows: [[t['dashboard.day.production'], kwh(day.productionKwh)], [t['dashboard.day.consumption'], kwh(day.consumptionKwh)], [t['dashboard.day.autarky'], percent(day.autarkyPercent)], [t['dashboard.day.self_consumption'], percent(day.selfConsumptionPercent)]]},
        {icon: <Home/>, title: t['dashboard.day.household'], rows: [[t['dashboard.day.consumption'], kwh(day.householdConsumptionKwh)], [t['dashboard.day.savings'], money(day.householdSavings)], [t['dashboard.day.grid_import'], kwh(day.gridImportKwh)]]},
        {icon: <Cpu/>, title: t['dashboard.day.mining'], rows: [[t['dashboard.day.consumption'], kwh(day.miningConsumptionKwh)], [t['dashboard.day.local_energy'], kwh(day.miningLocalEnergyKwh)], [t['dashboard.day.grid_energy'], kwh(day.miningGridEnergyKwh)], [t['dashboard.day.mined'], `${format(day.minedSats, 0)} sats`]]},
        {icon: <CircleDollarSign/>, title: t['dashboard.day.finance'], rows: [[t['dashboard.day.mining_revenue'], money(day.miningRevenue)], [t['dashboard.day.export_revenue'], money(day.exportRevenue)], [t['dashboard.day.import_cost'], money(day.importCost)], [t['dashboard.day.mining_net'], money(day.miningNetResult)]]},
    ];
    return <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">{groups.map((group) => <article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4" key={group.title}><div className="mb-3 flex items-center gap-2 text-sm font-semibold [&_svg]:h-4 [&_svg]:w-4 [&_svg]:text-[#8b8b95]">{group.icon}{group.title}</div><div className="space-y-2">{group.rows.map(([label, value]) => <div className="flex items-center justify-between gap-3 text-xs" key={label}><span className="text-[#777781]">{label}</span><strong className="text-right">{value}</strong></div>)}</div></article>)}</section>;
}

function MiningSourceCard({mining, format, t}: {mining: MiningOverviewDto; format: (value: number, digits?: number) => string; t: Record<string, string>}) {
    const total = mining.estimatedPvPowerWatts + mining.estimatedBatteryPowerWatts + mining.estimatedGridPowerWatts;
    const share = (value: number) => total > 0 ? value / total * 100 : 0;
    return <article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4 sm:p-5"><div className="flex flex-wrap items-end justify-between gap-3"><div><div className="flex items-center gap-2"><PlugZap className="text-orange-300" size={18}/><h2 className="text-sm font-semibold">{t['dashboard.mining.source_title']}</h2></div><p className="mt-1 text-xs text-[#73737d]">{t['dashboard.mining.source_estimate']}</p></div><span className="text-xs text-[#8c8c96]">{mining.activeMiners} / {mining.totalMiners} {t['dashboard.mining.active']}</span></div><div className="mt-4 flex h-3 overflow-hidden rounded-full bg-white/[0.05]"><div className="bg-yellow-300" style={{width: `${share(mining.estimatedPvPowerWatts)}%`}}/><div className="bg-cyan-300" style={{width: `${share(mining.estimatedBatteryPowerWatts)}%`}}/><div className="bg-red-400" style={{width: `${share(mining.estimatedGridPowerWatts)}%`}}/></div><div className="mt-3 grid gap-2 sm:grid-cols-3"><SourceLegend color="bg-yellow-300" label={t['dashboard.mining.source_pv']} value={`${format(mining.estimatedPvPowerWatts, 0)} W`}/><SourceLegend color="bg-cyan-300" label={t['dashboard.mining.source_battery']} value={`${format(mining.estimatedBatteryPowerWatts, 0)} W`}/><SourceLegend color="bg-red-400" label={t['dashboard.mining.source_grid']} value={`${format(mining.estimatedGridPowerWatts, 0)} W`}/></div></article>;
}

function appendLiveEnergySample(charts: DashboardChartsDto | null, energy: LiveEnergyDto): DashboardChartsDto | null {
    if (!charts) return null;

    // The persisted chart uses one-minute mean windows. Replace the current minute
    // locally so the chart remains live without issuing another expensive Influx query.
    const timestamp = Math.floor(Date.now() / 60_000) * 60_000;
    const upsert = (points: SeriesPointDto[], value: number) => [
        ...points.filter((point) => point.timestamp !== timestamp),
        {timestamp, value: Number.isFinite(value) ? value : 0},
    ].sort((left, right) => left.timestamp - right.timestamp).slice(-LIVE_CHART_WINDOW_POINTS);

    return {
        ...charts,
        live: {
            pvPower: upsert(charts.live.pvPower, energy.pvPowerKw),
            gridImport: upsert(charts.live.gridImport, energy.gridImportKw),
            gridExport: upsert(charts.live.gridExport, energy.gridExportKw),
            consumption: upsert(charts.live.consumption, energy.totalLoadKw),
            minerConsumption: upsert(charts.live.minerConsumption, energy.minerPowerKw),
        },
    };
}

function SourceLegend({color, label, value}: {color: string; label: string; value: string}) {
    return <div className="flex items-center gap-2 rounded-lg bg-white/[0.025] px-3 py-2 text-xs"><span className={`h-2 w-2 rounded-full ${color}`}/><span className="flex-1 text-[#7d7d87]">{label}</span><strong>{value}</strong></div>;
}

function InventoryCard({miners, t}: {miners: MinerDashboardItemDTO[]; t: Record<string, string>}) {
    return <article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4"><div className="mb-3 flex items-center justify-between"><div className="flex items-center gap-2"><Server className="text-violet-300" size={17}/><h2 className="text-sm font-semibold">{t['dashboard.miner.title']}</h2></div><span className="text-xs text-[#696973]">{miners.length}</span></div><div className="overflow-x-auto"><table className="w-full min-w-[580px] text-left text-xs"><thead className="text-[#696973]"><tr><th className="pb-2 font-medium">{t['dashboard.grid.miner.name']}</th><th className="pb-2 font-medium">{t['dashboard.grid.miner.status']}</th><th className="pb-2 font-medium">{t['dashboard.grid.miner.hashrate']}</th><th className="pb-2 font-medium">{t['dashboard.grid.miner.power']}</th><th className="pb-2 text-right font-medium">{t['dashboard.grid.miner.temp']}</th></tr></thead><tbody>{miners.map((miner) => <tr className="border-t border-white/[0.05]" key={`${miner.ip}-${miner.name}`}><td className="py-2.5"><strong className="block">{miner.name}</strong><span className="text-[10px] text-[#60606a]">{miner.ip}</span></td><td><StatusBadge status={miner.status} t={t}/></td><td>{miner.hashrate}</td><td>{miner.power}</td><td className="text-right">{miner.temp}</td></tr>)}{miners.length === 0 ? <tr><td className="py-8 text-center text-[#666670]" colSpan={5}>{t['dashboard.miner.empty']}</td></tr> : null}</tbody></table></div></article>;
}

function PoolCard({pools, t}: {pools: PoolDto[]; t: Record<string, string>}) {
    return <article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4"><div className="mb-3 flex items-center justify-between"><div className="flex items-center gap-2"><Grid3X3 className="text-cyan-300" size={17}/><h2 className="text-sm font-semibold">{t['dashboard.pool.title']}</h2></div><span className="text-xs text-[#696973]">{pools.length}</span></div><div className="space-y-2">{pools.map((pool) => <div className="flex items-center gap-3 rounded-lg bg-white/[0.025] px-3 py-2.5" key={pool.id}><Activity className={pool.status === 'ONLINE' ? 'text-emerald-300' : 'text-red-300'} size={15}/><span className="min-w-0 flex-1"><strong className="block truncate text-xs">{pool.url}</strong><span className="text-[10px] text-[#676771]">{pool.worker || t['dashboard.pool.no_worker']}</span></span><StatusBadge status={pool.status} t={t}/></div>)}{pools.length === 0 ? <div className="py-8 text-center text-xs text-[#666670]">{t['dashboard.pool.empty']}</div> : null}</div></article>;
}

function StatusBadge({status, t}: {status: string; t: Record<string, string>}) {
    const normalized = status.toLowerCase();
    const good = ['online', 'active', 'running', 'mining'].includes(normalized);
    const bad = ['offline', 'error'].includes(normalized);
    return <span className={`inline-flex rounded-md px-2 py-1 text-[10px] font-semibold ${good ? 'bg-emerald-400/10 text-emerald-300' : bad ? 'bg-red-400/10 text-red-300' : 'bg-amber-400/10 text-amber-300'}`}>{t[`dashboard.status.${normalized}`] ?? status}</span>;
}

function freshnessLabel(ageSeconds: number, t: Record<string, string>) {
    if (ageSeconds < 0) return t['dashboard.freshness.never'];
    if (ageSeconds < 5) return t['dashboard.freshness.now'];
    if (ageSeconds < 60) return t['dashboard.freshness.seconds'].replace('{seconds}', String(ageSeconds));
    return t['dashboard.freshness.minutes'].replace('{minutes}', String(Math.floor(ageSeconds / 60)));
}

function formatLockDuration(seconds: number) {
    if (seconds < 60) return `${Math.max(0, Math.ceil(seconds))} s`;
    return `${Math.ceil(seconds / 60)} min`;
}

function buildProblems(init: DashboardInitDto, live: LiveDashboardUpdateDto, t: Record<string, string>) {
    const problems: string[] = [];
    if (live.dataQuality.sourceStatus !== 'ONLINE') problems.push(t[`dashboard.problem.source_${live.dataQuality.sourceStatus.toLowerCase()}`]);
    init.miners.filter((miner) => ['ERROR', 'UNKNOWN'].includes(miner.status)).forEach((miner) => problems.push(t['dashboard.problem.miner'].replace('{miner}', miner.name)));
    init.pools.filter((pool) => ['OFFLINE', 'STALE'].includes(pool.status)).forEach((pool) => problems.push(t['dashboard.problem.pool'].replace('{pool}', pool.url)));
    return problems;
}
