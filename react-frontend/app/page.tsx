'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Sun, Plus, Zap, Settings, Activity } from 'lucide-react';
import { StartViewData } from './types';
import AppLogo from './components/app-logo';

import de from './locales/de.json';
import en from './locales/en.json';

const translations = { de, en };
type Language = 'de' | 'en';

function formatString(template: string, params: Record<string, string | number>) {
    return Object.keys(params).reduce((acc, key) => {
        return acc.replace(`{${key}}`, String(params[key]));
    }, template);
}

export default function PVSiteSelectionView() {
    const router = useRouter();

    const [data, setData] = useState<StartViewData | null>(null);
    const [selectedSite, setSelectedSite] = useState<string>("");
    const [lang, setLang] = useState<Language>('de');
    const [checkingSite, setCheckingSite] = useState(false);
    const [selectionError, setSelectionError] = useState<string | null>(null);

    const t = translations[lang];

    useEffect(() => {
        async function fetchData() {
            try {
                const response = await fetch('/api/start-info');
                if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);

                const result = await response.json();
                setData(result);
            } catch (error) {
                console.error("Could not load starting info", error);
            }
        }
        fetchData();
    }, []);

    if (!data) {
        return (
            <div className="flex justify-center items-center h-screen bg-gray-900 text-white">
                <div className="animate-spin mr-3"><Sun /></div>
                Loading...
            </div>
        );
    }

    return (
        <div className="flex flex-col items-center justify-center min-h-screen bg-gray-900 text-white p-4">
            <div className="bg-gray-800 p-8 rounded-xl shadow-2xl w-full max-w-md relative">

                {/* Sprachauswahl */}
                <div className="absolute top-4 right-4">
                    <select
                        className="bg-gray-700 text-xs border border-gray-600 rounded p-1 text-white focus:outline-none focus:border-blue-500 cursor-pointer"
                        value={lang}
                        onChange={(e) => setLang(e.target.value as Language)}
                    >
                        <option value="de">DE</option>
                        <option value="en">EN</option>
                    </select>
                </div>

                {/* Header Section */}
                <header className="mb-8 mt-1 flex flex-col items-center text-center">
                    <AppLogo className="-my-9" priority variant="full"/>
                    <h1 className="text-3xl font-bold mb-2">{t.title}</h1>
                    <p className="text-gray-400">{t.subtitle}</p>
                </header>

                {/* ComboBox: PVSite Selection */}
                <div className="mb-6">
                    <label className="block text-sm font-medium text-gray-300 mb-2">
                        {t.dropdownLabel}
                    </label>
                    <select
                        className="w-full p-3 bg-gray-700 border border-gray-600 rounded text-white focus:outline-none focus:border-blue-500 cursor-pointer"
                        value={selectedSite}
                        disabled={checkingSite}
                        onChange={async (e) => {
                            const siteId = e.target.value;
                            setSelectedSite(siteId);
                            if (siteId) {
                                setCheckingSite(true);
                                setSelectionError(null);
                                try {
                                    const response = await fetch(`/api/pv-site/${siteId}/profile-compatibility`, {cache: 'no-store'});
                                    if (!response.ok) throw new Error();
                                    const result = await response.json() as {compatible: boolean};
                                    router.push(`/site/${siteId}/${result.compatible ? 'dashboard' : 'repair-profiles'}`);
                                } catch {
                                    setSelectionError(t.profileCheckError);
                                    setSelectedSite('');
                                } finally {
                                    setCheckingSite(false);
                                }
                            }
                        }}
                    >
                        <option value="">{t.dropdownPlaceholder}</option>
                        {data.sites.map(site => (
                            <option key={site.id} value={site.id}>{site.name}</option>
                        ))}
                    </select>
                    {checkingSite ? <p className="mt-2 text-sm text-blue-300">{t.profileCheck}</p> : null}
                    {selectionError ? <p className="mt-2 text-sm text-red-400">{selectionError}</p> : null}
                </div>

                {/* Register Button & Limit Info */}
                <div className="mt-4 border-t border-gray-700 pt-6">
                    <button
                        disabled={data.limitExceeded}
                        title={data.limitExceeded ? t.limitTooltip : ""}
                        onClick={() => router.push('/setup')}
                        className={`w-full py-3 rounded font-bold transition-colors flex justify-center items-center gap-2 ${
                            data.limitExceeded
                                ? 'bg-gray-700 text-gray-500 cursor-not-allowed'
                                : 'bg-blue-600 hover:bg-blue-500 text-white'
                        }`}
                    >
                        <Plus size={20} />
                        {t.registerBtn}
                    </button>

                    {/* Hier wird die Hilfsfunktion für das JSON-Template genutzt */}
                    <div className="flex justify-between items-center mt-4 text-sm">
            <span className="text-gray-400">
              {formatString(t.limitText, { current: data.currentCount, limit: data.limit })}
            </span>
                        <span className={`px-2 py-1 rounded text-xs font-bold ${
                            data.limitExceeded ? "bg-red-900/50 text-red-400 border border-red-800" : "bg-green-900/50 text-green-400 border border-green-800"
                        }`}>
              {data.limitExceeded ? t.limitReached : t.limitAvailable}
            </span>
                    </div>
                </div>

                {/* Subtle Links */}
                <div className="mt-8 flex flex-col items-center gap-3">
                    <a
                        href="/lightning-wallet"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center gap-2 text-yellow-500 hover:text-yellow-400 transition-colors text-sm font-medium"
                    >
                        <Zap size={16} />
                        {t.linkLightning}
                    </a>

                    <div className="flex flex-wrap items-center justify-center gap-2 text-xs text-gray-500">
                        <a href="/config/pv/rest" target="_blank" rel="noopener noreferrer" className="hover:text-gray-300 transition-colors flex items-center gap-1">
                            <Activity size={14} /> {t.linkRest}
                        </a>
                        <span>•</span>
                        <a href="/config/pv/modbus/tcp" target="_blank" rel="noopener noreferrer" className="hover:text-gray-300 transition-colors flex items-center gap-1">
                            <Settings size={14} /> {t.linkModbus}
                        </a>
                        <span>•</span>
                        <a href="/config/pv/modbus/rtu" target="_blank" rel="noopener noreferrer" className="hover:text-gray-300 transition-colors">{t.linkModbusRtu}</a>
                        <span>•</span>
                        <a href="/config/pv/mqtt" target="_blank" rel="noopener noreferrer" className="hover:text-gray-300 transition-colors">{t.linkMqtt}</a>
                        <span>•</span>
                        <a href="/config/pv/websocket" target="_blank" rel="noopener noreferrer" className="hover:text-gray-300 transition-colors">{t.linkWebSocket}</a>
                    </div>
                </div>

            </div>
        </div>
    );
}
