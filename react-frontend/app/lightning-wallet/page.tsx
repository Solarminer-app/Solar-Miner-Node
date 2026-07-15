'use client';

import { useCallback, useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
    ArrowLeft, Wallet, Copy, Zap, Link as LinkIcon, Settings,
    History, QrCode, Network, ArrowUpCircle, ArrowDownCircle, AtSign, Check, X, AlertTriangle
} from 'lucide-react';

// Lokale Übersetzungen importieren (Pfade ggf. anpassen)
import de from './../locales/de.json';
import en from './../locales/en.json';

const translations = { de, en };
type Language = 'de' | 'en';
type Currency = 'EUR' | 'USD';

interface LightningTransaction {
    id: string;
    type: 'INCOMING' | 'OUTGOING';
    timestamp: string;
    memo: string;
    amountSat: number;
    amountFormatted: string;
    status: 'SETTLED' | 'PENDING' | 'EXPIRED';
}

interface WalletData {
    balanceSat: number;
    balanceFormatted: string;
    feeCreditSat: number;
    feeCreditFormatted: string;
    lightningAddress: string;
    bolt12Offer: string;
    stats: {
        activeChannels: number;
        localLiquidityFormatted: string;
        remoteLiquidityFormatted: string;
    };
    transactions: LightningTransaction[];
}

export default function LightningWalletView() {
    const router = useRouter();
    const [lang, setLang] = useState<Language>('de');
    const [currency, setCurrency] = useState<Currency>('EUR');

    // Cast für strikte TypeScript-Sicherheit bei dynamischen JSON-Keys
    const t = translations[lang] as Record<string, string>;

    const [data, setData] = useState<WalletData | null>(null);
    const [loading, setLoading] = useState(true);
    const [wsConnected, setWsConnected] = useState(true);
    const [wsEnabled, setWsEnabled] = useState(true);

    // Modal States
    const [isLightningModalOpen, setIsLightningModalOpen] = useState(false);
    const [isOnChainModalOpen, setIsOnChainModalOpen] = useState(false);

    // Form States
    const [payTarget, setPayTarget] = useState("");
    const [payAmount, setPayAmount] = useState<string>("");
    const [onChainAddress, setOnChainAddress] = useState("");
    const [onChainAmount, setOnChainAmount] = useState<string>("");
    const [feeRate, setFeeRate] = useState<string>("5"); // Standard: 5 sat/vB

    const [sending, setSending] = useState(false);

    // Haupt-Daten-Fetch
    const fetchWallet = useCallback(async () => {
        try {
            setLoading(true);
            const response = await fetch(`http://localhost:8080/api/lightning-wallet?currency=${currency}&locale=${lang}`);
            if (response.ok) {
                const result = await response.json();
                setData(result);
            }
        } catch (e) {
            console.error("Fehler beim Laden der Wallet", e);
        } finally {
            setLoading(false);
        }
    }, [currency, lang]);

    useEffect(() => {
        const timeout = window.setTimeout(() => void fetchWallet(), 0);
        return () => window.clearTimeout(timeout);
    }, [fetchWallet]);

    // WebSocket Verbindung aktivieren/trennen
    const toggleConnection = async () => {
        try {
            const response = await fetch('http://localhost:8080/api/lightning-wallet/connection/toggle', {
                method: 'POST'
            });
            if (response.ok) {
                const status = await response.json();
                setWsEnabled(status.enabled);
                setWsConnected(status.connected);
            }
        } catch (e) {
            console.error("Fehler beim Toggle der Verbindung", e);
        }
    };

    // Blitz-Auszahlung (Lightning) absenden
    const handleSendPayment = async () => {
        if (!payTarget.trim()) return;
        try {
            setSending(true);
            const amountSat = payAmount ? parseInt(payAmount) : null;

            const response = await fetch('http://localhost:8080/api/lightning-wallet/pay', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    target: payTarget,
                    amountSat: amountSat
                })
            });

            if (response.ok) {
                const result = await response.json();
                if (result.success) {
                    setIsLightningModalOpen(false);
                    setPayTarget("");
                    setPayAmount("");
                    fetchWallet();
                } else {
                    alert(t["lightning.notification.payment_failed"]);
                }
            }
        } catch (e) {
            console.error("Netzwerkfehler beim Senden", e);
        } finally {
            setSending(false);
        }
    };

    // Kette-Auszahlung (On-Chain) absenden
    const handleOnChainWithdraw = async () => {
        if (!onChainAddress.trim() || !onChainAmount) return;
        try {
            setSending(true);
            const response = await fetch('http://localhost:8080/api/lightning-wallet/withdraw/onchain', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    address: onChainAddress,
                    amountSat: parseInt(onChainAmount),
                    feeRateSatPerVByte: parseInt(feeRate)
                })
            });

            if (response.ok) {
                const result = await response.json();
                if (result.success) {
                    setIsOnChainModalOpen(false);
                    setOnChainAddress("");
                    setOnChainAmount("");
                    setFeeRate("5");
                    alert(t["lightning.notification.onchain_success"]);
                    fetchWallet();
                } else {
                    alert(t["lightning.notification.onchain_failed"]);
                }
            }
        } catch (e) {
            console.error("Fehler bei On-Chain Auszahlung", e);
        } finally {
            setSending(false);
        }
    };

    if (loading && !data) return <div className="flex justify-center items-center h-screen bg-gray-900 text-white">{t["lightning.loading"]}</div>;
    if (!data) return <div className="flex justify-center items-center h-screen bg-gray-900 text-white">{t["lightning.error.no_data"]}</div>;

    return (
        <div className="min-h-screen bg-gray-900 text-white p-4 md:p-8">
            <div className="max-w-7xl mx-auto space-y-6">

                {/* HEADER */}
                <header className="flex items-center justify-between gap-4 mb-8">
                    <div className="flex items-center gap-4">
                        <button
                            onClick={() => router.push('/')}
                            aria-label={t["lightning.action.back"]}
                            className="p-2 bg-gray-800 hover:bg-gray-700 rounded-full transition"
                        >
                            <ArrowLeft size={24} />
                        </button>
                        <div>
                            <h1 className="text-2xl md:text-3xl font-bold">{t["lightning.header.title"]}</h1>
                            <p className="text-gray-400 text-sm md:text-base">{t["lightning.header.subtitle"]}</p>
                        </div>
                    </div>

                    <div className="flex items-center gap-2">
                        <select
                            value={currency}
                            onChange={(e) => setCurrency(e.target.value as Currency)}
                            className="bg-gray-800 border border-gray-700 rounded px-2 py-1 text-sm outline-none cursor-pointer focus:border-blue-500"
                        >
                            <option value="EUR">EUR</option>
                            <option value="USD">USD</option>
                        </select>
                        <select
                            value={lang}
                            onChange={(e) => setLang(e.target.value as Language)}
                            className="bg-gray-800 border border-gray-700 rounded px-2 py-1 text-sm outline-none cursor-pointer focus:border-blue-500"
                        >
                            <option value="de">DE</option>
                            <option value="en">EN</option>
                        </select>
                    </div>
                </header>

                {/* DASHBOARD GRID */}
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

                    {/* MAIN COLUMN */}
                    <div className="lg:col-span-2 space-y-6">

                        <section className="bg-gray-800 rounded-xl p-6 shadow-lg border border-gray-700">
                            <div className="flex items-center gap-2 mb-4 text-blue-400">
                                <Wallet size={20} />
                                <h2 className="font-semibold text-lg text-white">{t["lightning.card.balance"]}</h2>
                            </div>

                            <div className="mb-6">
                                <div className="text-4xl font-bold">{data.balanceFormatted}</div>
                                {data.feeCreditSat > 0 && (
                                    <div className="text-sm text-green-400 mt-1">
                                        {t["lightning.label.fee_credit"]}: {data.feeCreditFormatted}
                                    </div>
                                )}
                            </div>

                            <div className="flex items-center justify-between bg-gray-900 p-3 rounded-lg mb-6 border border-gray-700">
                                <div className="flex items-center gap-3 overflow-hidden">
                                    <AtSign size={16} className="text-blue-500 shrink-0" />
                                    <div className="truncate">
                                        <div className="text-xs text-gray-400">{t["lightning.label.address"]}</div>
                                        <div className="font-semibold truncate">{data.lightningAddress}</div>
                                    </div>
                                </div>
                                <button
                                    onClick={() => {
                                        navigator.clipboard.writeText(data.lightningAddress);
                                        alert(t["lightning.notification.copied"]);
                                    }}
                                    className="p-2 bg-gray-700 hover:bg-gray-600 rounded transition shrink-0"
                                    title={t["lightning.action.copy"]}
                                >
                                    <Copy size={16} />
                                </button>
                            </div>

                            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                                <button
                                    onClick={() => setIsLightningModalOpen(true)}
                                    className="flex justify-center items-center gap-2 bg-blue-600 hover:bg-blue-500 py-3 rounded-lg font-semibold transition"
                                >
                                    <Zap size={18} /> {t["lightning.button.withdraw_lightning"]}
                                </button>
                                <button
                                    onClick={() => setIsOnChainModalOpen(true)}
                                    className="flex justify-center items-center gap-2 bg-gray-700 hover:bg-gray-600 py-3 rounded-lg font-semibold transition"
                                >
                                    <LinkIcon size={18} /> {t["lightning.button.withdraw_onchain"]}
                                </button>
                                <button className="flex justify-center items-center gap-2 bg-gray-800 border border-gray-600 text-gray-400 cursor-not-allowed py-3 rounded-lg font-semibold">
                                    <Settings size={18} /> {t["lightning.button.auto_rules"]}
                                </button>
                            </div>
                        </section>

                        <section className="bg-gray-800 rounded-xl p-6 shadow-lg border border-gray-700">
                            <div className="flex items-center gap-2 mb-4 text-gray-300">
                                <History size={20} />
                                <h2 className="font-semibold text-lg text-white">{t["lightning.section.history"]}</h2>
                            </div>
                            <div className="overflow-x-auto">
                                <table className="w-full text-left text-sm text-gray-300">
                                    <thead className="bg-gray-900 text-gray-400">
                                    <tr>
                                        <th className="p-3 rounded-tl-lg">{t["lightning.grid.type"]}</th>
                                        <th className="p-3">{t["lightning.grid.date"]}</th>
                                        <th className="p-3">{t["lightning.grid.memo"]}</th>
                                        <th className="p-3">{t["lightning.grid.amount"]}</th>
                                        <th className="p-3 rounded-tr-lg">{t["lightning.grid.status"]}</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {data.transactions.map((tx) => (
                                        <tr key={tx.id} className="border-b border-gray-700 hover:bg-gray-750">
                                            <td className="p-3">
                                                {tx.type === 'INCOMING' ? <ArrowDownCircle className="text-green-500" size={18}/> : <ArrowUpCircle className="text-red-500" size={18}/>}
                                            </td>
                                            <td className="p-3">{tx.timestamp}</td>
                                            <td className="p-3 text-gray-100">{tx.memo}</td>
                                            <td className="p-3 font-mono">{tx.amountFormatted}</td>
                                            <td className="p-3">
                          <span className={`px-2 py-1 rounded text-xs font-bold ${
                              tx.status === 'SETTLED' ? 'bg-green-900/50 text-green-400 border border-green-800' :
                                  tx.status === 'PENDING' ? 'bg-yellow-900/50 text-yellow-400 border border-yellow-800' :
                                      'bg-red-900/50 text-red-400 border border-red-800'
                          }`}>
                            {t[`lightning.transaction_status.${tx.status.toLowerCase()}`]}
                          </span>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        </section>
                    </div>

                    {/* SIDE COLUMN */}
                    <div className="space-y-6">

                        <section className="bg-gray-800 rounded-xl p-6 shadow-lg border border-gray-700">
                            <div className="flex items-center gap-2 mb-4">
                                <Network size={20} className="text-purple-400" />
                                <h2 className="font-semibold text-lg">{t["lightning.card.connection_status"]}</h2>
                            </div>
                            <p className="text-sm text-gray-400 mb-4">{t["lightning.bolt11.info"]}</p>
                            <div className="flex items-center justify-between">
                <span className={`px-3 py-1 rounded-full text-xs font-bold border ${
                    !wsEnabled ? 'bg-gray-800 text-gray-400 border-gray-600' :
                        wsConnected ? 'bg-green-900/50 text-green-400 border-green-800' : 'bg-yellow-900/50 text-yellow-400 border-yellow-800'
                }`}>
                  {!wsEnabled
                      ? t["lightning.connection.disabled"]
                      : wsConnected
                          ? t["lightning.connection.connected"]
                          : t["lightning.connection.connecting"]}
                </span>
                                <button
                                    onClick={toggleConnection}
                                    className="text-sm bg-gray-700 hover:bg-gray-600 px-3 py-1.5 rounded transition"
                                >
                                    {wsEnabled ? t["lightning.connection.disconnect"] : t["lightning.connection.enable"]}
                                </button>
                            </div>
                        </section>

                        <section className="bg-gray-800 rounded-xl p-6 shadow-lg border border-gray-700">
                            <div className="flex items-center gap-2 mb-4">
                                <Settings size={20} className="text-gray-400" />
                                <h2 className="font-semibold text-lg">{t["lightning.section.nerds"]}</h2>
                            </div>
                            <div className="space-y-4">
                                <div className="flex justify-between items-center border-b border-gray-700 pb-2">
                                    <span className="text-gray-400 text-sm">{t["lightning.stats.channels"]}</span>
                                    <span className="font-bold">{data.stats.activeChannels}</span>
                                </div>
                                <div className="flex justify-between items-center border-b border-gray-700 pb-2">
                                    <span className="text-gray-400 text-sm">{t["lightning.stats.local"]}</span>
                                    <span className="font-mono text-green-400">{data.stats.localLiquidityFormatted}</span>
                                </div>
                                <div className="flex justify-between items-center">
                                    <span className="text-gray-400 text-sm">{t["lightning.stats.remote"]}</span>
                                    <span className="font-mono text-blue-400">{data.stats.remoteLiquidityFormatted}</span>
                                </div>
                            </div>
                        </section>

                        <section className="bg-gray-800 rounded-xl p-6 shadow-lg border border-gray-700">
                            <div className="flex items-center gap-2 mb-4">
                                <QrCode size={20} className="text-yellow-400" />
                                <h2 className="font-semibold text-lg">{t["lightning.details.bolt12"]}</h2>
                            </div>
                            <div className="flex items-center gap-2">
                                <input
                                    type="text"
                                    readOnly
                                    value={data.bolt12Offer}
                                    className="w-full bg-gray-900 border border-gray-700 rounded p-2 text-sm text-gray-300 outline-none"
                                />
                                <button
                                    onClick={() => {
                                        navigator.clipboard.writeText(data.bolt12Offer);
                                        alert(t["lightning.notification.copied_bolt12"]);
                                    }}
                                    className="bg-gray-700 hover:bg-gray-600 p-2 rounded transition shrink-0"
                                >
                                    <Copy size={16} />
                                </button>
                            </div>
                        </section>

                    </div>
                </div>
            </div>

            {/* MODAL: LIGHTNING PAY */}
            {isLightningModalOpen && (
                <div className="fixed inset-0 bg-black/70 flex items-center justify-center p-4 z-50">
                    <div className="bg-gray-800 p-6 rounded-xl shadow-2xl w-full max-w-md border border-gray-700">
                        <div className="flex justify-between items-center mb-4">
                            <h3 className="text-xl font-bold">{t["lightning.dialog.lightning.title"]}</h3>
                            <button aria-label={t["lightning.action.close"]} onClick={() => setIsLightningModalOpen(false)} className="text-gray-400 hover:text-white">
                                <X size={20} />
                            </button>
                        </div>

                        <p className="text-sm text-gray-400 mb-4">{t["lightning.dialog.info"]}</p>

                        <div className="mb-4">
                            <label className="block text-sm font-medium mb-1">{t["lightning.dialog.address_label"]}</label>
                            <textarea
                                value={payTarget}
                                onChange={(e) => setPayTarget(e.target.value)}
                                className="w-full bg-gray-900 border border-gray-700 rounded-lg p-3 text-white min-h-[80px] outline-none focus:border-blue-500 text-sm"
                                placeholder={t["lightning.dialog.target_placeholder"]}
                            />
                        </div>

                        <div className="mb-6">
                            <label className="block text-sm font-medium mb-1">
                                {t["lightning.dialog.amount_label"]} <span className="text-gray-500 font-normal text-xs">{t["lightning.dialog.amount_optional"]}</span>
                            </label>
                            <input
                                type="number"
                                min="0"
                                value={payAmount}
                                onChange={(e) => setPayAmount(e.target.value)}
                                className="w-full bg-gray-900 border border-gray-700 rounded-lg p-3 text-white outline-none focus:border-blue-500 font-mono text-sm"
                                placeholder={t["lightning.dialog.amount_placeholder"]}
                            />
                            {payAmount && parseInt(payAmount) > data.balanceSat && (
                                <div className="flex items-center gap-1.5 text-xs text-red-400 mt-1.5">
                                    <AlertTriangle size={14} />
                                    <span>{t["lightning.dialog.amount_warning"]}</span>
                                </div>
                            )}
                        </div>

                        <div className="flex justify-end gap-3">
                            <button
                                onClick={() => setIsLightningModalOpen(false)}
                                disabled={sending}
                                className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg font-medium transition disabled:opacity-50"
                            >
                                {t["lightning.dialog.button.cancel"]}
                            </button>
                            <button
                                className="px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg font-medium transition flex items-center gap-2 disabled:opacity-50"
                                onClick={handleSendPayment}
                                disabled={sending || !payTarget.trim() || Boolean(payAmount && parseInt(payAmount) > data.balanceSat)}
                            >
                                {sending ? t["lightning.dialog.sending"] : <><Check size={18} /> {t["lightning.dialog.button.confirm"]}</>}
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* MODAL: ON-CHAIN WITHDRAW */}
            {isOnChainModalOpen && (
                <div className="fixed inset-0 bg-black/70 flex items-center justify-center p-4 z-50">
                    <div className="bg-gray-800 p-6 rounded-xl shadow-2xl w-full max-w-md border border-gray-700">
                        <div className="flex justify-between items-center mb-4">
                            <h3 className="text-xl font-bold">{t["lightning.button.withdraw_onchain"]}</h3>
                            <button aria-label={t["lightning.action.close"]} onClick={() => setIsOnChainModalOpen(false)} className="text-gray-400 hover:text-white">
                                <X size={20} />
                            </button>
                        </div>

                        {/* Bitcoin Adresse */}
                        <div className="mb-4">
                            <label className="block text-sm font-medium mb-1">{t["lightning.dialog.address_onchain_label"]}</label>
                            <input
                                type="text"
                                value={onChainAddress}
                                onChange={(e) => setOnChainAddress(e.target.value)}
                                className="w-full bg-gray-900 border border-gray-700 rounded-lg p-3 text-white outline-none focus:border-blue-500 text-sm"
                                placeholder={t["lightning.dialog.onchain_address_placeholder"]}
                            />
                        </div>

                        {/* Betrag */}
                        <div className="mb-4">
                            <label className="block text-sm font-medium mb-1">{t["lightning.dialog.amount_label"]}</label>
                            <input
                                type="number"
                                min="0"
                                value={onChainAmount}
                                onChange={(e) => setOnChainAmount(e.target.value)}
                                className="w-full bg-gray-900 border border-gray-700 rounded-lg p-3 text-white outline-none focus:border-blue-500 font-mono text-sm"
                                placeholder={t["lightning.dialog.onchain_amount_placeholder"]}
                            />
                            {onChainAmount && parseInt(onChainAmount) > data.balanceSat && (
                                <div className="flex items-center gap-1.5 text-xs text-red-400 mt-1.5">
                                    <AlertTriangle size={14} />
                                    <span>{t["lightning.dialog.amount_warning"]}</span>
                                </div>
                            )}
                        </div>

                        {/* Fee Rate (sat/vB) */}
                        <div className="mb-6">
                            <label className="block text-sm font-medium mb-1">{t["lightning.dialog.fee_rate_label"]}</label>
                            <input
                                type="number"
                                min="1"
                                value={feeRate}
                                onChange={(e) => setFeeRate(e.target.value)}
                                className="w-full bg-gray-900 border border-gray-700 rounded-lg p-3 text-white outline-none focus:border-blue-500 font-mono text-sm"
                            />
                        </div>

                        <div className="flex justify-end gap-3">
                            <button
                                onClick={() => setIsOnChainModalOpen(false)}
                                disabled={sending}
                                className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg font-medium transition disabled:opacity-50"
                            >
                                {t["lightning.dialog.button.cancel"]}
                            </button>
                            <button
                                className="px-4 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg font-medium transition flex items-center gap-2 disabled:opacity-50"
                                onClick={handleOnChainWithdraw}
                                disabled={sending || !onChainAddress.trim() || !onChainAmount || parseInt(onChainAmount) > data.balanceSat}
                            >
                                {sending ? t["lightning.dialog.sending"] : <><Check size={18} /> {t["lightning.button.withdraw_onchain_submit"]}</>}
                            </button>
                        </div>
                    </div>
                </div>
            )}

        </div>
    );
}
