import react from '@vitejs/plugin-react';
import {fileURLToPath, URL} from 'node:url';
import {defineConfig} from 'vite';

const resolveFromRoot = (path: string) => fileURLToPath(new URL(path, import.meta.url));

export default defineConfig({
    plugins: [react()],
    resolve: {
        alias: [
            {find: 'next/link', replacement: resolveFromRoot('./spa/next-link.tsx')},
            {find: 'next/navigation', replacement: resolveFromRoot('./spa/next-navigation.ts')},
            {find: 'next/image', replacement: resolveFromRoot('./spa/next-image.tsx')},
            {find: 'next/dynamic', replacement: resolveFromRoot('./spa/next-dynamic.tsx')},
        ],
    },
    build: {
        outDir: resolveFromRoot('../build/generated/react-frontend'),
        emptyOutDir: true,
        sourcemap: true,
        rollupOptions: {
            output: {
                manualChunks(id) {
                    if (!id.includes('node_modules')) return undefined;
                    if (id.includes('recharts') || id.includes('d3-')) return 'charts';
                    if (id.includes('leaflet')) return 'maps';
                    if (id.includes('lucide-react')) return 'icons';
                    if (id.includes('react')) return 'react';
                    return 'vendor';
                },
            },
        },
    },
    server: {
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
    preview: {
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
    },
});
