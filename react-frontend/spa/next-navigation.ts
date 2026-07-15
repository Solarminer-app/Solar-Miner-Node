import {useMemo} from 'react';
import {useLocation, useNavigate, useParams as useRouterParams} from 'react-router-dom';

export function useParams<T extends Record<string, string | string[] | undefined> = Record<string, string | undefined>>() {
    return useRouterParams() as T;
}

export function usePathname() {
    return useLocation().pathname;
}

export function useRouter() {
    const navigate = useNavigate();

    return useMemo(() => ({
        push: (href: string) => navigate(href),
        replace: (href: string) => navigate(href, {replace: true}),
        back: () => navigate(-1),
        forward: () => navigate(1),
        refresh: () => window.location.reload(),
        prefetch: async () => undefined,
    }), [navigate]);
}
