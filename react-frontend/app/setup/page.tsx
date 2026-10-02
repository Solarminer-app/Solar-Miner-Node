'use client';

import dynamic from 'next/dynamic';
import {useRouter} from 'next/navigation';
import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
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
    Pencil,
    Plus,
    RefreshCw,
    Server,
    Search,
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
type ConfiguredPvDevice = {
    id: string;
    providerId: string;
    label: string;
    values: Record<string, string>;
    selectedSectionKeys: string[];
};
type PvDeviceSection = {sectionKey: string; templateId: string; name: string; deviceType: 'INVERTER' | 'BATTERY' | 'SMART_METER'};
type PvDeviceProfile = {providerId: string; profileName: string; sections: PvDeviceSection[]};
type DiscoveredPvDevice = PvDeviceProfile & {host: string; port: number; slaveId: number; requiresAuth: boolean};
type DiscoveryReport = {devices: DiscoveredPvDevice[]; subnetPrefix: string; checkedHosts: number; totalHosts: number; complete: boolean};
type PvDevicePreviewValue = {key: string; value: number; unit: string};
type PvDeviceSectionPreview = {
    sectionKey: string;
    deviceType: PvDeviceSection['deviceType'];
    name: string;
    availability: 'AVAILABLE' | 'INCONCLUSIVE' | 'NO_RESPONSE' | 'ERROR';
    values: PvDevicePreviewValue[];
    message: string;
};
type PvDevicePreview = {sections: PvDeviceSectionPreview[]};

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

const steps = ['basics', 'source', 'panels', 'pools', 'telemetry', 'summary'] as const;
const inputClass = 'mt-1.5 w-full rounded-xl border border-white/10 bg-[#101014] px-3.5 py-3 text-sm text-white outline-none transition placeholder:text-[#5f5f68] focus:border-yellow-400/60 focus:ring-2 focus:ring-yellow-400/10 disabled:cursor-not-allowed disabled:opacity-60';

function endpointLabel(values: Record<string, string>) {
    if (values.host) return values.port ? `${values.host}:${values.port}` : values.host;
    return values.serialPort || values.brokerUri || values.url || '—';
}

function ProviderFields({option, values, onChange, fieldKeys}: {
    option: SetupOption;
    values: Record<string, string>;
    onChange: (key: string, value: string) => void;
    fieldKeys?: string[];
}) {
    return (
        <div className="grid gap-4 sm:grid-cols-2">
            {option.fields.filter((field) => !fieldKeys || fieldKeys.includes(field.key)).map((field) => (
                <label className={`text-sm text-[#c3c3cb] ${field.type === 'TEXT' || field.type === 'PASSWORD' ? 'sm:col-span-2' : ''}`} key={field.key}>
                    {field.label}{field.required ? <span className="ml-1 text-yellow-400">*</span> : null}
                    {field.type === 'SELECT' ? (
                        <select aria-label={field.label} className={inputClass} onChange={(event) => onChange(field.key, event.target.value)} required={field.required} value={values[field.key] ?? ''}>
                            <option value="">—</option>
                            {field.options.map((entry) => <option key={entry.value} value={entry.value}>{entry.label}</option>)}
                        </select>
                    ) : (
                        <input
                            aria-label={field.label}
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

function SectionPreviewCard({section, preview, checked, onToggle, t, locale}: {
    section: PvDeviceSection;
    preview?: PvDeviceSectionPreview;
    checked: boolean;
    onToggle: () => void;
    t: Record<string, string>;
    locale: string;
}) {
    const status = preview?.availability;
    const statusClass = status === 'AVAILABLE'
        ? 'bg-emerald-400/10 text-emerald-300'
        : status === 'INCONCLUSIVE'
            ? 'bg-yellow-400/10 text-yellow-200'
            : status
                ? 'bg-red-400/10 text-red-300'
                : 'bg-white/[0.05] text-[#777781]';
    return (
        <label className={`cursor-pointer rounded-xl border p-3 transition ${checked ? 'border-emerald-400/30 bg-emerald-400/[0.06]' : 'border-white/[0.08] bg-black/10'}`}>
            <div className="flex items-start gap-3">
                <input checked={checked} className="mt-1 accent-emerald-400" onChange={onToggle} type="checkbox"/>
                <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center justify-between gap-2">
                        <strong className="block truncate text-sm">{section.name}</strong>
                        <span className={`rounded-full px-2 py-1 text-[10px] font-semibold uppercase tracking-wide ${statusClass}`}>
                            {status ? t[`setup.source.preview.status.${status.toLowerCase()}`] : t['setup.source.preview.waiting']}
                        </span>
                    </span>
                    <span className="mt-0.5 block text-xs text-[#777781]">{t[`setup.source.type.${section.deviceType.toLowerCase()}`]}</span>
                </span>
            </div>
            {preview?.values.length ? (
                <div className="mt-3 grid grid-cols-2 gap-2 border-t border-white/[0.07] pt-3">
                    {preview.values.map((metric) => (
                        <span className="rounded-lg bg-black/20 px-2.5 py-2" key={metric.key}>
                            <span className="block truncate text-[10px] text-[#777781]">{t[`setup.source.preview.metric.${metric.key}`]}</span>
                            <strong className="mt-0.5 block text-sm text-[#e7e7ec]">{new Intl.NumberFormat(locale, {maximumFractionDigits: 2}).format(metric.value)} {metric.unit}</strong>
                        </span>
                    ))}
                </div>
            ) : null}
            {preview?.message ? <p className="mt-3 break-words text-xs leading-5 text-red-300/80">{preview.message}</p> : null}
        </label>
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
    const [pvDevices, setPvDevices] = useState<ConfiguredPvDevice[]>([]);
    const [profileSearch, setProfileSearch] = useState('');
    const [profiles, setProfiles] = useState<PvDeviceProfile[]>([]);
    const [selectedSectionKeys, setSelectedSectionKeys] = useState<string[]>([]);
    const [devicePreview, setDevicePreview] = useState<PvDevicePreview | null>(null);
    const [previewing, setPreviewing] = useState(false);
    const [previewError, setPreviewError] = useState(false);
    const previewSelectionKey = useRef('');
    const [discoveredDevices, setDiscoveredDevices] = useState<DiscoveredPvDevice[]>([]);
    const [discovering, setDiscovering] = useState(false);
    const [sourceMode, setSourceMode] = useState<'discover' | 'manual'>('discover');
    const [configurationOpen, setConfigurationOpen] = useState(false);
    const [subnetPrefix, setSubnetPrefix] = useState('');
    const [scanProvider, setScanProvider] = useState('AUTO');
    const [scanPort, setScanPort] = useState('502');
    const [scanSlaveId, setScanSlaveId] = useState('1');
    const [discoveryReport, setDiscoveryReport] = useState<DiscoveryReport | null>(null);
    const [discoveryError, setDiscoveryError] = useState('');
    const [profilesLoading, setProfilesLoading] = useState(false);
    const [profilesError, setProfilesError] = useState(false);
    const [profileReload, setProfileReload] = useState(0);
    const validationKey = useRef('');
    validationKey.current = JSON.stringify({selectedSource, providerValues});
    const [selectedPools, setSelectedPools] = useState<string[]>([]);
    const [submitting, setSubmitting] = useState(false);
    const [createdSiteId, setCreatedSiteId] = useState<string | null>(null);
    const [telemetryEnabled, setTelemetryEnabled] = useState(true);

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
    const [editingPanelId, setEditingPanelId] = useState<string | null>(null);
    const [panelLocationSelected, setPanelLocationSelected] = useState(false);
    const [panelMapOpen, setPanelMapOpen] = useState(false);
    const [panelFormError, setPanelFormError] = useState('');
    const [manualLatitude, setManualLatitude] = useState('');
    const [manualLongitude, setManualLongitude] = useState('');
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
                    if (next[option.id][field.key] === undefined) next[option.id][field.key] = option.kind === 'PV_SOURCE' && field.key === 'profile' ? '' : field.defaultValue ?? '';
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
    const selectedProfile = profiles.find((profile) => profile.providerId === selectedSource && profile.profileName === providerValues[selectedSource]?.profile);
    const selectedPoolOptions = catalog?.miningPools.filter((option) => selectedPools.includes(option.id)) ?? [];
    const filteredProfiles = profiles.filter((profile) => profile.profileName.toLowerCase().includes(profileSearch.trim().toLowerCase()));

    useEffect(() => {
        const controller = new AbortController();
        fetch(`${API_BASE_URL}/setup/pv-devices/network`, {signal: controller.signal})
            .then((response) => response.ok ? response.json() as Promise<{subnetPrefix: string}> : Promise.reject())
            .then((network) => {
                const host = window.location.hostname;
                const lanHost = /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(host) && /^\d+\.\d+\.\d+\.\d+$/.test(host);
                setSubnetPrefix((current) => current || (lanHost ? host.slice(0, host.lastIndexOf('.') + 1) : network.subnetPrefix));
            }).catch(() => undefined);
        return () => controller.abort();
    }, []);

    useEffect(() => {
        if (!selectedSource) return;
        const controller = new AbortController();
        setProfilesLoading(true);
        setProfilesError(false);
        setProfiles([]);
        const timeout = window.setTimeout(() => {
            const query = new URLSearchParams({providerId: selectedSource});
            fetch(`${API_BASE_URL}/setup/pv-devices/profiles?${query}`, {signal: controller.signal})
                .then((response) => response.ok ? response.json() as Promise<PvDeviceProfile[]> : Promise.reject())
                .then(setProfiles)
                .catch(() => {if (!controller.signal.aborted) setProfilesError(true);})
                .finally(() => {if (!controller.signal.aborted) setProfilesLoading(false);});
        }, 180);
        return () => {window.clearTimeout(timeout); controller.abort();};
    }, [selectedSource, catalog, profileReload]);

    useEffect(() => {
        if (selectedProfile) setSelectedSectionKeys(selectedProfile.sections.map((section) => section.sectionKey));
        setDevicePreview(null);
        setPreviewError(false);
        previewSelectionKey.current = '';
    }, [selectedSource, selectedProfile?.profileName]);

    const updateProviderValue = (providerId: string, key: string, value: string) => {
        setProviderValues((current) => ({...current, [providerId]: {...(current[providerId] ?? {}), [key]: value}}));
        setVerification((current) => ({...current, [providerId]: undefined}));
    };

    const providerFieldsComplete = (option: SetupOption) => option.fields.every((field) => !field.required || Boolean(providerValues[option.id]?.[field.key]?.trim()));

    const previewRequestKey = JSON.stringify({providerId: selectedSource, values: providerValues[selectedSource] ?? {}});
    useEffect(() => {
        if (step !== 1 || !selectedProfile || !verification[selectedSource]?.valid) {
            setDevicePreview(null);
            setPreviewing(false);
            setPreviewError(false);
            return;
        }
        let stopped = false;
        let running = false;
        let activeController: AbortController | null = null;
        const loadPreview = async () => {
            if (running || stopped) return;
            running = true;
            activeController = new AbortController();
            setPreviewing(true);
            try {
                const response = await fetch(`${API_BASE_URL}/setup/pv-devices/preview`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/json'},
                    body: JSON.stringify({providerId: selectedSource, values: providerValues[selectedSource] ?? {}}),
                    signal: activeController.signal,
                });
                if (!response.ok) throw new Error();
                const result = await response.json() as PvDevicePreview;
                if (stopped) return;
                setDevicePreview(result);
                setPreviewError(false);
                if (previewSelectionKey.current !== previewRequestKey) {
                    const unavailable = new Set(result.sections
                        .filter((section) => section.availability === 'NO_RESPONSE' || section.availability === 'ERROR')
                        .map((section) => section.sectionKey));
                    setSelectedSectionKeys((current) => current.filter((key) => !unavailable.has(key)));
                    previewSelectionKey.current = previewRequestKey;
                }
            } catch (error) {
                if (!stopped && !(error instanceof DOMException && error.name === 'AbortError')) setPreviewError(true);
            } finally {
                running = false;
                if (!stopped) setPreviewing(false);
            }
        };
        void loadPreview();
        const interval = window.setInterval(() => void loadPreview(), 5000);
        return () => {
            stopped = true;
            activeController?.abort();
            window.clearInterval(interval);
        };
    }, [step, selectedProfile?.profileName, selectedSource, verification[selectedSource]?.valid, previewRequestKey]);

    const testProvider = async (option: SetupOption) => {
        const requestKey = validationKey.current;
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
            if (requestKey !== validationKey.current) return;
            setVerification((current) => ({...current, [option.id]: {valid: result.valid, message: result.valid ? t['setup.connection.success'] : result.message || t['setup.connection.failed']}}));
        } catch {
            if (requestKey !== validationKey.current) return;
            setVerification((current) => ({...current, [option.id]: {valid: false, message: t['setup.connection.failed']}}));
        } finally {
            setTestingProvider(null);
        }
    };

    const addPvDevice = () => {
        if (!selectedSourceOption || !providerFieldsComplete(selectedSourceOption) || !verification[selectedSourceOption.id]?.valid || selectedSectionKeys.length === 0) {
            setError(t['setup.error.step']);
            return;
        }
        const rawValues = {...(providerValues[selectedSourceOption.id] ?? {})};
        const values: Record<string, string> = {
            ...rawValues,
            host: rawValues.host || endpointLabel(rawValues),
            port: rawValues.port || rawValues.baudRate || '',
        };
        const duplicate = pvDevices.some((device) => device.providerId === selectedSourceOption.id
            && endpointLabel(device.values) === endpointLabel(values)
            && device.values.profile === values.profile
            && device.selectedSectionKeys.join('|') === selectedSectionKeys.join('|'));
        if (!duplicate) {
            setPvDevices((current) => [...current, {
                id: crypto.randomUUID(),
                providerId: selectedSourceOption.id,
                label: selectedSourceOption.label,
                values,
                selectedSectionKeys,
            }]);
        }
        setVerification((current) => ({...current, [selectedSourceOption.id]: undefined}));
        setConfigurationOpen(false);
        setError(null);
    };

    const discoverPvDevices = async () => {
        setDiscovering(true);
        setDiscoveryReport(null);
        setDiscoveredDevices([]);
        setDiscoveryError('');
        setError(null);
        try {
            const response = await fetch(`${API_BASE_URL}/setup/pv-devices/scan`, {
                method: 'POST', headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({providerId: scanProvider, subnetPrefix, port: scanProvider === 'AUTO' ? null : Number(scanPort), slaveId: Number(scanSlaveId)}),
            });
            if (!response.ok) throw new Error(response.status === 429 ? 'busy' : 'failed');
            const report = await response.json() as DiscoveryReport;
            setDiscoveryReport(report);
            setDiscoveredDevices(report.devices);
        } catch {
            setDiscoveryError(t['setup.source.discovery_error']);
        } finally {
            setDiscovering(false);
        }
    };

    const useDiscoveredDevice = (device: DiscoveredPvDevice) => {
        setConfigurationOpen(true);
        setProfileSearch('');
        setSelectedSource(device.providerId);
        setProviderValues((current) => ({...current, [device.providerId]: {
            ...(current[device.providerId] ?? {}), host: device.providerId === 'REST_API' && device.port === 443 ? `https://${device.host}` : device.host, port: String(device.port), slaveId: String(device.slaveId), profile: device.profileName,
        }}));
        setProfiles((current) => current.some((profile) => profile.providerId === device.providerId && profile.profileName === device.profileName) ? current : [...current, device]);
        setSelectedSectionKeys(device.sections.map((section) => section.sectionKey));
        setVerification((current) => ({...current, [device.providerId]: undefined}));
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

    const nextPanelName = () => {
        let index = 1;
        while (panels.some((panel) => panel.name === `${t['setup.panels.default_name']} ${index}`)) index++;
        return `${t['setup.panels.default_name']} ${index}`;
    };

    const setPanelLocation = (location: {latitude: number; longitude: number}) => {
        setPanelDraft((current) => ({...current, ...location}));
        setManualLatitude(String(location.latitude));
        setManualLongitude(String(location.longitude));
        setPanelLocationSelected(true);
        setPanelFormError('');
    };

    const changePanelCoordinate = (axis: 'latitude' | 'longitude', value: string) => {
        const latitude = axis === 'latitude' ? value : manualLatitude;
        const longitude = axis === 'longitude' ? value : manualLongitude;
        setManualLatitude(latitude);
        setManualLongitude(longitude);
        const lat = Number(latitude);
        const lon = Number(longitude);
        const valid = latitude.trim() !== '' && longitude.trim() !== '' && Number.isFinite(lat) && Number.isFinite(lon)
            && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
        setPanelLocationSelected(valid);
        if (valid) setPanelDraft((current) => ({...current, latitude: lat, longitude: lon}));
        setPanelFormError('');
    };

    const editPanel = (panel: PanelGroup) => {
        setEditingPanelId(panel.id);
        setPanelDraft({name: panel.name, latitude: panel.latitude, longitude: panel.longitude,
            panelCount: panel.panelCount, powerPerPanelWatts: panel.powerPerPanelWatts,
            azimuthDegrees: panel.azimuthDegrees, slopeDegrees: panel.slopeDegrees});
        setManualLatitude(String(panel.latitude));
        setManualLongitude(String(panel.longitude));
        setPanelLocationSelected(true);
        setPanelMapOpen(false);
        setPanelFormError('');
    };

    const cancelPanelEdit = () => {
        setEditingPanelId(null);
        setPanelDraft((current) => ({...current, name: '', panelCount: 1, powerPerPanelWatts: 400,
            azimuthDegrees: 180, slopeDegrees: 30}));
        setPanelMapOpen(false);
        setPanelFormError('');
    };

    const addPanel = () => {
        if (!Number.isInteger(panelDraft.panelCount) || panelDraft.panelCount < 1
            || !Number.isFinite(panelDraft.powerPerPanelWatts) || panelDraft.powerPerPanelWatts <= 0
            || !Number.isFinite(panelDraft.azimuthDegrees) || panelDraft.azimuthDegrees < 0 || panelDraft.azimuthDegrees > 360
            || !Number.isFinite(panelDraft.slopeDegrees) || panelDraft.slopeDegrees < 0 || panelDraft.slopeDegrees > 90) {
            setPanelFormError(t['setup.panels.error.values']);
            return;
        }
        if (!panelLocationSelected) {
            setPanelMapOpen(true);
            setPanelFormError(t['setup.panels.error.location']);
            return;
        }
        const saved = {...panelDraft, name: panelDraft.name.trim() || nextPanelName(), id: editingPanelId || crypto.randomUUID()};
        setPanels((current) => editingPanelId ? current.map((panel) => panel.id === editingPanelId ? saved : panel) : [...current, saved]);
        setEditingPanelId(null);
        setPanelDraft((current) => ({...current, name: '', panelCount: 1, powerPerPanelWatts: 400,
            azimuthDegrees: 180, slopeDegrees: 30}));
        setPanelMapOpen(false);
        setPanelFormError('');
        setError(null);
    };

    const canContinue = () => {
        if (step === 0) return Boolean(basics.name.trim() && basics.setupDate && (basics.timeZone || timeZone) && basics.electricityPrice >= 0);
        if (step === 1) return pvDevices.length > 0;
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
        if (pvDevices.length === 0) return;
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
                    pvSource: {providerId: pvDevices[0].providerId, values: pvDevices[0].values},
                    pvDevices: pvDevices.map((device) => ({providerId: device.providerId, values: device.values, selectedSectionKeys: device.selectedSectionKeys})),
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
                    telemetryOptIn: telemetryEnabled,
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
                    <div className="flex items-center justify-between gap-3 px-1 md:hidden"><span className="text-sm text-[#b0b0ba]">{step + 1} / {steps.length}</span><strong className="text-sm text-yellow-200">{t[`setup.step.${steps[step]}`]}</strong></div>
                    <ol className="hidden min-w-[680px] items-center md:flex">
                        {steps.map((name, index) => (
                            <li aria-current={index === step ? 'step' : undefined} className="flex flex-1 items-center" key={name}>
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
                                <div className="mb-6 rounded-2xl border border-cyan-400/15 bg-cyan-400/[0.04] p-4 text-sm leading-6 text-[#c3d8dc]">
                                    <p className="font-semibold text-white">{t['setup.source.goal']}</p>
                                    <p className="mt-1">{t['setup.source.goal_hint']}</p>
                                </div>
                                <div className="mb-5 grid gap-3 sm:grid-cols-2">
                                    {(['discover', 'manual'] as const).map((mode) => <button aria-pressed={sourceMode === mode} className={`rounded-2xl border p-4 text-left transition focus-visible:ring-2 focus-visible:ring-yellow-400 ${sourceMode === mode ? 'border-yellow-400/50 bg-yellow-400/[0.06]' : 'border-white/10 bg-[#101014] hover:border-white/25'}`} key={mode} onClick={() => {setSourceMode(mode); setConfigurationOpen(false);}}>
                                        <span className="flex items-center justify-between gap-2"><strong>{t[`setup.source.mode.${mode}`]}</strong>{mode === 'discover' ? <Search className="text-yellow-300" size={20}/> : <Server className="text-[#aaaab4]" size={20}/>}</span>
                                        <span className="mt-2 block text-sm leading-5 text-[#a0a0aa]">{t[`setup.source.mode.${mode}_hint`]}</span>
                                    </button>)}
                                </div>
                                {sourceMode === 'discover' ? <div className="mb-5 rounded-2xl border border-white/10 bg-[#101014] p-5">
                                    <h3 className="font-semibold">{t['setup.source.find_title']}</h3>
                                    <p className="mt-1 text-sm leading-6 text-[#a0a0aa]">{t['setup.source.find_hint']}</p>
                                    <div className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end">
                                        <label className="w-full min-w-0 text-sm text-[#c3c3cb] sm:flex-1">{t['setup.source.network']}<input className={inputClass} disabled={discovering} onChange={(event) => setSubnetPrefix(event.target.value)} placeholder="192.168.1." value={subnetPrefix}/></label>
                                        <button className="inline-flex min-h-[46px] w-full items-center justify-center gap-2 rounded-xl bg-yellow-400 px-5 py-3 text-sm font-semibold text-black disabled:opacity-50 sm:w-auto" disabled={discovering || !subnetPrefix.trim()} onClick={() => void discoverPvDevices()}>{discovering ? <LoaderCircle className="animate-spin" size={16}/> : <Search size={16}/>} {discovering ? t['setup.source.discovering'] : t['setup.source.discover']}</button>
                                    </div>
                                    <p className="mt-2 text-xs leading-5 text-[#a0a0aa]">{t['setup.source.network_hint']}</p>
                                    <details className="mt-4 text-sm text-[#a0a0aa]"><summary className="cursor-pointer">{t['setup.source.scan_settings']}</summary><div className="mt-3 grid gap-3 sm:grid-cols-3">
                                        <label>{t['setup.source.scan_protocol']}<select className={inputClass} disabled={discovering} value={scanProvider} onChange={(event) => {setScanProvider(event.target.value); setScanPort(event.target.value === 'REST_API' ? '80' : '502');}}><option value="AUTO">{t['setup.source.scan_auto']}</option><option value="MODBUS_TCP">Modbus TCP</option><option value="REST_API">HTTP / REST</option></select></label>
                                        {scanProvider !== 'AUTO' ? <label>Port<input className={inputClass} disabled={discovering} min={1} max={65535} type="number" value={scanPort} onChange={(event) => setScanPort(event.target.value)}/></label> : null}
                                        {scanProvider !== 'REST_API' ? <label>{t['setup.source.device_id']}<input className={inputClass} disabled={discovering} min={1} max={255} type="number" value={scanSlaveId} onChange={(event) => setScanSlaveId(event.target.value)}/></label> : null}
                                    </div></details>
                                    <div aria-live="polite" role="status" className="mt-4">
                                        {discovering ? <p className="text-sm leading-6 text-yellow-200">{t['setup.source.scan_wait']}</p> : null}
                                        {discoveryReport ? <div className="rounded-xl bg-white/[0.04] p-3 text-sm leading-6"><p className="font-semibold text-white">{discoveryReport.complete ? t['setup.source.scan_complete'] : t['setup.source.scan_partial']} · {discoveryReport.checkedHosts}/{discoveryReport.totalHosts}</p><p className="text-[#b0b0ba]">{discoveredDevices.length ? t['setup.source.pick_found'] : t['setup.source.scan_empty']}</p>{!discoveryReport.complete ? <p className="text-yellow-200">{t['setup.source.partial_hint']}</p> : null}</div> : null}
                                        {discoveryError ? <p className="rounded-xl bg-red-400/10 p-3 text-sm leading-6 text-red-300">{discoveryError}</p> : null}
                                    </div>
                                    {discoveryReport || discoveryError ? <button className="mt-3 text-sm font-semibold text-yellow-200 underline underline-offset-4" onClick={() => {setSourceMode('manual'); setConfigurationOpen(false);}}>{t['setup.source.manual_fallback']}</button> : null}
                                </div> : null}
                                {pvDevices.length ? <div className="mb-5 space-y-2"><p className="text-xs font-semibold uppercase tracking-wider text-[#777781]">{t['setup.source.configured']}</p>{pvDevices.map((device) => <div className="flex items-center gap-3 rounded-xl border border-emerald-400/15 bg-emerald-400/[0.05] px-4 py-3" key={device.id}><CheckCircle2 className="shrink-0 text-emerald-300" size={17}/><div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold">{device.label} · {device.values.profile}</p><p className="truncate text-xs text-[#85858f]">{device.values.host}:{device.values.port} · {device.selectedSectionKeys.length} {t['setup.source.components']}</p></div><button aria-label={t['setup.source.remove']} className="grid h-8 w-8 place-items-center rounded-lg text-[#777781] transition hover:bg-red-400/10 hover:text-red-300" onClick={() => setPvDevices((current) => current.filter((entry) => entry.id !== device.id))}><Trash2 size={15}/></button></div>)}</div> : null}
                                {discoveredDevices.length ? <div className="mb-5 rounded-2xl border border-yellow-400/15 bg-yellow-400/[0.04] p-4"><p className="mb-3 text-xs font-semibold uppercase tracking-wider text-yellow-200">{t['setup.source.discovered']}</p><div className="grid gap-2 md:grid-cols-2">{discoveredDevices.map((device) => <button className="rounded-xl border border-white/[0.08] bg-[#101014] p-3 text-left transition hover:border-yellow-400/40" key={`${device.providerId}-${device.host}-${device.port}-${device.profileName}`} onClick={() => useDiscoveredDevice(device)}><p className="text-sm font-semibold">{device.profileName}</p><p className="mt-1 text-xs text-[#85858f]">{device.host}:{device.port} · {device.sections.length} {t['setup.source.components']}</p></button>)}</div></div> : null}
                                {sourceMode === 'manual' ? <div className="mb-5">
                                    <h3 className="mb-2 font-semibold">{t['setup.source.manual_title']}</h3>
                                    <p className="mb-4 text-sm leading-6 text-[#a0a0aa]">{t['setup.source.manual_hint']}</p>
                                    <div className="grid gap-3 sm:grid-cols-2">{catalog.pvSources.map((option) => <button aria-pressed={selectedSource === option.id && configurationOpen} className={`rounded-xl border p-4 text-left transition ${selectedSource === option.id && configurationOpen ? 'border-yellow-400/50 bg-yellow-400/[0.06]' : 'border-white/10 bg-[#101014] hover:border-white/25'}`} key={option.id} onClick={() => {setSelectedSource(option.id); setProfileReload((current) => current + 1); setProfileSearch(''); setConfigurationOpen(true); setError(null);}}><h4 className="font-semibold">{t[`setup.source.protocol.${option.id}`] || option.label}</h4><p className="mt-1 text-sm leading-5 text-[#a0a0aa]">{t[`setup.source.protocol.${option.id}_hint`] || option.description}</p></button>)}</div>
                                    <button className="mt-4 inline-flex items-center gap-2 text-xs text-[#aaaab4] underline underline-offset-4 disabled:opacity-50" disabled={refreshing} onClick={() => void refreshProfiles()}><RefreshCw className={refreshing ? 'animate-spin' : ''} size={14}/>{t['setup.source.refresh']}</button>
                                </div> : null}
                                {selectedSourceOption && configurationOpen ? (
                                    <div className="mt-5 rounded-2xl border border-white/[0.08] bg-[#101014] p-5">
                                        <div className="mb-5 flex items-start justify-between gap-3"><div><h3 className="font-semibold">{t['setup.source.connect_title']}</h3><p className="mt-1 text-sm text-[#a0a0aa]">{t['setup.source.connect_hint']}</p></div><span className="rounded-lg bg-white/5 px-2 py-1 text-xs text-[#aaaab4]">{selectedSourceOption.label}</span></div>
                                        <h4 className="mb-3 text-sm font-semibold text-yellow-200">1. {t['setup.source.choose_model']}</h4>
                                        <label className="mb-3 block text-sm text-[#c3c3cb]">{t['setup.source.profile_search']}<input className={inputClass} onChange={(event) => setProfileSearch(event.target.value)} placeholder={t['setup.source.profile_search_placeholder']} value={profileSearch}/></label>
                                        <div aria-live="polite" className="mb-4">
                                            {profilesLoading ? <p className="text-sm text-[#a0a0aa]">{t['setup.source.profiles_loading']}</p> : profilesError ? <p className="text-sm text-red-300">{t['setup.source.profiles_error']}</p> : <>
                                                <label className="text-sm text-[#c3c3cb]">{t['setup.source.model']}<select aria-label={t['setup.source.model']} className={inputClass} value={providerValues[selectedSource]?.profile ?? ''} onChange={(event) => updateProviderValue(selectedSource, 'profile', event.target.value)}><option value="">{t['setup.source.model_placeholder']}</option>{selectedProfile && !filteredProfiles.includes(selectedProfile) ? <option value={selectedProfile.profileName}>{selectedProfile.profileName}</option> : null}{filteredProfiles.map((profile) => <option key={profile.profileName} value={profile.profileName}>{profile.profileName}</option>)}</select></label>
                                                {!filteredProfiles.length ? <p className="mt-2 text-sm text-yellow-200">{t['setup.source.profiles_empty']}</p> : null}
                                            </>}
                                        </div>
                                        <h4 className="mb-3 mt-6 text-sm font-semibold text-yellow-200">2. {t['setup.source.connect_address']}</h4>
                                        <ProviderFields fieldKeys={['host', 'serialPort', 'brokerUri', 'url', 'apiToken', 'username', 'password']} onChange={(key, value) => updateProviderValue(selectedSourceOption.id, key, value)} option={selectedSourceOption} values={providerValues[selectedSourceOption.id] ?? {}}/>
                                        {selectedSourceOption.fields.some((field) => !['profile', 'host', 'serialPort', 'brokerUri', 'url', 'apiToken', 'username', 'password'].includes(field.key)) ? <details className="mt-4 text-sm text-[#aaaab4]"><summary className="cursor-pointer">{t['setup.source.advanced']}</summary><div className="mt-4"><ProviderFields fieldKeys={selectedSourceOption.fields.filter((field) => !['profile', 'host', 'serialPort', 'brokerUri', 'url', 'apiToken', 'username', 'password'].includes(field.key)).map((field) => field.key)} onChange={(key, value) => updateProviderValue(selectedSourceOption.id, key, value)} option={selectedSourceOption} values={providerValues[selectedSourceOption.id] ?? {}}/></div></details> : null}
                                        <button className="mt-4 inline-flex items-center gap-2 rounded-xl bg-violet-400/10 px-4 py-2.5 text-sm font-semibold text-violet-200 disabled:opacity-50" disabled={profilesLoading || !providerFieldsComplete(selectedSourceOption) || testingProvider !== null} onClick={() => void testProvider(selectedSourceOption)}>{testingProvider === selectedSourceOption.id ? <LoaderCircle className="animate-spin" size={16}/> : <DatabaseZap size={16}/>} {t['setup.connection.test']}</button>
                                        <Verification state={verification[selectedSourceOption.id]}/>
                                        {selectedProfile ? (
                                            <div className="mt-5">
                                                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                                                    <p className="text-xs font-semibold uppercase tracking-wider text-[#777781]">3. {t['setup.source.select_components']}</p>
                                                    {verification[selectedSourceOption.id]?.valid ? (
                                                        <span className="inline-flex items-center gap-1.5 text-xs text-[#85858f]">
                                                            <span className={`h-2 w-2 rounded-full ${previewing ? 'animate-pulse bg-yellow-300' : 'bg-emerald-300'}`}/>
                                                            {previewing ? t['setup.source.preview.loading'] : t['setup.source.preview.live']}
                                                        </span>
                                                    ) : null}
                                                </div>
                                                <div className="grid gap-2 sm:grid-cols-2">
                                                    {selectedProfile.sections.map((section) => {
                                                        const checked = selectedSectionKeys.includes(section.sectionKey);
                                                        const preview = devicePreview?.sections.find((entry) => entry.sectionKey === section.sectionKey);
                                                        return (
                                                            <SectionPreviewCard
                                                                checked={checked}
                                                                key={section.sectionKey}
                                                                locale={locale}
                                                                onToggle={() => setSelectedSectionKeys((current) => checked ? current.filter((key) => key !== section.sectionKey) : [...current, section.sectionKey])}
                                                                preview={preview}
                                                                section={section}
                                                                t={t}
                                                            />
                                                        );
                                                    })}
                                                </div>
                                                {!verification[selectedSourceOption.id]?.valid ? <p className="mt-3 text-xs text-[#777781]">{t['setup.source.preview.test_hint']}</p> : null}
                                                {previewError ? <p className="mt-3 text-xs text-red-300">{t['setup.source.preview.error']}</p> : null}
                                            </div>
                                        ) : null}
                                        <div className="mt-5 flex flex-wrap gap-2">
                                            <button className="inline-flex items-center gap-2 rounded-xl bg-emerald-400 px-4 py-2.5 text-sm font-semibold text-black transition hover:bg-emerald-300 disabled:opacity-40" disabled={!verification[selectedSourceOption.id]?.valid || selectedSectionKeys.length === 0} onClick={addPvDevice}>
                                                <Plus size={16}/>{t['setup.source.add']}
                                            </button>
                                        </div>
                                        <p className="mt-3 text-xs leading-5 text-[#a0a0aa]">{t['setup.source.add_hint']}</p>
                                    </div>
                                ) : null}
                            </div>
                        ) : null}

                        {step === 2 ? (
                            <div className="space-y-5">
                                <div className="rounded-2xl border border-cyan-400/15 bg-cyan-400/[0.04] px-5 py-4 text-sm leading-6 text-[#c3d8dc]">
                                    <h3 className="font-semibold text-white">{t['setup.panels.what_title']}</h3>
                                    <p className="mt-1">{t['setup.panels.what_hint']}</p>
                                </div>
                                <div className="grid items-start gap-5 xl:grid-cols-[1.1fr_0.9fr]">
                                    <div className="rounded-2xl border border-white/[0.08] bg-[#101014] p-5 sm:p-6">
                                        <h3 className="text-lg font-semibold">{editingPanelId ? t['setup.panels.edit'] : t['setup.panels.new']}</h3>
                                        <p className="mt-1 text-sm leading-6 text-[#a0a0aa]">{t['setup.panels.form_hint']}</p>
                                        <div className="mt-6 border-t border-white/10 pt-5">
                                            <h4 className="text-sm font-semibold text-yellow-200">1. {t['setup.panels.power_title']}</h4>
                                            <div className="mt-3 grid gap-4 sm:grid-cols-2">
                                                <label className="text-sm text-[#c3c3cb]">{t['setup.panels.count']}<input aria-label={t['setup.panels.count']} className={inputClass} min="1" step="1" onChange={(event) => setPanelDraft((current) => ({...current, panelCount: Number(event.target.value)}))} type="number" value={panelDraft.panelCount}/></label>
                                                <label className="text-sm text-[#c3c3cb]">{t['setup.panels.power']} (Wp)<input aria-label={t['setup.panels.power']} className={inputClass} min="1" step="1" onChange={(event) => setPanelDraft((current) => ({...current, powerPerPanelWatts: Number(event.target.value)}))} type="number" value={panelDraft.powerPerPanelWatts}/></label>
                                            </div>
                                            <p className="mt-2 text-xs leading-5 text-[#a0a0aa]">{t['setup.panels.power_hint']}</p>
                                            <p aria-live="polite" className="mt-3 rounded-xl bg-emerald-400/[0.06] px-3 py-2 text-sm text-emerald-200">{t['setup.panels.total_power']}: <strong>{Number.isFinite(panelDraft.panelCount * panelDraft.powerPerPanelWatts) ? new Intl.NumberFormat(locale, {maximumFractionDigits: 2}).format(panelDraft.panelCount * panelDraft.powerPerPanelWatts / 1000) : '—'} kWp</strong></p>
                                        </div>
                                        <div className="mt-6 border-t border-white/10 pt-5">
                                            <h4 className="text-sm font-semibold text-yellow-200">2. {t['setup.panels.orientation_title']}</h4>
                                            <p className="mt-1 text-xs leading-5 text-[#a0a0aa]">{t['setup.panels.orientation_hint']}</p>
                                            <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-4" role="group" aria-label={t['setup.panels.azimuth']}>
                                                {[{key: 'north', value: 0}, {key: 'east', value: 90}, {key: 'south', value: 180}, {key: 'west', value: 270}].map((direction) => <button aria-pressed={panelDraft.azimuthDegrees === direction.value} className={panelDraft.azimuthDegrees === direction.value ? 'rounded-xl border border-yellow-400/60 bg-yellow-400/10 px-2 py-3 text-sm font-semibold text-yellow-200' : 'rounded-xl border border-white/10 px-2 py-3 text-sm text-[#b0b0ba] hover:border-white/25'} key={direction.key} onClick={() => setPanelDraft((current) => ({...current, azimuthDegrees: direction.value}))} type="button">{t['setup.panels.direction.' + direction.key]}</button>)}
                                            </div>
                                            <details className="mt-3 text-xs text-[#a0a0aa]"><summary className="cursor-pointer">{t['setup.panels.exact_direction']}</summary><label className="mt-3 block text-sm">{t['setup.panels.azimuth']}<input aria-label={t['setup.panels.azimuth']} className={inputClass} max="360" min="0" onChange={(event) => setPanelDraft((current) => ({...current, azimuthDegrees: Number(event.target.value)}))} type="number" value={panelDraft.azimuthDegrees}/></label></details>
                                            <label className="mt-5 block text-sm text-[#c3c3cb]">{t['setup.panels.tilt_title']} ({panelDraft.slopeDegrees}°)<input aria-label={t['setup.panels.tilt_title']} className="mt-3 block w-full accent-yellow-400" max="90" min="0" onChange={(event) => setPanelDraft((current) => ({...current, slopeDegrees: Number(event.target.value)}))} step="5" type="range" value={panelDraft.slopeDegrees}/></label>
                                            <div className="mt-1 flex justify-between text-xs text-[#a0a0aa]"><span>{t['setup.panels.flat']}</span><span>{t['setup.panels.steep']}</span></div>
                                            <details className="mt-3 text-xs text-[#a0a0aa]"><summary className="cursor-pointer">{t['setup.panels.exact_tilt']}</summary><label className="mt-3 block text-sm">{t['setup.panels.slope']}<input aria-label={t['setup.panels.slope']} className={inputClass} max="90" min="0" onChange={(event) => setPanelDraft((current) => ({...current, slopeDegrees: Number(event.target.value)}))} type="number" value={panelDraft.slopeDegrees}/></label></details>
                                        </div>
                                        <div className="mt-6 border-t border-white/10 pt-5">
                                            <h4 className="text-sm font-semibold text-yellow-200">3. {t['setup.panels.location_title']}</h4>
                                            <p className="mt-1 text-xs leading-5 text-[#a0a0aa]">{t['setup.panels.location_hint']}</p>
                                            <div className="mt-3 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-white/10 bg-black/20 p-3">
                                                <span className="flex items-center gap-2 text-sm"><MapPin className={panelLocationSelected ? 'text-emerald-300' : 'text-[#777781]'} size={17}/>{panelLocationSelected ? t['setup.panels.location_set'] : t['setup.panels.location_missing']}</span>
                                                <button aria-expanded={panelMapOpen} className="rounded-lg border border-white/15 px-3 py-2 text-sm font-semibold text-white hover:bg-white/[0.06]" onClick={() => setPanelMapOpen((open) => !open)} type="button">{panelLocationSelected ? t['setup.panels.change_location'] : t['setup.panels.select_location']}</button>
                                            </div>
                                            {panelLocationSelected ? <p className="mt-2 text-xs text-[#a0a0aa]">{panelDraft.latitude.toFixed(5)}, {panelDraft.longitude.toFixed(5)}</p> : null}
                                            {panelMapOpen ? <div className="mt-3 space-y-3"><PanelLocationMap labels={{select: t['details.panels.map.select'], move: t['details.panels.map.move'], selected: t['details.panels.map.selected'], useLocation: t['details.panels.map.use_location'], locationError: t['details.panels.map.location_error']}} onChange={setPanelLocation} value={panelDraft}/><details className="text-sm text-[#a0a0aa]"><summary className="cursor-pointer">{t['setup.panels.coordinates']}</summary><div className="mt-3 grid gap-3 sm:grid-cols-2"><label>{t['setup.panels.latitude']}<input aria-label={t['setup.panels.latitude']} className={inputClass} max="90" min="-90" onChange={(event) => changePanelCoordinate('latitude', event.target.value)} step="any" type="number" value={manualLatitude}/></label><label>{t['setup.panels.longitude']}<input aria-label={t['setup.panels.longitude']} className={inputClass} max="180" min="-180" onChange={(event) => changePanelCoordinate('longitude', event.target.value)} step="any" type="number" value={manualLongitude}/></label></div></details></div> : null}
                                        </div>
                                        <label className="mt-6 block text-sm text-[#c3c3cb]">{t['setup.panels.name_optional']}<input aria-label={t['setup.panels.name_optional']} className={inputClass} maxLength={120} onChange={(event) => setPanelDraft((current) => ({...current, name: event.target.value}))} placeholder={nextPanelName()} value={panelDraft.name}/></label>
                                        {panelFormError ? <p aria-live="assertive" className="mt-4 rounded-xl border border-red-400/20 bg-red-400/[0.07] p-3 text-sm text-red-300">{panelFormError}</p> : null}
                                        <div className="mt-5 flex flex-wrap gap-2"><button className="inline-flex flex-1 items-center justify-center gap-2 rounded-xl bg-emerald-400 px-4 py-3 font-semibold text-black transition hover:bg-emerald-300" onClick={addPanel} type="button">{editingPanelId ? <Pencil size={17}/> : <Plus size={17}/>} {editingPanelId ? t['setup.panels.save'] : t['setup.panels.add']}</button>{editingPanelId ? <button className="rounded-xl border border-white/10 px-4 py-3 text-sm text-[#b0b0ba]" onClick={cancelPanelEdit} type="button">{t['setup.panels.cancel']}</button> : null}</div>
                                    </div>
                                    <div className="rounded-2xl border border-white/[0.08] bg-[#101014] p-5 sm:p-6">
                                        <h3 className="font-semibold">{t['setup.panels.created']}</h3>
                                        {panels.length ? <><p className="mt-2 text-sm text-[#a0a0aa]">{panels.length} {t['setup.summary.panel_groups']} · {panels.reduce((sum, panel) => sum + panel.panelCount, 0)} {t['setup.summary.panels']} · {(panels.reduce((sum, panel) => sum + panel.panelCount * panel.powerPerPanelWatts, 0) / 1000).toFixed(2)} kWp</p><div className="mt-4 space-y-3">{panels.map((panel) => <article className={editingPanelId === panel.id ? 'rounded-xl border border-yellow-400/40 bg-yellow-400/[0.04] p-4' : 'rounded-xl border border-white/10 bg-black/20 p-4'} key={panel.id}><div className="flex items-start justify-between gap-3"><div className="min-w-0"><h4 className="truncate font-semibold">{panel.name}</h4><p className="mt-1 text-sm text-[#b0b0ba]">{panel.panelCount} × {panel.powerPerPanelWatts} Wp = {(panel.panelCount * panel.powerPerPanelWatts / 1000).toFixed(2)} kWp</p><p className="mt-1 text-xs text-[#a0a0aa]">{t['setup.panels.azimuth']}: {panel.azimuthDegrees}° · {t['setup.panels.slope']}: {panel.slopeDegrees}°</p><p className="mt-1 flex items-center gap-1 text-xs text-[#a0a0aa]"><MapPin size={12}/>{panel.latitude.toFixed(5)}, {panel.longitude.toFixed(5)}</p></div><div className="flex shrink-0 items-center gap-1"><button aria-label={t['setup.panels.edit'] + ': ' + panel.name} className="grid h-9 w-9 place-items-center rounded-lg text-[#b0b0ba] hover:bg-white/[0.06]" onClick={() => editPanel(panel)} type="button"><Pencil size={16}/></button><button aria-label={t['setup.panels.remove'] + ': ' + panel.name} className="grid h-9 w-9 place-items-center rounded-lg text-[#b0b0ba] hover:bg-red-400/10 hover:text-red-300" onClick={() => {setPanels((current) => current.filter((entry) => entry.id !== panel.id)); if (editingPanelId === panel.id) cancelPanelEdit();}} type="button"><Trash2 size={16}/></button></div></div></article>)}</div></> : <p className="mt-4 rounded-xl border border-dashed border-white/10 px-4 py-8 text-center text-sm leading-6 text-[#a0a0aa]">{t['setup.panels.empty']}</p>}
                                        <p className="mt-4 text-xs leading-5 text-[#a0a0aa]">{t['setup.panels.more_hint']}</p>
                                    </div>
                                </div>
                            </div>
                        ) : null}

                        {step === 3 ? (
                            <div><div className="mb-5 rounded-xl border border-cyan-400/15 bg-cyan-400/[0.05] px-4 py-3 text-sm leading-6 text-cyan-100">{t['setup.pools.optional_hint']}</div><div className="grid gap-4 lg:grid-cols-2">{catalog.miningPools.map((option) => {const selected = selectedPools.includes(option.id); return <article className={`rounded-2xl border p-5 transition ${selected ? 'border-yellow-400/40 bg-yellow-400/[0.04]' : 'border-white/[0.08] bg-[#101014]'}`} key={option.id}><button className="w-full text-left" onClick={() => {setSelectedPools((current) => selected ? current.filter((id) => id !== option.id) : [...current, option.id]); setError(null);}}><div className="flex items-start justify-between"><Pickaxe className={selected ? 'text-yellow-300' : 'text-[#777781]'} size={22}/><span className={`grid h-6 w-6 place-items-center rounded-md border ${selected ? 'border-yellow-400 bg-yellow-400 text-black' : 'border-white/15'}`}>{selected ? <Check size={14}/> : null}</span></div><h3 className="mt-4 font-semibold">{option.label}</h3><p className="mt-1.5 text-sm leading-5 text-[#85858f]">{option.description}</p></button>{selected ? <div className="mt-5 border-t border-white/[0.07] pt-5"><ProviderFields onChange={(key, value) => updateProviderValue(option.id, key, value)} option={option} values={providerValues[option.id] ?? {}}/><button className="mt-5 inline-flex items-center gap-2 rounded-xl bg-violet-400/10 px-4 py-2.5 text-sm font-semibold text-violet-200 transition hover:bg-violet-400/20 disabled:opacity-50" disabled={!providerFieldsComplete(option) || testingProvider === option.id} onClick={() => void testProvider(option)}>{testingProvider === option.id ? <LoaderCircle className="animate-spin" size={16}/> : <DatabaseZap size={16}/>} {t['setup.connection.test']}</button><Verification state={verification[option.id]}/></div> : null}</article>;})}</div></div>
                        ) : null}

                        {step === 4 ? (
                            <div className="max-w-xl"><label className="flex cursor-pointer items-center justify-between gap-4 rounded-2xl border border-white/[0.08] bg-[#101014] p-5 transition hover:border-yellow-400/30"><span className="flex items-start gap-3"><DatabaseZap className={`mt-0.5 shrink-0 ${telemetryEnabled ? 'text-emerald-300' : 'text-[#777781]'}`} size={22}/><span><span className="block text-sm font-semibold text-white">{t['setup.step.telemetry.label']}</span><span className="mt-1 block text-xs leading-5 text-[#85858f]">{t['setup.step.telemetry.hint']}</span></span></span><input checked={telemetryEnabled} className="h-5 w-5 shrink-0 accent-emerald-400" onChange={() => setTelemetryEnabled(!telemetryEnabled)} type="checkbox"/></label></div>
                        ) : null}
                        {step === 5 ? (
                            <div className="grid gap-4 md:grid-cols-2"><div className="rounded-2xl border border-white/[0.08] bg-[#101014] p-5"><CircleDollarSign className="text-emerald-300" size={21}/><h3 className="mt-3 font-semibold">{basics.name}</h3><p className="mt-2 text-sm text-[#85858f]">{basics.setupDate} · {basics.timeZone || timeZone}</p><p className="mt-1 text-sm text-[#85858f]">{t['setup.basics.electricity']}: {basics.electricityPrice} {currency}/kWh</p></div><div className="rounded-2xl border border-white/[0.08] bg-[#101014] p-5"><Server className="text-violet-300" size={21}/><h3 className="mt-3 font-semibold">{pvDevices.length} {t['setup.source.devices']}</h3><p className="mt-2 text-sm text-[#85858f]">{pvDevices.map((device) => `${device.label} (${device.values.host})`).join(', ')}</p></div><div className="rounded-2xl border border-white/[0.08] bg-[#101014] p-5"><Sun className="text-yellow-300" size={21}/><h3 className="mt-3 font-semibold">{panels.length} {t['setup.summary.panel_groups']}</h3><p className="mt-2 text-sm text-[#85858f]">{panels.reduce((sum, panel) => sum + panel.panelCount, 0)} {t['setup.summary.panels']} · {(panels.reduce((sum, panel) => sum + panel.panelCount * panel.powerPerPanelWatts, 0) / 1000).toFixed(2)} kWp</p></div><div className="rounded-2xl border border-white/[0.08] bg-[#101014] p-5"><Pickaxe className="text-cyan-300" size={21}/><h3 className="mt-3 font-semibold">{selectedPoolOptions.length ? selectedPoolOptions.map((pool) => pool.label).join(', ') : t['setup.summary.no_pool']}</h3><p className="mt-2 text-sm text-[#85858f]">{t['setup.summary.miners_later']}</p></div><div className="rounded-2xl border border-white/[0.08] bg-[#101014] p-5"><DatabaseZap className="text-cyan-300" size={21}/><h3 className="mt-3 font-semibold">{t['setup.summary.data_sharing']}</h3><p className="mt-2 text-sm text-[#85858f]">{telemetryEnabled ? t['setup.step.telemetry.on'] : t['setup.step.telemetry.off']}</p></div></div>
                        ) : null}
                    </div>

                    {error ? <div role="alert" className="mx-5 mb-4 rounded-xl border border-red-400/20 bg-red-400/[0.07] px-4 py-3 text-sm text-red-300 sm:mx-7">{error}</div> : null}
                    {step === 1 ? <p aria-live="polite" className="border-t border-white/[0.07] px-5 py-3 text-sm text-[#b0b0ba] sm:px-7">{pvDevices.length ? t['setup.source.ready'] : t['setup.source.continue_hint']}</p> : null}
                    <footer className="flex items-center justify-between gap-3 border-t border-white/[0.07] px-5 py-4 sm:px-7">
                        <button className="inline-flex items-center gap-2 rounded-xl border border-white/10 px-4 py-2.5 text-sm font-medium text-[#b8b8c1] transition hover:bg-white/[0.05] disabled:invisible" disabled={step === 0 || submitting} onClick={() => {setStep((current) => Math.max(0, current - 1)); setError(null);}}><ArrowLeft size={16}/>{t['setup.action.previous']}</button>
                        {step < steps.length - 1 ? <button className="inline-flex items-center gap-2 rounded-xl bg-yellow-400 px-5 py-2.5 text-sm font-semibold text-black transition hover:bg-yellow-300 disabled:opacity-40" disabled={step === 1 && pvDevices.length === 0} onClick={next}>{t['setup.action.next']}<ArrowRight size={16}/></button> : <button className="inline-flex items-center gap-2 rounded-xl bg-emerald-400 px-5 py-2.5 text-sm font-semibold text-black transition hover:bg-emerald-300 disabled:cursor-wait disabled:opacity-60" disabled={submitting} onClick={() => void submit()}>{submitting ? <LoaderCircle className="animate-spin" size={16}/> : <Check size={16}/>} {t['setup.action.create']}</button>}
                    </footer>
                </section>
            </div>
        </main>
    );
}
