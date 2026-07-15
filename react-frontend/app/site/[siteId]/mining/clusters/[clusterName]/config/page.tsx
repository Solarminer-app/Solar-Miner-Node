'use client';

import Link from 'next/link';
import {useParams, useRouter} from 'next/navigation';
import {useCallback, useEffect, useMemo, useState} from 'react';
import {ArrowLeft, Beaker, Braces, CopyPlus, Play, Plus, Save, Settings2, Trash2} from 'lucide-react';
import {CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis} from 'recharts';

import de from '../../../../../locales/de.json';
import en from '../../../../../locales/en.json';
import type {
    ClusterConditionDto,
    ClusterConfigDto,
    ClusterControllerActionDto,
    ClusterOperatingModeDto,
    ClusterSimulationDto,
    ClusterValueExpressionDto,
} from '../../../../../types';
import {useSitePreferences} from '../../../../site-preferences-context';

const translations = {de, en};
const API_BASE_URL = 'http://localhost:8080/api/pv-site';
const inputClass = 'w-full rounded-lg border border-white/10 bg-[#101014] px-3 py-2 text-sm text-white outline-none focus:border-yellow-400/60';

const predicate = (): ClusterConditionDto => ({
    type: 'PREDICATE', operator: null, subConditions: [], variable: 'POTENTIAL_PV_SURPLUS',
    aggregation: 'MEDIAN', windowValue: 5, windowUnit: 'MINUTES', comparator: 'GREATER_OR_EQUAL', threshold: 0,
});
const logical = (): ClusterConditionDto => ({...predicate(), type: 'LOGICAL', operator: 'AND', subConditions: [predicate()]});
const expression = (): ClusterValueExpressionDto => ({
    type: 'DYNAMIC_VARIABLE', constantWatts: null, variable: 'POTENTIAL_PV_SURPLUS', aggregation: 'MEDIAN',
    windowValue: 5, windowUnit: 'MINUTES', multiplier: 1000, offset: 0, percentage: null,
});
const action = (): ClusterControllerActionDto => ({
    actionType: 'power', targetType: 'CLUSTER_DYNAMIC', strategy: 'EFFICIENCY_FIRST', valueExpression: expression(), stepSizeWatts: 250,
});
const mode = (index: number): ClusterOperatingModeDto => ({
    name: `Mode ${index + 1}`, startCondition: logical(), stopCondition: predicate(), actions: [action()],
    minRunTimeMinutes: 15, minIdleTimeMinutes: 5, powerChangeLockTimeMinutes: 15,
});

export default function ClusterConfigPage() {
    const {siteId, clusterName: routeName} = useParams<{siteId: string; clusterName: string}>();
    const router = useRouter();
    const {locale, isHydrated} = useSitePreferences();
    const t = translations[locale] as Record<string, string>;
    const tr = useCallback((key: string, fallback: string) => t[key] ?? fallback, [t]);
    const requestedName = decodeURIComponent(routeName);
    const [config, setConfig] = useState<ClusterConfigDto | null>(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [simulating, setSimulating] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [sourceType, setSourceType] = useState<'PRESET' | 'HISTORICAL'>('PRESET');
    const [preset, setPreset] = useState('SUNNY');
    const [historicalDate, setHistoricalDate] = useState(() => {
        const date = new Date(); date.setDate(date.getDate() - 1); return date.toISOString().slice(0, 10);
    });
    const [simulation, setSimulation] = useState<ClusterSimulationDto | null>(null);

    const loadConfig = useCallback(async () => {
        setLoading(true);
        try {
            const response = await fetch(`${API_BASE_URL}/${siteId}/mining/configs/${encodeURIComponent(requestedName)}`, {cache: 'no-store'});
            if (!response.ok) throw new Error(String(response.status));
            const next = await response.json() as ClusterConfigDto;
            setConfig(next);
            setPreset(next.options.simulationPresets[0]?.id ?? 'SUNNY');
            setError(null);
        } catch (reason) {
            console.error('Failed to load cluster config', reason);
            setError(tr('cluster.config.error.load', 'Konfiguration konnte nicht geladen werden.'));
        } finally { setLoading(false); }
    }, [requestedName, siteId, tr]);

    useEffect(() => { if (isHydrated) void loadConfig(); }, [isHydrated, loadConfig]);

    const updateMode = (index: number, next: ClusterOperatingModeDto) => setConfig(current => current ? ({...current, modes: current.modes.map((entry, i) => i === index ? next : entry)}) : current);
    const save = async () => {
        if (!config) return;
        setSaving(true); setError(null);
        try {
            const response = await fetch(`${API_BASE_URL}/${siteId}/mining/configs`, {
                method: 'POST', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({originalName: config.existing ? requestedName : null, name: config.name, modes: config.modes}),
            });
            if (!response.ok) throw new Error(await response.text());
            const saved = await response.json() as ClusterConfigDto;
            setConfig(saved);
            if (requestedName === 'new') router.replace(`/site/${siteId}/mining/clusters/${encodeURIComponent(saved.name)}/config`);
        } catch (reason) {
            console.error('Failed to save cluster config', reason);
            setError(tr('cluster.config.error.save', 'Konfiguration konnte nicht gespeichert werden.'));
        } finally { setSaving(false); }
    };
    const simulate = async () => {
        if (!config) return;
        setSimulating(true); setError(null);
        try {
            const response = await fetch(`${API_BASE_URL}/${siteId}/mining/configs/simulate`, {
                method: 'POST', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({clusterName: requestedName, modes: config.modes, sourceType, historicalDate, preset, intervalMinutes: 5}),
            });
            if (!response.ok) throw new Error(await response.text());
            setSimulation(await response.json() as ClusterSimulationDto);
        } catch (reason) {
            console.error('Failed to simulate cluster config', reason);
            setError(tr('cluster.simulator.error', 'Simulation konnte nicht ausgeführt werden.'));
        } finally { setSimulating(false); }
    };

    const chartData = useMemo(() => simulation?.points.map(point => ({
        ...point,
        time: new Intl.DateTimeFormat(locale === 'de' ? 'de-DE' : 'en-US', {hour: '2-digit', minute: '2-digit'}).format(new Date(point.timestamp)),
        targetKw: point.targetPowerWatts / 1000,
        allocatedKw: point.allocatedPowerWatts / 1000,
    })) ?? [], [locale, simulation]);

    if (loading || !config) return <main className="grid min-h-[60vh] place-items-center text-[#9999a3]">{error ?? tr('cluster.config.loading', 'Konfiguration wird geladen...')}</main>;

    return (
        <main className="min-h-screen bg-[#0b0b0d] px-4 py-6 text-white md:px-8">
            <div className="mx-auto max-w-[1500px] space-y-6">
                <header className="flex flex-col gap-4 rounded-2xl border border-white/[0.07] bg-[#141418] p-5 lg:flex-row lg:items-center lg:justify-between">
                    <div className="flex items-start gap-4">
                        <Link className="rounded-lg border border-white/10 p-2 text-[#aaaab4] hover:bg-white/5 hover:text-white" href={`/site/${siteId}/mining`}><ArrowLeft size={19}/></Link>
                        <div><p className="text-xs font-semibold uppercase tracking-[0.16em] text-yellow-400">{tr('cluster.config.eyebrow', 'Controller DSL')}</p><h1 className="mt-1 text-2xl font-semibold">{tr('cluster.config.title', 'Cluster-Konfiguration')}</h1><p className="mt-1 text-sm text-[#91919c]">{tr('cluster.config.subtitle', 'Betriebsmodi bearbeiten und mit echten oder synthetischen Tagesdaten testen.')}</p></div>
                    </div>
                    <button className="inline-flex items-center justify-center gap-2 rounded-xl bg-yellow-400 px-5 py-3 text-sm font-semibold text-black hover:bg-yellow-300 disabled:opacity-50" disabled={saving || !config.name.trim()} onClick={() => void save()} type="button"><Save size={17}/>{saving ? tr('cluster.config.saving', 'Speichert...') : tr('cluster.config.save', 'Konfiguration speichern')}</button>
                </header>
                {error && <p className="rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-300">{error}</p>}

                <section className="rounded-2xl border border-white/[0.07] bg-[#141418] p-5">
                    <label className="block max-w-xl text-sm text-[#b5b5be]">{tr('cluster.config.name', 'Konfigurationsname')}<input className={`${inputClass} mt-2`} disabled={config.existing} onChange={event => setConfig({...config, name: event.target.value})} value={config.name}/></label>
                </section>

                <div className="space-y-4">
                    <div className="flex items-center justify-between"><div><h2 className="text-lg font-semibold">{tr('cluster.config.modes', 'Betriebsmodi')}</h2><p className="text-sm text-[#8d8d97]">{tr('cluster.config.priority', 'Die Reihenfolge bestimmt die Priorität der Modi.')}</p></div><button className="inline-flex items-center gap-2 rounded-lg border border-yellow-400/30 bg-yellow-400/10 px-3 py-2 text-sm text-yellow-300" onClick={() => setConfig({...config, modes: [...config.modes, mode(config.modes.length)]})} type="button"><Plus size={16}/>{tr('cluster.config.add_mode', 'Modus hinzufügen')}</button></div>
                    {config.modes.map((entry, index) => <ModeEditor config={config} index={index} key={`${index}-${entry.name}`} mode={entry} onChange={next => updateMode(index, next)} onDelete={() => setConfig({...config, modes: config.modes.filter((_, i) => i !== index)})} tr={tr}/>) }
                </div>

                <section className="rounded-2xl border border-violet-400/20 bg-gradient-to-br from-violet-500/[0.08] to-[#141418] p-5">
                    <div className="flex items-start gap-3"><span className="rounded-xl bg-violet-400/10 p-2 text-violet-300"><Beaker size={20}/></span><div><h2 className="text-lg font-semibold">{tr('cluster.simulator.title', 'Controller-Simulator')}</h2><p className="mt-1 text-sm text-[#9c9ca7]">{tr('cluster.simulator.subtitle', 'Verwendet dieselbe ControllerDSL und dieselbe Decision Engine wie der laufende Cluster.')}</p></div></div>
                    <div className="mt-5 grid gap-4 lg:grid-cols-[180px_minmax(220px,1fr)_auto]">
                        <label className="text-sm text-[#b5b5be]">{tr('cluster.simulator.source', 'Datenquelle')}<select className={`${inputClass} mt-2`} onChange={event => setSourceType(event.target.value as 'PRESET' | 'HISTORICAL')} value={sourceType}><option value="PRESET">{tr('cluster.simulator.source.preset', 'Preset')}</option><option value="HISTORICAL">{tr('cluster.simulator.source.historical', 'Vergangener Tag')}</option></select></label>
                        {sourceType === 'PRESET' ? <label className="text-sm text-[#b5b5be]">{tr('cluster.simulator.preset', 'Tagesprofil')}<select className={`${inputClass} mt-2`} onChange={event => setPreset(event.target.value)} value={preset}>{config.options.simulationPresets.map(item => <option key={item.id} value={item.id}>{tr(item.labelKey, item.id)}</option>)}</select></label> : <label className="text-sm text-[#b5b5be]">{tr('cluster.simulator.date', 'Historisches Datum')}<input className={`${inputClass} mt-2`} max={new Date(Date.now() - 86400000).toISOString().slice(0, 10)} onChange={event => setHistoricalDate(event.target.value)} type="date" value={historicalDate}/></label>}
                        <button className="mt-auto inline-flex h-[42px] items-center justify-center gap-2 rounded-lg bg-violet-500 px-5 text-sm font-semibold hover:bg-violet-400 disabled:opacity-50" disabled={simulating || config.modes.length === 0} onClick={() => void simulate()} type="button"><Play size={16}/>{simulating ? tr('cluster.simulator.running', 'Simuliert...') : tr('cluster.simulator.run', 'Simulation starten')}</button>
                    </div>
                    {simulation && <SimulationResult data={simulation} chartData={chartData} tr={tr}/>} 
                </section>
            </div>
        </main>
    );
}

function ModeEditor({config, mode, index, onChange, onDelete, tr}: {config: ClusterConfigDto; mode: ClusterOperatingModeDto; index: number; onChange: (mode: ClusterOperatingModeDto) => void; onDelete: () => void; tr: (key: string, fallback: string) => string}) {
    return <article className="rounded-2xl border border-white/[0.07] bg-[#141418] p-5">
        <div className="flex items-center gap-3"><span className="grid h-9 w-9 place-items-center rounded-lg bg-yellow-400/10 font-mono text-sm text-yellow-300">{index + 1}</span><input className={`${inputClass} max-w-md font-semibold`} onChange={event => onChange({...mode, name: event.target.value})} value={mode.name}/><button aria-label={tr('common.delete', 'Löschen')} className="ml-auto rounded-lg p-2 text-red-300 hover:bg-red-500/10" onClick={onDelete} type="button"><Trash2 size={17}/></button></div>
        <div className="mt-5 grid gap-4 xl:grid-cols-2"><ConditionEditor condition={mode.startCondition} label={tr('cluster.config.start_condition', 'Startbedingung')} onChange={startCondition => onChange({...mode, startCondition})} options={config.options} tr={tr}/><ConditionEditor condition={mode.stopCondition} label={tr('cluster.config.stop_condition', 'Stopbedingung')} onChange={stopCondition => onChange({...mode, stopCondition})} options={config.options} tr={tr}/></div>
        <div className="mt-4 rounded-xl border border-white/[0.06] bg-[#101014] p-4"><div className="mb-3 flex items-center justify-between"><h3 className="flex items-center gap-2 text-sm font-semibold"><Braces className="text-cyan-300" size={16}/>{tr('cluster.config.actions', 'Aktionen')}</h3><button className="inline-flex items-center gap-1 text-xs text-cyan-300" onClick={() => onChange({...mode, actions: [...mode.actions, action()]})} type="button"><Plus size={14}/>{tr('cluster.config.add_action', 'Aktion')}</button></div><div className="space-y-3">{mode.actions.map((entry, actionIndex) => <ActionEditor action={entry} key={actionIndex} onChange={next => onChange({...mode, actions: mode.actions.map((item, i) => i === actionIndex ? next : item)})} onDelete={() => onChange({...mode, actions: mode.actions.filter((_, i) => i !== actionIndex)})} options={config.options} tr={tr}/>)}</div></div>
        <div className="mt-4 grid gap-3 sm:grid-cols-3"><NumberField label={tr('cluster.config.min_run', 'Min. Laufzeit (min)')} onChange={value => onChange({...mode, minRunTimeMinutes: value})} value={mode.minRunTimeMinutes}/><NumberField label={tr('cluster.config.min_idle', 'Min. Pause (min)')} onChange={value => onChange({...mode, minIdleTimeMinutes: value})} value={mode.minIdleTimeMinutes}/><NumberField label={tr('cluster.config.power_lock', 'Leistungssperre (min)')} onChange={value => onChange({...mode, powerChangeLockTimeMinutes: value})} value={mode.powerChangeLockTimeMinutes}/></div>
    </article>;
}

function ConditionEditor({condition, label, options, onChange, tr}: {condition: ClusterConditionDto; label: string; options: ClusterConfigDto['options']; onChange: (condition: ClusterConditionDto) => void; tr: (key: string, fallback: string) => string}) {
    return <div className="rounded-xl border border-white/[0.06] bg-[#101014] p-4"><div className="mb-3 flex items-center justify-between"><h3 className="text-sm font-semibold">{label}</h3><select className="rounded-md border border-white/10 bg-[#18181d] px-2 py-1 text-xs" onChange={event => onChange(event.target.value === 'LOGICAL' ? logical() : predicate())} value={condition.type}><option value="PREDICATE">{tr('cluster.condition.predicate', 'Messwert')}</option><option value="LOGICAL">{tr('cluster.condition.logical', 'Logische Gruppe')}</option></select></div>{condition.type === 'LOGICAL' ? <><div className="flex gap-2"><select className={inputClass} onChange={event => onChange({...condition, operator: event.target.value as ClusterConditionDto['operator']})} value={condition.operator ?? 'AND'}>{options.logicalOperators.map(value => <option key={value}>{value}</option>)}</select><button className="shrink-0 rounded-lg border border-white/10 px-3 text-xs text-yellow-300" onClick={() => onChange({...condition, subConditions: [...condition.subConditions, predicate()]})} type="button"><CopyPlus size={15}/></button></div><div className="mt-3 space-y-3">{condition.subConditions.map((child, index) => <div className="relative pl-3" key={index}><ConditionEditor condition={child} label={`${label} ${index + 1}`} onChange={next => onChange({...condition, subConditions: condition.subConditions.map((entry, i) => i === index ? next : entry)})} options={options} tr={tr}/><button className="absolute right-2 top-2 rounded p-1 text-red-300" onClick={() => onChange({...condition, subConditions: condition.subConditions.filter((_, i) => i !== index)})} type="button"><Trash2 size={13}/></button></div>)}</div></> : <div className="grid gap-3 sm:grid-cols-2"><SelectField label={tr('cluster.condition.variable', 'Variable')} onChange={value => onChange({...condition, variable: value})} options={options.variables} value={condition.variable ?? ''}/><SelectField label={tr('cluster.condition.comparator', 'Vergleich')} onChange={value => onChange({...condition, comparator: value})} options={options.comparators} value={condition.comparator ?? ''}/><SelectField label={tr('cluster.condition.aggregation', 'Aggregation')} onChange={value => onChange({...condition, aggregation: value})} options={options.aggregations} value={condition.aggregation ?? ''}/><NumberField label={tr('cluster.condition.window', 'Zeitfenster')} onChange={value => onChange({...condition, windowValue: value})} value={condition.windowValue ?? 1}/><SelectField label={tr('cluster.condition.unit', 'Einheit')} onChange={value => onChange({...condition, windowUnit: value})} options={options.timeUnits} value={condition.windowUnit ?? ''}/><NumberField label={tr('cluster.condition.threshold', 'Schwellwert')} onChange={value => onChange({...condition, threshold: value})} value={condition.threshold ?? 0}/></div>}</div>;
}

function ActionEditor({action: current, options, onChange, onDelete, tr}: {action: ClusterControllerActionDto; options: ClusterConfigDto['options']; onChange: (action: ClusterControllerActionDto) => void; onDelete: () => void; tr: (key: string, fallback: string) => string}) {
    const expr = current.valueExpression;
    return <div className="grid gap-3 rounded-lg border border-white/[0.06] bg-[#17171c] p-3 lg:grid-cols-4"><SelectField label={tr('cluster.action.type', 'Aktion')} onChange={actionType => onChange({...current, actionType})} options={options.actionTypes} value={current.actionType}/><SelectField label={tr('cluster.action.target', 'Ziel')} onChange={targetType => onChange({...current, targetType})} options={options.targetTypes} value={current.targetType}/><SelectField label={tr('cluster.action.strategy', 'Verteilung')} onChange={strategy => onChange({...current, strategy})} options={options.distributionStrategies} value={current.strategy}/><SelectField label={tr('cluster.action.expression', 'Zielwert')} onChange={type => onChange({...current, valueExpression: {...expression(), type: type as ClusterValueExpressionDto['type']}})} options={options.expressionTypes} value={expr.type}/>{expr.type === 'DYNAMIC_VARIABLE' && <><SelectField label={tr('cluster.action.variable', 'Variable')} onChange={variable => onChange({...current, valueExpression: {...expr, variable}})} options={options.variables} value={expr.variable ?? ''}/><NumberField label={tr('cluster.action.multiplier', 'Multiplikator')} onChange={multiplier => onChange({...current, valueExpression: {...expr, multiplier}})} value={expr.multiplier ?? 1}/><NumberField label={tr('cluster.action.offset', 'Offset (W)')} onChange={offset => onChange({...current, valueExpression: {...expr, offset}})} value={expr.offset ?? 0}/></>}{expr.type === 'CONSTANT' && <NumberField label={tr('cluster.action.constant', 'Leistung (W)')} onChange={constantWatts => onChange({...current, valueExpression: {...expr, constantWatts}})} value={expr.constantWatts ?? 0}/>} {expr.type === 'CAPACITY_PERCENTAGE' && <NumberField label={tr('cluster.action.percentage', 'Kapazitätsanteil (0–1)')} onChange={percentage => onChange({...current, valueExpression: {...expr, percentage}})} value={expr.percentage ?? 0}/>}<NumberField label={tr('cluster.action.step', 'Schrittweite (W)')} onChange={stepSizeWatts => onChange({...current, stepSizeWatts})} value={current.stepSizeWatts}/><button className="self-end justify-self-end rounded-lg p-2 text-red-300 hover:bg-red-500/10" onClick={onDelete} type="button"><Trash2 size={16}/></button></div>;
}

function SelectField({label, value, options, onChange}: {label: string; value: string; options: string[]; onChange: (value: string) => void}) { return <label className="text-xs text-[#9c9ca6]">{label}<select className={`${inputClass} mt-1`} onChange={event => onChange(event.target.value)} value={value}>{options.map(option => <option key={option} value={option}>{option}</option>)}</select></label>; }
function NumberField({label, value, onChange}: {label: string; value: number; onChange: (value: number) => void}) { return <label className="text-xs text-[#9c9ca6]">{label}<input className={`${inputClass} mt-1`} onChange={event => onChange(Number(event.target.value))} type="number" value={value}/></label>; }

function SimulationResult({data, chartData, tr}: {data: ClusterSimulationDto; chartData: Array<Record<string, string | number>>; tr: (key: string, fallback: string) => string}) {
    const summary = data.summary;
    return <div className="mt-6 space-y-4"><div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-6">{[
        [tr('cluster.simulator.energy', 'Mining-Energie'), `${summary.simulatedEnergyKwh.toFixed(1)} kWh`], [tr('cluster.simulator.pv_energy', 'davon PV'), `${summary.pvPoweredEnergyKwh.toFixed(1)} kWh`], [tr('cluster.simulator.grid_energy', 'geschätztes Netz'), `${summary.estimatedGridEnergyKwh.toFixed(1)} kWh`], [tr('cluster.simulator.peak', 'Spitzenziel'), `${Math.round(summary.peakTargetWatts)} W`], [tr('cluster.simulator.active', 'Aktiv'), `${summary.activeMinutes} min`], [tr('cluster.simulator.mode', 'Häufigster Modus'), summary.mostActiveMode],
    ].map(([label, value]) => <div className="rounded-xl border border-white/[0.06] bg-black/20 p-3" key={label}><span className="text-xs text-[#92929c]">{label}</span><strong className="mt-1 block text-sm">{value}</strong></div>)}</div><div className="h-[390px] rounded-xl border border-white/[0.06] bg-black/20 p-4"><ResponsiveContainer height="100%" width="100%"><LineChart data={chartData}><CartesianGrid stroke="#292932" strokeDasharray="3 3"/><XAxis dataKey="time" minTickGap={32} stroke="#777783" tick={{fontSize: 11}}/><YAxis stroke="#777783" tick={{fontSize: 11}} unit=" kW"/><Tooltip contentStyle={{background: '#18181d', border: '1px solid #34343d'}} labelStyle={{color: '#fff'}}/><Legend/><Line dataKey="pvPowerKw" dot={false} name={tr('cluster.simulator.chart.pv', 'PV')} stroke="#facc15" strokeWidth={2}/><Line dataKey="loadPowerKw" dot={false} name={tr('cluster.simulator.chart.load', 'Grundlast')} stroke="#60a5fa"/><Line dataKey="targetKw" dot={false} name={tr('cluster.simulator.chart.target', 'Controller-Ziel')} stroke="#c084fc" strokeWidth={2}/><Line dataKey="allocatedKw" dot={false} name={tr('cluster.simulator.chart.allocated', 'Zugewiesen')} stroke="#34d399"/></LineChart></ResponsiveContainer></div><p className="text-xs text-[#858590]">{tr('cluster.simulator.disclaimer', 'Dry-Run: Bedingungen, Moduswechsel und Leistungsziel laufen über dieselbe DSL-Engine. Hardwarebefehle werden nicht gesendet; Sperren und Zuweisung werden geschätzt.')}</p></div>;
}
