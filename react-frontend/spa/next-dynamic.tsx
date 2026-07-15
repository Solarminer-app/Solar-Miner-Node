import {lazy, Suspense, type ComponentType} from 'react';

type Loader<Props> = () => Promise<{default: ComponentType<Props>}>;

export default function dynamic<Props extends object>(loader: Loader<Props>, _options?: {ssr?: boolean}) {
    void _options;
    const LazyComponent = lazy(loader);
    return function DynamicComponent(props: Props) {
        return <Suspense fallback={null}><LazyComponent {...props}/></Suspense>;
    };
}
