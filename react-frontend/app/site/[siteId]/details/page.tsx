'use client';

import {FormEvent, PropsWithChildren, useCallback, useEffect, useMemo, useState} from 'react';
import dynamic from 'next/dynamic';
import {useParams} from 'next/navigation';
import {
    CalendarDays,
    CircleDollarSign,
    Cpu,
    Edit3,
    Grid3X3,
    Layers3,
    MapPin,
    Plus,
    RefreshCw,
    Ruler,
    Save,
    Sun,
    Trash2,
    X,
    Zap,
} from 'lucide-react';

import de from '../../../locales/de.json';
import en from '../../../locales/en.json';
import type {MinerCostDto, PanelGroupDto, PriceDto, PVSiteDetailsDto} from '../../../types';
import {useSitePreferences} from '../site-preferences-context';

const translations = {de, en};
const API_BASE_URL = 'http://localhost:8080/api';
const PanelLocationMap = dynamic(() => import('../../../components/panel-location-map'), {ssr: false});

type PriceType = 'feed-in' | 'electricity';

type DialogState =
    | {kind: 'site'}
    | {kind: 'panel'; panel: PanelGroupDto | null}
    | {kind: 'price'; priceType: PriceType}
    | {kind: 'miner'; miner: MinerCostDto}
    | null;

type PanelGroupDraft = Omit<PanelGroupDto, 'id' | 'peakPowerKw'>;

const inputClassName = 'mt-1.5 w-full rounded-xl border border-white/10 bg-[#0e0e11] px-3 py-2.5 text-sm text-white outline-none transition placeholder:text-[#5f5f68] focus:border-yellow-400/60 focus:ring-2 focus:ring-yellow-400/10';

function Modal({
    title,
    onClose,
    onSubmit,
    submitLabel,
    cancelLabel,
    saving,
    error,
    wide = false,
    children,
}: PropsWithChildren<{
    title: string;
    onClose: () => void;
    onSubmit: (event: FormEvent<HTMLFormElement>) => void;
    submitLabel: string;
    cancelLabel: string;
    saving: boolean;
    error: string | null;
    wide?: boolean;
}>) {
    return (
        <div aria-modal="true" className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-black/75 p-4 backdrop-blur-sm" role="dialog">
            <form className={`my-8 w-full overflow-hidden rounded-2xl border border-white/10 bg-[#18181c] shadow-2xl ${wide ? 'max-w-5xl' : 'max-w-2xl'}`} onSubmit={onSubmit}>
                <div className="flex items-center justify-between border-b border-white/[0.07] px-5 py-4">
                    <h2 className="text-lg font-semibold text-white">{title}</h2>
                    <button aria-label={cancelLabel} className="grid h-9 w-9 place-items-center rounded-lg text-[#8f8f99] transition hover:bg-white/[0.06] hover:text-white" onClick={onClose} type="button">
                        <X size={19}/>
                    </button>
                </div>
                <div className="max-h-[70vh] overflow-y-auto p-5">{children}</div>
                {error && <p className="mx-5 mb-4 rounded-xl border border-red-500/20 bg-red-500/[0.07] px-4 py-3 text-sm text-red-300">{error}</p>}
                <div className="flex justify-end gap-3 border-t border-white/[0.07] px-5 py-4">
                    <button className="rounded-xl border border-white/10 px-4 py-2.5 text-sm font-medium text-[#b7b7c0] transition hover:bg-white/[0.05] hover:text-white" disabled={saving} onClick={onClose} type="button">
                        {cancelLabel}
                    </button>
                    <button className="inline-flex items-center gap-2 rounded-xl bg-yellow-400 px-4 py-2.5 text-sm font-semibold text-black transition hover:bg-yellow-300 disabled:cursor-wait disabled:opacity-60" disabled={saving} type="submit">
                        {saving ? <RefreshCw className="animate-spin" size={16}/> : <Save size={16}/>}
                        {submitLabel}
                    </button>
                </div>
            </form>
        </div>
    );
}

function InfoRow({label, value}: {label: string; value: string}) {
    return (
        <div className="flex items-center justify-between gap-4 border-b border-white/5 py-3 last:border-0">
            <span className="text-sm text-[#92929d]">{label}</span>
            <span className="text-right text-sm font-medium text-[#f5f5f7]">{value}</span>
        </div>
    );
}

function PriceTable({
    title,
    prices,
    emptyLabel,
    validFromLabel,
    amountLabel,
    formatDate,
    addLabel,
    deleteLabel,
    onAdd,
    onDelete,
}: {
    title: string;
    prices: PriceDto[];
    emptyLabel: string;
    validFromLabel: string;
    amountLabel: string;
    formatDate: (date: string) => string;
    addLabel: string;
    deleteLabel: string;
    onAdd: () => void;
    onDelete: (price: PriceDto) => void;
}) {
    return (
        <section className="overflow-hidden rounded-2xl border border-white/[0.07] bg-[#151518]">
            <div className="flex items-center justify-between gap-3 border-b border-white/[0.07] px-5 py-4">
                <div className="flex items-center gap-3">
                    <CircleDollarSign className="text-emerald-400" size={19}/>
                    <h3 className="font-semibold text-white">{title}</h3>
                </div>
                <button className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-400/10 px-3 py-2 text-xs font-semibold text-emerald-300 transition hover:bg-emerald-400/20" onClick={onAdd} type="button">
                    <Plus size={14}/>{addLabel}
                </button>
            </div>
            {prices.length === 0 ? (
                <p className="px-5 py-8 text-sm text-[#85858f]">{emptyLabel}</p>
            ) : (
                <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm">
                        <thead className="bg-white/[0.025] text-xs uppercase tracking-[0.12em] text-[#777781]">
                        <tr>
                            <th className="px-5 py-3 font-medium">{validFromLabel}</th>
                            <th className="px-5 py-3 text-right font-medium">{amountLabel}</th>
                            <th className="w-14 px-3 py-3"><span className="sr-only">{deleteLabel}</span></th>
                        </tr>
                        </thead>
                        <tbody className="divide-y divide-white/5">
                        {prices.map((price, index) => (
                            <tr className="transition-colors hover:bg-white/[0.025]" key={`${price.validFrom}-${price.price.currency}-${index}`}>
                                <td className="px-5 py-3.5 text-[#b7b7c0]">{formatDate(price.validFrom)}</td>
                                <td className="px-5 py-3.5 text-right font-medium text-white">{price.price.formatted}</td>
                                <td className="px-3 py-2 text-right">
                                    <button aria-label={deleteLabel} className="grid h-8 w-8 place-items-center rounded-lg text-[#767680] transition hover:bg-red-500/10 hover:text-red-400" onClick={() => onDelete(price)} type="button">
                                        <Trash2 size={15}/>
                                    </button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}
        </section>
    );
}

export default function PVSiteDetailsPage() {
    const params = useParams<{siteId: string}>();
    const siteId = params.siteId;
    const {locale, currency, isHydrated} = useSitePreferences();
    const t = translations[locale] as Record<string, string>;

    const [details, setDetails] = useState<PVSiteDetailsDto | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [dialog, setDialog] = useState<DialogState>(null);
    const [saving, setSaving] = useState(false);
    const [mutationError, setMutationError] = useState<string | null>(null);
    const [siteDraft, setSiteDraft] = useState({setupDate: '', timeZone: '', pvCost: 0});
    const [panelDraft, setPanelDraft] = useState<PanelGroupDraft>({
        name: '',
        latitude: 0,
        longitude: 0,
        panelCount: 1,
        powerPerPanelWatts: 400,
        azimuthDegrees: 180,
        slopeDegrees: 30,
    });
    const [priceDraft, setPriceDraft] = useState({validFrom: '', amount: 0});
    const [minerCostDraft, setMinerCostDraft] = useState(0);

    const numberLocale = locale === 'de' ? 'de-DE' : 'en-US';
    const numberFormatter = useMemo(
        () => new Intl.NumberFormat(numberLocale, {maximumFractionDigits: 2}),
        [numberLocale],
    );
    const dateFormatter = useMemo(
        () => new Intl.DateTimeFormat(numberLocale, {dateStyle: 'medium'}),
        [numberLocale],
    );
    const timeZones = useMemo(() => {
        try {
            const intl = Intl as typeof Intl & {supportedValuesOf?: (key: 'timeZone') => string[]};
            return intl.supportedValuesOf?.('timeZone') ?? ['Europe/Berlin', 'UTC'];
        } catch {
            return ['Europe/Berlin', 'UTC'];
        }
    }, []);

    const loadDetails = useCallback(async (signal?: AbortSignal) => {
        setLoading(true);
        setError(null);

        const query = new URLSearchParams({locale, currency});
        try {
            const response = await fetch(
                `${API_BASE_URL}/pv-site/${siteId}/details?${query.toString()}`,
                {signal},
            );
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const data: PVSiteDetailsDto = await response.json();
            if (!signal?.aborted) setDetails(data);
        } catch (requestError) {
            if (signal?.aborted) return;
            setError(requestError instanceof Error ? requestError.message : 'Unknown error');
        } finally {
            if (!signal?.aborted) setLoading(false);
        }
    }, [currency, locale, siteId]);

    useEffect(() => {
        if (!siteId || !isHydrated) return;

        const controller = new AbortController();
        const timeout = window.setTimeout(() => {
            void loadDetails(controller.signal);
        }, 0);

        return () => {
            window.clearTimeout(timeout);
            controller.abort();
        };
    }, [isHydrated, loadDetails, siteId]);

    const mutateDetails = async (path: string, init: RequestInit) => {
        setSaving(true);
        setMutationError(null);
        const query = new URLSearchParams({locale, currency});

        try {
            const response = await fetch(
                `${API_BASE_URL}/pv-site/${siteId}/details${path}?${query.toString()}`,
                {
                    ...init,
                    headers: {'Content-Type': 'application/json', ...init.headers},
                },
            );
            if (!response.ok) {
                const problem = await response.json().catch(() => null) as {detail?: string} | null;
                throw new Error(problem?.detail || `HTTP ${response.status}`);
            }

            const data: PVSiteDetailsDto = await response.json();
            setDetails(data);
            setDialog(null);
        } catch (requestError) {
            setMutationError(requestError instanceof Error ? requestError.message : t['details.error.save']);
        } finally {
            setSaving(false);
        }
    };

    const openSiteEditor = () => {
        setMutationError(null);
        setSiteDraft({
            setupDate: details?.setupDate ?? '',
            timeZone: details?.timeZone ?? 'Europe/Berlin',
            pvCost: details?.pvCost.amount ?? 0,
        });
        setDialog({kind: 'site'});
    };

    const openPanelEditor = (panel: PanelGroupDto | null) => {
        setMutationError(null);
        const referencePanel = details?.panelGroups.find((group) => group.latitude !== 0 || group.longitude !== 0);
        setPanelDraft(panel ? {
            name: panel.name ?? '',
            latitude: panel.latitude,
            longitude: panel.longitude,
            panelCount: panel.panelCount,
            powerPerPanelWatts: panel.powerPerPanelWatts,
            azimuthDegrees: panel.azimuthDegrees,
            slopeDegrees: panel.slopeDegrees,
        } : {
            name: t['details.panels.new_name'],
            latitude: referencePanel?.latitude ?? 0,
            longitude: referencePanel?.longitude ?? 0,
            panelCount: 1,
            powerPerPanelWatts: 400,
            azimuthDegrees: 180,
            slopeDegrees: 30,
        });
        setDialog({kind: 'panel', panel});
    };

    const openPriceEditor = (priceType: PriceType) => {
        setMutationError(null);
        setPriceDraft({
            validFrom: new Date().toLocaleDateString('sv-SE'),
            amount: 0,
        });
        setDialog({kind: 'price', priceType});
    };

    const openMinerCostEditor = (miner: MinerCostDto) => {
        setMutationError(null);
        setMinerCostDraft(miner.cost.amount);
        setDialog({kind: 'miner', miner});
    };

    const submitDialog = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!dialog) return;

        if (dialog.kind === 'site') {
            void mutateDetails('', {
                method: 'PUT',
                body: JSON.stringify({...siteDraft, currency}),
            });
        } else if (dialog.kind === 'panel') {
            void mutateDetails(
                dialog.panel ? `/panel-groups/${dialog.panel.id}` : '/panel-groups',
                {
                    method: dialog.panel ? 'PUT' : 'POST',
                    body: JSON.stringify(panelDraft),
                },
            );
        } else if (dialog.kind === 'price') {
            void mutateDetails(`/prices/${dialog.priceType}`, {
                method: 'POST',
                body: JSON.stringify({...priceDraft, currency}),
            });
        } else {
            void mutateDetails(`/miners/${dialog.miner.id}/cost`, {
                method: 'PUT',
                body: JSON.stringify({amount: minerCostDraft, currency}),
            });
        }
    };

    const deletePanel = (panel: PanelGroupDto) => {
        if (!window.confirm(t['details.panels.delete_confirm'])) return;
        void mutateDetails(`/panel-groups/${panel.id}`, {method: 'DELETE'});
    };

    const deletePrice = (priceType: PriceType, price: PriceDto) => {
        if (!window.confirm(t['details.prices.delete_confirm'])) return;
        void mutateDetails(`/prices/${priceType}/${price.validFrom}`, {method: 'DELETE'});
    };

    const formatDate = (date: string) => dateFormatter.format(new Date(`${date}T00:00:00`));

    if (loading && !details) {
        return (
            <div className="grid min-h-[calc(100vh-72px)] place-items-center bg-[#0b0b0d] text-[#b8b8c1]">
                <div className="flex items-center gap-3">
                    <RefreshCw className="animate-spin text-yellow-400" size={20}/>
                    <span>{t['details.loading']}</span>
                </div>
            </div>
        );
    }

    if (!details || error) {
        return (
            <div className="grid min-h-[calc(100vh-72px)] place-items-center bg-[#0b0b0d] px-6">
                <div className="max-w-md rounded-2xl border border-red-500/20 bg-red-500/[0.06] p-7 text-center">
                    <h1 className="text-xl font-semibold text-white">{t['details.error.title']}</h1>
                    <p className="mt-2 text-sm leading-6 text-[#a6a6b0]">{t['details.error.message']}</p>
                    <button
                        className="mt-5 rounded-xl bg-white px-4 py-2 text-sm font-semibold text-black transition hover:bg-[#dddddf]"
                        onClick={() => void loadDetails()}
                        type="button"
                    >
                        {t['details.error.retry']}
                    </button>
                </div>
            </div>
        );
    }

    const kpis = [
        {
            label: t['details.kpi.peak_power'],
            value: `${numberFormatter.format(details.totalPeakPowerKw)} kWp`,
            icon: Sun,
            accent: 'text-yellow-400 bg-yellow-400/10',
        },
        {
            label: t['details.kpi.panels'],
            value: numberFormatter.format(details.totalPanels),
            icon: Grid3X3,
            accent: 'text-sky-400 bg-sky-400/10',
        },
        {
            label: t['details.kpi.groups'],
            value: numberFormatter.format(details.totalGroups),
            icon: Layers3,
            accent: 'text-violet-400 bg-violet-400/10',
        },
        {
            label: t['details.kpi.miners'],
            value: numberFormatter.format(details.miners.length),
            icon: Cpu,
            accent: 'text-orange-400 bg-orange-400/10',
        },
    ];

    return (
        <div className="min-h-[calc(100vh-72px)] bg-[#0b0b0d] px-4 py-7 text-white sm:px-6 lg:px-8">
            <div className="mx-auto max-w-[1440px] space-y-7">
                <header className="relative overflow-hidden rounded-3xl border border-white/[0.08] bg-[#151518] px-6 py-7 sm:px-8">
                    <div className="pointer-events-none absolute -right-24 -top-32 h-72 w-72 rounded-full bg-yellow-400/[0.08] blur-3xl"/>
                    <div className="relative flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
                        <div>
                            <div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.2em] text-yellow-400">
                                <Zap size={14}/>
                                {t['details.eyebrow']}
                            </div>
                            <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{details.name}</h1>
                            <p className="mt-2 max-w-2xl text-sm leading-6 text-[#9999a3]">{t['details.subtitle']}</p>
                        </div>
                        <button
                            className="inline-flex items-center justify-center gap-2 rounded-xl border border-white/10 bg-white/[0.05] px-4 py-2.5 text-sm font-medium transition hover:border-white/20 hover:bg-white/[0.09] disabled:opacity-50"
                            disabled={loading}
                            onClick={() => void loadDetails()}
                            type="button"
                        >
                            <RefreshCw className={loading ? 'animate-spin' : ''} size={16}/>
                            {t['details.action.refresh']}
                        </button>
                    </div>
                </header>

                {mutationError && !dialog && (
                    <div className="flex items-center justify-between gap-4 rounded-xl border border-red-500/20 bg-red-500/[0.07] px-4 py-3 text-sm text-red-300">
                        <span>{mutationError}</span>
                        <button aria-label={t['details.action.close']} onClick={() => setMutationError(null)} type="button"><X size={17}/></button>
                    </div>
                )}

                <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                    {kpis.map(({label, value, icon: Icon, accent}) => (
                        <article className="rounded-2xl border border-white/[0.07] bg-[#151518] p-5" key={label}>
                            <div className="flex items-start justify-between gap-4">
                                <div>
                                    <p className="text-sm text-[#888892]">{label}</p>
                                    <p className="mt-2 text-2xl font-bold tracking-tight">{value}</p>
                                </div>
                                <span className={`grid h-10 w-10 place-items-center rounded-xl ${accent}`}>
                                    <Icon size={20}/>
                                </span>
                            </div>
                        </article>
                    ))}
                </section>

                <div className="grid gap-6 xl:grid-cols-12">
                    <section className="rounded-2xl border border-white/[0.07] bg-[#151518] p-5 xl:col-span-4">
                        <div className="mb-3 flex items-center justify-between gap-3">
                            <div className="flex items-center gap-3">
                                <CalendarDays className="text-sky-400" size={20}/>
                                <h2 className="text-lg font-semibold">{t['details.config.title']}</h2>
                            </div>
                            <button aria-label={t['details.action.edit']} className="grid h-9 w-9 place-items-center rounded-lg text-[#85858f] transition hover:bg-sky-400/10 hover:text-sky-300" onClick={openSiteEditor} type="button">
                                <Edit3 size={16}/>
                            </button>
                        </div>
                        <InfoRow label={t['details.config.setup_date']} value={formatDate(details.setupDate)}/>
                        <InfoRow label={t['details.config.time_zone']} value={details.timeZone}/>
                        <InfoRow label={t['details.config.pv_cost']} value={details.pvCost.formatted ?? `${numberFormatter.format(details.pvCost.amount)} ${details.pvCost.currency}`}/>
                    </section>

                    <section className="rounded-2xl border border-white/[0.07] bg-[#151518] p-5 xl:col-span-8">
                        <div className="mb-4 flex items-center justify-between gap-3">
                            <div className="flex items-center gap-3">
                                <Layers3 className="text-violet-400" size={20}/>
                                <h2 className="text-lg font-semibold">{t['details.panels.title']}</h2>
                            </div>
                            <button className="inline-flex items-center gap-1.5 rounded-lg bg-violet-400/10 px-3 py-2 text-xs font-semibold text-violet-300 transition hover:bg-violet-400/20" onClick={() => openPanelEditor(null)} type="button">
                                <Plus size={14}/>{t['details.panels.add']}
                            </button>
                        </div>
                        {details.panelGroups.length === 0 ? (
                            <p className="py-8 text-sm text-[#85858f]">{t['details.panels.empty']}</p>
                        ) : (
                            <div className="grid gap-4 md:grid-cols-2">
                                {details.panelGroups.map((group) => (
                                    <article className="rounded-xl border border-white/[0.07] bg-[#0f0f12] p-4" key={group.id}>
                                        <div className="flex items-center justify-between gap-3">
                                            <h3 className="truncate font-semibold">{group.name || '—'}</h3>
                                            <div className="flex items-center gap-1">
                                                <span className="rounded-lg bg-yellow-400/10 px-2.5 py-1 text-xs font-semibold text-yellow-400">
                                                    {numberFormatter.format(group.peakPowerKw)} kWp
                                                </span>
                                                <button aria-label={t['details.action.edit']} className="grid h-8 w-8 place-items-center rounded-lg text-[#777781] transition hover:bg-white/[0.06] hover:text-white" onClick={() => openPanelEditor(group)} type="button"><Edit3 size={14}/></button>
                                                <button aria-label={t['details.action.delete']} className="grid h-8 w-8 place-items-center rounded-lg text-[#777781] transition hover:bg-red-500/10 hover:text-red-400" onClick={() => deletePanel(group)} type="button"><Trash2 size={14}/></button>
                                            </div>
                                        </div>
                                        <div className="mt-4 grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
                                            <div>
                                                <p className="text-xs text-[#75757f]">{t['details.panels.panel_count']}</p>
                                                <p className="mt-1 font-medium">{numberFormatter.format(group.panelCount)}</p>
                                            </div>
                                            <div>
                                                <p className="text-xs text-[#75757f]">{t['details.panels.panel_power']}</p>
                                                <p className="mt-1 font-medium">{numberFormatter.format(group.powerPerPanelWatts)} W</p>
                                            </div>
                                            <div>
                                                <p className="flex items-center gap-1 text-xs text-[#75757f]"><Ruler size={12}/>{t['details.panels.orientation']}</p>
                                                <p className="mt-1 font-medium">{numberFormatter.format(group.azimuthDegrees)}° / {numberFormatter.format(group.slopeDegrees)}°</p>
                                            </div>
                                            <div>
                                                <p className="flex items-center gap-1 text-xs text-[#75757f]"><MapPin size={12}/>{t['details.panels.location']}</p>
                                                <p className="mt-1 truncate font-medium" title={`${group.latitude}, ${group.longitude}`}>
                                                    {numberFormatter.format(group.latitude)}, {numberFormatter.format(group.longitude)}
                                                </p>
                                            </div>
                                        </div>
                                    </article>
                                ))}
                            </div>
                        )}
                    </section>
                </div>

                <section className="overflow-hidden rounded-2xl border border-white/[0.07] bg-[#151518]">
                    <div className="flex items-center gap-3 border-b border-white/[0.07] px-5 py-4">
                        <Cpu className="text-orange-400" size={20}/>
                        <h2 className="text-lg font-semibold">{t['details.hardware.title']}</h2>
                    </div>
                    {details.miners.length === 0 ? (
                        <p className="px-5 py-8 text-sm text-[#85858f]">{t['details.hardware.empty']}</p>
                    ) : (
                        <div className="overflow-x-auto">
                            <table className="w-full min-w-[620px] text-left text-sm">
                                <thead className="bg-white/[0.025] text-xs uppercase tracking-[0.12em] text-[#777781]">
                                <tr>
                                    <th className="px-5 py-3 font-medium">{t['details.hardware.name']}</th>
                                    <th className="px-5 py-3 font-medium">{t['details.hardware.ip']}</th>
                                    <th className="px-5 py-3 text-right font-medium">{t['details.hardware.cost']}</th>
                                    <th className="w-14 px-3 py-3"><span className="sr-only">{t['details.action.edit']}</span></th>
                                </tr>
                                </thead>
                                <tbody className="divide-y divide-white/5">
                                {details.miners.map((miner) => (
                                    <tr className="transition-colors hover:bg-white/[0.025]" key={miner.id}>
                                        <td className="px-5 py-4 font-medium text-white">{miner.name}</td>
                                        <td className="px-5 py-4 font-mono text-xs text-[#a4a4ae]">{miner.ipAddress}</td>
                                        <td className="px-5 py-4 text-right font-medium text-white">{miner.cost.formatted}</td>
                                        <td className="px-3 py-2 text-right">
                                            <button aria-label={t['details.action.edit']} className="grid h-8 w-8 place-items-center rounded-lg text-[#767680] transition hover:bg-orange-400/10 hover:text-orange-300" onClick={() => openMinerCostEditor(miner)} type="button"><Edit3 size={15}/></button>
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </section>

                <section>
                    <div className="mb-4 flex items-center gap-3">
                        <CircleDollarSign className="text-emerald-400" size={20}/>
                        <h2 className="text-lg font-semibold">{t['details.prices.title']}</h2>
                    </div>
                    <div className="grid gap-6 lg:grid-cols-2">
                        <PriceTable
                            addLabel={t['details.prices.add']}
                            amountLabel={t['details.prices.amount']}
                            deleteLabel={t['details.action.delete']}
                            emptyLabel={t['details.prices.empty']}
                            formatDate={formatDate}
                            onAdd={() => openPriceEditor('feed-in')}
                            onDelete={(price) => deletePrice('feed-in', price)}
                            prices={details.feedInTariffs}
                            title={t['details.prices.feed_in']}
                            validFromLabel={t['details.prices.valid_from']}
                        />
                        <PriceTable
                            addLabel={t['details.prices.add']}
                            amountLabel={t['details.prices.amount']}
                            deleteLabel={t['details.action.delete']}
                            emptyLabel={t['details.prices.empty']}
                            formatDate={formatDate}
                            onAdd={() => openPriceEditor('electricity')}
                            onDelete={(price) => deletePrice('electricity', price)}
                            prices={details.electricityPrices}
                            title={t['details.prices.electricity']}
                            validFromLabel={t['details.prices.valid_from']}
                        />
                    </div>
                </section>
            </div>

            {dialog?.kind === 'site' && (
                <Modal
                    cancelLabel={t['details.action.cancel']}
                    error={mutationError}
                    onClose={() => setDialog(null)}
                    onSubmit={submitDialog}
                    saving={saving}
                    submitLabel={t['details.action.save']}
                    title={t['details.config.edit_title']}
                >
                    <div className="grid gap-5 sm:grid-cols-2">
                        <label className="text-sm text-[#b7b7c0]">
                            {t['details.config.setup_date']}
                            <input className={inputClassName} onChange={(event) => setSiteDraft((draft) => ({...draft, setupDate: event.target.value}))} required type="date" value={siteDraft.setupDate}/>
                        </label>
                        <label className="text-sm text-[#b7b7c0]">
                            {t['details.config.pv_cost']} ({currency})
                            <input className={inputClassName} min="0" onChange={(event) => setSiteDraft((draft) => ({...draft, pvCost: Number(event.target.value)}))} required step="0.01" type="number" value={siteDraft.pvCost}/>
                        </label>
                        <label className="text-sm text-[#b7b7c0] sm:col-span-2">
                            {t['details.config.time_zone']}
                            <select className={inputClassName} onChange={(event) => setSiteDraft((draft) => ({...draft, timeZone: event.target.value}))} required value={siteDraft.timeZone}>
                                {[...new Set([siteDraft.timeZone, ...timeZones])].filter(Boolean).map((zone) => <option key={zone} value={zone}>{zone}</option>)}
                            </select>
                        </label>
                    </div>
                </Modal>
            )}

            {dialog?.kind === 'panel' && (
                <Modal
                    cancelLabel={t['details.action.cancel']}
                    error={mutationError}
                    onClose={() => setDialog(null)}
                    onSubmit={submitDialog}
                    saving={saving}
                    submitLabel={t['details.action.save']}
                    title={dialog.panel ? t['details.panels.edit_title'] : t['details.panels.create_title']}
                    wide
                >
                    <div className="grid gap-5 sm:grid-cols-2">
                        <label className="text-sm text-[#b7b7c0] sm:col-span-2">
                            {t['details.panels.name']}
                            <input className={inputClassName} maxLength={120} onChange={(event) => setPanelDraft((draft) => ({...draft, name: event.target.value}))} required value={panelDraft.name ?? ''}/>
                        </label>
                        <div className="sm:col-span-2">
                            <div className="mb-3">
                                <h3 className="font-semibold text-white">{t['details.panels.map.title']}</h3>
                                <p className="mt-1 text-sm leading-6 text-[#92929c]">{t['details.panels.map.description']}</p>
                            </div>
                            <PanelLocationMap
                                labels={{
                                    select: t['details.panels.map.select'],
                                    move: t['details.panels.map.move'],
                                    selected: t['details.panels.map.selected'],
                                    useLocation: t['details.panels.map.use_location'],
                                    locationError: t['details.panels.map.location_error'],
                                }}
                                onChange={(location) => setPanelDraft((draft) => ({...draft, ...location}))}
                                value={panelDraft}
                            />
                        </div>
                        <label className="text-sm text-[#b7b7c0]">
                            {t['details.panels.panel_count']}
                            <input className={inputClassName} min="1" onChange={(event) => setPanelDraft((draft) => ({...draft, panelCount: Number(event.target.value)}))} required step="1" type="number" value={panelDraft.panelCount}/>
                        </label>
                        <label className="text-sm text-[#b7b7c0]">
                            {t['details.panels.panel_power']} (W)
                            <input className={inputClassName} min="0.01" onChange={(event) => setPanelDraft((draft) => ({...draft, powerPerPanelWatts: Number(event.target.value)}))} required step="0.01" type="number" value={panelDraft.powerPerPanelWatts}/>
                        </label>
                        <label className="text-sm text-[#b7b7c0]">
                            {t['details.panels.latitude']}
                            <input className={inputClassName} max="90" min="-90" onChange={(event) => setPanelDraft((draft) => ({...draft, latitude: Number(event.target.value)}))} required step="0.000001" type="number" value={panelDraft.latitude}/>
                        </label>
                        <label className="text-sm text-[#b7b7c0]">
                            {t['details.panels.longitude']}
                            <input className={inputClassName} max="180" min="-180" onChange={(event) => setPanelDraft((draft) => ({...draft, longitude: Number(event.target.value)}))} required step="0.000001" type="number" value={panelDraft.longitude}/>
                        </label>
                        <label className="text-sm text-[#b7b7c0]">
                            {t['details.panels.azimuth']}
                            <input className={inputClassName} max="360" min="0" onChange={(event) => setPanelDraft((draft) => ({...draft, azimuthDegrees: Number(event.target.value)}))} required step="0.1" type="number" value={panelDraft.azimuthDegrees}/>
                        </label>
                        <label className="text-sm text-[#b7b7c0]">
                            {t['details.panels.slope']}
                            <input className={inputClassName} max="90" min="0" onChange={(event) => setPanelDraft((draft) => ({...draft, slopeDegrees: Number(event.target.value)}))} required step="0.1" type="number" value={panelDraft.slopeDegrees}/>
                        </label>
                    </div>
                </Modal>
            )}

            {dialog?.kind === 'price' && (
                <Modal
                    cancelLabel={t['details.action.cancel']}
                    error={mutationError}
                    onClose={() => setDialog(null)}
                    onSubmit={submitDialog}
                    saving={saving}
                    submitLabel={t['details.action.save']}
                    title={dialog.priceType === 'feed-in' ? t['details.prices.add_feed_in'] : t['details.prices.add_electricity']}
                >
                    <div className="grid gap-5 sm:grid-cols-2">
                        <label className="text-sm text-[#b7b7c0]">
                            {t['details.prices.valid_from']}
                            <input className={inputClassName} onChange={(event) => setPriceDraft((draft) => ({...draft, validFrom: event.target.value}))} required type="date" value={priceDraft.validFrom}/>
                        </label>
                        <label className="text-sm text-[#b7b7c0]">
                            {t['details.prices.amount']} ({currency})
                            <input className={inputClassName} min="0" onChange={(event) => setPriceDraft((draft) => ({...draft, amount: Number(event.target.value)}))} required step="0.0001" type="number" value={priceDraft.amount}/>
                        </label>
                    </div>
                </Modal>
            )}

            {dialog?.kind === 'miner' && (
                <Modal
                    cancelLabel={t['details.action.cancel']}
                    error={mutationError}
                    onClose={() => setDialog(null)}
                    onSubmit={submitDialog}
                    saving={saving}
                    submitLabel={t['details.action.save']}
                    title={`${t['details.hardware.edit_cost']}: ${dialog.miner.name}`}
                >
                    <label className="block text-sm text-[#b7b7c0]">
                        {t['details.hardware.cost']} ({currency})
                        <input autoFocus className={inputClassName} min="0" onChange={(event) => setMinerCostDraft(Number(event.target.value))} required step="0.01" type="number" value={minerCostDraft}/>
                    </label>
                </Modal>
            )}
        </div>
    );
}
