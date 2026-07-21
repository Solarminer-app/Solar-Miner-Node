'use client';

import {useCallback, useEffect, useMemo, useState} from 'react';
import {useParams, useRouter} from 'next/navigation';
import {AlertTriangle, ArrowLeft, CheckCircle2, LoaderCircle, RefreshCw, Save} from 'lucide-react';

import de from '../../../locales/de.json';
import en from '../../../locales/en.json';
import {useSitePreferences} from '../site-preferences-context';

type ProfileSection = {sectionKey: string; name: string};
type ProfileCandidate = {profileName: string; sections: ProfileSection[]};
type ProfileIssue = {
    deviceId: string;
    deviceName: string;
    deviceType: string;
    providerId: string;
    currentProfileName: string;
    currentSectionKey: string;
    candidates: ProfileCandidate[];
};
type CompatibilityResult = {compatible: boolean; issues: ProfileIssue[]};
type RepairSelection = {profileName: string; sectionKey: string};

const translations = {de, en};
const inputClass = 'mt-1.5 w-full rounded-xl border border-white/10 bg-[#0e0e11] px-3 py-2.5 text-sm text-white outline-none focus:border-yellow-400/60';

function initialSelection(issue: ProfileIssue): RepairSelection {
    const profile = issue.candidates[0];
    return {profileName: profile?.profileName ?? '', sectionKey: profile?.sections[0]?.sectionKey ?? ''};
}

export default function RepairProfilesPage() {
    const {siteId} = useParams<{siteId: string}>();
    const router = useRouter();
    const {locale, isHydrated} = useSitePreferences();
    const t = translations[locale] as Record<string, string>;
    const [result, setResult] = useState<CompatibilityResult | null>(null);
    const [selections, setSelections] = useState<Record<string, RepairSelection>>({});
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const response = await fetch(`/api/pv-site/${siteId}/profile-compatibility`, {cache: 'no-store'});
            if (!response.ok) throw new Error();
            const data = await response.json() as CompatibilityResult;
            if (data.compatible) {
                router.replace(`/site/${siteId}/dashboard`);
                return;
            }
            setResult(data);
            setSelections(Object.fromEntries(data.issues.map((issue) => [issue.deviceId, initialSelection(issue)])));
        } catch {
            setError(t['profile_repair.error']);
        } finally {
            setLoading(false);
        }
    }, [router, siteId, t]);

    useEffect(() => {
        if (!isHydrated) return;
        void load();
    }, [isHydrated, load]);

    const canSave = useMemo(() => Boolean(result?.issues.length)
        && result!.issues.every((issue) => selections[issue.deviceId]?.profileName && selections[issue.deviceId]?.sectionKey),
    [result, selections]);

    const selectProfile = (issue: ProfileIssue, profileName: string) => {
        const profile = issue.candidates.find((candidate) => candidate.profileName === profileName);
        setSelections((current) => ({
            ...current,
            [issue.deviceId]: {profileName, sectionKey: profile?.sections[0]?.sectionKey ?? ''},
        }));
    };

    const save = async () => {
        if (!result || !canSave) return;
        setSaving(true);
        setError(null);
        try {
            const response = await fetch(`/api/pv-site/${siteId}/profile-compatibility`, {
                method: 'PUT',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({repairs: result.issues.map((issue) => ({deviceId: issue.deviceId, ...selections[issue.deviceId]}))}),
            });
            if (!response.ok) throw new Error();
            const updated = await response.json() as CompatibilityResult;
            if (!updated.compatible) {
                setResult(updated);
                setSelections(Object.fromEntries(updated.issues.map((issue) => [issue.deviceId, initialSelection(issue)])));
                throw new Error();
            }
            router.replace(`/site/${siteId}/dashboard`);
        } catch {
            setError(t['profile_repair.error']);
        } finally {
            setSaving(false);
        }
    };

    const refreshProfiles = async () => {
        setRefreshing(true);
        setError(null);
        try {
            const response = await fetch(`/api/setup/catalog/refresh?locale=${locale}`, {method: 'POST'});
            if (!response.ok) throw new Error();
            await load();
        } catch {
            setError(t['profile_repair.error']);
        } finally {
            setRefreshing(false);
        }
    };

    if (loading || !result) {
        return <div className="grid min-h-[60vh] place-items-center text-[#b7b7c0]"><div className="flex items-center gap-3"><LoaderCircle className="animate-spin text-yellow-400" size={20}/>{error ?? t['profile_repair.loading']}</div></div>;
    }

    return (
        <main className="mx-auto max-w-5xl space-y-6 px-4 py-8 sm:px-6">
            <header className="rounded-3xl border border-orange-400/20 bg-orange-400/[0.05] p-6 sm:p-8">
                <div className="flex items-start gap-4">
                    <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-orange-400/10 text-orange-300"><AlertTriangle size={22}/></span>
                    <div><h1 className="text-2xl font-bold text-white sm:text-3xl">{t['profile_repair.title']}</h1><p className="mt-2 max-w-3xl text-sm leading-6 text-[#aaaab4]">{t['profile_repair.subtitle']}</p></div>
                </div>
            </header>

            {result.issues.map((issue) => {
                const selection = selections[issue.deviceId] ?? initialSelection(issue);
                const profile = issue.candidates.find((candidate) => candidate.profileName === selection.profileName);
                return <section className="rounded-2xl border border-white/[0.08] bg-[#151518] p-5" key={issue.deviceId}>
                    <div className="flex flex-wrap items-start justify-between gap-3 border-b border-white/[0.07] pb-4">
                        <div><h2 className="font-semibold text-white">{issue.deviceName}</h2><p className="mt-1 text-xs text-[#85858f]">{issue.deviceType} · {issue.providerId}</p></div>
                        <span className="rounded-lg bg-red-400/10 px-3 py-1.5 text-xs text-red-300">{t['profile_repair.current']}: {issue.currentProfileName} · {issue.currentSectionKey}</span>
                    </div>
                    {issue.candidates.length === 0 ? <p className="mt-4 rounded-xl border border-red-400/15 bg-red-400/[0.05] p-4 text-sm text-red-300">{t['profile_repair.no_candidates']}</p> : <div className="mt-4 grid gap-4 sm:grid-cols-2">
                        <label className="text-sm text-[#b7b7c0]">{t['profile_repair.profile']}<select className={inputClass} onChange={(event) => selectProfile(issue, event.target.value)} value={selection.profileName}>{issue.candidates.map((candidate) => <option key={candidate.profileName}>{candidate.profileName}</option>)}</select></label>
                        <label className="text-sm text-[#b7b7c0]">{t['profile_repair.section']}<select className={inputClass} onChange={(event) => setSelections((current) => ({...current, [issue.deviceId]: {...selection, sectionKey: event.target.value}}))} value={selection.sectionKey}>{profile?.sections.map((section) => <option key={section.sectionKey} value={section.sectionKey}>{section.name} · {section.sectionKey}</option>)}</select></label>
                    </div>}
                </section>;
            })}

            {error ? <p className="rounded-xl border border-red-400/20 bg-red-400/[0.06] p-4 text-sm text-red-300">{error}</p> : null}
            <div className="flex flex-wrap justify-between gap-3">
                <button className="inline-flex items-center gap-2 rounded-xl border border-white/10 px-4 py-2.5 text-sm text-[#b7b7c0] hover:bg-white/[0.05]" onClick={() => router.push('/')} type="button"><ArrowLeft size={16}/>{t['profile_repair.back']}</button>
                <div className="flex flex-wrap gap-3"><button className="inline-flex items-center gap-2 rounded-xl border border-white/10 px-4 py-2.5 text-sm text-[#b7b7c0] hover:bg-white/[0.05] disabled:opacity-40" disabled={refreshing || saving} onClick={() => void refreshProfiles()} type="button"><RefreshCw className={refreshing ? 'animate-spin' : ''} size={16}/>{t['profile_repair.refresh']}</button><button className="inline-flex items-center gap-2 rounded-xl bg-yellow-400 px-5 py-2.5 text-sm font-semibold text-black disabled:opacity-40" disabled={!canSave || saving} onClick={() => void save()} type="button">{saving ? <LoaderCircle className="animate-spin" size={16}/> : canSave ? <Save size={16}/> : <CheckCircle2 size={16}/>} {saving ? t['profile_repair.saving'] : t['profile_repair.save']}</button></div>
            </div>
        </main>
    );
}
