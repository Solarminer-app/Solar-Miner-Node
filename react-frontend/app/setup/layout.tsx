import {PropsWithChildren} from 'react';

import {SitePreferencesProvider} from '../site/[siteId]/site-preferences-context';

export default function SetupLayout({children}: PropsWithChildren) {
    return <SitePreferencesProvider>{children}</SitePreferencesProvider>;
}
