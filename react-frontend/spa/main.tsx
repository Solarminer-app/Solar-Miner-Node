import {createRoot} from 'react-dom/client';
import {BrowserRouter, Navigate, Outlet, Route, Routes} from 'react-router-dom';

import '../app/globals.css';
import HomePage from '../app/page';
import RestConfigPage from '../app/config/pv/rest/page';
import ModbusConfigPage from '../app/config/pv/modbus/tcp/page';
import LightningWalletPage from '../app/lightning-wallet/page';
import SetupPage from '../app/setup/page';
import SiteLayout from '../app/site/[siteId]/layout';
import DashboardPage from '../app/site/[siteId]/dashboard/page';
import DetailsPage from '../app/site/[siteId]/details/page';
import FinancePage from '../app/site/[siteId]/finance/page';
import MiningPage from '../app/site/[siteId]/mining/page';
import ClusterConfigPage from '../app/site/[siteId]/mining/clusters/[clusterName]/config/page';
import MinerDetailsPage from '../app/site/[siteId]/mining/miners/[minerId]/page';
import {SitePreferencesProvider} from '../app/site/[siteId]/site-preferences-context';

function SiteShell() {
    return <SiteLayout><Outlet/></SiteLayout>;
}

function Application() {
    return (
        <SitePreferencesProvider>
            <BrowserRouter>
                <Routes>
                    <Route element={<HomePage/>} path="/"/>
                    <Route element={<SetupPage/>} path="/setup"/>
                    <Route element={<LightningWalletPage/>} path="/lightning-wallet"/>
                    <Route element={<RestConfigPage/>} path="/config/pv/rest"/>
                    <Route element={<ModbusConfigPage/>} path="/config/pv/modbus/tcp"/>
                    <Route element={<SiteShell/>} path="/site/:siteId">
                        <Route element={<Navigate replace to="dashboard"/>} index/>
                        <Route element={<DashboardPage/>} path="dashboard"/>
                        <Route element={<DetailsPage/>} path="details"/>
                        <Route element={<FinancePage/>} path="finance"/>
                        <Route element={<MiningPage/>} path="mining"/>
                        <Route element={<ClusterConfigPage/>} path="mining/clusters/:clusterName/config"/>
                        <Route element={<MinerDetailsPage/>} path="mining/miners/:minerId"/>
                    </Route>
                    <Route element={<Navigate replace to="/"/>} path="*"/>
                </Routes>
            </BrowserRouter>
        </SitePreferencesProvider>
    );
}

createRoot(document.getElementById('root')!).render(<Application/>);
