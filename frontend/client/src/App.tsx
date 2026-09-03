import { Suspense, lazy } from 'react';
import { Route, Routes } from 'react-router-dom';
import { Page } from '@navikt/ds-react';
import { AppHeader } from './components/common/AppHeader';
import { DelayedLoader } from './components/common/DelayedLoader';
import { CalendarPage } from './pages/CalendarPage';
import { ComparePage } from './pages/ComparePage';
import { DayPage } from './pages/DayPage';
import { LandingPage } from './pages/LandingPage';
import { ServiceOverviewPage } from './pages/ServiceOverviewPage';
import { NotFoundPage } from './pages/NotFoundPage';

/**
 * Admin lastes først når noen går dit.
 *
 * De aller fleste besøkende er uinnloggede og skal bare se kalenderen. Å sende
 * dem hele administrasjonsgrensesnittet ville nesten doblet det de må laste ned
 * for å se en åpningstid.
 */
const AdminLayout = lazy(() =>
  import('./components/admin/AdminLayout').then((m) => ({ default: m.AdminLayout })),
);
const AdminOverviewPage = lazy(() =>
  import('./pages/admin/AdminOverviewPage').then((m) => ({ default: m.AdminOverviewPage })),
);
const RulesPage = lazy(() =>
  import('./pages/admin/RulesPage').then((m) => ({ default: m.RulesPage })),
);
const RuleFormPage = lazy(() =>
  import('./pages/admin/RuleFormPage').then((m) => ({ default: m.RuleFormPage })),
);
const GroupsPage = lazy(() =>
  import('./pages/admin/GroupsPage').then((m) => ({ default: m.GroupsPage })),
);
const GroupDetailPage = lazy(() =>
  import('./pages/admin/GroupDetailPage').then((m) => ({ default: m.GroupDetailPage })),
);
const ServicesPage = lazy(() =>
  import('./pages/admin/ServicesPage').then((m) => ({ default: m.ServicesPage })),
);

export function App() {
  return (
    <Page footerPosition="belowFold">
      <AppHeader />
      <Page.Block as="main" width="xl" gutters className="oh-main">
        <Suspense fallback={<DelayedLoader />}>
          <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/dag/:dato" element={<DayPage />} />
            <Route path="/tjenester" element={<ServiceOverviewPage />} />
            <Route path="/t/:serviceId" element={<CalendarPage />} />
            <Route path="/sammenlign" element={<ComparePage />} />
            <Route path="/admin" element={<AdminLayout />}>
              <Route index element={<AdminOverviewPage />} />
              <Route path="regler" element={<RulesPage />} />
              <Route path="regler/ny" element={<RuleFormPage />} />
              <Route path="regler/:ruleId" element={<RuleFormPage />} />
              <Route path="grupper" element={<GroupsPage />} />
              <Route path="grupper/:groupId" element={<GroupDetailPage />} />
              <Route path="tjenester" element={<ServicesPage />} />
            </Route>
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </Suspense>
      </Page.Block>
    </Page>
  );
}
