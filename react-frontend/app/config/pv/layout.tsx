import {PropsWithChildren} from 'react';

import {SitePreferencesProvider} from '../../site/[siteId]/site-preferences-context';

export default function PVConfigLayout({children}: PropsWithChildren) {
    return <SitePreferencesProvider>{children}</SitePreferencesProvider>;
}
