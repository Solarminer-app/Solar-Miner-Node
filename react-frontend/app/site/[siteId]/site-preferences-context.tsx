"use client";

import {
    useCallback,
    createContext,
    PropsWithChildren,
    useContext,
    useEffect,
    useMemo,
    useSyncExternalStore,
} from "react";

export type LocaleCode = "de" | "en";
export type CurrencyCode = "EUR" | "USD" | "CHF";

const STORAGE_KEYS = {
    locale: "solarminer.locale",
    currency: "solarminer.currency",
    timeZone: "solarminer.timeZone",
} as const;
const PREFERENCES_CHANGE_EVENT = "solarminer:preferences-change";

type SitePreferencesContextValue = {
    locale: LocaleCode;
    setLocale: (locale: LocaleCode) => void;
    currency: CurrencyCode;
    setCurrency: (currency: CurrencyCode) => void;
    timeZone: string;
    setTimeZone: (timeZone: string) => void;
    isHydrated: boolean;
};

const SitePreferencesContext = createContext<
    SitePreferencesContextValue | undefined
>(undefined);

function isLocaleCode(value: string | null): value is LocaleCode {
    return value === "de" || value === "en";
}

function isCurrencyCode(value: string | null): value is CurrencyCode {
    return value === "EUR" || value === "USD" || value === "CHF";
}

function subscribe(onStoreChange: () => void): () => void {
    window.addEventListener(PREFERENCES_CHANGE_EVENT, onStoreChange);
    window.addEventListener("storage", onStoreChange);

    return () => {
        window.removeEventListener(PREFERENCES_CHANGE_EVENT, onStoreChange);
        window.removeEventListener("storage", onStoreChange);
    };
}

function subscribeToHydration(): () => void {
    return () => undefined;
}

function getLocaleSnapshot(): LocaleCode {
    const storedLocale = localStorage.getItem(STORAGE_KEYS.locale);
    if (isLocaleCode(storedLocale)) return storedLocale;

    return navigator.language.toLowerCase().startsWith("de") ? "de" : "en";
}

function getCurrencySnapshot(): CurrencyCode {
    const storedCurrency = localStorage.getItem(STORAGE_KEYS.currency);
    return isCurrencyCode(storedCurrency) ? storedCurrency : "EUR";
}

function getTimeZoneSnapshot(): string {
    return (
        localStorage.getItem(STORAGE_KEYS.timeZone) ||
        Intl.DateTimeFormat().resolvedOptions().timeZone ||
        "Europe/Berlin"
    );
}

function notifyPreferenceChange(): void {
    window.dispatchEvent(new Event(PREFERENCES_CHANGE_EVENT));
}

export function SitePreferencesProvider({ children }: PropsWithChildren) {
    const locale = useSyncExternalStore<LocaleCode>(
        subscribe,
        getLocaleSnapshot,
        () => "de",
    );
    const currency = useSyncExternalStore<CurrencyCode>(
        subscribe,
        getCurrencySnapshot,
        () => "EUR",
    );
    const timeZone = useSyncExternalStore(
        subscribe,
        getTimeZoneSnapshot,
        () => "Europe/Berlin",
    );
    const isHydrated = useSyncExternalStore(
        subscribeToHydration,
        () => true,
        () => false,
    );

    const setLocale = useCallback((nextLocale: LocaleCode) => {
        localStorage.setItem(STORAGE_KEYS.locale, nextLocale);
        notifyPreferenceChange();
    }, []);

    const setCurrency = useCallback((nextCurrency: CurrencyCode) => {
        localStorage.setItem(STORAGE_KEYS.currency, nextCurrency);
        notifyPreferenceChange();
    }, []);

    const setTimeZone = useCallback((nextTimeZone: string) => {
        localStorage.setItem(STORAGE_KEYS.timeZone, nextTimeZone);
        notifyPreferenceChange();
    }, []);

    useEffect(() => {
        document.documentElement.lang = locale;
    }, [locale]);

    const value = useMemo<SitePreferencesContextValue>(
        () => ({
            locale,
            setLocale,
            currency,
            setCurrency,
            timeZone,
            setTimeZone,
            isHydrated,
        }),
        [
            currency,
            isHydrated,
            locale,
            setCurrency,
            setLocale,
            setTimeZone,
            timeZone,
        ],
    );

    return (
        <SitePreferencesContext.Provider value={value}>
            {children}
        </SitePreferencesContext.Provider>
    );
}

export function useSitePreferences(): SitePreferencesContextValue {
    const context = useContext(SitePreferencesContext);

    if (!context) {
        throw new Error(
            "useSitePreferences must be used inside SitePreferencesProvider.",
        );
    }

    return context;
}
