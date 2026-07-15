'use client';

import {FormEvent, useCallback, useEffect, useMemo, useState} from 'react';
import {useParams} from 'next/navigation';
import {
    ArrowDownRight,
    ArrowUpRight,
    Bitcoin,
    ChartNoAxesCombined,
    CircleDollarSign,
    Download,
    FileSpreadsheet,
    FileText,
    Gauge,
    Grid2X2Check,
    Landmark,
    Leaf,
    RefreshCw,
    Scale,
    Trash2,
    TrendingUp,
    Wallet,
    Zap,
} from 'lucide-react';
import {
    Bar,
    CartesianGrid,
    ComposedChart,
    Legend,
    Line,
    ReferenceLine,
    ResponsiveContainer,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts';

import de from '../../../locales/de.json';
import en from '../../../locales/en.json';
import type {BitcoinSaleDto, FinancePageDto, MoneyDto, PVStatisticDto} from '../../../types';
import {useSitePreferences} from '../site-preferences-context';

const translations = {de, en};
const API_BASE_URL = 'http://localhost:8080/api/pv-site';

type ExportType = 'csv' | 'mining-pdf' | 'pv-pdf' | 'sales-pdf';
type FinanceTab = 'overview' | 'history' | 'ledger';

export default function FinancePage() {
    const {siteId} = useParams<{siteId: string}>();
    const {locale, currency, timeZone, isHydrated} = useSitePreferences();
    const t = translations[locale] as Record<string, string>;
    const intlLocale = locale === 'de' ? 'de-DE' : 'en-US';

    const [data, setData] = useState<FinancePageDto | null>(null);
    const [from, setFrom] = useState('');
    const [to, setTo] = useState('');
    const [activeTab, setActiveTab] = useState<FinanceTab>('overview');
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [exporting, setExporting] = useState<ExportType | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [saleDate, setSaleDate] = useState('');
    const [saleBtc, setSaleBtc] = useState('');
    const [saleFiat, setSaleFiat] = useState('');

    const loadData = useCallback(async (selectedFrom?: string, selectedTo?: string) => {
        const query = new URLSearchParams({currency, timeZone});
        if (selectedFrom) query.set('from', selectedFrom);
        if (selectedTo) query.set('to', selectedTo);
        setLoading(true);
        try {
            const response = await fetch(`${API_BASE_URL}/${siteId}/finance?${query}`, {cache: 'no-store'});
            if (!response.ok) throw new Error(String(response.status));
            const nextData = await response.json() as FinancePageDto;
            setData(nextData);
            setFrom(nextData.from);
            setTo(nextData.to);
            setSaleDate((current) => current || nextData.to);
            setError(null);
        } catch (reason) {
            console.error('Failed to load finance page', reason);
            setError(t['finance.error.load']);
        } finally {
            setLoading(false);
        }
    }, [currency, siteId, t, timeZone]);

    useEffect(() => {
        if (!siteId || !isHydrated) return;
        const timeout = window.setTimeout(() => void loadData(), 0);
        return () => window.clearTimeout(timeout);
    }, [isHydrated, loadData, siteId]);

    const formatMoney = useCallback((money: MoneyDto) => new Intl.NumberFormat(intlLocale, {
        style: 'currency',
        currency: money.currency,
        maximumFractionDigits: 2,
    }).format(Number.isFinite(money.amount) ? money.amount : 0), [intlLocale]);
    const formatNumber = useCallback((value: number, digits = 2) => new Intl.NumberFormat(intlLocale, {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits,
    }).format(Number.isFinite(value) ? value : 0), [intlLocale]);
    const formatDate = useCallback((value: string | null) => {
        if (!value) return t['finance.value.not_available'];
        const parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
        if (!parts) return t['finance.value.not_available'];
        const date = new Date(Date.UTC(Number(parts[1]), Number(parts[2]) - 1, Number(parts[3])));
        if (Number.isNaN(date.getTime())) return t['finance.value.not_available'];
        return new Intl.DateTimeFormat(intlLocale, {dateStyle: 'medium', timeZone: 'UTC'}).format(date);
    }, [intlLocale, t]);

    const trendData = useMemo(() => {
        if (!data) return [];
        let cumulative = 0;
        return [...data.days].reverse().map((day) => {
            const pvValue = day.householdSavings.amount + day.feedInRevenue.amount;
            const miningNet = day.btcHistoricValue.amount - day.miningCost.amount;
            const operating = pvValue + miningNet;
            cumulative += operating;
            return {date: day.date, pvValue, miningNet, operating, cumulative};
        });
    }, [data]);

    const periodTrend = useMemo(() => {
        if (trendData.length < 2) return null;
        const middle = Math.ceil(trendData.length / 2);
        const first = trendData.slice(0, middle);
        const second = trendData.slice(middle);
        if (!second.length) return null;
        const average = (values: typeof trendData) => values.reduce((sum, value) => sum + value.operating, 0) / values.length;
        const previous = average(first);
        const current = average(second);
        return {previous, current, percent: Math.abs(previous) > 0.01 ? (current - previous) / Math.abs(previous) * 100 : null};
    }, [trendData]);

    const saveSale = async (event: FormEvent) => {
        event.preventDefault();
        setSaving(true);
        try {
            const response = await fetch(`${API_BASE_URL}/${siteId}/finance/sales`, {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({saleDate, amountBtc: Number(saleBtc), fiatAmount: Number(saleFiat), currency}),
            });
            if (!response.ok) throw new Error(String(response.status));
            setSaleBtc('');
            setSaleFiat('');
            await loadData(from, to);
        } catch (reason) {
            console.error('Failed to save bitcoin sale', reason);
            setError(t['finance.error.sale']);
        } finally {
            setSaving(false);
        }
    };

    const deleteSale = async (sale: BitcoinSaleDto) => {
        if (!window.confirm(t['finance.sale.delete_confirm'])) return;
        setSaving(true);
        try {
            const response = await fetch(`${API_BASE_URL}/${siteId}/finance/sales`, {
                method: 'DELETE',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify({saleDate: sale.saleDate, amountBtc: sale.amountBtc, fiatAmount: sale.fiatValue.amount, currency: sale.fiatValue.currency}),
            });
            if (!response.ok) throw new Error(String(response.status));
            await loadData(from, to);
        } catch (reason) {
            console.error('Failed to delete bitcoin sale', reason);
            setError(t['finance.error.sale']);
        } finally {
            setSaving(false);
        }
    };

    const exportReport = async (reportType: ExportType) => {
        if (!from || !to) return;
        setExporting(reportType);
        setError(null);
        const query = new URLSearchParams({from, to, currency, timeZone, locale});
        try {
            const response = await fetch(`${API_BASE_URL}/${siteId}/finance/export/${reportType}?${query}`);
            if (!response.ok) throw new Error(String(response.status));
            const url = URL.createObjectURL(await response.blob());
            const disposition = response.headers.get('Content-Disposition');
            const filename = disposition?.match(/filename="?([^";]+)"?/i)?.[1] ?? `finance-export.${reportType === 'csv' ? 'csv' : 'pdf'}`;
            const anchor = document.createElement('a');
            anchor.href = url;
            anchor.download = filename;
            document.body.appendChild(anchor);
            anchor.click();
            anchor.remove();
            window.setTimeout(() => URL.revokeObjectURL(url), 0);
        } catch (reason) {
            console.error('Failed to export finance report', reason);
            setError(t['finance.error.export']);
        } finally {
            setExporting(null);
        }
    };

    if (loading && !data) return <div className="flex min-h-[70vh] items-center justify-center text-[#9c9ca5]"><RefreshCw className="mr-2 animate-spin" size={20}/>{t['finance.loading']}</div>;
    if (!data) return <div className="flex min-h-[70vh] items-center justify-center text-red-300">{error ?? t['finance.error.load']}</div>;

    const allTime = data.allTimeKpis;
    const period = data.periodInsights;
    const capital = data.allTimeInsights;
    const roiWidth = Math.max(0, Math.min(100, allTime.roiProgressPercent));
    const scenarioDelta = allTime.unrealizedValue.amount * 0.3;
    const scenarios = [
        {label: t['finance.scenario.bear'], change: '-30 %', value: capital.netPosition.amount - scenarioDelta, tone: 'red'},
        {label: t['finance.scenario.current'], change: '±0 %', value: capital.netPosition.amount, tone: 'yellow'},
        {label: t['finance.scenario.bull'], change: '+30 %', value: capital.netPosition.amount + scenarioDelta, tone: 'green'},
    ];

    return <main className="min-h-screen bg-[#0b0b0e] px-3 py-4 text-white sm:px-5 lg:px-7"><div className="mx-auto max-w-[1700px] space-y-4">
        <header className="flex flex-wrap items-end justify-between gap-4 rounded-2xl border border-white/[0.07] bg-[#131318] px-4 py-4 sm:px-5"><div><p className="text-[10px] font-bold uppercase tracking-[0.2em] text-yellow-300">{t['finance.eyebrow']}</p><h1 className="mt-1 text-2xl font-bold">{t['finance.title']}</h1><p className="mt-1 text-sm text-[#81818b]">{t['finance.dashboard.subtitle']}</p></div><div className="flex flex-wrap gap-2"><ExportButton active={exporting === 'csv'} icon={<FileSpreadsheet/>} label={t['finance.export.csv']} onClick={() => void exportReport('csv')}/><ExportButton active={exporting === 'mining-pdf'} icon={<FileText/>} label={t['finance.export.mining_pdf']} onClick={() => void exportReport('mining-pdf')}/><ExportButton active={exporting === 'pv-pdf'} icon={<Download/>} label={t['finance.export.pv_pdf']} onClick={() => void exportReport('pv-pdf')}/></div></header>
        {error ? <div className="rounded-xl border border-red-400/20 bg-red-400/[0.06] px-4 py-3 text-sm text-red-200">{error}</div> : null}

        <section className="grid gap-4 xl:grid-cols-12">
            <article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4 sm:p-5 xl:col-span-8"><div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-sm font-semibold">{t['finance.capital.title']}</h2><p className="mt-1 text-xs text-[#74747e]">{t['finance.capital.subtitle']}</p></div><div className="text-right"><span className="text-xs text-[#74747e]">{t['finance.kpi.break_even']}</span><strong className="block text-sm">{formatDate(allTime.estimatedBreakEvenDate)}</strong></div></div><div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"><FinanceMetric label={t['finance.capital.value']} value={formatMoney(capital.totalCapitalValue)} icon={<TrendingUp/>} tone="green"/><FinanceMetric label={t['finance.capital.cost']} value={formatMoney(capital.totalCapitalCost)} icon={<Wallet/>} tone="red"/><FinanceMetric label={t['finance.capital.net']} value={formatMoney(capital.netPosition)} icon={<Scale/>} tone={capital.netPosition.amount >= 0 ? 'green' : 'red'}/><FinanceMetric label={t['finance.capital.remaining']} value={formatMoney(capital.remainingToBreakEven)} icon={<Gauge/>} tone="yellow"/></div><div className="mt-5"><div className="mb-2 flex items-center justify-between text-xs"><span className="text-[#777781]">{t['finance.kpi.roi']}</span><strong>{formatNumber(allTime.roiProgressPercent, 1)} %</strong></div><div className="h-3 overflow-hidden rounded-full bg-white/[0.06]"><div className="h-full rounded-full bg-gradient-to-r from-yellow-400 to-emerald-400" style={{width: `${roiWidth}%`}}/></div><div className="mt-2 flex justify-between text-[10px] text-[#5f5f68]"><span>{formatMoney(allTime.totalInvestment)}</span><span>{t['finance.capital.average_day']}: {formatMoney(capital.averageDailyCapitalValue)}</span></div></div></article>
            <article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4 sm:p-5 xl:col-span-4"><div className="flex items-center gap-2"><Bitcoin className="text-yellow-300" size={18}/><h2 className="text-sm font-semibold">{t['finance.scenario.title']}</h2></div><p className="mt-1 text-xs text-[#74747e]">{t['finance.scenario.subtitle']}</p><div className="mt-4 space-y-2">{scenarios.map((scenario) => <div className="flex items-center gap-3 rounded-lg bg-white/[0.025] px-3 py-2.5" key={scenario.label}><span className={`grid h-8 w-8 place-items-center rounded-lg ${scenario.tone === 'red' ? 'bg-red-400/10 text-red-300' : scenario.tone === 'green' ? 'bg-emerald-400/10 text-emerald-300' : 'bg-yellow-400/10 text-yellow-300'}`}>{scenario.tone === 'red' ? <ArrowDownRight size={16}/> : <ArrowUpRight size={16}/>}</span><span className="flex-1"><span className="block text-[10px] text-[#70707a]">{scenario.label} · {scenario.change}</span><strong className={scenario.value >= 0 ? 'text-emerald-300' : 'text-red-300'}>{formatMoney({amount: scenario.value, currency})}</strong></span></div>)}</div></article>
        </section>

        <section className="flex flex-wrap items-end justify-between gap-3 rounded-2xl border border-white/[0.07] bg-[#101014] p-3"><div className="flex flex-wrap gap-2"><DateField label={t['finance.filter.from']} min={data.setupDate} max={to} value={from} onChange={setFrom}/><DateField label={t['finance.filter.to']} min={from} value={to} onChange={setTo}/><button className="mt-auto inline-flex h-9 items-center gap-2 rounded-lg bg-yellow-400 px-4 text-xs font-bold text-black disabled:opacity-40" disabled={loading || !from || !to} onClick={() => void loadData(from, to)}><RefreshCw className={loading ? 'animate-spin' : ''} size={14}/>{t['finance.filter.apply']}</button></div><span className="text-xs text-[#777781]">{period.daysWithData} {t['finance.period.days']}</span></section>

        <nav className="flex gap-1 rounded-xl bg-[#101014] p-1" aria-label={t['finance.tabs.label']}>{(['overview', 'history', 'ledger'] as FinanceTab[]).map((tab) => <button className={`rounded-lg px-4 py-2 text-xs font-semibold transition ${activeTab === tab ? 'bg-white/[0.08] text-white' : 'text-[#73737d] hover:text-white'}`} key={tab} onClick={() => setActiveTab(tab)}>{t[`finance.tab.${tab}`]}</button>)}</nav>

        {activeTab === 'overview' ? <OverviewTab data={data} trendData={trendData} periodTrend={periodTrend} formatMoney={formatMoney} formatNumber={formatNumber} formatDate={formatDate} locale={intlLocale} t={t}/> : null}
        {activeTab === 'history' ? <HistoryTab days={data.days} formatMoney={formatMoney} formatNumber={formatNumber} formatDate={formatDate} t={t}/> : null}
        {activeTab === 'ledger' ? <LedgerTab currency={currency} data={data} exporting={exporting} saving={saving} saleDate={saleDate} saleBtc={saleBtc} saleFiat={saleFiat} setSaleDate={setSaleDate} setSaleBtc={setSaleBtc} setSaleFiat={setSaleFiat} saveSale={saveSale} deleteSale={deleteSale} exportReport={exportReport} formatMoney={formatMoney} formatNumber={formatNumber} formatDate={formatDate} t={t}/> : null}
    </div></main>;
}

function OverviewTab({data, trendData, periodTrend, formatMoney, formatNumber, formatDate, locale, t}: {data: FinancePageDto; trendData: Array<{date: string; pvValue: number; miningNet: number; operating: number; cumulative: number}>; periodTrend: {previous: number; current: number; percent: number | null} | null; formatMoney: (money: MoneyDto) => string; formatNumber: (value: number, digits?: number) => string; formatDate: (date: string | null) => string; locale: string; t: Record<string, string>}) {
    const period = data.periodInsights;
    const currency = period.operatingResult.currency;
    const contributions = [{label: t['finance.value.mining'], value: period.miningRevenueHistoric.amount, color: 'bg-yellow-300'}, {label: t['finance.value.household'], value: period.householdSavings.amount, color: 'bg-violet-300'}, {label: t['finance.value.feed_in'], value: period.feedInRevenue.amount, color: 'bg-emerald-300'}];
    const contributionTotal = Math.max(0, contributions.reduce((sum, item) => sum + item.value, 0));
    return <section className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4"><FinanceMetric label={t['finance.period.result']} value={formatMoney(period.operatingResult)} detail={t['finance.period.all_sources']} icon={<CircleDollarSign/>} tone={period.operatingResult.amount >= 0 ? 'green' : 'red'}/><FinanceMetric label={t['finance.period.mining_net']} value={formatMoney(period.miningNetHistoric)} detail={`${formatMoney(period.miningRevenueHistoric)} ${t['finance.period.revenue']}`} icon={<Bitcoin/>} tone={period.miningNetHistoric.amount >= 0 ? 'green' : 'red'}/><FinanceMetric label={t['finance.period.average_day']} value={formatMoney(period.averageDailyOperatingResult)} detail={periodTrend?.percent == null ? t['finance.value.not_available'] : `${periodTrend.percent >= 0 ? '+' : ''}${formatNumber(periodTrend.percent, 1)} % ${t['finance.period.vs_previous']}`} icon={<ChartNoAxesCombined/>} tone="cyan"/><FinanceMetric label={t['finance.period.profitable_days']} value={`${period.profitableMiningDays} / ${period.daysWithData}`} detail={period.daysWithData ? `${formatNumber(period.profitableMiningDays / period.daysWithData * 100, 0)} %` : '—'} icon={<Grid2X2Check/>} tone="violet"/></div>
        <div className="grid gap-4 xl:grid-cols-12">
            <article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4 xl:col-span-8"><div><h2 className="text-sm font-semibold">{t['finance.trend.title']}</h2><p className="mt-1 text-xs text-[#74747e]">{t['finance.trend.subtitle']}</p></div><div className="mt-4 h-[340px]">{trendData.length ? <ResponsiveContainer height="100%" width="100%"><ComposedChart data={trendData}><CartesianGrid stroke="#28282f" strokeDasharray="3 3"/><XAxis dataKey="date" stroke="#666670" tickFormatter={(value) => new Date(`${value}T00:00:00Z`).toLocaleDateString(locale, {month: 'short', day: '2-digit', timeZone: 'UTC'})}/><YAxis stroke="#666670"/><Tooltip contentStyle={{background: '#17171c', border: '1px solid #303038', borderRadius: 8}} formatter={(value) => new Intl.NumberFormat(locale, {style: 'currency', currency}).format(Number(value))}/><Legend/><ReferenceLine stroke="#55555f" y={0}/><Bar dataKey="pvValue" fill="#a78bfa" name={t['finance.trend.pv_value']} stackId="daily"/><Bar dataKey="miningNet" fill="#facc15" name={t['finance.trend.mining_net']} stackId="daily"/><Line dataKey="cumulative" dot={false} name={t['finance.trend.cumulative']} stroke="#34d399" strokeWidth={2}/></ComposedChart></ResponsiveContainer> : <div className="grid h-full place-items-center text-sm text-[#666670]">{t['finance.history.empty']}</div>}</div></article>
            <div className="space-y-4 xl:col-span-4"><article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4"><div className="flex items-center gap-2"><Leaf className="text-emerald-300" size={17}/><h2 className="text-sm font-semibold">{t['finance.value.title']}</h2></div><div className="mt-4 flex h-3 overflow-hidden rounded-full bg-white/[0.05]">{contributions.map((item) => <div className={item.color} key={item.label} style={{width: `${contributionTotal > 0 ? item.value / contributionTotal * 100 : 0}%`}}/>)}</div><div className="mt-3 space-y-2">{contributions.map((item) => <InsightRow color={item.color} key={item.label} label={item.label} value={formatMoney({amount: item.value, currency})}/>)}</div><div className="mt-3 border-t border-white/[0.06] pt-3"><InsightRow label={t['finance.value.total']} value={formatMoney(period.totalValueCreated)}/></div></article><article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4"><div className="flex items-center gap-2"><Zap className="text-red-300" size={17}/><h2 className="text-sm font-semibold">{t['finance.cost.title']}</h2></div><div className="mt-3 space-y-2"><InsightRow color="bg-red-400" label={t['finance.cost.grid']} value={formatMoney(period.miningGridCost)}/><InsightRow color="bg-orange-300" label={t['finance.cost.opportunity']} value={formatMoney(period.miningOpportunityCost)}/><InsightRow label={t['finance.cost.total']} value={formatMoney(period.miningEnergyCost)}/></div></article></div>
        </div>
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4"><InsightCard title={t['finance.unit.cost_btc']} value={period.minedBtc > 0 ? formatMoney(period.costPerMinedBtc) : '—'} subtitle={t['finance.unit.cost_btc_help']} icon={<Bitcoin/>}/><InsightCard title={t['finance.unit.break_even_price']} value={period.minedBtc > 0 ? formatMoney(period.breakEvenBtcPrice) : '—'} subtitle={t['finance.unit.break_even_help']} icon={<Scale/>}/><InsightCard title={t['finance.unit.grid_share']} value={`${formatNumber(period.gridMiningSharePercent, 1)} %`} subtitle={`${formatNumber(period.miningEnergyKwh, 1)} kWh ${t['finance.unit.mining_energy']}`} icon={<Landmark/>}/><InsightCard title={t['finance.unit.best_worst']} value={period.bestDay ? `${formatMoney(period.bestDayResult)} / ${formatMoney(period.worstDayResult)}` : '—'} subtitle={period.bestDay ? `${formatDate(period.bestDay)} · ${formatDate(period.worstDay)}` : t['finance.value.not_available']} icon={<TrendingUp/>}/></div>
    </section>;
}

function HistoryTab({days, formatMoney, formatNumber, formatDate, t}: {days: PVStatisticDto[]; formatMoney: (money: MoneyDto) => string; formatNumber: (value: number, digits?: number) => string; formatDate: (date: string | null) => string; t: Record<string, string>}) {
    return <section className="overflow-x-auto rounded-2xl border border-white/[0.07] bg-[#111115]"><table className="w-full min-w-[1450px] text-left text-xs"><thead className="border-b border-white/[0.07] text-[#777781]"><tr><th className="px-4 py-3">{t['finance.grid.date']}</th><th>{t['finance.grid.production']}</th><th>{t['finance.grid.household']}</th><th>{t['finance.grid.exported']}</th><th>{t['finance.grid.mining_pv']}</th><th>{t['finance.grid.mining_grid']}</th><th>{t['finance.grid.grid_cost']}</th><th>{t['finance.grid.opportunity_cost']}</th><th>{t['finance.grid.mined']}</th><th>{t['finance.grid.historic_value']}</th><th>{t['finance.grid.net']}</th></tr></thead><tbody>{days.map((day) => {const net = day.btcHistoricValue.amount + day.householdSavings.amount + day.feedInRevenue.amount - day.miningCost.amount; return <tr className="border-t border-white/[0.05] hover:bg-white/[0.02]" key={day.date}><td className="px-4 py-3 font-semibold">{formatDate(day.date)}</td><td>{formatNumber(day.totalPvProduction)} kWh</td><td>{formatNumber(day.householdPvUsage)} kWh</td><td>{formatNumber(day.exportedKwh)} kWh</td><td>{formatNumber(day.miningPvUsage)} kWh</td><td>{formatNumber(day.miningGridUsage)} kWh</td><td className="text-red-200">{formatMoney(day.miningGridCost)}</td><td className="text-orange-200">{formatMoney(day.miningOpportunityCost)}</td><td className="font-mono text-yellow-200">{formatNumber(day.minedBtc, 8)} BTC</td><td>{formatMoney(day.btcHistoricValue)}</td><td className={net >= 0 ? 'font-semibold text-emerald-300' : 'font-semibold text-red-300'}>{formatMoney({amount: net, currency: day.miningCost.currency})}</td></tr>;})}</tbody></table>{days.length === 0 ? <p className="py-12 text-center text-sm text-[#777781]">{t['finance.history.empty']}</p> : null}</section>;
}

function LedgerTab({currency, data, exporting, saving, saleDate, saleBtc, saleFiat, setSaleDate, setSaleBtc, setSaleFiat, saveSale, deleteSale, exportReport, formatMoney, formatNumber, formatDate, t}: {currency: string; data: FinancePageDto; exporting: ExportType | null; saving: boolean; saleDate: string; saleBtc: string; saleFiat: string; setSaleDate: (value: string) => void; setSaleBtc: (value: string) => void; setSaleFiat: (value: string) => void; saveSale: (event: FormEvent) => Promise<void>; deleteSale: (sale: BitcoinSaleDto) => Promise<void>; exportReport: (type: ExportType) => Promise<void>; formatMoney: (money: MoneyDto) => string; formatNumber: (value: number, digits?: number) => string; formatDate: (date: string | null) => string; t: Record<string, string>}) {
    return <section className="space-y-4"><div className="flex justify-end"><ExportButton active={exporting === 'sales-pdf'} icon={<FileText/>} label={t['finance.export.sales_pdf']} onClick={() => void exportReport('sales-pdf')}/></div><form className="grid gap-3 rounded-2xl border border-white/[0.07] bg-[#111115] p-4 sm:grid-cols-4 sm:items-end" onSubmit={(event) => void saveSale(event)}><DateField label={t['finance.sale.date']} value={saleDate} onChange={setSaleDate}/><InputField label={t['finance.sale.btc']} min="0.00000001" step="0.00000001" value={saleBtc} onChange={setSaleBtc}/><InputField label={t['finance.sale.fiat'].replace('{currency}', currency)} min="0" step="0.01" value={saleFiat} onChange={setSaleFiat}/><button className="h-9 rounded-lg bg-yellow-400 px-4 text-xs font-bold text-black disabled:opacity-40" disabled={saving} type="submit">{t['finance.sale.add']}</button></form><div className="overflow-x-auto rounded-2xl border border-white/[0.07] bg-[#111115]"><table className="w-full text-left text-xs"><thead className="border-b border-white/[0.07] text-[#777781]"><tr><th className="px-4 py-3">{t['finance.grid.date']}</th><th>{t['finance.sale.sold']}</th><th>{t['finance.sale.revenue']}</th><th className="w-16"/></tr></thead><tbody>{data.sales.map((sale, index) => <tr className="border-t border-white/[0.05]" key={`${sale.saleDate}-${sale.amountBtc}-${index}`}><td className="px-4 py-3">{formatDate(sale.saleDate)}</td><td className="font-mono text-yellow-200">{formatNumber(sale.amountBtc, 8)} BTC</td><td>{formatMoney(sale.fiatValue)}</td><td><button aria-label={t['finance.sale.delete']} className="rounded-lg p-2 text-red-300 hover:bg-red-400/10" disabled={saving} onClick={() => void deleteSale(sale)} type="button"><Trash2 size={15}/></button></td></tr>)}</tbody></table>{data.sales.length === 0 ? <p className="py-12 text-center text-sm text-[#777781]">{t['finance.ledger.empty']}</p> : null}</div></section>;
}

function FinanceMetric({label, value, detail, icon, tone}: {label: string; value: string; detail?: string; icon: React.ReactNode; tone: string}) {const colors: Record<string, string> = {green: 'bg-emerald-400/10 text-emerald-300', red: 'bg-red-400/10 text-red-300', yellow: 'bg-yellow-400/10 text-yellow-300', cyan: 'bg-cyan-400/10 text-cyan-300', violet: 'bg-violet-400/10 text-violet-300'}; return <article className="flex items-center gap-3 rounded-xl border border-white/[0.06] bg-white/[0.02] p-3"><span className={`grid h-9 w-9 shrink-0 place-items-center rounded-lg [&_svg]:h-4 [&_svg]:w-4 ${colors[tone]}`}>{icon}</span><span className="min-w-0"><span className="block truncate text-[10px] text-[#73737d]">{label}</span><strong className="block truncate text-base">{value}</strong>{detail ? <span className="block truncate text-[10px] text-[#62626c]">{detail}</span> : null}</span></article>;}
function InsightCard({title, value, subtitle, icon}: {title: string; value: string; subtitle: string; icon: React.ReactNode}) {return <article className="rounded-2xl border border-white/[0.07] bg-[#131318] p-4"><div className="flex items-center gap-2 text-[#777781] [&_svg]:h-4 [&_svg]:w-4"><span>{icon}</span><span className="text-[10px]">{title}</span></div><strong className="mt-2 block text-lg">{value}</strong><p className="mt-1 text-[10px] leading-4 text-[#676771]">{subtitle}</p></article>;}
function InsightRow({label, value, color}: {label: string; value: string; color?: string}) {return <div className="flex items-center gap-2 text-xs">{color ? <span className={`h-2 w-2 rounded-full ${color}`}/> : null}<span className="flex-1 text-[#777781]">{label}</span><strong>{value}</strong></div>;}
function ExportButton({active, icon, label, onClick}: {active: boolean; icon: React.ReactNode; label: string; onClick: () => void}) {return <button className="inline-flex h-9 items-center gap-2 rounded-lg border border-white/10 bg-white/[0.03] px-3 text-xs font-semibold text-[#b5b5bd] transition hover:bg-white/[0.07] disabled:opacity-40" disabled={active} onClick={onClick} type="button">{active ? <RefreshCw className="animate-spin" size={14}/> : <span className="[&_svg]:h-4 [&_svg]:w-4">{icon}</span>}{label}</button>;}
function DateField({label, value, onChange, min, max}: {label: string; value: string; onChange: (value: string) => void; min?: string; max?: string}) {return <label className="text-[10px] text-[#777781]"><span className="mb-1 block">{label}</span><input className="h-9 rounded-lg border border-white/10 bg-[#17171b] px-3 text-xs text-white outline-none focus:border-yellow-400/50" max={max} min={min} onChange={(event) => onChange(event.target.value)} required type="date" value={value}/></label>;}
function InputField({label, value, onChange, min, step}: {label: string; value: string; onChange: (value: string) => void; min: string; step: string}) {return <label className="text-[10px] text-[#777781]"><span className="mb-1 block">{label}</span><input className="h-9 w-full rounded-lg border border-white/10 bg-[#17171b] px-3 text-xs text-white outline-none focus:border-yellow-400/50" min={min} onChange={(event) => onChange(event.target.value)} required step={step} type="number" value={value}/></label>;}
