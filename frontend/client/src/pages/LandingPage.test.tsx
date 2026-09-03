import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Theme } from '@navikt/ds-react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { QueryResponse, Service } from '../api/types';
import { LandingPage } from './LandingPage';
import { DayPage } from './DayPage';

/*
 * Fixturene speiler backendens faktiske svar, ikke en forenklet idé om dem.
 * Grunnregelen er en ukesregel (`??.??.???? ?`), avviket er festet til en dato
 * — det er nettopp det skillet visningen bygger på.
 */
const BASE_RULE = '??.??.???? ? 1-5 08:00-15:30';
const TODAY = '2025-05-13T09:00:00+02:00';

const SERVICES: Service[] = [
  {
    id: 's1',
    name: 'Dagpenger',
    type: 'TJENESTE',
    team: 'Team A',
    monitorlink: null,
    logglink: null,
    description: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: null,
  },
  {
    id: 's2',
    name: 'Sykepenger',
    type: 'TJENESTE',
    team: 'Team B',
    monitorlink: null,
    logglink: null,
    description: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: null,
  },
];

function day(
  resourceId: string,
  date: string,
  hours: string,
  extra: Partial<QueryResponse> = {},
): QueryResponse {
  const [openingTime, closingTime] = hours.split('-');
  return {
    resourceId,
    date,
    isOpen: true,
    openingTime,
    closingTime,
    displayHeader: null,
    displayText: null,
    onlyShowForNavEmployees: false,
    unstableOpeningHours: false,
    redDay: false,
    matchedRule: { name: 'Normal åpningstid', rule: BASE_RULE },
    ...extra,
  };
}

/** Hele månedsrutenettet for mai 2025: 28. april til 8. juni. */
function monthOfDays(resourceId: string, overrides: Record<string, QueryResponse> = {}) {
  const days: QueryResponse[] = [];
  for (let d = new Date('2025-04-28T00:00:00Z'); d <= new Date('2025-06-08T00:00:00Z'); d.setUTCDate(d.getUTCDate() + 1)) {
    const date = d.toISOString().slice(0, 10);
    const weekday = d.getUTCDay();
    const weekend = weekday === 0 || weekday === 6;
    days.push(overrides[date] ?? day(resourceId, date, weekend ? '00:00-00:00' : '08:00-15:30'));
  }
  return days;
}

function mockApi(byService: Record<string, QueryResponse[]>) {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      const json = (body: unknown) =>
        new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' } });

      if (url === '/me') return json({ loggedIn: false, isAdmin: false });
      if (url.includes('/range')) {
        const id = /service\/([^/?]+)\/range/.exec(url)?.[1] ?? '';
        return json(byService[id] ?? []);
      }
      if (url.startsWith('/api/openinghours/service')) return json(SERVICES);
      return json({});
    }),
  );
}

function renderPage(ui: React.ReactNode, path = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Theme theme="light" hasBackground={false}>
          <Routes>
            <Route path="/" element={ui} />
            <Route path="/dag/:dato" element={ui} />
          </Routes>
        </Theme>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date(TODAY));
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('LandingPage', () => {
  it('holder kalenderen stille når alt følger normalen', async () => {
    mockApi({ s1: monthOfDays('s1'), s2: monthOfDays('s2') });
    renderPage(<LandingPage />);

    expect(await screen.findByText('Ingen avvik i dag')).toBeInTheDocument();
    expect(screen.getByText('Ingen avvik denne måneden')).toBeInTheDocument();
    // Helger er stengt for begge tjenestene, men det er normalen for lørdag og
    // søndag — og en kalender som roper hver helg er verdiløs.
    expect(screen.queryByText(/Stengt hele dagen/)).not.toBeInTheDocument();
  });

  it('viser tidlig stenging som avvik, med åpningstiden som gjelder', async () => {
    mockApi({
      s1: monthOfDays('s1', {
        '2025-05-13': day('s1', '2025-05-13', '08:00-12:00', {
          matchedRule: { name: 'Kortdag', rule: '13.05.2025 ? ? 08:00-12:00' },
        }),
      }),
      s2: monthOfDays('s2'),
    });
    renderPage(<LandingPage />);

    expect(await screen.findByText('Ett avvik i dag')).toBeInTheDocument();
    // Ikke «stenger fire timer tidligere»: klokkeslettene som faktisk gjelder.
    expect(screen.getAllByText('Åpent 08:00–12:00').length).toBeGreaterThan(0);
    expect(screen.queryByText(/normalt/)).not.toBeInTheDocument();
  });

  it('lister kommende avvik med dato', async () => {
    mockApi({
      s1: monthOfDays('s1', {
        '2025-05-29': day('s1', '2025-05-29', '00:00-00:00', {
          redDay: true,
          matchedRule: { name: 'Kristi himmelfart', rule: '29.05.2025 ? ? 00:00-00:00' },
        }),
      }),
      s2: monthOfDays('s2'),
    });
    renderPage(<LandingPage />);

    // Overskriften «Neste avvik» står der også mens siden laster, så den kan
    // ikke brukes som ventepunkt — det må innholdet under den.
    expect(await screen.findByRole('link', { name: '29. mai' })).toBeInTheDocument();
    expect(screen.getAllByText(/Kristi himmelfartsdag/).length).toBeGreaterThan(0);
  });

  it('tier om tjenester uten regler — de regnes som døgnåpne', async () => {
    // Uten regler svarer backend med døgnåpent hver dag. Da er døgnåpent
    // normalen, ingenting avviker, og kalenderen skal stå tom framfor å farge
    // 42 dager røde for noe brukeren ikke kan gjøre noe med.
    const utenRegler = monthOfDays('s2').map((d) => ({
      ...d,
      openingTime: '00:00',
      closingTime: '23:59',
      warningMessage: 'Ingen regel treffer',
    }));
    mockApi({ s1: monthOfDays('s1'), s2: utenRegler });
    renderPage(<LandingPage />);

    expect(await screen.findByText('Ingen avvik denne måneden')).toBeInTheDocument();
    expect(screen.queryByText(/ingen normal/)).not.toBeInTheDocument();
  });

  it('lar måneden styres fra URL-en', async () => {
    mockApi({ s1: monthOfDays('s1'), s2: monthOfDays('s2') });
    renderPage(<LandingPage />, '/?maned=2025-05');

    expect(await screen.findByRole('heading', { name: 'Mai 2025' })).toBeInTheDocument();
  });

  it('sier fra når en tjeneste ikke svarer, framfor å la avvik forsvinne stille', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        const json = (body: unknown) =>
          new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' } });
        if (url === '/me') return json({ loggedIn: false });
        if (url.includes('service/s1/range')) return json(monthOfDays('s1'));
        if (url.includes('/range')) return new Response('nei', { status: 500 });
        if (url.startsWith('/api/openinghours/service')) return json(SERVICES);
        return json({});
      }),
    );
    renderPage(<LandingPage />);

    expect(await screen.findByText(/svarte ikke og er utelatt fra kalenderen/)).toBeInTheDocument();
  });
});

describe('DayPage', () => {
  it('viser avvikene i sin helhet og slår sammen resten', async () => {
    mockApi({
      s1: monthOfDays('s1', {
        '2025-05-13': day('s1', '2025-05-13', '08:00-12:00', {
          displayHeader: 'Redusert åpningstid',
          displayText: 'Vi stenger tidlig på grunn av vedlikehold.',
          matchedRule: { name: 'Kortdag', rule: '13.05.2025 ? ? 08:00-12:00' },
        }),
      }),
      s2: monthOfDays('s2'),
    });
    renderPage(<DayPage />, '/dag/2025-05-13');

    expect(await screen.findByRole('heading', { name: 'Avvik denne dagen' })).toBeInTheDocument();
    expect(screen.getByText('Vi stenger tidlig på grunn av vedlikehold.')).toBeInTheDocument();
    expect(screen.getByText('Redusert åpningstid')).toBeInTheDocument();
    expect(screen.getByText('Kortere åpent')).toBeInTheDocument();
    expect(screen.getByText(/Som vanlig \(1 tjeneste\)/)).toBeInTheDocument();
  });

  it('sier rett ut når ingenting avviker', async () => {
    mockApi({ s1: monthOfDays('s1'), s2: monthOfDays('s2') });
    renderPage(<DayPage />, '/dag/2025-05-14');

    expect(
      await screen.findByText('Ingen avvik denne dagen. Alle tjenester er åpne som vanlig.'),
    ).toBeInTheDocument();
    expect(screen.getByText(/Som vanlig \(2 tjenester\)/)).toBeInTheDocument();
  });

  it('viser normalplanen for tjenestene som er som vanlig', async () => {
    mockApi({ s1: monthOfDays('s1'), s2: monthOfDays('s2') });
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderPage(<DayPage />, '/dag/2025-05-14');

    await user.click(await screen.findByText(/Som vanlig/));
    expect(screen.getAllByText('08:00–15:30').length).toBeGreaterThan(0);
  });

  it('faller tilbake til i dag når datoen i URL-en er tull', async () => {
    mockApi({ s1: monthOfDays('s1'), s2: monthOfDays('s2') });
    renderPage(<DayPage />, '/dag/hva-som-helst');

    expect(await screen.findByText('I dag')).toBeInTheDocument();
  });
});
