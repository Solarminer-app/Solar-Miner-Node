'use client';

import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import Link from 'next/link';
import {Activity, AlertTriangle, Bolt, CheckCircle2, Cpu, Gauge, LoaderCircle, Network, Pause, Play, Plus, Radio, RefreshCw, Search, Server, Settings2, ShieldAlert, Thermometer, Trash2, Unlink, WalletCards, X} from 'lucide-react';
import {useParams} from 'next/navigation';

import de from '../../../locales/de.json';
import en from '../../../locales/en.json';
import type {DiscoveredMinerDto, MinerDto, MiningPageDto} from '../../../types';
import {useSitePreferences} from '../site-preferences-context';

const translations = {de, en};
const ELECTRICAL_RISK_THRESHOLD_WATTS = 3200;

const inputClassName = 'mt-1.5 w-full rounded-lg border border-[#34343c] bg-[#101013] px-3 py-2.5 text-sm text-white outline-none transition focus:border-yellow-400/60 focus:ring-2 focus:ring-yellow-400/10';

export default function MiningPage() {
    const {siteId} = useParams<{siteId: string}>();
    const {locale, isHydrated} = useSitePreferences();
    const t = translations[locale] as Record<string, string>;

    const [data, setData] = useState<MiningPageDto | null>(null);
    const [selectedClusterName, setSelectedClusterName] = useState<string | null>(null);
    const [selectedMinerIds, setSelectedMinerIds] = useState<string[]>([]);
    const [assignMinerIds, setAssignMinerIds] = useState<string[]>([]);
    const [showAssignment, setShowAssignment] = useState(false);
    const [section, setSection] = useState<'inventory' | 'clusters'>('inventory');
    const [showMinerConnection, setShowMinerConnection] = useState(false);
    const [showPoolConnection, setShowPoolConnection] = useState(false);
    const [powerTargetMiner, setPowerTargetMiner] = useState<MinerDto | null>(null);
    const [minimumPowerWatts, setMinimumPowerWatts] = useState(0);
    const [maximumPowerWatts, setMaximumPowerWatts] = useState(0);
    const [electricalRiskAcknowledged, setElectricalRiskAcknowledged] = useState(false);
    const [subnet, setSubnet] = useState('192.168.178');
    const [discoveredMiners, setDiscoveredMiners] = useState<DiscoveredMinerDto[]>([]);
    const [selectedDiscoveredMiner, setSelectedDiscoveredMiner] = useState<DiscoveredMinerDto | null>(null);
    const [username, setUsername] = useState('root');
    const [password, setPassword] = useState('root');
    const [poolToken, setPoolToken] = useState('');
    const [referralInput, setReferralInput] = useState('');
    const [savingReferral, setSavingReferral] = useState(false);
    const referralEditing = useRef(false);
    const [scanning, setScanning] = useState(false);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [deletingItem, setDeletingItem] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    const loadData = useCallback(async (signal?: AbortSignal) => {
        try {
            const response = await fetch(`/api/pv-site/${siteId}/mining`, {signal});
            if (!response.ok) throw new Error(String(response.status));
            const nextData: MiningPageDto = await response.json();
            if (signal?.aborted) return;

            setData(nextData);
            if (!referralEditing.current) setReferralInput(nextData.devFee?.referralCode ?? '');
            setSelectedClusterName((current) => {
                if (current && nextData.clusters.some((cluster) => cluster.name === current)) return current;
                return nextData.clusters[0]?.name ?? null;
            });
            setError(null);
        } catch (reason) {
            if (signal?.aborted) return;
            console.error('Failed to load mining page', reason);
            setError(t['mining.error.load']);
        } finally {
            if (!signal?.aborted) setLoading(false);
        }
    }, [siteId, t]);

    useEffect(() => {
        if (!siteId || !isHydrated) return;
        const controller = new AbortController();
        const refresh = () => void loadData(controller.signal);
        const timeout = window.setTimeout(refresh, 0);
        const interval = window.setInterval(refresh, 10000);
        return () => {
            window.clearTimeout(timeout);
            window.clearInterval(interval);
            controller.abort();
        };
    }, [isHydrated, loadData, siteId]);

    const selectedCluster = useMemo(
        () => data?.clusters.find((cluster) => cluster.name === selectedClusterName) ?? null,
        [data, selectedClusterName],
    );

    const clusterByMinerId = useMemo(() => {
        const result = new Map<string, string>();
        data?.clusters.forEach((cluster) => cluster.miners.forEach((miner) => result.set(miner.id, cluster.name)));
        return result;
    }, [data]);

    const runAction = async (path: string, body?: object) => {
        setSaving(true);
        setError(null);
        try {
            const response = await fetch(`/api/pv-site/${siteId}/mining/${path}`, {
                method: 'POST',
                headers: body ? {'Content-Type': 'application/json'} : undefined,
                body: body ? JSON.stringify(body) : undefined,
            });
            if (!response.ok) throw new Error(String(response.status));
            await loadData();
            setSelectedMinerIds([]);
            setAssignMinerIds([]);
            setShowAssignment(false);
        } catch (reason) {
            console.error('Failed to update mining cluster', reason);
            setError(t['mining.error.action']);
        } finally {
            setSaving(false);
        }
    };

    const clusterPath = (action: string) => `clusters/${encodeURIComponent(selectedClusterName ?? '')}/${action}`;
    const toggleSelection = (id: string, selected: string[], setSelected: (ids: string[]) => void) => {
        setSelected(selected.includes(id) ? selected.filter((entry) => entry !== id) : [...selected, id]);
    };

    const discoverMiners = async () => {
        setScanning(true);
        setError(null);
        setSelectedDiscoveredMiner(null);
        try {
            const query = new URLSearchParams({subnet});
            const response = await fetch(`/api/pv-site/${siteId}/mining/miners/discovery?${query}`);
            if (!response.ok) throw new Error(String(response.status));
            setDiscoveredMiners(await response.json() as DiscoveredMinerDto[]);
        } catch (reason) {
            console.error('Failed to discover miners', reason);
            setError(t['mining.error.discovery']);
        } finally {
            setScanning(false);
        }
    };

    const connectMiner = async () => {
        if (!selectedDiscoveredMiner) return;
        setSaving(true);
        setError(null);
        try {
            const response = await fetch(`/api/pv-site/${siteId}/mining/miners`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({...selectedDiscoveredMiner, username, password}),
            });
            if (!response.ok) throw new Error(String(response.status));
            await loadData();
            setShowMinerConnection(false);
            setDiscoveredMiners([]);
            setSelectedDiscoveredMiner(null);
        } catch (reason) {
            console.error('Failed to connect miner', reason);
            setError(t['mining.error.connect_miner']);
        } finally {
            setSaving(false);
        }
    };

    const connectPool = async () => {
        setSaving(true);
        setError(null);
        try {
            const response = await fetch(`/api/pv-site/${siteId}/mining/pools`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({type: 'BRAIINS', accessToken: poolToken}),
            });
            if (!response.ok) throw new Error(String(response.status));
            await loadData();
            setShowPoolConnection(false);
            setPoolToken('');
        } catch (reason) {
            console.error('Failed to connect pool', reason);
            setError(t['mining.error.connect_pool']);
        } finally {
            setSaving(false);
        }
    };

    const deleteInventoryItem = async (type: 'miner' | 'pool', id: string, name: string) => {
        const confirmation = t[`mining.inventory.delete_${type}_confirm`].replace('{name}', name);
        if (!window.confirm(confirmation)) return;

        const itemKey = `${type}:${id}`;
        setDeletingItem(itemKey);
        setError(null);
        try {
            const collection = type === 'miner' ? 'miners' : 'pools';
            const response = await fetch(`/api/pv-site/${siteId}/mining/${collection}/${encodeURIComponent(id)}`, {
                method: 'DELETE',
            });
            if (!response.ok) throw new Error(await response.text());
            await loadData();
        } catch (reason) {
            console.error(`Failed to delete ${type}`, reason);
            setError(t[`mining.error.delete_${type}`]);
        } finally {
            setDeletingItem(null);
        }
    };

    const openPowerTargetEditor = (miner: MinerDto) => {
        const hardwareMinimum = miner.hardwareMinPowerWatts;
        const hardwareMaximum = miner.hardwareMaxPowerWatts;
        const configuredMinimum = Math.min(hardwareMaximum, Math.max(hardwareMinimum, miner.configuredMinPowerWatts || hardwareMinimum));
        const configuredMaximum = Math.min(hardwareMaximum, Math.max(configuredMinimum, miner.configuredMaxPowerWatts || miner.hardwareDefaultPowerWatts));
        setPowerTargetMiner(miner);
        setMinimumPowerWatts(configuredMinimum);
        setMaximumPowerWatts(configuredMaximum);
        setElectricalRiskAcknowledged(false);
    };

    const savePowerTargets = async () => {
        if (!powerTargetMiner) return;
        setSaving(true);
        setError(null);
        try {
            const response = await fetch(`/api/pv-site/${siteId}/mining/miners/${powerTargetMiner.id}/power-targets`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({minimumPowerWatts, maximumPowerWatts, electricalRiskAcknowledged}),
            });
            if (!response.ok) throw new Error(await response.text());
            setPowerTargetMiner(null);
            await loadData();
        } catch (reason) {
            console.error('Failed to update miner power targets', reason);
            setError(t['mining.power_targets.error']);
        } finally {
            setSaving(false);
        }
    };

    const saveReferral = async () => {
        const referralCode = referralInput.trim();
        if (!referralCode) return;
        setSavingReferral(true);
        setError(null);
        try {
            const response = await fetch(`/api/pv-site/${siteId}/mining/referral`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({referralCode}),
            });
            if (!response.ok) {
                if (response.status === 422) throw new Error('INVALID_REFERRAL');
                throw new Error(String(response.status));
            }
            referralEditing.current = false;
            await loadData();
        } catch (reason) {
            console.error('Failed to save referral', reason);
            setError(reason instanceof Error && reason.message === 'INVALID_REFERRAL'
                ? t['mining.fee.referral_invalid']
                : t['mining.fee.referral_error']);
        } finally {
            setSavingReferral(false);
        }
    };

    const deleteReferral = async () => {
        setSavingReferral(true);
        setError(null);
        try {
            const response = await fetch(`/api/pv-site/${siteId}/mining/referral`, {method: 'DELETE'});
            if (!response.ok) throw new Error(String(response.status));
            referralEditing.current = false;
            setReferralInput('');
            await loadData();
        } catch (reason) {
            console.error('Failed to delete referral', reason);
            setError(t['mining.fee.referral_error']);
        } finally {
            setSavingReferral(false);
        }
    };

    if (loading && !data) {
        return <div className="flex min-h-[70vh] items-center justify-center text-gray-300">
            <RefreshCw className="mr-2 animate-spin" size={20}/> {t['mining.loading']}
        </div>;
    }

    const hardwareMinimum = powerTargetMiner?.hardwareMinPowerWatts ?? 0;
    const hardwareMaximum = powerTargetMiner?.hardwareMaxPowerWatts ?? 1;
    const hardwareDefault = Math.min(hardwareMaximum, Math.max(hardwareMinimum, powerTargetMiner?.hardwareDefaultPowerWatts ?? hardwareMaximum));
    const sliderStep = Math.max(1, powerTargetMiner?.powerStepWatts ?? 10);
    const safeBoundary = Math.min(hardwareDefault, ELECTRICAL_RISK_THRESHOLD_WATTS);
    const hardwareRange = Math.max(1, hardwareMaximum - hardwareMinimum);
    const safePercentage = Math.max(0, Math.min(100, ((safeBoundary - hardwareMinimum) / hardwareRange) * 100));
    const rangeBackground = `linear-gradient(to right, #10b981 0%, #10b981 ${safePercentage}%, #ef4444 ${safePercentage}%, #ef4444 100%)`;
    const isOverclocked = maximumPowerWatts > hardwareDefault;
    const hasElectricalRisk = maximumPowerWatts > ELECTRICAL_RISK_THRESHOLD_WATTS;
    const canSavePowerTargets = minimumPowerWatts >= hardwareMinimum
        && maximumPowerWatts <= hardwareMaximum
        && minimumPowerWatts <= maximumPowerWatts
        && (!hasElectricalRisk || electricalRiskAcknowledged);
    const clusterMetrics = selectedCluster ? {
        hashrate: selectedCluster.miners.reduce((sum, miner) => sum + miner.hashrateThs, 0),
        power: selectedCluster.miners.reduce((sum, miner) => sum + miner.powerWatts, 0),
        maxTemperature: selectedCluster.miners.reduce((maximum, miner) => Math.max(maximum, miner.temperatureCelsius), 0),
        warnings: selectedCluster.miners.filter((miner) => miner.status !== 'MINING' || miner.temperatureCelsius >= 80).length,
    } : null;

    return (
        <div className="min-h-[calc(100vh-72px)] bg-[#0b0b0d] px-4 py-6 text-white md:px-8">
            <div className="mx-auto max-w-[1500px] space-y-6">
                <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                        <p className="mb-1 text-xs font-semibold uppercase tracking-[0.18em] text-yellow-400">
                            {t['mining.eyebrow']} · {data?.siteName}
                        </p>
                        <h1 className="text-2xl font-bold tracking-tight">{t['mining.title']}</h1>
                        <p className="mt-1 text-sm text-[#9c9ca5]">{t['mining.subtitle']}</p>
                    </div>
                    <button
                        type="button"
                        onClick={() => void loadData()}
                        className="inline-flex items-center justify-center gap-2 rounded-lg border border-[#2d2d34] bg-[#17171b] px-4 py-2 text-sm text-gray-200 hover:border-[#44444d]"
                    >
                        <RefreshCw size={16}/> {t['mining.action.refresh']}
                    </button>
                </header>

                {error ? <div className="rounded-lg border border-red-900/60 bg-red-950/30 px-4 py-3 text-sm text-red-200">{error}</div> : null}

                <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                    {[
                        {label: t['mining.kpi.clusters'], value: data?.totalClusters ?? 0, icon: <Server size={22}/>},
                        {label: t['mining.kpi.active'], value: data?.activeClusters ?? 0, icon: <Activity size={22}/>},
                        {label: t['mining.kpi.miners'], value: data?.totalMiners ?? 0, icon: <Cpu size={22}/>},
                        {label: t['mining.kpi.hashrate'], value: `${(data?.totalHashrateThs ?? 0).toFixed(2)} TH/s`, icon: <Gauge size={22}/>},
                    ].map((item) => (
                        <div key={item.label} className="flex items-center justify-between rounded-xl border border-[#25252b] bg-[#17171b] p-4">
                            <div>
                                <span className="text-xs text-[#92929c]">{item.label}</span>
                                <strong className="mt-1 block text-xl">{item.value}</strong>
                            </div>
                            <span className="text-yellow-400">{item.icon}</span>
                        </div>
                    ))}
                </section>

                {data?.devFee ? (
                    <section className="grid gap-5 rounded-xl border border-[#25252b] bg-[#111113] p-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(280px,0.65fr)]">
                        <div className="min-w-0">
                            <div className="flex flex-wrap items-start justify-between gap-3">
                                <div>
                                    <div className="flex items-center gap-2"><Gauge className="text-yellow-400" size={18}/><h2 className="font-semibold">{t['mining.fee.title']}</h2></div>
                                    <p className="mt-1 text-xs text-[#92929c]">{t['mining.fee.subtitle']}</p>
                                </div>
                                <span className={`rounded-full px-2.5 py-1 text-xs ${data.devFee.backendAvailable ? 'bg-emerald-400/10 text-emerald-300' : 'bg-amber-400/10 text-amber-300'}`}>
                                    {data.devFee.backendAvailable ? t['mining.fee.verified'] : t['mining.fee.unavailable']}
                                </span>
                            </div>

                            <div className="mt-5 flex h-4 overflow-hidden rounded-full bg-white/[0.05]" aria-label={t['mining.fee.distribution']}>
                                <div className="bg-yellow-400" style={{width: `${Math.max(0, data.devFee.userPercentage)}%`}}/>
                                {data.devFee.allocations.map((allocation) => <div className={allocation.beneficiaryType === 'REFERRAL' ? 'bg-violet-400' : 'bg-cyan-400'} key={`${allocation.beneficiaryType}-${allocation.beneficiaryName}`} style={{width: `${Math.max(0, allocation.percentage)}%`}}/>)}
                                {!data.devFee.allocations.length && data.devFee.totalFeePercentage > 0 ? <div className="bg-[#555561]" style={{width: `${data.devFee.totalFeePercentage}%`}}/> : null}
                            </div>

                            <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                                <FeeAllocationRow color="bg-yellow-400" label={t['mining.fee.your_pool']} percentage={data.devFee.userPercentage} totalHashrate={data.totalHashrateThs}/>
                                {data.devFee.allocations.map((allocation) => <FeeAllocationRow color={allocation.beneficiaryType === 'REFERRAL' ? 'bg-violet-400' : 'bg-cyan-400'} key={`${allocation.beneficiaryType}-${allocation.beneficiaryName}`} label={allocation.beneficiaryType === 'REFERRAL' ? `${t['mining.fee.referral']} · ${allocation.beneficiaryName}` : allocation.beneficiaryName} percentage={allocation.percentage} totalHashrate={data.totalHashrateThs}/>)}
                                {!data.devFee.allocations.length ? <FeeAllocationRow color="bg-[#555561]" label={t['mining.fee.backend_target']} percentage={data.devFee.totalFeePercentage} totalHashrate={data.totalHashrateThs}/> : null}
                            </div>
                        </div>

                        <div className="rounded-xl border border-white/[0.07] bg-[#17171b] p-4">
                            <h3 className="text-sm font-semibold">{t['mining.fee.referral_title']}</h3>
                            <p className="mt-1 text-xs leading-5 text-[#92929c]">{t['mining.fee.referral_hint']}</p>
                            <label className="mt-4 block text-xs text-[#b7b7c0]">
                                {t['mining.fee.referral_code']}
                                <input className={inputClassName} maxLength={128} onChange={(event) => { referralEditing.current = true; setReferralInput(event.target.value); }} placeholder={t['mining.fee.referral_placeholder']} value={referralInput}/>
                            </label>
                            <div className="mt-3 flex flex-wrap justify-end gap-2">
                                {data.devFee.referralCode ? <button className="rounded-lg px-3 py-2 text-xs text-red-300 hover:bg-red-400/10 disabled:opacity-40" disabled={savingReferral} onClick={() => void deleteReferral()} type="button">{t['mining.fee.referral_remove']}</button> : null}
                                <button className="rounded-lg bg-violet-400 px-3 py-2 text-xs font-semibold text-black disabled:opacity-40" disabled={!referralInput.trim() || savingReferral} onClick={() => void saveReferral()} type="button">{savingReferral ? t['mining.fee.referral_checking'] : t['mining.fee.referral_save']}</button>
                            </div>
                            {data.devFee.referralCode && data.devFee.referralValid ? <p className="mt-3 flex items-center gap-2 text-xs text-emerald-300"><CheckCircle2 size={14}/>{t['mining.fee.referral_active'].replace('{code}', data.devFee.referralCode)}</p> : null}
                            {data.devFee.referralCode && !data.devFee.referralValid ? <p className="mt-3 flex items-center gap-2 text-xs text-red-300"><AlertTriangle size={14}/>{t['mining.fee.referral_no_longer_valid']}</p> : null}
                        </div>
                    </section>
                ) : null}

                <nav className="grid gap-2 rounded-xl border border-[#25252b] bg-[#111113] p-2 sm:grid-cols-2" aria-label={t['mining.section.navigation']}>
                    <button
                        className={`flex items-center gap-3 rounded-lg px-4 py-3 text-left transition ${section === 'inventory' ? 'bg-yellow-400 text-black' : 'text-[#a9a9b2] hover:bg-white/[0.05] hover:text-white'}`}
                        onClick={() => setSection('inventory')}
                        type="button"
                    >
                        <Network size={19}/>
                        <span><strong className="block text-sm">{t['mining.section.inventory']}</strong><span className={`text-xs ${section === 'inventory' ? 'text-black/65' : 'text-[#74747e]'}`}>{t['mining.section.inventory_hint']}</span></span>
                    </button>
                    <button
                        className={`flex items-center gap-3 rounded-lg px-4 py-3 text-left transition ${section === 'clusters' ? 'bg-yellow-400 text-black' : 'text-[#a9a9b2] hover:bg-white/[0.05] hover:text-white'}`}
                        onClick={() => setSection('clusters')}
                        type="button"
                    >
                        <Server size={19}/>
                        <span><strong className="block text-sm">{t['mining.section.clusters']}</strong><span className={`text-xs ${section === 'clusters' ? 'text-black/65' : 'text-[#74747e]'}`}>{t['mining.section.clusters_hint']}</span></span>
                    </button>
                </nav>

                {section === 'inventory' ? (
                    <div className="grid gap-6 xl:grid-cols-2">
                        <section className="overflow-hidden rounded-xl border border-[#25252b] bg-[#111113]">
                            <div className="flex items-center justify-between gap-4 border-b border-[#25252b] p-5">
                                <div>
                                    <div className="flex items-center gap-2"><Radio className="text-yellow-400" size={18}/><h2 className="font-semibold">{t['mining.inventory.miners']}</h2></div>
                                    <p className="mt-1 text-xs text-[#92929c]">{t['mining.inventory.miners_hint']}</p>
                                </div>
                                <button className="inline-flex items-center gap-2 rounded-lg bg-yellow-400 px-3 py-2 text-xs font-semibold text-black hover:bg-yellow-300" onClick={() => setShowMinerConnection(true)} type="button">
                                    <Plus size={15}/>{t['mining.inventory.add_miner']}
                                </button>
                            </div>
                            <div className="divide-y divide-[#25252b]">
                                {data?.connectedMiners.map((miner) => {
                                    const minerName = miner.name || miner.model || miner.ipAddress;
                                    const itemKey = `miner:${miner.id}`;
                                    return <div className="flex items-center gap-2 p-2 pr-3 transition hover:bg-white/[0.04]" key={miner.id}>
                                        <Link className="flex min-w-0 flex-1 items-center gap-4 rounded-lg p-2" href={`/site/${siteId}/mining/miners/${miner.id}`}>
                                            <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-yellow-400/10 text-yellow-300"><Cpu size={19}/></span>
                                            <div className="min-w-0 flex-1">
                                                <strong className="block truncate text-sm">{minerName}</strong>
                                                <span className="block truncate text-xs text-[#92929c]">{miner.ipAddress} · {miner.model}</span>
                                            </div>
                                            <span className={`hidden rounded-full px-2.5 py-1 text-xs sm:inline-flex ${clusterByMinerId.has(miner.id) ? 'bg-violet-500/15 text-violet-300' : 'bg-white/[0.06] text-[#a0a0aa]'}`}>
                                                {clusterByMinerId.get(miner.id) ?? t['mining.inventory.unassigned']}
                                            </span>
                                        </Link>
                                        <button aria-label={t['mining.inventory.delete_miner'].replace('{name}', minerName)} className="grid h-9 w-9 shrink-0 place-items-center rounded-lg text-[#777781] transition hover:bg-red-500/10 hover:text-red-300 disabled:cursor-not-allowed disabled:opacity-40" disabled={deletingItem !== null} onClick={() => void deleteInventoryItem('miner', miner.id, minerName)} title={t['mining.inventory.delete_miner'].replace('{name}', minerName)} type="button">
                                            {deletingItem === itemKey ? <LoaderCircle className="animate-spin" size={16}/> : <Trash2 size={16}/>}
                                        </button>
                                    </div>;
                                })}
                                {!data?.connectedMiners.length ? <p className="p-8 text-center text-sm text-[#92929c]">{t['mining.inventory.no_miners']}</p> : null}
                            </div>
                        </section>

                        <section className="overflow-hidden rounded-xl border border-[#25252b] bg-[#111113]">
                            <div className="flex items-center justify-between gap-4 border-b border-[#25252b] p-5">
                                <div>
                                    <div className="flex items-center gap-2"><WalletCards className="text-emerald-400" size={18}/><h2 className="font-semibold">{t['mining.inventory.pools']}</h2></div>
                                    <p className="mt-1 text-xs text-[#92929c]">{t['mining.inventory.pools_hint']}</p>
                                </div>
                                <button className="inline-flex items-center gap-2 rounded-lg bg-emerald-400 px-3 py-2 text-xs font-semibold text-black hover:bg-emerald-300" onClick={() => setShowPoolConnection(true)} type="button">
                                    <Plus size={15}/>{t['mining.inventory.add_pool']}
                                </button>
                            </div>
                            <div className="grid gap-3 p-4 sm:grid-cols-2">
                                {data?.connectedPools.map((pool) => {
                                    const poolName = pool.name || pool.type;
                                    const itemKey = `pool:${pool.id}`;
                                    return <article className="flex items-start gap-3 rounded-lg border border-[#2b2b32] bg-[#17171b] p-4" key={pool.id}>
                                        <div className="min-w-0 flex-1">
                                            <span className="text-[11px] font-semibold uppercase tracking-[0.14em] text-emerald-400">{pool.type}</span>
                                            <strong className="mt-2 block truncate text-sm">{poolName}</strong>
                                            <span className="mt-1 block truncate text-xs text-[#92929c]" title={pool.stratumUrl}>{pool.stratumUrl}</span>
                                        </div>
                                        <button aria-label={t['mining.inventory.delete_pool'].replace('{name}', poolName)} className="grid h-9 w-9 shrink-0 place-items-center rounded-lg text-[#777781] transition hover:bg-red-500/10 hover:text-red-300 disabled:cursor-not-allowed disabled:opacity-40" disabled={deletingItem !== null} onClick={() => void deleteInventoryItem('pool', pool.id, poolName)} title={t['mining.inventory.delete_pool'].replace('{name}', poolName)} type="button">
                                            {deletingItem === itemKey ? <LoaderCircle className="animate-spin" size={16}/> : <Trash2 size={16}/>}
                                        </button>
                                    </article>;
                                })}
                                {!data?.connectedPools.length ? <p className="col-span-full p-8 text-center text-sm text-[#92929c]">{t['mining.inventory.no_pools']}</p> : null}
                            </div>
                        </section>
                    </div>
                ) : (
                <div className="grid gap-6 lg:grid-cols-[minmax(240px,0.28fr)_minmax(0,1fr)]">
                    <section className="rounded-xl border border-[#25252b] bg-[#111113] p-4">
                        <div className="mb-3 flex items-center justify-between gap-2"><h2 className="text-sm font-semibold">{t['mining.clusters.title']}</h2><Link className="inline-flex items-center gap-1 rounded-lg border border-yellow-400/25 bg-yellow-400/10 px-2.5 py-1.5 text-xs font-semibold text-yellow-300" href={`/site/${siteId}/mining/clusters/new/config`}><Plus size={13}/>{t['mining.cluster_config.new']}</Link></div>
                        <div className="space-y-2">
                            {data?.clusters.map((cluster) => (
                                <button
                                    key={cluster.name}
                                    type="button"
                                    onClick={() => {
                                        setSelectedClusterName(cluster.name);
                                        setSelectedMinerIds([]);
                                    }}
                                    className={`flex w-full items-center justify-between rounded-lg border px-3 py-3 text-left transition ${selectedClusterName === cluster.name
                                        ? 'border-yellow-500/50 bg-yellow-500/10'
                                        : 'border-[#25252b] bg-[#17171b] hover:border-[#3b3b43]'}`}
                                >
                                    <span>
                                        <strong className="block text-sm">{cluster.name}</strong>
                                        <span className="text-xs text-[#92929c]">
                                            {t['mining.clusters.miner_count'].replace('{count}', String(cluster.miners.length))}
                                        </span>
                                    </span>
                                    <span className={`h-2.5 w-2.5 rounded-full ${cluster.running ? 'bg-emerald-400' : 'bg-gray-600'}`}/>
                                </button>
                            ))}
                            {!data?.clusters.length ? <p className="py-8 text-center text-sm text-[#92929c]">{t['mining.clusters.empty']}</p> : null}
                        </div>
                    </section>

                    <section className="min-w-0 rounded-xl border border-[#25252b] bg-[#111113] p-4 md:p-5">
                        <div className="mb-4 flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
                            <div>
                                <div className="flex flex-wrap items-center gap-2">
                                    <h2 className="text-lg font-semibold">{selectedCluster?.name ?? t['mining.cluster.none']}</h2>
                                    {selectedCluster ? <span className={`rounded-full px-2.5 py-1 text-[11px] font-semibold ${selectedCluster.running ? 'bg-emerald-400/10 text-emerald-300' : 'bg-white/[0.06] text-[#9999a3]'}`}>{selectedCluster.running ? t['mining.status.running'] : t['mining.status.stopped']}</span> : null}
                                </div>
                                {selectedCluster ? <p className="mt-1 text-xs text-[#92929c]">{t['mining.clusters.miner_count'].replace('{count}', String(selectedCluster.miners.length))}</p> : null}
                            </div>
                            <div className="flex flex-wrap gap-2">
                                {selectedCluster ? <Link className="inline-flex items-center gap-2 rounded-lg border border-violet-400/30 bg-violet-400/10 px-3 py-2 text-xs font-semibold text-violet-200" href={`/site/${siteId}/mining/clusters/${encodeURIComponent(selectedCluster.name)}/config`}><Settings2 size={15}/>{t['mining.cluster_config.edit']}</Link> : null}
                                <button
                                    type="button"
                                    disabled={!selectedCluster || selectedCluster.running || saving || selectedCluster.miners.length === 0}
                                    onClick={() => void runAction(clusterPath('start'))}
                                    className="inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-40"
                                >
                                    <Play size={15}/> {t['mining.action.start']}
                                </button>
                                <button
                                    type="button"
                                    disabled={!selectedCluster?.running || saving}
                                    onClick={() => void runAction(clusterPath('stop'))}
                                    className="inline-flex items-center gap-2 rounded-lg bg-red-600 px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-40"
                                >
                                    <Pause size={15}/> {t['mining.action.stop']}
                                </button>
                                <button
                                    type="button"
                                    disabled={!selectedCluster || !data?.unassignedMiners.length || saving}
                                    onClick={() => setShowAssignment(true)}
                                    className="inline-flex items-center gap-2 rounded-lg border border-[#34343c] bg-[#202025] px-3 py-2 text-xs font-semibold disabled:cursor-not-allowed disabled:opacity-40"
                                >
                                    <Plus size={15}/> {t['mining.action.assign']}
                                </button>
                                <button
                                    type="button"
                                    disabled={!selectedMinerIds.length || saving}
                                    onClick={() => void runAction(clusterPath('miners/remove'), {minerIds: selectedMinerIds})}
                                    className="inline-flex items-center gap-2 rounded-lg border border-red-900/60 bg-red-950/30 px-3 py-2 text-xs font-semibold text-red-200 disabled:cursor-not-allowed disabled:opacity-40"
                                >
                                    <Unlink size={15}/> {t['mining.action.remove']}
                                </button>
                            </div>
                        </div>

                        {clusterMetrics && selectedCluster?.miners.length ? (
                            <div className="mb-5 grid grid-cols-2 gap-2 lg:grid-cols-4">
                                {[
                                    {label: t['mining.cluster.metrics.hashrate'], value: `${clusterMetrics.hashrate.toFixed(2)} TH/s`, icon: <Gauge size={16}/>, tone: 'text-yellow-300 bg-yellow-400/10'},
                                    {label: t['mining.cluster.metrics.power'], value: `${clusterMetrics.power.toLocaleString()} W`, icon: <Bolt size={16}/>, tone: 'text-sky-300 bg-sky-400/10'},
                                    {label: t['mining.cluster.metrics.temperature'], value: `${clusterMetrics.maxTemperature.toFixed(1)} °C`, icon: <Thermometer size={16}/>, tone: clusterMetrics.maxTemperature >= 80 ? 'text-orange-300 bg-orange-400/10' : 'text-emerald-300 bg-emerald-400/10'},
                                    {label: t['mining.cluster.metrics.warnings'], value: String(clusterMetrics.warnings), icon: <AlertTriangle size={16}/>, tone: clusterMetrics.warnings ? 'text-red-300 bg-red-400/10' : 'text-emerald-300 bg-emerald-400/10'},
                                ].map((metric) => <div className="rounded-xl border border-white/[0.06] bg-[#17171b] p-3" key={metric.label}><div className="flex items-center gap-2"><span className={`grid h-8 w-8 place-items-center rounded-lg ${metric.tone}`}>{metric.icon}</span><span className="text-[11px] text-[#85858f]">{metric.label}</span></div><strong className="mt-2 block text-lg">{metric.value}</strong></div>)}
                            </div>
                        ) : null}

                        <div className="hidden overflow-x-auto md:block">
                            <table className="w-full min-w-[980px] border-collapse text-left text-sm">
                                <thead className="border-b border-[#29292f] text-xs text-[#92929c]">
                                <tr>
                                    <th className="w-10 px-3 py-3"/>
                                    <th className="px-3 py-3 font-medium">{t['mining.grid.miner']}</th>
                                    <th className="px-3 py-3 font-medium">{t['mining.grid.status']}</th>
                                    <th className="px-3 py-3 font-medium">{t['mining.grid.hashrate']}</th>
                                    <th className="px-3 py-3 font-medium">{t['mining.grid.power']}</th>
                                    <th className="px-3 py-3 font-medium">{t['mining.grid.temperature']}</th>
                                    <th className="px-3 py-3 font-medium">{t['mining.grid.targets']}</th>
                                    <th className="px-3 py-3 font-medium">{t['mining.grid.locks']}</th>
                                </tr>
                                </thead>
                                <tbody className="divide-y divide-[#242429]">
                                {selectedCluster?.miners.map((miner) => (
                                    <tr key={miner.id} className="hover:bg-white/[0.025]">
                                        <td className="px-3 py-4">
                                            <input
                                                type="checkbox"
                                                aria-label={t['mining.grid.select'].replace('{name}', miner.name || miner.ipAddress)}
                                                checked={selectedMinerIds.includes(miner.id)}
                                                onChange={() => toggleSelection(miner.id, selectedMinerIds, setSelectedMinerIds)}
                                                className="accent-yellow-400"
                                            />
                                        </td>
                                        <td className="px-3 py-4">
                                            <Link className="block font-semibold text-white hover:text-yellow-300 hover:underline" href={`/site/${siteId}/mining/miners/${miner.id}`}>{miner.name || miner.model}</Link>
                                            <span className="text-xs text-[#92929c]">{miner.ipAddress} · {miner.pool || t['mining.pool.none']}</span>
                                        </td>
                                        <td className="px-3 py-4">
                                            <span className={`rounded-full px-2 py-1 text-xs font-semibold ${miner.status === 'MINING' ? 'bg-emerald-500/15 text-emerald-300' : 'bg-red-500/15 text-red-300'}`}>
                                                {t[`mining.miner_status.${miner.status.toLowerCase()}`] ?? miner.status}
                                            </span>
                                        </td>
                                        <td className="px-3 py-4 font-mono text-yellow-300">{miner.hashrateThs.toFixed(2)} TH/s</td>
                                        <td className="px-3 py-4">
                                            <span className="block font-medium">{miner.powerWatts} W</span>
                                            <button
                                                className="mt-1 inline-flex items-center gap-1 text-xs font-semibold text-yellow-300 hover:text-yellow-200 disabled:cursor-not-allowed disabled:text-[#666670]"
                                                disabled={!miner.supportsDynamicPowerScaling}
                                                onClick={() => openPowerTargetEditor(miner)}
                                                title={miner.supportsDynamicPowerScaling ? t['mining.power_targets.edit'] : t['mining.power_targets.unsupported']}
                                                type="button"
                                            >
                                                <Bolt size={12}/>{t['mining.power_targets.edit']}
                                            </button>
                                        </td>
                                        <td className={`px-3 py-4 ${miner.temperatureCelsius >= 80 ? 'text-orange-300' : 'text-gray-300'}`}>{miner.temperatureCelsius.toFixed(1)} °C</td>
                                        <td className="px-3 py-4 text-xs text-[#b3b3bc]">
                                            <span className="block">{t['mining.grid.hardware']}: {miner.hardwareMinPowerWatts}–{miner.hardwareMaxPowerWatts} W</span>
                                            <span className="block text-emerald-300">{t['mining.power_targets.core_default']}: {miner.hardwareDefaultPowerWatts} W</span>
                                            <span className="block text-yellow-300">{t['mining.grid.configured']}: {miner.configuredMinPowerWatts}–{miner.configuredMaxPowerWatts} W</span>
                                        </td>
                                        <td className="px-3 py-4 text-xs text-[#b3b3bc]">
                                            {miner.powerChangeLockMinutes != null
                                                ? t['mining.grid.power_lock'].replace('{minutes}', String(miner.powerChangeLockMinutes))
                                                : t['mining.grid.default']}
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                            {selectedCluster && selectedCluster.miners.length === 0 ? (
                                <p className="py-12 text-center text-sm text-[#92929c]">{t['mining.miners.empty']}</p>
                            ) : null}
                            {!selectedCluster ? <p className="py-12 text-center text-sm text-[#92929c]">{t['mining.cluster.select']}</p> : null}
                        </div>
                        <div className="grid gap-3 md:hidden">
                            {selectedCluster?.miners.map((miner) => (
                                <article className={`rounded-xl border p-4 ${selectedMinerIds.includes(miner.id) ? 'border-yellow-400/40 bg-yellow-400/[0.05]' : 'border-white/[0.07] bg-[#17171b]'}`} key={miner.id}>
                                    <div className="flex items-start gap-3">
                                        <input aria-label={t['mining.grid.select'].replace('{name}', miner.name || miner.ipAddress)} checked={selectedMinerIds.includes(miner.id)} className="mt-1 accent-yellow-400" onChange={() => toggleSelection(miner.id, selectedMinerIds, setSelectedMinerIds)} type="checkbox"/>
                                        <div className="min-w-0 flex-1">
                                            <div className="flex items-start justify-between gap-2">
                                                <div className="min-w-0"><Link className="block truncate font-semibold text-white hover:text-yellow-300" href={`/site/${siteId}/mining/miners/${miner.id}`}>{miner.name || miner.model}</Link><span className="mt-0.5 block truncate text-xs text-[#85858f]">{miner.ipAddress} · {miner.pool || t['mining.pool.none']}</span></div>
                                                <span className={`shrink-0 rounded-full px-2 py-1 text-[10px] font-semibold ${miner.status === 'MINING' ? 'bg-emerald-500/15 text-emerald-300' : 'bg-red-500/15 text-red-300'}`}>{t[`mining.miner_status.${miner.status.toLowerCase()}`] ?? miner.status}</span>
                                            </div>
                                            <div className="mt-4 grid grid-cols-3 gap-2">
                                                <MiniMetric label={t['mining.grid.hashrate']} value={`${miner.hashrateThs.toFixed(2)} TH/s`}/>
                                                <MiniMetric label={t['mining.grid.power']} value={`${miner.powerWatts} W`}/>
                                                <MiniMetric label={t['mining.grid.temperature']} tone={miner.temperatureCelsius >= 80 ? 'text-orange-300' : undefined} value={`${miner.temperatureCelsius.toFixed(1)} °C`}/>
                                            </div>
                                            <div className="mt-3 rounded-lg bg-black/20 px-3 py-2 text-[11px] leading-5 text-[#9999a3]"><span className="block">{t['mining.grid.hardware']}: {miner.hardwareMinPowerWatts}–{miner.hardwareMaxPowerWatts} W</span><span className="block text-yellow-300">{t['mining.grid.configured']}: {miner.configuredMinPowerWatts}–{miner.configuredMaxPowerWatts} W</span></div>
                                            <button className="mt-3 inline-flex items-center gap-1.5 rounded-lg border border-yellow-400/20 bg-yellow-400/10 px-3 py-2 text-xs font-semibold text-yellow-300 disabled:border-white/5 disabled:bg-white/[0.03] disabled:text-[#666670]" disabled={!miner.supportsDynamicPowerScaling} onClick={() => openPowerTargetEditor(miner)} type="button"><Bolt size={13}/>{t['mining.power_targets.edit']}</button>
                                        </div>
                                    </div>
                                </article>
                            ))}
                            {selectedCluster && selectedCluster.miners.length === 0 ? <p className="py-10 text-center text-sm text-[#92929c]">{t['mining.miners.empty']}</p> : null}
                            {!selectedCluster ? <p className="py-10 text-center text-sm text-[#92929c]">{t['mining.cluster.select']}</p> : null}
                        </div>
                    </section>
                </div>
                )}
            </div>

            {powerTargetMiner ? (
                <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-black/80 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-label={t['mining.power_targets.title']}>
                    <div className="my-8 w-full max-w-2xl overflow-hidden rounded-2xl border border-[#34343d] bg-[#151519] shadow-2xl">
                        <div className="flex items-start justify-between border-b border-[#2b2b32] p-5">
                            <div className="flex items-start gap-3">
                                <span className="rounded-xl bg-yellow-400/10 p-2.5 text-yellow-300"><Bolt size={21}/></span>
                                <div>
                                    <h2 className="font-semibold">{t['mining.power_targets.title']}</h2>
                                    <p className="mt-1 text-xs text-[#92929c]">{powerTargetMiner.name || powerTargetMiner.model} · {powerTargetMiner.ipAddress}</p>
                                </div>
                            </div>
                            <button className="rounded-lg p-2 text-[#aaaab4] hover:bg-white/5 hover:text-white" onClick={() => setPowerTargetMiner(null)} type="button" aria-label={t['mining.action.close']}><X size={19}/></button>
                        </div>

                        <div className="space-y-5 p-5">
                            <div className="grid gap-3 sm:grid-cols-3">
                                <PowerInfo label={t['mining.power_targets.current']} value={`${powerTargetMiner.powerWatts} W`}/>
                                <PowerInfo label={t['mining.power_targets.core_default']} value={`${hardwareDefault} W`} tone="green"/>
                                <PowerInfo label={t['mining.grid.temperature']} value={`${powerTargetMiner.temperatureCelsius.toFixed(1)} °C`} tone={powerTargetMiner.temperatureCelsius >= 90 ? 'red' : powerTargetMiner.temperatureCelsius >= 80 ? 'amber' : 'green'}/>
                            </div>

                            {powerTargetMiner.temperatureCelsius >= 80 ? (
                                <div className={`flex gap-3 rounded-xl border p-4 text-sm ${powerTargetMiner.temperatureCelsius >= 90 ? 'border-red-500/30 bg-red-500/10 text-red-200' : 'border-amber-400/30 bg-amber-400/10 text-amber-100'}`}>
                                    <Thermometer className="mt-0.5 shrink-0" size={18}/>
                                    <div><strong className="block">{powerTargetMiner.temperatureCelsius >= 90 ? t['mining.power_targets.heat_critical'] : t['mining.power_targets.heat_warning']}</strong><p className="mt-1 text-xs leading-5 opacity-80">{t['mining.power_targets.heat_description']}</p></div>
                                </div>
                            ) : null}

                            <section className="rounded-xl border border-white/[0.07] bg-black/20 p-4">
                                <div className="flex flex-wrap items-center justify-between gap-3">
                                    <div><h3 className="text-sm font-semibold">{t['mining.power_targets.range_title']}</h3><p className="mt-1 text-xs text-[#898994]">{t['mining.power_targets.range_description']}</p></div>
                                    <button className="rounded-lg border border-emerald-400/25 bg-emerald-400/10 px-3 py-2 text-xs font-semibold text-emerald-300 hover:bg-emerald-400/15" onClick={() => { setMinimumPowerWatts(hardwareMinimum); setMaximumPowerWatts(hardwareDefault); setElectricalRiskAcknowledged(false); }} type="button"><CheckCircle2 className="mr-1 inline" size={14}/>{t['mining.power_targets.use_default']}</button>
                                </div>

                                <div className="mt-6 space-y-6">
                                    <PowerSlider
                                        label={t['mining.power_targets.minimum']}
                                        value={minimumPowerWatts}
                                        min={hardwareMinimum}
                                        max={maximumPowerWatts}
                                        step={sliderStep}
                                        background={rangeBackground}
                                        onChange={setMinimumPowerWatts}
                                    />
                                    <PowerSlider
                                        label={t['mining.power_targets.maximum']}
                                        value={maximumPowerWatts}
                                        min={minimumPowerWatts}
                                        max={hardwareMaximum}
                                        step={sliderStep}
                                        background={rangeBackground}
                                        onChange={(value) => { setMaximumPowerWatts(value); if (value <= ELECTRICAL_RISK_THRESHOLD_WATTS) setElectricalRiskAcknowledged(false); }}
                                    />
                                </div>

                                <div className="mt-4 flex justify-between text-[11px] text-[#858590]"><span>{t['mining.power_targets.hardware_min']} {hardwareMinimum} W</span><span className="text-emerald-300">{t['mining.power_targets.core_default']} {hardwareDefault} W</span><span>{t['mining.power_targets.hardware_max']} {hardwareMaximum} W</span></div>
                                <div className="mt-3 flex flex-wrap gap-4 text-[11px]"><span className="inline-flex items-center gap-1.5 text-emerald-300"><i className="h-2.5 w-2.5 rounded-full bg-emerald-500"/>{t['mining.power_targets.safe_zone']}</span><span className="inline-flex items-center gap-1.5 text-red-300"><i className="h-2.5 w-2.5 rounded-full bg-red-500"/>{t['mining.power_targets.risk_zone']}</span></div>
                            </section>

                            {isOverclocked ? <WarningCard icon={<AlertTriangle size={18}/>} title={t['mining.power_targets.overclock_title']} text={t['mining.power_targets.overclock_description']} tone="amber"/> : null}

                            {hasElectricalRisk ? (
                                <div className="rounded-xl border border-red-500/35 bg-red-500/10 p-4 text-red-100">
                                    <div className="flex gap-3"><ShieldAlert className="mt-0.5 shrink-0 text-red-300" size={20}/><div><strong className="text-sm">{t['mining.power_targets.electrical_title']}</strong><p className="mt-1 text-xs leading-5 text-red-200/80">{t['mining.power_targets.electrical_description']}</p></div></div>
                                    <label className="mt-4 flex cursor-pointer items-start gap-3 rounded-lg border border-red-400/20 bg-black/15 p-3 text-xs leading-5"><input className="mt-1 accent-red-500" checked={electricalRiskAcknowledged} onChange={event => setElectricalRiskAcknowledged(event.target.checked)} type="checkbox"/><span>{t['mining.power_targets.electrical_acknowledgement']}</span></label>
                                </div>
                            ) : null}

                            <p className="rounded-lg border border-sky-500/20 bg-sky-500/[0.07] px-4 py-3 text-xs leading-5 text-sky-200">{t['mining.power_targets.controller_hint']}</p>
                        </div>

                        <div className="flex justify-end gap-2 border-t border-[#2b2b32] p-4">
                            <button className="rounded-lg px-4 py-2 text-sm text-gray-300 hover:bg-white/5" onClick={() => setPowerTargetMiner(null)} type="button">{t['mining.action.cancel']}</button>
                            <button className="rounded-lg bg-yellow-400 px-4 py-2 text-sm font-semibold text-black disabled:cursor-not-allowed disabled:opacity-40" disabled={!canSavePowerTargets || saving} onClick={() => void savePowerTargets()} type="button">{saving ? t['mining.power_targets.saving'] : t['mining.power_targets.save']}</button>
                        </div>
                    </div>
                </div>
            ) : null}

            {showMinerConnection ? (
                <div className="fixed inset-0 z-50 grid place-items-center overflow-y-auto bg-black/75 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-label={t['mining.connect_miner.title']}>
                    <div className="my-8 w-full max-w-3xl overflow-hidden rounded-xl border border-[#303038] bg-[#17171b] shadow-2xl">
                        <div className="flex items-start justify-between border-b border-[#29292f] p-5">
                            <div><h2 className="font-semibold">{t['mining.connect_miner.title']}</h2><p className="mt-1 max-w-xl text-xs leading-5 text-[#92929c]">{t['mining.connect_miner.description']}</p></div>
                            <button type="button" aria-label={t['mining.action.close']} onClick={() => setShowMinerConnection(false)} className="rounded-lg p-2 hover:bg-white/5"><X size={19}/></button>
                        </div>
                        <div className="space-y-5 p-5">
                            <div className="grid items-end gap-3 sm:grid-cols-[minmax(0,1fr)_auto]">
                                <label className="text-sm text-[#b7b7c0]">
                                    {t['mining.connect_miner.subnet']}
                                    <input className={inputClassName} onChange={(event) => setSubnet(event.target.value)} placeholder="192.168.178" value={subnet}/>
                                </label>
                                <button className="inline-flex h-[42px] items-center justify-center gap-2 rounded-lg border border-yellow-400/30 bg-yellow-400/10 px-4 text-sm font-semibold text-yellow-300 hover:bg-yellow-400/20 disabled:opacity-40" disabled={scanning || !subnet.trim()} onClick={() => void discoverMiners()} type="button">
                                    {scanning ? <RefreshCw className="animate-spin" size={16}/> : <Search size={16}/>} {scanning ? t['mining.connect_miner.scanning'] : t['mining.connect_miner.scan']}
                                </button>
                            </div>

                            <div>
                                <h3 className="mb-2 text-xs font-semibold uppercase tracking-[0.14em] text-[#777781]">{t['mining.connect_miner.results']}</h3>
                                <div className="max-h-56 space-y-2 overflow-y-auto">
                                    {discoveredMiners.map((miner) => (
                                        <button className={`flex w-full items-center gap-3 rounded-lg border p-3 text-left ${selectedDiscoveredMiner?.ipAddress === miner.ipAddress ? 'border-yellow-400/50 bg-yellow-400/10' : 'border-[#303038] bg-[#111113] hover:border-[#484852]'}`} key={miner.ipAddress} onClick={() => setSelectedDiscoveredMiner(miner)} type="button">
                                            <Cpu className="text-yellow-300" size={18}/>
                                            <span className="min-w-0 flex-1"><strong className="block truncate text-sm">{miner.model}</strong><span className="text-xs text-[#92929c]">{miner.ipAddress}</span></span>
                                            <span className="rounded-md bg-white/[0.05] px-2 py-1 text-xs text-[#a9a9b2]">{miner.operatingSystem}</span>
                                        </button>
                                    ))}
                                    {!scanning && discoveredMiners.length === 0 ? <p className="rounded-lg border border-dashed border-[#303038] px-4 py-7 text-center text-sm text-[#777781]">{t['mining.connect_miner.none']}</p> : null}
                                </div>
                            </div>

                            {selectedDiscoveredMiner && selectedDiscoveredMiner.operatingSystem !== 'AGENT' ? (
                                <div className="grid gap-4 sm:grid-cols-2">
                                    <label className="text-sm text-[#b7b7c0]">{t['mining.connect_miner.username']}<input autoComplete="username" className={inputClassName} onChange={(event) => setUsername(event.target.value)} value={username}/></label>
                                    <label className="text-sm text-[#b7b7c0]">{t['mining.connect_miner.password']}<input autoComplete="current-password" className={inputClassName} onChange={(event) => setPassword(event.target.value)} type="password" value={password}/></label>
                                </div>
                            ) : null}
                            <p className="rounded-lg border border-sky-500/20 bg-sky-500/[0.07] px-4 py-3 text-xs leading-5 text-sky-200">{t['mining.connect_miner.separation_hint']}</p>
                        </div>
                        <div className="flex justify-end gap-2 border-t border-[#29292f] p-4">
                            <button className="rounded-lg px-4 py-2 text-sm text-gray-300 hover:bg-white/5" onClick={() => setShowMinerConnection(false)} type="button">{t['mining.action.cancel']}</button>
                            <button className="rounded-lg bg-yellow-400 px-4 py-2 text-sm font-semibold text-black disabled:opacity-40" disabled={!selectedDiscoveredMiner || saving} onClick={() => void connectMiner()} type="button">{saving ? t['mining.connect_miner.connecting'] : t['mining.connect_miner.connect']}</button>
                        </div>
                    </div>
                </div>
            ) : null}

            {showPoolConnection ? (
                <div className="fixed inset-0 z-50 grid place-items-center bg-black/75 p-4 backdrop-blur-sm" role="dialog" aria-modal="true" aria-label={t['mining.connect_pool.title']}>
                    <div className="w-full max-w-xl overflow-hidden rounded-xl border border-[#303038] bg-[#17171b] shadow-2xl">
                        <div className="flex items-start justify-between border-b border-[#29292f] p-5">
                            <div><h2 className="font-semibold">{t['mining.connect_pool.title']}</h2><p className="mt-1 text-xs leading-5 text-[#92929c]">{t['mining.connect_pool.description']}</p></div>
                            <button type="button" aria-label={t['mining.action.close']} onClick={() => setShowPoolConnection(false)} className="rounded-lg p-2 hover:bg-white/5"><X size={19}/></button>
                        </div>
                        <div className="space-y-4 p-5">
                            <label className="text-sm text-[#b7b7c0]">{t['mining.connect_pool.type']}<input className={`${inputClassName} text-[#92929c]`} disabled value={t['mining.connect_pool.braiins']}/></label>
                            <label className="text-sm text-[#b7b7c0]">{t['mining.connect_pool.token']}<input autoComplete="off" className={inputClassName} onChange={(event) => setPoolToken(event.target.value)} type="password" value={poolToken}/></label>
                            <p className="rounded-lg border border-sky-500/20 bg-sky-500/[0.07] px-4 py-3 text-xs leading-5 text-sky-200">{t['mining.connect_pool.separation_hint']}</p>
                        </div>
                        <div className="flex justify-end gap-2 border-t border-[#29292f] p-4">
                            <button className="rounded-lg px-4 py-2 text-sm text-gray-300 hover:bg-white/5" onClick={() => setShowPoolConnection(false)} type="button">{t['mining.action.cancel']}</button>
                            <button className="rounded-lg bg-emerald-400 px-4 py-2 text-sm font-semibold text-black disabled:opacity-40" disabled={!poolToken.trim() || saving} onClick={() => void connectPool()} type="button">{t['mining.connect_pool.connect']}</button>
                        </div>
                    </div>
                </div>
            ) : null}

            {showAssignment ? (
                <div className="fixed inset-0 z-50 grid place-items-center bg-black/70 p-4" role="dialog" aria-modal="true" aria-label={t['mining.assign.title']}>
                    <div className="w-full max-w-2xl rounded-xl border border-[#303038] bg-[#17171b] shadow-2xl">
                        <div className="flex items-center justify-between border-b border-[#29292f] p-5">
                            <div>
                                <h2 className="font-semibold">{t['mining.assign.title']}</h2>
                                <p className="text-xs text-[#92929c]">{selectedCluster?.name}</p>
                            </div>
                            <button type="button" aria-label={t['mining.action.close']} onClick={() => setShowAssignment(false)} className="rounded-lg p-2 hover:bg-white/5"><X size={19}/></button>
                        </div>
                        <div className="max-h-[55vh] divide-y divide-[#29292f] overflow-y-auto p-2">
                            {data?.unassignedMiners.map((miner) => (
                                <label key={miner.id} className="flex cursor-pointer items-center gap-4 rounded-lg px-3 py-3 hover:bg-white/[0.03]">
                                    <input
                                        type="checkbox"
                                        checked={assignMinerIds.includes(miner.id)}
                                        onChange={() => toggleSelection(miner.id, assignMinerIds, setAssignMinerIds)}
                                        className="accent-yellow-400"
                                    />
                                    <span className="flex-1">
                                        <strong className="block text-sm">{miner.name || miner.model}</strong>
                                        <span className="text-xs text-[#92929c]">{miner.ipAddress} · {miner.status}</span>
                                    </span>
                                    <span className="text-xs text-yellow-300">{miner.hashrateThs.toFixed(2)} TH/s</span>
                                </label>
                            ))}
                        </div>
                        <div className="flex justify-end gap-2 border-t border-[#29292f] p-4">
                            <button type="button" onClick={() => setShowAssignment(false)} className="rounded-lg px-4 py-2 text-sm text-gray-300 hover:bg-white/5">{t['mining.action.cancel']}</button>
                            <button
                                type="button"
                                disabled={!assignMinerIds.length || saving}
                                onClick={() => void runAction(clusterPath('miners'), {minerIds: assignMinerIds})}
                                className="rounded-lg bg-yellow-400 px-4 py-2 text-sm font-semibold text-black disabled:opacity-40"
                            >
                                {t['mining.assign.confirm'].replace('{count}', String(assignMinerIds.length))}
                            </button>
                        </div>
                    </div>
                </div>
            ) : null}
        </div>
    );
}

function PowerInfo({label, value, tone = 'default'}: {label: string; value: string; tone?: 'default' | 'green' | 'amber' | 'red'}) {
    const tones = {default: 'text-white', green: 'text-emerald-300', amber: 'text-amber-300', red: 'text-red-300'};
    return <div className="rounded-xl border border-white/[0.07] bg-black/20 p-3"><span className="text-xs text-[#898994]">{label}</span><strong className={`mt-1 block text-lg ${tones[tone]}`}>{value}</strong></div>;
}

function FeeAllocationRow({color, label, percentage, totalHashrate}: {color: string; label: string; percentage: number; totalHashrate: number}) {
    const hashrate = totalHashrate * Math.max(0, percentage) / 100;
    return <div className="flex items-center gap-2 rounded-lg bg-white/[0.025] px-3 py-2.5"><span className={`h-2.5 w-2.5 shrink-0 rounded-full ${color}`}/><span className="min-w-0 flex-1"><strong className="block truncate text-xs">{label}</strong><span className="text-[11px] text-[#777781]">{percentage.toFixed(2)} %</span></span><strong className="font-mono text-xs">{hashrate.toFixed(2)} TH/s</strong></div>;
}

function MiniMetric({label, value, tone = 'text-white'}: {label: string; value: string; tone?: string}) {
    return <div className="min-w-0 rounded-lg bg-white/[0.035] px-2.5 py-2"><span className="block truncate text-[10px] text-[#777781]">{label}</span><strong className={`mt-1 block truncate text-xs ${tone}`}>{value}</strong></div>;
}

function PowerSlider({label, value, min, max, step, background, onChange}: {label: string; value: number; min: number; max: number; step: number; background: string; onChange: (value: number) => void}) {
    return <label className="block"><span className="flex items-center justify-between gap-4 text-xs text-[#a2a2ac]"><span>{label}</span><strong className="font-mono text-base text-white">{value} W</strong></span><input className="power-target-range mt-3" max={max} min={min} onChange={event => onChange(Number(event.target.value))} step={step} style={{'--power-range-background': background} as React.CSSProperties} type="range" value={value}/></label>;
}

function WarningCard({icon, title, text, tone}: {icon: React.ReactNode; title: string; text: string; tone: 'amber' | 'red'}) {
    const style = tone === 'red' ? 'border-red-500/30 bg-red-500/10 text-red-100' : 'border-amber-400/30 bg-amber-400/10 text-amber-100';
    return <div className={`flex gap-3 rounded-xl border p-4 ${style}`}>{icon}<div><strong className="block text-sm">{title}</strong><p className="mt-1 text-xs leading-5 opacity-80">{text}</p></div></div>;
}
