'use client';

import Link from 'next/link';
import {ChangeEvent, useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {
    ArrowLeft,
    Check,
    CheckCircle2,
    ChevronDown,
    CircleAlert,
    CloudDownload,
    Download,
    FileJson,
    Gauge,
    LoaderCircle,
    Network,
    Plus,
    Radio,
    RotateCw,
    Save,
    Search,
    ServerCog,
    Trash2,
    Upload,
    Wifi,
    XCircle,
} from 'lucide-react';

import de from '../../locales/de.json';
import en from '../../locales/en.json';
import {useSitePreferences} from '../../site/[siteId]/site-preferences-context';

const translations = {de, en};
const API_BASE_URL = 'http://localhost:8080/api/config/pv';
const MAX_IMPORT_BYTES = 1_000_000;
const AUTOSAVE_DELAY_MS = 650;
const LIVE_APPLY_DELAY_MS = 850;
const inputClass = 'w-full rounded-lg border border-white/10 bg-[#0e0e12] px-3 py-2 text-sm text-white outline-none transition placeholder:text-[#5f5f68] focus:border-yellow-400/60 focus:ring-2 focus:ring-yellow-400/10 disabled:opacity-50';
const compactInputClass = 'w-full min-w-[100px] rounded-md border border-white/10 bg-[#0d0d10] px-2.5 py-1.5 text-sm text-white outline-none focus:border-yellow-400/50';

type Protocol = 'rest' | 'modbus';
type Template = {id: string; name: string; fields: Array<{field: string; unit: string}>};
type OperationType = {value: string; label: string};
type Catalog = {
    templates: Template[];
    localProfiles: string[];
    communityProfiles: string[];
    httpMethods?: string[];
    responseTypes?: string[];
    parameterTypes: string[];
    operationTypes?: OperationType[];
    byteOrders?: string[];
};
type RestField = {
    field: string;
    unit: string;
    urlExtension: string;
    httpMethod: string;
    responseType: string;
    dataPath: string;
    scaleFactor: number;
    formula: string;
    parameterType: string;
};
type ModbusField = {
    field: string;
    unit: string;
    startAddress: number;
    size: number;
    scaleFactor: number;
    formula: string;
    parameterType: string;
    operationType: string;
    byteOrder: string;
};
type Fingerprint = {
    address: number | null;
    size: number;
    parameterType: string;
    operationType: string;
    byteOrder: string;
    expectedValue: string;
};
type Profile = {
    name: string;
    templateId: string;
    fingerprint?: Fingerprint | null;
    fields: Array<RestField | ModbusField>;
};
type FieldTest = {value: number | null; textValue: string | null; errorCode: string | null};
type ConnectionTest = {
    connected: boolean;
    fingerprintMatches: boolean;
    message: string;
    fields: Record<string, FieldTest>;
};
type SaveState = 'saved' | 'dirty' | 'saving' | 'error';
type LiveState = 'idle' | 'waiting' | 'testing' | 'complete' | 'error';

function replace(template: string, values: Record<string, string | number>) {
    return Object.entries(values).reduce((result, [key, value]) => result.replaceAll(`{${key}}`, String(value)), template);
}

function protocolPath(protocol: Protocol) {
    return protocol === 'rest' ? 'rest' : 'modbus/tcp';
}

function isRestField(field: RestField | ModbusField): field is RestField {
    return 'urlExtension' in field;
}

async function errorMessage(response: Response, fallback: string) {
    try {
        const body = await response.json() as {detail?: string; message?: string};
        return body.detail || body.message || fallback;
    } catch {
        return fallback;
    }
}

export default function ConfigEditor({protocol}: {protocol: Protocol}) {
    const {locale, setLocale} = useSitePreferences();
    const t = translations[locale] as Record<string, string>;
    const apiPath = `${API_BASE_URL}/${protocolPath(protocol)}`;
    const importRef = useRef<HTMLInputElement>(null);
    const lastSavedProfileRef = useRef<string | null>(null);
    const currentProfileRef = useRef<Profile | null>(null);
    const saveQueueRef = useRef<Promise<void>>(Promise.resolve());

    const [catalog, setCatalog] = useState<Catalog | null>(null);
    const [templateId, setTemplateId] = useState('');
    const [selectedName, setSelectedName] = useState('');
    const [profile, setProfile] = useState<Profile | null>(null);
    const [appliedProfile, setAppliedProfile] = useState<Profile | null>(null);
    const [newName, setNewName] = useState('');
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [testResult, setTestResult] = useState<ConnectionTest | null>(null);
    const [saveState, setSaveState] = useState<SaveState>('saved');
    const [liveState, setLiveState] = useState<LiveState>('idle');
    const [connectionRevision, setConnectionRevision] = useState(0);
    const [fieldQuery, setFieldQuery] = useState('');
    const [expandedField, setExpandedField] = useState<string | null>(null);
    const [restTarget, setRestTarget] = useState({baseUrl: 'http://192.168.178.50:8123', apiToken: ''});
    const [modbusTarget, setModbusTarget] = useState({host: '192.168.178.50', port: 502, slaveId: 1});

    const selectedTemplate = useMemo(
        () => catalog?.templates.find((template) => template.id === templateId) ?? catalog?.templates[0],
        [catalog, templateId],
    );

    useEffect(() => {
        currentProfileRef.current = profile;
    }, [profile]);

    const refreshCatalog = useCallback(async (preferredName?: string) => {
        const response = await fetch(`${apiPath}/catalog`, {cache: 'no-store'});
        if (!response.ok) throw new Error(await errorMessage(response, t['config.error.load']));
        const nextCatalog = await response.json() as Catalog;
        setCatalog(nextCatalog);
        setTemplateId((current) => nextCatalog.templates.some((template) => template.id === current)
            ? current
            : (nextCatalog.templates[0]?.id ?? ''));
        setSelectedName((current) => {
            if (preferredName && nextCatalog.localProfiles.includes(preferredName)) return preferredName;
            if (current && nextCatalog.localProfiles.includes(current)) return current;
            return nextCatalog.localProfiles[0] ?? '';
        });
    }, [apiPath, t]);

    useEffect(() => {
        let cancelled = false;
        const timeout = window.setTimeout(() => {
            void refreshCatalog()
                .catch((reason) => !cancelled && setError(reason instanceof Error ? reason.message : t['config.error.load']))
                .finally(() => !cancelled && setLoading(false));
        }, 0);
        return () => {
            cancelled = true;
            window.clearTimeout(timeout);
        };
    }, [refreshCatalog, t]);

    useEffect(() => {
        if (!selectedName || !templateId) return;
        let cancelled = false;
        const timeout = window.setTimeout(() => {
            setBusy('load');
            void fetch(`${apiPath}/profiles/${encodeURIComponent(selectedName)}?templateId=${encodeURIComponent(templateId)}`, {cache: 'no-store'})
                .then(async (response) => {
                    if (!response.ok) throw new Error(await errorMessage(response, t['config.error.open']));
                    return response.json() as Promise<Profile>;
                })
                .then((nextProfile) => {
                    if (!cancelled) {
                        lastSavedProfileRef.current = JSON.stringify(nextProfile);
                        currentProfileRef.current = nextProfile;
                        setProfile(nextProfile);
                        setAppliedProfile(nextProfile);
                        setSaveState('saved');
                        setLiveState('waiting');
                        setExpandedField(nextProfile.fields[0]?.field ?? null);
                        setFieldQuery('');
                        setTestResult(null);
                        setError(null);
                    }
                })
                .catch((reason) => !cancelled && setError(reason instanceof Error ? reason.message : t['config.error.open']))
                .finally(() => !cancelled && setBusy(null));
        }, 0);
        return () => {
            cancelled = true;
            window.clearTimeout(timeout);
        };
    }, [apiPath, selectedName, t, templateId]);

    useEffect(() => {
        if (!profile) return;
        const snapshot = JSON.stringify(profile);
        if (snapshot === lastSavedProfileRef.current) {
            setSaveState('saved');
            return;
        }

        setSaveState('dirty');
        const profileToSave = profile;
        const timeout = window.setTimeout(() => {
            saveQueueRef.current = saveQueueRef.current
                .catch(() => undefined)
                .then(async () => {
                    if (JSON.stringify(currentProfileRef.current) !== snapshot) return;
                    setSaveState('saving');
                    setError(null);
                    const response = await fetch(`${apiPath}/profiles/${encodeURIComponent(profileToSave.name)}`, {
                        method: 'PUT',
                        headers: {'Content-Type': 'application/json'},
                        body: snapshot,
                    });
                    if (!response.ok) throw new Error(await errorMessage(response, t['config.error.save']));
                    const savedProfile = await response.json() as Profile;
                    lastSavedProfileRef.current = JSON.stringify(savedProfile);
                    setAppliedProfile(savedProfile);
                    setLiveState('waiting');

                    if (JSON.stringify(currentProfileRef.current) === snapshot) {
                        currentProfileRef.current = savedProfile;
                        setProfile(savedProfile);
                        setSaveState('saved');
                    } else {
                        setSaveState('dirty');
                    }
                })
                .catch((reason) => {
                    if (JSON.stringify(currentProfileRef.current) === snapshot) {
                        setSaveState('error');
                        setError(reason instanceof Error ? reason.message : t['config.error.save']);
                    }
                });
        }, AUTOSAVE_DELAY_MS);

        return () => window.clearTimeout(timeout);
    }, [apiPath, profile, t]);

    useEffect(() => {
        if (!appliedProfile) return;

        const controller = new AbortController();
        const timeout = window.setTimeout(() => {
            setLiveState('testing');
            setError(null);
            const body = protocol === 'rest'
                ? {...restTarget, fields: appliedProfile.fields}
                : {...modbusTarget, fingerprint: appliedProfile.fingerprint, fields: appliedProfile.fields};
            void fetch(`${apiPath}/test`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(body),
                signal: controller.signal,
            })
                .then(async (response) => {
                    if (!response.ok) throw new Error(await errorMessage(response, t['config.error.test']));
                    return response.json() as Promise<ConnectionTest>;
                })
                .then((result) => {
                    setTestResult(result);
                    setLiveState('complete');
                })
                .catch((reason) => {
                    if (reason instanceof DOMException && reason.name === 'AbortError') return;
                    setTestResult(null);
                    setLiveState('error');
                    setError(reason instanceof Error ? reason.message : t['config.error.test']);
                });
        }, LIVE_APPLY_DELAY_MS);

        return () => {
            window.clearTimeout(timeout);
            controller.abort();
        };
    }, [apiPath, appliedProfile, connectionRevision, modbusTarget, protocol, restTarget, t]);

    const run = async (key: string, action: () => Promise<void>) => {
        setBusy(key);
        setError(null);
        setNotice(null);
        try {
            await action();
        } catch (reason) {
            setError(reason instanceof Error ? reason.message : t['config.error.general']);
        } finally {
            setBusy(null);
        }
    };

    const createProfile = () => run('create', async () => {
        const name = newName.trim();
        if (!name || !templateId) throw new Error(t['config.error.name']);
        const response = await fetch(`${apiPath}/profiles`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({name, templateId}),
        });
        if (!response.ok) throw new Error(await errorMessage(response, t['config.error.create']));
        const created = await response.json() as Profile;
        lastSavedProfileRef.current = JSON.stringify(created);
        currentProfileRef.current = created;
        setProfile(created);
        setAppliedProfile(created);
        setSaveState('saved');
        setLiveState('waiting');
        setExpandedField(created.fields[0]?.field ?? null);
        setNewName('');
        await refreshCatalog(created.name);
        setNotice(t['config.notice.created']);
    });

    const deleteProfile = () => {
        if (!profile || !window.confirm(replace(t['config.delete.confirm'], {name: profile.name}))) return Promise.resolve();
        return run('delete', async () => {
            const response = await fetch(`${apiPath}/profiles/${encodeURIComponent(profile.name)}?templateId=${encodeURIComponent(profile.templateId)}`, {method: 'DELETE'});
            if (!response.ok) throw new Error(await errorMessage(response, t['config.error.delete']));
            lastSavedProfileRef.current = null;
            currentProfileRef.current = null;
            setProfile(null);
            setAppliedProfile(null);
            setLiveState('idle');
            setSelectedName('');
            await refreshCatalog();
            setNotice(t['config.notice.deleted']);
        });
    };

    const exportProfile = () => {
        if (!profile) return Promise.resolve();
        return run('export', async () => {
            const response = await fetch(`${apiPath}/profiles/${encodeURIComponent(profile.name)}/export?templateId=${encodeURIComponent(profile.templateId)}`);
            if (!response.ok) throw new Error(await errorMessage(response, t['config.error.export']));
            const blobUrl = URL.createObjectURL(await response.blob());
            const anchor = document.createElement('a');
            anchor.href = blobUrl;
            anchor.download = `${profile.name}.json`;
            document.body.appendChild(anchor);
            anchor.click();
            anchor.remove();
            window.setTimeout(() => URL.revokeObjectURL(blobUrl), 0);
        });
    };

    const importProfile = (event: ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        event.target.value = '';
        if (!file) return;
        void run('import', async () => {
            if (file.size > MAX_IMPORT_BYTES) throw new Error(t['config.error.import_size']);
            const name = file.name.replace(/\.json$/i, '').trim();
            if (!name || !templateId) throw new Error(t['config.error.name']);
            const response = await fetch(`${apiPath}/profiles/import?name=${encodeURIComponent(name)}&templateId=${encodeURIComponent(templateId)}`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: await file.text(),
            });
            if (!response.ok) throw new Error(await errorMessage(response, t['config.error.import']));
            const imported = await response.json() as Profile;
            lastSavedProfileRef.current = JSON.stringify(imported);
            currentProfileRef.current = imported;
            setProfile(imported);
            setAppliedProfile(imported);
            setSaveState('saved');
            setLiveState('waiting');
            setExpandedField(imported.fields[0]?.field ?? null);
            await refreshCatalog(imported.name);
            setNotice(t['config.notice.imported']);
        });
    };

    const downloadCommunity = (name: string) => run(`community:${name}`, async () => {
        const response = await fetch(`${apiPath}/community/${encodeURIComponent(name)}?templateId=${encodeURIComponent(templateId)}`, {method: 'POST'});
        if (!response.ok) throw new Error(await errorMessage(response, t['config.error.community']));
        const downloaded = await response.json() as Profile;
        lastSavedProfileRef.current = JSON.stringify(downloaded);
        currentProfileRef.current = downloaded;
        setProfile(downloaded);
        setAppliedProfile(downloaded);
        setSaveState('saved');
        setLiveState('waiting');
        setExpandedField(downloaded.fields[0]?.field ?? null);
        await refreshCatalog(downloaded.name);
        setNotice(t['config.notice.community']);
    });

    const updateField = (fieldName: string, patch: Record<string, string | number>) => {
        setProfile((current) => current ? {
            ...current,
            fields: current.fields.map((field) => field.field === fieldName ? {...field, ...patch} as RestField | ModbusField : field),
        } : current);
        setTestResult(null);
        setLiveState('waiting');
        setNotice(null);
    };

    const updateFingerprint = (patch: Partial<Fingerprint>) => {
        if (!catalog) return;
        setProfile((current) => {
            if (!current) return current;
            const defaults: Fingerprint = {
                address: null,
                size: 1,
                parameterType: catalog.parameterTypes[0] ?? 'int32',
                operationType: catalog.operationTypes?.[0]?.value ?? 'READ_HOLDING_REGISTER',
                byteOrder: catalog.byteOrders?.[0] ?? 'BIG_ENDIAN',
                expectedValue: '',
            };
            return {...current, fingerprint: {...defaults, ...current.fingerprint, ...patch}};
        });
        setTestResult(null);
        setLiveState('waiting');
        setNotice(null);
    };

    const fieldLabel = (field: RestField | ModbusField) => t[`config.field.${field.field}`] ?? field.field;
    const liveValue = (field: RestField | ModbusField) => {
        const result = testResult?.fields[field.field];
        if (!result) return '—';
        if (result.errorCode) return t['config.test.read_failed'];
        if (result.value != null) return `${new Intl.NumberFormat(locale === 'de' ? 'de-DE' : 'en-US', {maximumFractionDigits: 4}).format(result.value)} ${field.unit}`;
        return result.textValue ?? '—';
    };
    const filteredFields = useMemo(() => {
        if (!profile) return [];
        const query = fieldQuery.trim().toLocaleLowerCase(locale);
        if (!query) return profile.fields;
        return profile.fields.filter((field) => {
            const label = t[`config.field.${field.field}`] ?? field.field;
            const connection = isRestField(field) ? `${field.urlExtension} ${field.dataPath}` : `${field.startAddress}`;
            return `${label} ${field.field} ${field.unit} ${connection}`.toLocaleLowerCase(locale).includes(query);
        });
    }, [fieldQuery, locale, profile, t]);
    const saveStatusClass = saveState === 'error'
        ? 'bg-red-400/10 text-red-300'
        : saveState === 'saved'
            ? 'bg-emerald-400/10 text-emerald-300'
            : 'bg-yellow-400/10 text-yellow-200';
    const liveStatusText = liveState === 'waiting' || liveState === 'testing'
        ? t['config.test.updating']
        : testResult
            ? t[testResult.connected ? 'config.test.connected' : 'config.test.failed']
            : t['config.test.auto'];

    if (loading && !catalog) {
        return <main className="grid min-h-screen place-items-center bg-[#09090b] text-[#b9b9c2]"><span className="inline-flex items-center gap-3"><LoaderCircle className="animate-spin text-yellow-400"/>{t['config.loading']}</span></main>;
    }

    return (
        <main className="min-h-screen bg-[#09090b] text-white">
            <header className="border-b border-white/[0.07] bg-[#0d0d10]/95 px-4 py-4 backdrop-blur sm:px-6">
                <div className="mx-auto flex max-w-[1700px] flex-wrap items-center justify-between gap-4">
                    <div className="flex items-center gap-3">
                        <Link aria-label={t['config.back']} className="grid h-10 w-10 place-items-center rounded-xl border border-white/10 text-[#aaaab4] transition hover:bg-white/[0.05] hover:text-white" href="/"><ArrowLeft size={18}/></Link>
                        <div><p className="text-[11px] font-bold uppercase tracking-[0.2em] text-yellow-400">SolarMiner Config Lab</p><h1 className="mt-0.5 text-xl font-bold">{t[protocol === 'rest' ? 'config.title.rest' : 'config.title.modbus']}</h1></div>
                    </div>
                    <div className="flex items-center gap-2">
                        <nav className="flex rounded-xl border border-white/10 bg-[#111115] p-1">
                            <Link className={`rounded-lg px-3 py-2 text-sm font-semibold transition ${protocol === 'rest' ? 'bg-violet-400/15 text-violet-200' : 'text-[#888892] hover:text-white'}`} href="/config/pv/rest">REST</Link>
                            <Link className={`rounded-lg px-3 py-2 text-sm font-semibold transition ${protocol === 'modbus' ? 'bg-cyan-400/15 text-cyan-200' : 'text-[#888892] hover:text-white'}`} href="/config/pv/modbus/tcp">Modbus TCP</Link>
                        </nav>
                        <select aria-label={t['config.language']} className="rounded-xl border border-white/10 bg-[#111115] px-3 py-2 text-sm" onChange={(event) => setLocale(event.target.value as 'de' | 'en')} value={locale}><option value="de">DE</option><option value="en">EN</option></select>
                    </div>
                </div>
            </header>

            <div className="mx-auto grid max-w-[1800px] gap-4 p-3 sm:p-4 xl:grid-cols-[280px_minmax(0,1fr)]">
                <aside className="h-fit space-y-4 rounded-2xl border border-white/[0.08] bg-[#121216] p-3 xl:sticky xl:top-4">
                    <div><p className="text-xs font-semibold uppercase tracking-wider text-[#777781]">{t['config.sidebar.template']}</p><select className={`${inputClass} mt-2`} onChange={(event) => {setTemplateId(event.target.value); setSelectedName(''); setProfile(null);}} value={templateId}>{catalog?.templates.map((template) => <option key={template.id} value={template.id}>{template.name}</option>)}</select></div>
                    <div><p className="text-xs font-semibold uppercase tracking-wider text-[#777781]">{t['config.sidebar.new']}</p><div className="mt-2 flex gap-2"><input className={inputClass} maxLength={80} onChange={(event) => setNewName(event.target.value)} placeholder={t['config.sidebar.name_placeholder']} value={newName}/><button aria-label={t['config.action.create']} className="grid min-w-11 place-items-center rounded-xl bg-yellow-400 text-black transition hover:bg-yellow-300 disabled:opacity-40" disabled={busy !== null || !newName.trim()} onClick={() => void createProfile()}><Plus size={18}/></button></div></div>
                    <button className="flex w-full items-center justify-center gap-2 rounded-lg border border-dashed border-white/15 px-3 py-2 text-xs font-semibold text-[#b5b5bd] transition hover:border-violet-400/40 hover:bg-violet-400/[0.05] hover:text-violet-200" disabled={busy !== null} onClick={() => importRef.current?.click()}><Upload size={15}/>{t['config.action.import']}</button>
                    <input ref={importRef} accept="application/json,.json" className="hidden" onChange={importProfile} type="file"/>

                    <div><div className="mb-2 flex items-center justify-between"><p className="text-xs font-semibold uppercase tracking-wider text-[#777781]">{t['config.sidebar.local']}</p><span className="rounded-full bg-white/[0.05] px-2 py-0.5 text-[10px] text-[#777781]">{catalog?.localProfiles.length ?? 0}</span></div><div className="max-h-64 space-y-1 overflow-y-auto pr-1">{catalog?.localProfiles.map((name) => <button className={`flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-sm transition ${selectedName === name ? 'bg-yellow-400/10 text-yellow-200 ring-1 ring-yellow-400/20' : 'text-[#aaaab4] hover:bg-white/[0.04] hover:text-white'}`} key={name} onClick={() => setSelectedName(name)}><FileJson size={14}/><span className="truncate">{name}</span>{selectedName === name ? <Check className="ml-auto" size={13}/> : null}</button>)}{!catalog?.localProfiles.length ? <p className="rounded-xl border border-dashed border-white/[0.08] px-3 py-5 text-center text-xs text-[#666670]">{t['config.sidebar.local_empty']}</p> : null}</div></div>

                    <div><p className="mb-2 text-xs font-semibold uppercase tracking-wider text-[#777781]">{t['config.sidebar.community']}</p><div className="max-h-44 space-y-1 overflow-y-auto pr-1">{catalog?.communityProfiles.map((name) => {const installed = catalog.localProfiles.includes(name); return <div className="flex items-center gap-2 rounded-lg bg-[#0e0e12] px-2.5 py-1.5" key={name}><CloudDownload className="text-cyan-300" size={14}/><span className="min-w-0 flex-1 truncate text-xs text-[#b2b2ba]">{name}</span><button aria-label={t['config.action.community']} className="rounded-md p-1.5 text-[#777781] transition hover:bg-cyan-400/10 hover:text-cyan-200 disabled:text-emerald-400" disabled={installed || busy !== null} onClick={() => void downloadCommunity(name)}>{busy === `community:${name}` ? <LoaderCircle className="animate-spin" size={13}/> : installed ? <Check size={13}/> : <Download size={13}/>}</button></div>;})}{!catalog?.communityProfiles.length ? <p className="text-xs text-[#666670]">{t['config.sidebar.community_empty']}</p> : null}</div></div>
                </aside>

                <section className="min-w-0 space-y-3">
                    {error ? <div className="flex items-start gap-3 rounded-2xl border border-red-400/20 bg-red-400/[0.07] px-4 py-3 text-sm text-red-200"><CircleAlert className="mt-0.5 shrink-0" size={17}/><span>{error}</span></div> : null}
                    {notice ? <div className="flex items-center gap-3 rounded-2xl border border-emerald-400/20 bg-emerald-400/[0.07] px-4 py-3 text-sm text-emerald-200"><CheckCircle2 size={17}/><span>{notice}</span></div> : null}

                    {!profile ? <div className="grid min-h-[600px] place-items-center rounded-3xl border border-white/[0.08] bg-[#121216]"><div className="max-w-md px-6 text-center"><ServerCog className="mx-auto text-[#4f4f58]" size={48}/><h2 className="mt-5 text-xl font-semibold">{t['config.empty.title']}</h2><p className="mt-2 text-sm leading-6 text-[#7e7e88]">{t['config.empty.description']}</p></div></div> : <>
                        <article className="rounded-2xl border border-white/[0.08] bg-[#121216] px-4 py-3">
                            <div className="flex flex-wrap items-center justify-between gap-3"><div className="flex min-w-0 items-center gap-3"><span className={`grid h-9 w-9 shrink-0 place-items-center rounded-lg ${protocol === 'rest' ? 'bg-violet-400/10 text-violet-200' : 'bg-cyan-400/10 text-cyan-200'}`}>{protocol === 'rest' ? <Radio size={18}/> : <Network size={18}/>}</span><div className="min-w-0"><h2 className="truncate text-lg font-bold">{profile.name}</h2><p className="truncate text-xs text-[#777781]">{selectedTemplate?.name} · {profile.fields.length} {t['config.fields.count']}</p></div></div><div className="flex flex-wrap items-center gap-2"><span className={`inline-flex items-center gap-2 rounded-lg px-2.5 py-1.5 text-xs font-semibold ${saveStatusClass}`}>{saveState === 'saving' ? <LoaderCircle className="animate-spin" size={14}/> : saveState === 'saved' ? <CheckCircle2 size={14}/> : saveState === 'error' ? <CircleAlert size={14}/> : <Save size={14}/>} {t[`config.autosave.${saveState}`]}</span><button aria-label={t['config.action.export']} className="grid h-8 w-8 place-items-center rounded-lg border border-white/10 text-[#b2b2ba] transition hover:bg-white/[0.05]" disabled={busy !== null} onClick={() => void exportProfile()}><Download size={15}/></button><button aria-label={t['config.action.delete']} className="grid h-8 w-8 place-items-center rounded-lg border border-red-400/20 text-red-300 transition hover:bg-red-400/10" disabled={busy !== null} onClick={() => void deleteProfile()}><Trash2 size={15}/></button></div></div>
                        </article>

                        <article className="rounded-2xl border border-white/[0.08] bg-[#121216] p-4"><div className="flex flex-wrap items-center justify-between gap-3"><div className="flex items-center gap-2"><Wifi className="text-violet-300" size={17}/><h3 className="text-sm font-semibold">{t['config.test.title']}</h3><span className="hidden text-xs text-[#686872] sm:inline">· {t['config.test.auto']}</span></div><div className="flex items-center gap-2"><span className={`inline-flex items-center gap-2 rounded-lg px-2.5 py-1.5 text-xs font-semibold ${liveState === 'error' || (testResult && !testResult.connected) ? 'bg-red-400/10 text-red-300' : testResult?.connected ? 'bg-emerald-400/10 text-emerald-300' : 'bg-white/[0.05] text-[#92929c]'}`}>{liveState === 'waiting' || liveState === 'testing' ? <LoaderCircle className="animate-spin" size={14}/> : testResult?.connected ? <CheckCircle2 size={14}/> : testResult ? <XCircle size={14}/> : <Gauge size={14}/>} {liveStatusText}</span><button aria-label={t['config.test.refresh']} className="grid h-8 w-8 place-items-center rounded-lg border border-white/10 text-[#92929c] transition hover:bg-white/[0.05] hover:text-white" onClick={() => {setLiveState('waiting'); setConnectionRevision((value) => value + 1);}}><RotateCw size={14}/></button></div></div>
                            {protocol === 'rest' ? <div className="mt-3 grid gap-2 md:grid-cols-[1.25fr_1fr]"><label className="text-[11px] text-[#92929c]">{t['config.test.base_url']}<input className={`${inputClass} mt-1`} onChange={(event) => {setRestTarget({...restTarget, baseUrl: event.target.value}); setLiveState('waiting');}} value={restTarget.baseUrl}/></label><label className="text-[11px] text-[#92929c]">{t['config.test.token']}<input autoComplete="off" className={`${inputClass} mt-1`} onChange={(event) => {setRestTarget({...restTarget, apiToken: event.target.value}); setLiveState('waiting');}} type="password" value={restTarget.apiToken}/></label></div> : <div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-[1fr_120px_120px]"><label className="text-[11px] text-[#92929c]">{t['config.test.host']}<input className={`${inputClass} mt-1`} onChange={(event) => {setModbusTarget({...modbusTarget, host: event.target.value}); setLiveState('waiting');}} value={modbusTarget.host}/></label><label className="text-[11px] text-[#92929c]">{t['config.test.port']}<input className={`${inputClass} mt-1`} max={65535} min={1} onChange={(event) => {setModbusTarget({...modbusTarget, port: Number(event.target.value)}); setLiveState('waiting');}} type="number" value={modbusTarget.port}/></label><label className="text-[11px] text-[#92929c]">{t['config.test.slave']}<input className={`${inputClass} mt-1`} max={247} min={1} onChange={(event) => {setModbusTarget({...modbusTarget, slaveId: Number(event.target.value)}); setLiveState('waiting');}} type="number" value={modbusTarget.slaveId}/></label></div>}
                        </article>

                        {protocol === 'modbus' && catalog ? <article className="rounded-2xl border border-white/[0.08] bg-[#121216] p-4"><div className="flex flex-wrap items-center justify-between gap-2"><div><h3 className="text-sm font-semibold">{t['config.fingerprint.title']}</h3><p className="mt-0.5 text-xs text-[#7e7e88]">{t['config.fingerprint.description']}</p></div>{profile.fingerprint?.expectedValue && testResult?.connected ? <span className={`rounded-lg px-2.5 py-1 text-xs font-semibold ${testResult.fingerprintMatches ? 'bg-emerald-400/10 text-emerald-300' : 'bg-orange-400/10 text-orange-300'}`}>{t[testResult.fingerprintMatches ? 'config.fingerprint.match' : 'config.fingerprint.no_match']}</span> : null}</div><div className="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-6"><SmallField label={t['config.field.address']}><input className={compactInputClass} min={0} onChange={(event) => updateFingerprint({address: event.target.value ? Number(event.target.value) : null})} type="number" value={profile.fingerprint?.address ?? ''}/></SmallField><SmallField label={t['config.field.size']}><input className={compactInputClass} min={1} onChange={(event) => updateFingerprint({size: Number(event.target.value)})} type="number" value={profile.fingerprint?.size ?? 1}/></SmallField><SmallField label={t['config.field.type']}><select className={compactInputClass} onChange={(event) => updateFingerprint({parameterType: event.target.value})} value={profile.fingerprint?.parameterType ?? catalog.parameterTypes[0]}>{catalog.parameterTypes.map((value) => <option key={value}>{value}</option>)}</select></SmallField><SmallField label={t['config.field.operation']}><select className={compactInputClass} onChange={(event) => updateFingerprint({operationType: event.target.value})} value={profile.fingerprint?.operationType ?? catalog.operationTypes?.[0]?.value}>{catalog.operationTypes?.map((value) => <option key={value.value} value={value.value}>{value.label}</option>)}</select></SmallField><SmallField label={t['config.field.byte_order']}><select className={compactInputClass} onChange={(event) => updateFingerprint({byteOrder: event.target.value})} value={profile.fingerprint?.byteOrder ?? catalog.byteOrders?.[0]}>{catalog.byteOrders?.map((value) => <option key={value}>{value}</option>)}</select></SmallField><SmallField label={t['config.field.expected']}><input className={compactInputClass} maxLength={128} onChange={(event) => updateFingerprint({expectedValue: event.target.value})} value={profile.fingerprint?.expectedValue ?? ''}/></SmallField></div></article> : null}

                        <article className="rounded-2xl border border-white/[0.08] bg-[#121216] p-4"><div className="mb-3 flex flex-wrap items-end justify-between gap-3"><div><h3 className="text-sm font-semibold">{t['config.fields.title']}</h3><p className="mt-0.5 text-xs text-[#7e7e88]">{t['config.fields.description']}</p></div><label className="relative block w-full sm:w-72"><Search className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[#65656f]" size={14}/><input aria-label={t['config.fields.search']} className="w-full rounded-lg border border-white/10 bg-[#0d0d10] py-1.5 pl-8 pr-3 text-sm text-white outline-none placeholder:text-[#5f5f68] focus:border-yellow-400/50" onChange={(event) => setFieldQuery(event.target.value)} placeholder={t['config.fields.search']} value={fieldQuery}/></label></div><div className="space-y-1.5">{filteredFields.map((field) => {const expanded = expandedField === field.field; const connection = isRestField(field) ? field.urlExtension : `${t['config.field.address']} ${field.startAddress}`; return <div className="overflow-hidden rounded-lg border border-white/[0.07] bg-[#0d0d10]" key={field.field}><button aria-expanded={expanded} className="grid w-full grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-3 px-3 py-2 text-left transition hover:bg-white/[0.025]" onClick={() => setExpandedField(expanded ? null : field.field)}><ChevronDown className={`text-[#686872] transition-transform ${expanded ? 'rotate-180' : ''}`} size={15}/><span className="min-w-0"><span className="flex min-w-0 items-baseline gap-2"><span className="truncate text-sm font-semibold">{fieldLabel(field)}</span><span className="hidden truncate font-mono text-[10px] text-[#55555f] md:inline">{field.field} · {connection}</span></span></span><span className={`rounded-md px-2 py-1 font-mono text-[11px] ${testResult?.fields[field.field]?.errorCode ? 'bg-red-400/10 text-red-300' : testResult?.fields[field.field] ? 'bg-emerald-400/10 text-emerald-300' : 'bg-white/[0.04] text-[#777781]'}`}>{liveValue(field)}</span></button>{expanded ? <div className="border-t border-white/[0.06] px-3 py-3">{isRestField(field) ? <RestFieldEditor catalog={catalog!} field={field} labels={t} onChange={(patch) => updateField(field.field, patch)}/> : <ModbusFieldEditor catalog={catalog!} field={field} labels={t} onChange={(patch) => updateField(field.field, patch)}/>}</div> : null}</div>;})}{filteredFields.length === 0 ? <div className="rounded-lg border border-dashed border-white/10 px-4 py-8 text-center text-sm text-[#666670]">{t['config.fields.no_results']}</div> : null}</div></article>
                    </>}
                </section>
            </div>
        </main>
    );
}

function SmallField({label, children}: {label: string; children: React.ReactNode}) {
    return <label className="text-xs text-[#85858f]"><span className="mb-1.5 block">{label}</span>{children}</label>;
}

function RestFieldEditor({field, catalog, labels, onChange}: {field: RestField; catalog: Catalog; labels: Record<string, string>; onChange: (patch: Partial<RestField>) => void}) {
    return <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-7"><SmallField label={labels['config.field.method']}><select className={compactInputClass} onChange={(event) => onChange({httpMethod: event.target.value})} value={field.httpMethod}>{catalog.httpMethods?.map((value) => <option key={value}>{value}</option>)}</select></SmallField><SmallField label={labels['config.field.response']}><select className={compactInputClass} onChange={(event) => onChange({responseType: event.target.value})} value={field.responseType}>{catalog.responseTypes?.map((value) => <option key={value}>{value}</option>)}</select></SmallField><div className="xl:col-span-2"><SmallField label={labels['config.field.url_path']}><input className={compactInputClass} maxLength={512} onChange={(event) => onChange({urlExtension: event.target.value})} value={field.urlExtension}/></SmallField></div><SmallField label={labels['config.field.data_path']}><input className={compactInputClass} maxLength={256} onChange={(event) => onChange({dataPath: event.target.value})} value={field.dataPath}/></SmallField><SmallField label={labels['config.field.factor']}><input className={compactInputClass} onChange={(event) => onChange({scaleFactor: Number(event.target.value)})} step="any" type="number" value={field.scaleFactor}/></SmallField><SmallField label={labels['config.field.formula']}><input className={compactInputClass} maxLength={128} onChange={(event) => onChange({formula: event.target.value})} value={field.formula}/></SmallField><SmallField label={labels['config.field.type']}><select className={compactInputClass} onChange={(event) => onChange({parameterType: event.target.value})} value={field.parameterType}>{catalog.parameterTypes.map((value) => <option key={value}>{value}</option>)}</select></SmallField></div>;
}

function ModbusFieldEditor({field, catalog, labels, onChange}: {field: ModbusField; catalog: Catalog; labels: Record<string, string>; onChange: (patch: Partial<ModbusField>) => void}) {
    return <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-8"><SmallField label={labels['config.field.address']}><input className={compactInputClass} max={65535} min={0} onChange={(event) => onChange({startAddress: Number(event.target.value)})} type="number" value={field.startAddress}/></SmallField><SmallField label={labels['config.field.size']}><input className={compactInputClass} max={125} min={1} onChange={(event) => onChange({size: Number(event.target.value)})} type="number" value={field.size}/></SmallField><SmallField label={labels['config.field.factor']}><input className={compactInputClass} onChange={(event) => onChange({scaleFactor: Number(event.target.value)})} step="any" type="number" value={field.scaleFactor}/></SmallField><SmallField label={labels['config.field.formula']}><input className={compactInputClass} maxLength={128} onChange={(event) => onChange({formula: event.target.value})} value={field.formula}/></SmallField><SmallField label={labels['config.field.type']}><select className={compactInputClass} onChange={(event) => onChange({parameterType: event.target.value})} value={field.parameterType}>{catalog.parameterTypes.map((value) => <option key={value}>{value}</option>)}</select></SmallField><div className="xl:col-span-2"><SmallField label={labels['config.field.operation']}><select className={compactInputClass} onChange={(event) => onChange({operationType: event.target.value})} value={field.operationType}>{catalog.operationTypes?.map((value) => <option key={value.value} value={value.value}>{value.label}</option>)}</select></SmallField></div><SmallField label={labels['config.field.byte_order']}><select className={compactInputClass} onChange={(event) => onChange({byteOrder: event.target.value})} value={field.byteOrder}>{catalog.byteOrders?.map((value) => <option key={value}>{value}</option>)}</select></SmallField></div>;
}
