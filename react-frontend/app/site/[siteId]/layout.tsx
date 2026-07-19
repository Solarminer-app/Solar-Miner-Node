"use client";

import Link from "next/link";
import {useParams, usePathname} from "next/navigation";
import {PropsWithChildren, ReactNode, useCallback, useEffect, useMemo, useState,} from "react";
import {createPortal} from "react-dom";
import AppLogo from "../../components/app-logo";
import de from "../../locales/de.json";
import en from "../../locales/en.json";
import type {CurrencyCode, LocaleCode} from "./site-preferences-context";
import {useSitePreferences} from "./site-preferences-context";

const SITE_ROUTE_PREFIX = "/site";

type SupportedLanguage = {
    flag: string; code: LocaleCode;
};

type TranslationKey = string;
const translations = {de, en} as Record<LocaleCode, Record<string, string>>;

const languages: SupportedLanguage[] = [{flag: "🇩🇪", code: "de"}, {flag: "🇺🇸", code: "en"}];

const currencies: CurrencyCode[] = ["EUR", "USD", "CHF"];

function isUuid(value: string): boolean {
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value,);
}

function getTimeZones(): string[] {
    try {
        const intl = Intl as typeof Intl & {
            supportedValuesOf?: (key: "timeZone") => string[];
        };

        return intl.supportedValuesOf?.("timeZone") ?? ["Europe/Berlin", "UTC"];
    } catch {
        return ["Europe/Berlin", "UTC"];
    }
}

type IconName = "home" | "wrench" | "cluster" | "money" | "menu" | "close";

function Icon({name, size = 20}: { name: IconName; size?: number }) {
    const paths: Record<IconName, ReactNode> = {
        home: (<>
            <path d="M3 10.5 12 3l9 7.5"/>
            <path d="M5 9.5V21h14V9.5"/>
            <path d="M9 21v-7h6v7"/>
        </>), wrench: (<>
            <path d="M14.7 6.3a4 4 0 0 0-5 5L3 18l3 3 6.7-6.7a4 4 0 0 0 5-5l-2.4 2.4-3-3 2.4-2.4Z"/>
        </>), cluster: (<>
            <rect x="4" y="4" width="6" height="6" rx="1"/>
            <rect x="14" y="4" width="6" height="6" rx="1"/>
            <rect x="9" y="14" width="6" height="6" rx="1"/>
            <path d="M7 10v2h10v-2M12 12v2"/>
        </>), money: (<>
            <rect x="3" y="5" width="18" height="14" rx="2"/>
            <path d="M7 9h.01M17 15h.01"/>
            <circle cx="12" cy="12" r="3"/>
        </>), menu: (<>
            <path d="M4 7h16M4 12h16M4 17h16"/>
        </>), close: (<>
            <path d="m6 6 12 12M18 6 6 18"/>
        </>),
    };

    return (<svg
        aria-hidden="true"
        fill="none"
        height={size}
        viewBox="0 0 24 24"
        width={size}
    >
        <g
            stroke="currentColor"
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth="1.8"
        >
            {paths[name]}
        </g>
    </svg>);
}

type SettingsProps = {
    locale: LocaleCode;
    currency: CurrencyCode;
    timeZone: string;
    timeZones: string[];
    onLocaleChange: (locale: LocaleCode) => void;
    onCurrencyChange: (currency: CurrencyCode) => void;
    onTimeZoneChange: (timeZone: string) => void;
    fullWidth?: boolean;
    t: (key: TranslationKey) => string;
};

function Settings({
                      locale,
                      currency,
                      timeZone,
                      timeZones,
                      onLocaleChange,
                      onCurrencyChange,
                      onTimeZoneChange,
                      fullWidth = false,
                      t,
                  }: SettingsProps) {
    return (<div className={`settings ${fullWidth ? "settings--stacked" : ""}`}>
        <label className="field">
            <span className="sr-only">{t("settings.language")}</span>
            <select
                aria-label={t("settings.language")}
                onChange={(event) => onLocaleChange(event.target.value as LocaleCode)}
                value={locale}
            >
                {languages.map((language) => (<option key={language.code} value={language.code}>
                    {language.flag} {t(`language.${language.code}`)}
                </option>))}
            </select>
        </label>

        <label className="field field--currency">
            <span className="sr-only">{t("settings.currency")}</span>
            <select
                aria-label={t("settings.currency")}
                onChange={(event) => onCurrencyChange(event.target.value as CurrencyCode)}
                value={currency}
            >
                {currencies.map((entry) => (<option key={entry} value={entry}>
                    {entry}
                </option>))}
            </select>
        </label>

        <label className="field field--timezone">
            <span className="sr-only">{t("settings.timeZone")}</span>
            <select
                aria-label={t("settings.timeZone")}
                onChange={(event) => onTimeZoneChange(event.target.value)}
                value={timeZone}
            >
                {timeZones.map((entry) => (<option key={entry} value={entry}>
                    {entry}
                </option>))}
            </select>
        </label>
    </div>);
}

export default function SiteLayout({children}: PropsWithChildren) {
    return <SiteLayoutContent>{children}</SiteLayoutContent>;
}

function SiteLayoutContent({children}: PropsWithChildren) {
    const params = useParams<{ siteId?: string | string[] }>();
    const pathname = usePathname();

    const rawSiteId = params.siteId;
    const siteId = Array.isArray(rawSiteId) ? rawSiteId[0] : rawSiteId ?? "";
    const basePath = `${SITE_ROUTE_PREFIX}/${siteId}`;

    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
    const [toast, setToast] = useState<string | null>(null);
    const {
        locale, setLocale, currency, setCurrency, timeZone, setTimeZone,
    } = useSitePreferences();

    const timeZones = useMemo(() => getTimeZones(), []);
    const t = useCallback((key: TranslationKey) => translations[locale][key], [locale],);

    const navigation = useMemo(() => [{
        label: t("nav.dashboard"), href: `${basePath}/dashboard`, icon: "home" as const, exact: true,
    }, {
        label: t("nav.info"), href: `${basePath}/details`, icon: "wrench" as const,
    }, {
        label: t("nav.mining"), href: `${basePath}/mining`, icon: "cluster" as const,
    }, {
        label: t("nav.finance"), href: `${basePath}/finance`, icon: "money" as const,
    },], [basePath, t],);

    useEffect(() => {
        const timeout = window.setTimeout(() => setMobileMenuOpen(false), 0);
        return () => window.clearTimeout(timeout);
    }, [pathname]);

    useEffect(() => {
        if (!mobileMenuOpen) {
            return;
        }

        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";

        const closeOnEscape = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                setMobileMenuOpen(false);
            }
        };

        window.addEventListener("keydown", closeOnEscape);

        return () => {
            document.body.style.overflow = previousOverflow;
            window.removeEventListener("keydown", closeOnEscape);
        };
    }, [mobileMenuOpen]);

    const changeCurrency = (nextCurrency: CurrencyCode) => {
        setCurrency(nextCurrency);
        window.dispatchEvent(new CustomEvent("solarminer:currency-change", {
            detail: {currency: nextCurrency},
        }),);

        setToast(`${t("notification.currencyChanged")} (${nextCurrency})`);
        window.setTimeout(() => setToast(null), 2500);
    };

    const changeTimeZone = (nextTimeZone: string) => {
        setTimeZone(nextTimeZone);
        window.dispatchEvent(new CustomEvent("solarminer:timezone-change", {
            detail: {timeZone: nextTimeZone},
        }),);
    };

    const isActive = (href: string, exact = false) => exact ? pathname === href : pathname === href || pathname.startsWith(`${href}/`);

    if (!isUuid(siteId)) {
        return (<main className="invalid-site">
            <h1>{t("app.title")}</h1>
            <p>{t("error.invalidSite")}</p>
            <style>{`
                .invalid-site {
                    min-height: 100vh;
                    display: grid;
                    place-content: center;
                    gap: 0.5rem;
                    padding: 2rem;
                    color: #f7f7f8;
                    background: #0b0b0d;
                    text-align: center;
                }

                h1,
                p {
                    margin: 0;
                }

                p {
                    color: #a7a7ad;
                }
            `}</style>
        </main>);
    }

    return (<div className="app-shell">
        <header className="navbar">
            <Link className="brand" href={basePath + "/dashboard"}>
                <AppLogo priority/>
                <span>{t("app.title")}</span>
            </Link>

            <nav aria-label={t("nav.main_label")} className="desktop-nav">
                {navigation.map((item) => (<Link
                    aria-current={isActive(item.href, item.exact) ? "page" : undefined}
                    className={`nav-link ${isActive(item.href, item.exact) ? "nav-link--active" : ""}`}
                    href={item.href}
                    key={item.href}
                >
                    <Icon name={item.icon}/>
                    <span>{item.label}</span>
                </Link>))}
            </nav>

            <div className="desktop-settings">
                <Settings
                    currency={currency}
                    locale={locale}
                    onCurrencyChange={changeCurrency}
                    onLocaleChange={setLocale}
                    onTimeZoneChange={changeTimeZone}
                    t={t}
                    timeZone={timeZone}
                    timeZones={timeZones}
                />
            </div>

            <button
                aria-expanded={mobileMenuOpen}
                aria-label={t("nav.menu_open")}
                className="icon-button mobile-menu-button"
                onClick={() => setMobileMenuOpen(true)}
                type="button"
            >
                <Icon name="menu" size={26}/>
            </button>
        </header>

        <main className="content">{children}</main>

        {mobileMenuOpen && createPortal(<div
            aria-label={t("nav.mobile_label")}
            aria-modal="true"
            className="mobile-dialog"
            role="dialog"
        >
            <div className="mobile-dialog__header">
                <span className="mobile-dialog__title">{t("app.title")}</span>
                <button
                    aria-label={t("nav.menu_close")}
                    className="icon-button mobile-dialog__close"
                    onClick={() => setMobileMenuOpen(false)}
                    type="button"
                >
                    <Icon name="close" size={28}/>
                </button>
            </div>

            <nav className="mobile-nav">
                {navigation.map((item) => (<Link
                    aria-current={isActive(item.href, item.exact) ? "page" : undefined}
                    className={`mobile-nav-link ${isActive(item.href, item.exact) ? "mobile-nav-link--active" : ""}`}
                    href={item.href}
                    key={item.href}
                >
                    <Icon name={item.icon} size={24}/>
                    <span>{item.label}</span>
                </Link>))}
            </nav>

            <div className="separator"/>

            <Settings
                currency={currency}
                fullWidth
                locale={locale}
                onCurrencyChange={changeCurrency}
                onLocaleChange={setLocale}
                onTimeZoneChange={changeTimeZone}
                t={t}
                timeZone={timeZone}
                timeZones={timeZones}
            />
        </div>, document.body)}

        {toast && (<div aria-live="polite" className="toast" role="status">
            {toast}
        </div>)}

        <style>{`
            :root {
                color-scheme: dark;
                --app-bg: #0b0b0d;
                --surface: #0f0f11;
                --surface-raised: #17171b;
                --border: #25252b;
                --text: #f7f7f8;
                --muted: #9c9ca5;
                --accent: #ffca28;
                --header-height: 72px;
            }

            * {
                box-sizing: border-box;
            }

            html,
            body {
                margin: 0;
                min-height: 100%;
                background: var(--app-bg);
                color: var(--text);
                font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont,
                "Segoe UI", sans-serif;
            }

            a {
                color: inherit;
            }

            button,
            select {
                font: inherit;
            }

            .app-shell {
                width: 100%;
                min-width: 0;
                min-height: 100vh;
                overflow-x: clip;
                background: var(--app-bg);
            }

            .navbar {
                position: sticky;
                z-index: 30;
                top: 0;
                display: grid;
                width: 100%;
                min-width: 0;
                grid-template-columns: minmax(150px, auto) 1fr auto;
                align-items: center;
                min-height: var(--header-height);
                padding: 0 24px;
                border-bottom: 1px solid var(--border);
                background: color-mix(in srgb, var(--surface) 94%, transparent);
                backdrop-filter: blur(16px);
            }

            .brand {
                display: inline-flex;
                align-items: center;
                gap: 10px;
                color: var(--text);
                font-size: 1.15rem;
                font-weight: 750;
                letter-spacing: -0.02em;
                text-decoration: none;
            }

            .desktop-nav {
                display: flex;
                align-self: stretch;
                align-items: center;
                justify-content: center;
                gap: 4px;
            }

            .nav-link {
                position: relative;
                display: inline-flex;
                align-items: center;
                gap: 8px;
                height: 100%;
                padding: 0 15px;
                color: var(--muted);
                font-size: 0.9rem;
                font-weight: 650;
                text-decoration: none;
                transition: color 160ms ease,
                background-color 160ms ease;
            }

            .nav-link:hover {
                color: var(--text);
                background: rgba(255, 255, 255, 0.035);
            }

            .nav-link::after {
                position: absolute;
                right: 14px;
                bottom: 0;
                left: 14px;
                height: 2px;
                border-radius: 999px 999px 0 0;
                background: var(--accent);
                content: "";
                opacity: 0;
                transform: scaleX(0.65);
                transition: opacity 160ms ease,
                transform 160ms ease;
            }

            .nav-link--active {
                color: var(--text);
            }

            .nav-link--active::after {
                opacity: 1;
                transform: scaleX(1);
            }

            .desktop-settings {
                justify-self: end;
            }

            .settings {
                display: flex;
                align-items: center;
                gap: 8px;
            }

            .field {
                display: block;
            }

            .field select {
                width: 140px;
                height: 36px;
                padding: 0 34px 0 11px;
                border: 1px solid var(--border);
                border-radius: 8px;
                outline: none;
                color: var(--text);
                background: var(--surface-raised);
                font-size: 0.82rem;
                cursor: pointer;
                transition: border-color 160ms ease,
                box-shadow 160ms ease;
            }

            .field select:focus-visible {
                border-color: var(--accent);
                box-shadow: 0 0 0 3px rgba(255, 202, 40, 0.14);
            }

            .field--currency select {
                width: 88px;
            }

            .field--timezone select {
                width: 176px;
            }

            .content {
                width: 100%;
                max-width: 100%;
                min-width: 0;
            }

            .icon-button {
                display: inline-grid;
                width: 44px;
                height: 44px;
                place-items: center;
                padding: 0;
                border: 0;
                border-radius: 10px;
                color: var(--text);
                background: transparent;
                cursor: pointer;
            }

            .icon-button:hover {
                background: rgba(255, 255, 255, 0.07);
            }

            .mobile-menu-button {
                display: none;
                justify-self: end;
            }

            .mobile-dialog {
                position: fixed;
                z-index: 10000;
                inset: 0;
                isolation: isolate;
                overflow-y: auto;
                padding: max(18px, env(safe-area-inset-top)) max(18px, env(safe-area-inset-right)) max(18px, env(safe-area-inset-bottom)) max(18px, env(safe-area-inset-left));
                background: var(--surface);
                animation: mobile-dialog-in 180ms ease-out;
            }

            .mobile-dialog__header {
                position: sticky;
                z-index: 2;
                top: 0;
                display: flex;
                align-items: center;
                justify-content: space-between;
                min-height: 52px;
                background: var(--surface);
            }

            .mobile-dialog__close {
                position: relative;
                z-index: 3;
                flex: 0 0 44px;
            }

            .mobile-dialog__title {
                font-size: 1.1rem;
                font-weight: 750;
            }

            .mobile-nav {
                display: grid;
                gap: 6px;
                margin-top: 30px;
            }

            .mobile-nav-link {
                display: flex;
                align-items: center;
                gap: 20px;
                min-height: 58px;
                padding: 0 16px;
                border: 1px solid transparent;
                border-radius: 12px;
                color: var(--muted);
                font-size: 1.15rem;
                font-weight: 700;
                text-decoration: none;
            }

            .mobile-nav-link--active {
                border-color: var(--border);
                color: var(--text);
                background: var(--surface-raised);
            }

            .mobile-nav-link--active svg {
                color: var(--accent);
            }

            .separator {
                height: 1px;
                margin: 26px 0;
                background: var(--border);
            }

            .settings--stacked {
                display: grid;
                gap: 12px;
            }

            .settings--stacked .field select {
                width: 100%;
                height: 48px;
                font-size: 0.95rem;
            }

            .toast {
                position: fixed;
                z-index: 120;
                right: 24px;
                bottom: 24px;
                max-width: min(380px, calc(100vw - 48px));
                padding: 13px 16px;
                border: 1px solid var(--border);
                border-radius: 10px;
                color: var(--text);
                background: var(--surface-raised);
                box-shadow: 0 16px 50px rgba(0, 0, 0, 0.35);
            }

            .sr-only {
                position: absolute;
                width: 1px;
                height: 1px;
                overflow: hidden;
                clip: rect(0, 0, 0, 0);
                white-space: nowrap;
                clip-path: inset(50%);
            }

            @keyframes mobile-dialog-in {
                from {
                    opacity: 0;
                    transform: translateY(-8px);
                }

                to {
                    opacity: 1;
                    transform: translateY(0);
                }
            }

            @media (max-width: 1180px) {
                .navbar {
                    grid-template-columns: auto 1fr auto;
                }

                .desktop-settings {
                    display: none;
                }
            }

            @media (max-width: 760px) {
                .navbar {
                    min-height: 64px;
                    padding: 0 16px;
                }

                .desktop-nav {
                    display: none;
                }

                .mobile-menu-button {
                    display: inline-grid;
                }
            }

            @media (prefers-reduced-motion: reduce) {
                *,
                *::before,
                *::after {
                    scroll-behavior: auto !important;
                    transition-duration: 0.01ms !important;
                    animation-duration: 0.01ms !important;
                    animation-iteration-count: 1 !important;
                }
            }
        `}</style>
    </div>);
}
