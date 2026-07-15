'use client';

import dynamic from 'next/dynamic';
import {useRouter} from 'next/navigation';
import {useCallback, useEffect, useMemo, useState} from 'react';
import {
    ArrowLeft,
    ArrowRight,
    BatteryCharging,
    Check,
    CheckCircle2,
    CircleDollarSign,
    DatabaseZap,
    ExternalLink,
    LoaderCircle,
    MapPin,
    Pickaxe,
    Plus,
    RefreshCw,
    Server,
    Sun,
    Trash2,
    XCircle,
} from 'lucide-react';

import de from '../locales/de.json';
import en from '../locales/en.json';
import AppLogo from '../components/app-logo';
import {useSitePreferences} from '../site/[siteId]/site-preferences-context';

const PanelLocationMap = dynamic(() => import('../components/panel-location-map'), {ssr: false});
const translations = {de, en};
const API_BASE_URL = '/api';

type SelectOption = {value: string; label: string};
type SetupField = {
    key: string;
    label: string;
    helpText: string;
    type: 'TEXT' | 'NUMBER' | 'PASSWORD' | 'SELECT';
    required: boolean;
    defaultValue: string;
    minimum: number | null;
    maximum: number | null;
    options: SelectOption[];
};
type SetupOption = {
    id: string;
    kind: 'PV_SOURCE' | 'MINING_POOL';
    label: string;
    description: string;
    recommended: boolean;
    fields: SetupField[];
};
type SetupCatalog = {
    currentSiteCount: number;
    siteLimit: number;
    limitReached: boolean;
    pvSources: SetupOption[];
    miningPools: SetupOption[];
};
type ProviderValues = Record<string, Record<string, string>>;
type VerificationState = Record<string, {valid: boolean; message: string} | undefined>;

type PanelGroup = {
    id: string;
    name: string;
    latitude: number;
    longitude: number;
    panelCount: number;
    powerPerPanelWatts: number;
    azimuthDegrees: number;
    slopeDegrees: number;
};

const steps = ['basics', 'source', 'panels', 'pools', 'summary'] as const;
const inputClass = 'mt-1.5 w-full rounded-xl border border-white/10 bg-[#101014] px-3.5 py-3 text-sm text-white outline-none transition placeholder:text-[#5f5f68] focus:border-yellow-400/60 focus:ring-2 focus:ring-yellow-400/10 disabled:cursor-not-allowed disabled:opacity-60';

function ProviderFields({option, values, onChange}: {
    option: SetupOption;
    values: Record<string, string>;
    onChange: (key: string, value: string) => void;
}) {
    return (
        <div className="grid gap-4 sm:grid-cols-2">
            {option.fields.map((field) => (
                <label className={`text-sm text-[#c3c3cb] ${field.type === 'TEXT' || field.type === 'PASSWORD' ? 'sm:col-span-2' : ''}`} key={field.key}>
                    {field.label}{field.required ? <span className="ml-1 text-yellow-400">*</span> : null}
                    {field.type === 'SELECT' ? (
                        <select className={inputClass} onChange={(event) => onChange(field.key, event.target.value)} required={field.required} value={values[field.key] ?? ''}>
                            <option value="">—</option>
                            {field.options.map((entry) => <option key={entry.value} value={entry.value}>{entry.label}</option>)}
                        </select>
                    ) : (
                        <input
                            className={inputClass}
                            max={field.maximum ?? undefined}
                            min={field.minimum ?? undefined}
                            onChange={(event) => onChange(field.key, event.target.value)}
                            required={field.required}
                            type={field.type === 'PASSWORD' ? 'password' : field.type === 'NUMBER' ? 'number' : 'text'}
                            value={values[field.key] ?? ''}
                        />
                    )}
                    {field.helpText ? <span className="mt-1.5 block text-xs leading-5 text-[#777781]">{field.helpText}</span> : null}
                </label>
            ))}
        </div>
    );
}

function Verification({state}: {state: {valid: boolean; message: string} | undefined}) {
    if (!state) return null;
    return (
        <div className={`mt-4 flex items-start gap-2 rounded-xl border px-3 py-2.5 text-sm ${state.valid ? 'border-emerald-400/20 bg-emerald-400/[0.07] text-emerald-300' : 'border-red-400/20 bg-red-400/[0.07] text-red-300'}`}>
            {state.valid ? <CheckCircle2 className="mt-0.5 shrink-0" size={16}/> : <XCircle className="mt-0.5 shrink-0" size={16}/>} 
            <span>{state.message}</span>
        </div>
    );
}

export default function SetupPage() {
    const router = useRouter();
    const {locale, setLocale, currency, setCurrency, timeZone} = useSitePreferences();
    const [catalog, setCatalog] = useState<SetupCatalog | null>(null);
    const [loading, setLoading] = useState(true);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [step, setStep] = useState(0);
    const [providerValues, setProviderValues] = useState<ProviderValues>({});
    const [verification, setVerification] = useState<VerificationState>({});
    const [testingProvider, setTestingProvider] = useState<string | null>(null);
    const [selectedSource, setSelectedSource] = useState('');
    const [selectedPools, setSelectedPools] = useState<string[]>([]);
    const [submitting, setSubmitting] = useState(false);
    const [createdSiteId, setCreatedSiteId] = useState<string | null>(null);

    const [basics, setBasics] = useState({
        name: '',
        setupDate: new Date().toLocaleDateString('sv-SE'),
        timeZone: '',
        pvCost: 0,
        electricityPrice: 0,
        feedInTariff: 0,
        batteryCapacityWh: 0,
    });
    const [panels, setPanels] = useState<PanelGroup[]>([]);
    const [panelDraft, setPanelDraft] = useState<Omit<PanelGroup, 'id'>>({
        name: '',
        latitude: 0,
        longitude: 0,
        panelCount: 1,
        powerPerPanelWatts: 400,
        azimuthDegrees: 180,
        slopeDegrees: 30,
    });

    const t = translations[locale] as Record<string, string>;

    const applyCatalog = useCallback((nextCatalog: SetupCatalog) => {
        setCatalog(nextCatalog);
        const options = [...nextCatalog.pvSources, ...nextCatalog.miningPools];
        setProviderValues((current) => {
            const next = {...current};
            for (const option of options) {
                next[option.id] = {...(next[option.id] ?? {})};
                for (const field of option.fields) {
                    if (next[option.id][field.key] === undefined) next[option.id][field.key] = field.defaultValue ?? '';
                }
            }
            return next;
        });
        setSelectedSource((current) => current || nextCatalog.pvSources.find((option) => option.recommended)?.id || nextCatalog.pvSources[0]?.id || '');
    }, []);

    useEffect(() => {
        let cancelled = false;
        fetch(`${API_BASE_URL}/setup/catalog?locale=${locale}`)
            .then((response) => {
                if (!response.ok) throw new Error();
                return response.json() as Promise<SetupCatalog>;
            })
            .then((nextCatalog) => {
                if (!cancelled) applyCatalog(nextCatalog);
            })
            .catch(() => {
                if (!cancelled) setError(t['setup.error.catalog']);
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [applyCatalog, locale, t]);

    const timeZones = useMemo(() => {
        const intl = Intl as typeof Intl & {supportedValuesOf?: (key: 'timeZone') => string[]};
        return intl.supportedValuesOf?.('timeZone') ?? ['Europe/Berlin', 'UTC'];
    }, []);

    const selectedSourceOption = catalog?.pvSources.find((option) => option.id === selectedSource);
    const selectedPoolOptions = catalog?.miningPools.filter((option) => selectedPools.includes(option.id)) ?? [];

    const updateProviderValue = (providerId: string, key: string, value: string) => {
        setProviderValues((current) => ({...current, [providerId]: {...(current[providerId] ?? {}), [key]: value}}));
        setVerification((current) => ({...current, [providerId]: undefined}));
    };

    const providerFieldsComplete = (option: SetupOption) => option.fields.every((field) => !field.required || Boolean(providerValues[option.id]?.[field.key]?.trim()));

    const testProvider = async (option: SetupOption) => {
        setTestingProvider(option.id);
        setVerification((current) => ({...current, [option.id]: undefined}));
        try {
            const response = await fetch(`${API_BASE_URL}/setup/options/${option.kind}/${option.id}/validate`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({values: providerValues[option.id] ?? {}}),
            });
            if (!response.ok) throw new Error();
            const result = await response.json() as {valid: boolean; message: string};
            setVerification((current) => ({...current, [option.id]: {valid: result.valid, message: result.valid ? t['setup.connection.success'] : result.message || t['setup.connection.failed']}}));
        } catch {
            setVerification((current) => ({...current, [option.id]: {valid: false, message: t['setup.connection.failed']}}));
        } finally {
            setTestingProvider(null);
        }
    };

    const refreshProfiles = async () => {
        setRefreshing(true);
        setError(null);
        try {
            const response = await fetch(`${API_BASE_URL}/setup/catalog/refresh?locale=${locale}`, {method: 'POST'});
            if (!response.ok) throw new Error();
            applyCatalog(await response.json() as SetupCatalog);
        } catch {
            setError(t['setup.error.refresh']);
        } finally {
            setRefreshing(false);
        }
    };

    const addPanel = () => {
        if (!panelDraft.name.trim() || panelDraft.panelCount <= 0 || panelDraft.powerPerPanelWatts <= 0 || (panelDraft.latitude === 0 && panelDraft.longitude === 0)) {
            setError(t['setup.error.panel']);
            return;
        }
        setPanels((current) => [...current, {...panelDraft, id: crypto.randomUUID()}]);
        setPanelDraft((current) => ({...current, name: '', panelCount: 1}));
        setError(null);
    };

    const canContinue = () => {
        if (step === 0) return Boolean(basics.name.trim() && basics.setupDate && (basics.timeZone || timeZone) && basics.electricityPrice >= 0);
        if (step === 1) return Boolean(selectedSourceOption && providerFieldsComplete(selectedSourceOption) && verification[selectedSourceOption.id]?.valid);
        if (step === 2) return panels.length > 0;
        if (step === 3) return selectedPoolOptions.every((option) => providerFieldsComplete(option) && verification[option.id]?.valid);
        return true;
    };

    const next = () => {
        if (!canContinue()) {
            setError(t['setup.error.step']);
            return;
        }
        setError(null);
        setStep((current) => Math.min(current + 1, steps.length - 1));
    };

    const submit = async () => {
        if (!selectedSourceOption) return;
        setSubmitting(true);
        setError(null);
        try {
            const response = await fetch(`${API_BASE_URL}/setup`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({
                    ...basics,
                    timeZone: basics.timeZone || timeZone,
                    currency,
                    pvSource: {providerId: selectedSourceOption.id, values: providerValues[selectedSourceOption.id]},
                    panelGroups: panels.map((panel) => ({
                        name: panel.name,
                        latitude: panel.latitude,
                        longitude: panel.longitude,
                        panelCount: panel.panelCount,
                        powerPerPanelWatts: panel.powerPerPanelWatts,
                        azimuthDegrees: panel.azimuthDegrees,
                        slopeDegrees: panel.slopeDegrees,
                    })),
                    miningPools: selectedPoolOptions.map((option) => ({providerId: option.id, values: providerValues[option.id]})),
                }),
            });
            if (!response.ok) throw new Error(await response.text());
            const result = await response.json() as {siteId: string};
            setCreatedSiteId(result.siteId);
        } catch {
            setError(t['setup.error.create']);
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return <main className="grid min-h-screen place-items-center bg-[#0a0a0c] text-white"><div className="flex items-center gap-3 text-[#b8b8c0]"><LoaderCircle className="animate-spin text-yellow-400"/>{t['setup.loading']}</div></main>;
    }

    if (createdSiteId) {
        return (
            <main className="grid min-h-screen place-items-center bg-[#0a0a0c] p-5 text-white">
                <section className="w-full max-w-xl rounded-3xl border border-emerald-400/20 bg-[#151518] p-8 text-center shadow-2xl">
                    <div className="mx-auto grid h-16 w-16 place-items-center rounded-2xl bg-emerald-400/10 text-emerald-300"><CheckCircle2 size={34}/></div>
                    <h1 className="mt-6 text-2xl font-bold">{t['setup.complete.title']}</h1>
                    <p className="mt-3 text-sm leading-6 text-[#9898a2]">{t['setup.complete.description']}</p>
                    <div className="mt-7 grid gap-3 sm:grid-cols-2">
                        <button className="rounded-xl bg-yellow-400 px-4 py-3 font-semibold text-black transition hover:bg-yellow-300" onClick={() => router.push(`/site/${createdSiteId}/dashboard`)}>{t['setup.complete.dashboard']}</button>
                        <button className="inline-flex items-center justify-center gap-2 rounded-xl border border-white/10 px-4 py-3 font-semibold text-white transition hover:bg-white/[0.05]" onClick={() => router.push(`/site/${createdSiteId}/mining`)}>{t['setup.complete.miners']}<ExternalLink size={16}/></button>
                    </div>
                </section>
            </main>
        );
    }

    if (!catalog || catalog.limitReached) {
        return (
            <main className="grid min-h-screen place-items-center bg-[#0a0a0c] p-5 text-white">
                <section className="w-full max-w-lg rounded-3xl border border-yellow-400/20 bg-[#151518] p-8 text-center">
                    <Sun className="mx-auto text-yellow-400" size={42}/><h1 className="mt-5 text-2xl font-bold">{catalog?.limitReached ? t['setup.limit.title'] : t['setup.error.catalog']}</h1>
                    <button className="mt-6 rounded-xl bg-white/10 px-4 py-2.5" onClick={() => router.push('/')}>{t['setup.action.back_home']}</button>
                </section>
            </main>
        );
    }

    return (
        <main className="min-h-screen bg-[#0a0a0c] px-4 py-6 text-white sm:px-6 lg:px-8">
            <div className="mx-auto max-w-6xl">
                <header className="mb-6 flex flex-wrap items-center justify-between gap-4">
                    <div className="flex items-center gap-4">
                        <button aria-label={t['setup.action.back_home']} className="grid h-10 w-10 place-items-center rounded-xl border border-white/10 text-[#aaaab4] transition hover:bg-white/[0.05] hover:text-white" onClick={() => router.push('/')}><ArrowLeft size={19}/></button>
                        <AppLogo/>
                        <div><p className="text-xs font-semibold uppercase tracking-[0.2em] text-yellow-400">{t['setup.eyebrow']}</p><h1 className="mt-1 text-2xl font-bold">{t['setup.title']}</h1><p className="mt-1 text-sm text-[#8e8e98]">{t['setup.subtitle']}</p></div>
                    </div>
                    <div className="flex items-center gap-2">
                        <select className="rounded-lg border border-white/10 bg-[#151518] px-3 py-2 text-sm" onChange={(event) => setLocale(event.target.value as 'de' | 'en')} value={locale}><option value="de">Deutsch</option><option value="en">English</option></select>
                        <span className="rounded-lg bg-white/[0.04] px-3 py-2 text-xs text-[#8e8e98]">{catalog.currentSiteCount}/{catalog.siteLimit}</span>
                    </div>
                </header>

                <div className="mb-6 overflow-x-auto rounded-2xl border border-white/[0.07] bg-[#131316] p-3">
                    <ol className="flex min-w-[680px] items-center">
                        {steps.map((name, index) => (
                            <li className="flex flex-1 items-center" key={name}>
                                <div className="flex items-center gap-2.5">
                                    <span className={`grid h-8 w-8 place-items-center rounded-full text-xs font-bold ${index < step ? 'bg-emerald-400 text-black' : index === step ? 'bg-yellow-400 text-black ring-4 ring-yellow-400/10' : 'bg-white/[0.06] text-[#777781]'}`}>{index < step ? <Check size={15}/> : index + 1}</span>
                                    <span className={`text-sm font-medium ${index === step ? 'text-white' : 'text-[#7f7f89]'}`}>{t[`setup.step.${name}`]}</span>
                                </div>
                                {index < steps.length - 1 ? <span className={`mx-4 h-px flex-1 ${index < step ? 'bg-emerald-400/40' : 'bg-white/[0.07]'}`}/> : null}
                            </li>
                        ))}
                    </ol>
                </div>

                <section className="overflow-hidden rounded-3xl border border-white/[0.08] bg-[#151518] shadow-2xl">
                    <div className="border-b border-white/[0.07] px-5 py-5 sm:px-7"><h2 className="text-xl font-semibold">{t[`setup.step.${steps[step]}.title`]}</h2><p className="mt-1.5 text-sm leading-6 text-[#8f8f99]">{t[`setup.step.${steps[step]}.description`]}</p></div>
                    <div className="min-h-[480px] p-5 sm:p-7">
                        {step === 0 ? (
                            <div className="grid gap-5 lg:grid-cols-2">
                                <label className="text-sm text-[#c3c3cb] lg:col-span-2">{t['setup.basics.name']}<input autoFocus className={inputClass} maxLength={120} onChange={(event) => setBasics({...basics, name: event.target.value})} required value={basics.name}/></label>
                                <label className="text-sm text-[#c3c3cb]">{t['setup.basics.date']}<input className={inputClass} onChange={(event) => setBasics({...basics, setupDate: event.target.value})} type="date" value={basics.setupDate}/></label>
                                <label className="text-sm text-[#c3c3cb]">{t['setup.basics.timezone']}<select className={inputClass} onChange={(event) => setBasics({...basics, timeZone: event.target.value})} value={basics.timeZone || timeZone}>{timeZones.map((zone) => <option key={zone}>{zone}</option>)}</select></label>
                                <label className="text-sm text-[#c3c3cb]">{t['setup.basics.currency']}<select className={inputClass} onChange={(event) => setCurrency(event.target.value as 'EUR' | 'USD' | 'CHF')} value={currency}><option>EUR</option><option>USD</option><option>CHF</option></select></label>
                                <label className="text-sm text-[#c3c3cb]">{t['setup.basics.pv_cost']} ({currency})<input className={inputClass} min="0" onChange={(event) => setBasics({...basics, pvCost: Number(event.target.value)})} step="0.01" type="number" value={basics.pvCost}/></label>
                                <label className="text-sm text-[#c3c3cb]">{t['setup.basics.electricity']} ({currency}/kWh)<input className={inputClass} min="0" onChange={(event) => setBasics({...basics, electricityPrice: Number(event.target.value)})} required step="0.0001" type="number" value={basics.electricityPrice}/></label>
                                <label className="text-sm text-[#c3c3cb]">{t['setup.basics.feed_in']} ({currency}/kWh)<input className={inputClass} min="0" onChange={(event) => setBasics({...basics, feedInTariff: Number(event.target.value)})} step="0.0001" type="number" value={basics.feedInTariff}/></label>
                                <label className="text-sm text-[#c3c3cb] lg:col-span-2"><span className="inline-flex items-center gap-2"><BatteryCharging className="text-cyan-300" size={16}/>{t['setup.basics.battery']}</span><input className={inputClass} min="0" onChange={(event) => setBasics({...basics, batteryCapacityWh: Number(event.target.value)})} step="1" type="number" value={basics.batteryCapacityWh}/></label>
                            </div>
                        ) : null}

                        {step === 1 ? (
                            <div>
                                <div className="mb-5 flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-[#8e8e98]">{t['setup.source.backend_hint']}</p><button className="inline-flex items-center gap-2 rounded-xl border border-white/10 px-3 py-2 text-sm text-[#b9b9c2] transition hover:bg-white/[0.05]" disabled={refreshing} onClick={() => void refreshProfiles()}><RefreshCw className={refreshing ? 'animate-spin' : ''} size={15}/>{t['setup.source.refresh']}</button></div>
                                <div className="grid gap-3 md:grid-cols-2">{catalog.pvSources.map((option) => <button className={`rounded-2xl border p-4 text-left transition ${selectedSource === option.id ? 'border-yellow-400/50 bg-yellow-400/[0.06]' : 'border-white/[0.08] bg-[#101014] hover:border-white/20'}`} key={option.id} onClick={() => {setSelectedSource(option.id); setError(null);}}><div className="flex items-start justify-between gap-3"><Server className={selectedSource === option.id ? 'text-yellow-300' : 'text-[#777781]'} size={22}/>{option.recommended ? <span className="rounded-full bg-yellow-400/10 px-2 py-1 text-[10px] font-semibold uppercase tracking-wider text-yellow-300">{t['setup.recommended']}</span> : null}</div><h3 className="mt-4 font-semibold">{option.label}</h3><p className="mt-1.5 text-sm leading-5 text-[#85858f]">{option.description}</p></button>)}</div>
                                {selectedSourceOption ? <div className="mt-5 rounded-2xl border border-white/[0.08] bg-[#101014] p-5"><ProviderFields onChange={(key, value) => updateProviderValue(selectedSourceOption.id, key, value)} option={selectedSourceOption} values={providerValues[selectedSourceOption.id] ?? {}}/><button className="mt-5 inline-flex items-center gap-2 rounded-xl bg-violet-400/10 px-4 py-2.5 text-sm font-semibold text-violet-200 transition hover:bg-violet-400/20 disabled:opacity-50" disabled={!providerFieldsComplete(selectedSourceOption) || testingProvider === selectedSourceOption.id} onClick={() => void testProvider(selectedSourceOption)}>{testingProvider === selectedSourceOption.id ? <LoaderCircle className="animate-spin" size={16}/> : <DatabaseZap size={16}/>} {t['setup.connection.test']}</button><Verification state={verification[selectedSourceOption.id]}/></div> : null}
                            </div>
                        ) : null}

                        {step === 2 ? (
                            <div className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
                                <div><h3 className="mb-3 font-semibold">{t['setup.panels.created']}</h3>{panels.length === 0 ? <div className="rounded-2xl border border-dashed border-white/10 px-5 py-10 text-center text-sm text-[#777781]">{t['setup.panels.empty']}</div> : <div className="space-y-3">{panels.map((panel) => <article className="flex items-center justify-between gap-4 rounded-2xl border border-white/[0.07] bg-[#101014] p-4" key={panel.id}><div><p className="font-semibold">{panel.name}</p><p className="mt-1 text-xs text-[#85858f]">{panel.panelCount} × {panel.powerPerPanelWatts} W · {(panel.panelCount * panel.powerPerPanelWatts / 1000).toFixed(2)} kWp</p><p className="mt-1 flex items-center gap-1 text-xs text-[#686872]"><MapPin size={12}/>{panel.latitude.toFixed(5)}, {panel.longitude.toFixed(5)}</p></div><button aria-label={t['setup.panels.remove']} className="grid h-9 w-9 place-items-center rounded-lg text-[#777781] transition hover:bg-red-400/10 hover:text-red-300" onClick={() => setPanels((current) => current.filter((entry) => entry.id !== panel.id))}><Trash2 size={16}/></button></article>)}</div>}</div>
                                <div className="rounded-2xl border border-white/[0.08] bg-[#101014] p-5"><h3 className="font-semibold">{t['setup.panels.new']}</h3><div className="mt-4 grid gap-4 sm:grid-cols-2"><label className="text-sm text-[#c3c3cb] sm:col-span-2">{t['setup.panels.name']}<input className={inputClass} onChange={(event) => setPanelDraft({...panelDraft, name: event.target.value})} value={panelDraft.name}/></label><label className="text-sm text-[#c3c3cb]">{t['setup.panels.count']}<input className={inputClass} min="1" onChange={(event) => setPanelDraft({...panelDraft, panelCount: Number(event.target.value)})} type="number" value={panelDraft.panelCount}/></label><label className="text-sm text-[#c3c3cb]">{t['setup.panels.power']} (W)<input className={inputClass} min="1" onChange={(event) => setPanelDraft({...panelDraft, powerPerPanelWatts: Number(event.target.value)})} type="number" value={panelDraft.powerPerPanelWatts}/></label><label className="text-sm text-[#c3c3cb]">{t['setup.panels.azimuth']}<input className={inputClass} max="360" min="0" onChange={(event) => setPanelDraft({...panelDraft, azimuthDegrees: Number(event.target.value)})} type="number" value={panelDraft.azimuthDegrees}/></label><label className="text-sm text-[#c3c3cb]">{t['setup.panels.slope']}<input className={inputClass} max="90" min="0" onChange={(event) => setPanelDraft({...panelDraft, slopeDegrees: Number(event.target.value)})} type="number" value={panelDraft.slopeDegrees}/></label><div className="sm:col-span-2"><PanelLocationMap labels={{select: t['details.panels.map.select'], move: t['details.panels.map.move'], selected: t['details.panels.map.selected'], useLocation: t['details.panels.map.use_location'], locationError: t['details.panels.map.location_error']}} onChange={(location) => setPanelDraft({...panelDraft, ...location})} value={panelDraft}/></div></div><button className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-400 px-4 py-3 font-semibold text-black transition hover:bg-emerald-300" onClick={addPanel}><Plus size={17}/>{t['setup.panels.add']}</button></div>
                            </div>
                        ) : null}

                        {step === 3 ? (
                            <div><div className="mb-5 rounded-xl border border-cyan-400/15 bg-cyan-400/[0.05] px-4 py-3 text-sm leading-6 text-cyan-100">{t['setup.pools.optional_hint']}</div><div className="grid gap-4 lg:grid-cols-2">{catalog.miningPools.map((option) => {const selected = selectedPools.includes(option.id); return <article className={`rounded-2xl border p-5 transition ${selected ? 'border-yellow-400/40 bg-yellow-400/[0.04]' : 'border-white/[0.08] bg-[#101014]'}`} key={option.id}><button className="w-full text-left" onClick={() => {setSelectedPools((current) => selected ? current.filter((id) => id !== option.id) : [...current, option.id]); setError(null);}}><div className="flex items-start justify-between"><Pickaxe className={selected ? 'text-yellow-300' : 'text-[#777781]'} size={22}/><span className={`grid h-6 w-6 place-items-center rounded-md border ${selected ? 'border-yellow-400 bg-yellow-400 text-black' : 'border-white/15'}`}>{selected ? <Check size={14}/> : null}</span></div><h3 className="mt-4 font-semibold">{option.label}</h3><p className="mt-1.5 text-sm leading-5 text-[#85858f]">{option.description}</p></button>{selected ? <div className="mt-5 border-t border-white/[0.07] pt-5"><ProviderFields onChange={(key, value) => updateProviderValue(option.id, key, value)} option={option} values={providerValues[option.id] ?? {}}/><button className="mt-5 inline-flex items-center gap-2 rounded-xl bg-violet-400/10 px-4 py-2.5 text-sm font-semibold text-violet-200 transition hover:bg-violet-400/20 disabled:opacity-50" disabled={!providerFieldsComplete(option) || testingProvider === option.id} onClick={() => void testProvider(option)}>{testingProvider === option.id ? <LoaderCircle className="animate-spin" size={16}/> : <DatabaseZap size={16}/>} {t['setup.connection.test']}</button><Verification state={verification[option.id]}/></div> : null}</article>;})}</div></div>
                        ) : null}

                        {step === 4 ? (
                            <div className="grid gap-4 md:grid-cols-2"><div className="rounded-2xl border border-white/[0.08] bg-[#101014] p-5"><CircleDollarSign className="text-emerald-300" size={21}/><h3 className="mt-3 font-semibold">{basics.name}</h3><p className="mt-2 text-sm text-[#85858f]">{basics.setupDate} · {basics.timeZone || timeZone}</p><p className="mt-1 text-sm text-[#85858f]">{t['setup.basics.electricity']}: {basics.electricityPrice} {currency}/kWh</p></div><div className="rounded-2xl border border-white/[0.08] bg-[#101014] p-5"><Server className="text-violet-300" size={21}/><h3 className="mt-3 font-semibold">{selectedSourceOption?.label}</h3><p className="mt-2 text-sm text-[#85858f]">{providerValues[selectedSource]?.host}</p></div><div className="rounded-2xl border border-white/[0.08] bg-[#101014] p-5"><Sun className="text-yellow-300" size={21}/><h3 className="mt-3 font-semibold">{panels.length} {t['setup.summary.panel_groups']}</h3><p className="mt-2 text-sm text-[#85858f]">{panels.reduce((sum, panel) => sum + panel.panelCount, 0)} {t['setup.summary.panels']} · {(panels.reduce((sum, panel) => sum + panel.panelCount * panel.powerPerPanelWatts, 0) / 1000).toFixed(2)} kWp</p></div><div className="rounded-2xl border border-white/[0.08] bg-[#101014] p-5"><Pickaxe className="text-cyan-300" size={21}/><h3 className="mt-3 font-semibold">{selectedPoolOptions.length ? selectedPoolOptions.map((pool) => pool.label).join(', ') : t['setup.summary.no_pool']}</h3><p className="mt-2 text-sm text-[#85858f]">{t['setup.summary.miners_later']}</p></div></div>
                        ) : null}
                    </div>

                    {error ? <div className="mx-5 mb-4 rounded-xl border border-red-400/20 bg-red-400/[0.07] px-4 py-3 text-sm text-red-300 sm:mx-7">{error}</div> : null}
                    <footer className="flex items-center justify-between gap-3 border-t border-white/[0.07] px-5 py-4 sm:px-7">
                        <button className="inline-flex items-center gap-2 rounded-xl border border-white/10 px-4 py-2.5 text-sm font-medium text-[#b8b8c1] transition hover:bg-white/[0.05] disabled:invisible" disabled={step === 0 || submitting} onClick={() => {setStep((current) => Math.max(0, current - 1)); setError(null);}}><ArrowLeft size={16}/>{t['setup.action.previous']}</button>
                        {step < steps.length - 1 ? <button className="inline-flex items-center gap-2 rounded-xl bg-yellow-400 px-5 py-2.5 text-sm font-semibold text-black transition hover:bg-yellow-300" onClick={next}>{t['setup.action.next']}<ArrowRight size={16}/></button> : <button className="inline-flex items-center gap-2 rounded-xl bg-emerald-400 px-5 py-2.5 text-sm font-semibold text-black transition hover:bg-emerald-300 disabled:cursor-wait disabled:opacity-60" disabled={submitting} onClick={() => void submit()}>{submitting ? <LoaderCircle className="animate-spin" size={16}/> : <Check size={16}/>} {t['setup.action.create']}</button>}
                    </footer>
                </section>
            </div>
        </main>
    );
}
