import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Theme } from '@navikt/ds-react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DailyCacheResponse, QueryResponse, Service } from '../api/types';
import { LandingPage } from './LandingPage';
import { DayPage } from './DayPage';

/**
 * Landingsside og dagsvisning mot et mocket API.
 *
 * Begge sidene teller og sorterer 47 tjenester på tvers av flere kall. Den
 * logikken er dekket av `lib/summary.test.ts`; det disse testene vokter er
 * koblingen — at svarene faktisk finner fram til riktig kolonne, riktig dag og
 * riktig rekkefølge på skjermen.
 */

const TODAY = '2026-05-12';

function service(id: string, name: string, team = 'Team test'): Service {
  return {
    id,
    name,
    type: 'TJENESTE',
    team,
    monitorlink: null,
    logglink: null,
    description: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: null,
  };
}

const SERVICES = [
  service('s1', 'Dagpengesøknad', 'Team dagpenger'),
  service('s2', 'Meldekort', 'Team dagpenger'),
  service('s3', 'Skriv til oss', 'Team kontaktsenter'),
  service('s4', 'Sykepengesøknad', 'Team sykefravær'),
];

function daily(overrides: Partial<DailyCacheResponse> & { serviceId: string }): DailyCacheResponse {
  return {
    serviceName: 'Tjeneste',
    isOpen: true,
    openingHours: '08:00-15:30',
    displayHeader: null,
    displayText: null,
    onlyShowForNavEmployees: false,
    unstableOpeningHours: false,
    redDay: false,
    ruleName: 'Ordinær åpningstid',
    rule: '??.??.???? ? 1-5 08:00-15:30',
    ...overrides,
  };
}

const DAILY: Record<string, DailyCacheResponse> = {
  s1: daily({ serviceId: 's1', serviceName: 'Dagpengesøknad' }),
  s2: daily({ serviceId: 's2', serviceName: 'Meldekort', unstableOpeningHours: true }),
  s3: daily({
    serviceId: 's3',
    serviceName: 'Skriv til oss',
    isOpen: false,
    openingHours: '00:00-00:00',
    displayText: 'Tjenesten åpner igjen i morgen 09:00.',
  }),
  /*
   * Ingen regel traff. Backend markerer ikke dette i dagcachen — den sender sin
   * standardregel, som er døgnåpen. Fixturen speiler det svaret nøyaktig, slik at
   * testen vokter gjenkjenningen og ikke en form API-et aldri sender.
   */
  s4: daily({
    serviceId: 's4',
    serviceName: 'Sykepengesøknad',
    openingHours: '00:00-23:59',
    ruleName: 'No Rules stated',
    rule: '??.??.???? ? ? 00:00-23:59',
    displayHeader: 'Default regel',
    displayText: 'Åpent - ingen gjeldende dato regler',
  }),
};

function rangeDay(resourceId: string, date: string, overrides: Partial<QueryResponse> = {}): QueryResponse {
  return {
    resourceId,
    date,
    isOpen: true,
    openingTime: '08:00',
    closingTime: '15:30',
    displayHeader: null,
    displayText: null,
    onlyShowForNavEmployees: false,
    unstableOpeningHours: false,
    redDay: false,
    ...overrides,
  };
}

function mockFetch() {
  return vi.fn(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost');
    const body = (data: unknown) =>
      new Response(JSON.stringify(data), { status: 200, headers: { 'Content-Type': 'application/json' } });

    if (url.pathname === '/me') return body({ loggedIn: true, name: 'Kari', isAdmin: false });
    if (url.pathname === '/api/openinghours/service') return body(SERVICES);
    if (url.pathname === '/api/openinghours/daily') return body(DAILY);

    const range = /^\/api\/openinghours\/query\/service\/(.+)\/range$/.exec(url.pathname);
    if (range) {
      const id = range[1];
      const from = url.searchParams.get('from') ?? TODAY;
      const to = url.searchParams.get('to') ?? from;
      const days: QueryResponse[] = [];
      for (let d = from; d <= to; d = nextDay(d)) {
        // 17. mai er rød for alle: gir stripen en dag å merke som rød dag.
        days.push(
          d.endsWith('-17')
            ? rangeDay(id, d, { redDay: true, isOpen: false, openingTime: '00:00', closingTime: '00:00' })
            : rangeDay(id, d),
        );
      }
      return body(days);
    }

    return new Response('null', { status: 404 });
  });
}

function nextDay(iso: string): string {
  return new Date(new Date(`${iso}T00:00:00Z`).getTime() + 86_400_000).toISOString().slice(0, 10);
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Theme theme="light">
          <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/dag/:dato" element={<DayPage />} />
          </Routes>
        </Theme>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date('2026-05-12T13:20:00+02:00'));
  vi.stubGlobal('fetch', mockFetch());
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('LandingPage', () => {
  it('oppsummerer dagen med ett tall per tilstand', async () => {
    renderAt('/');

    expect(await screen.findByText('1 av 4 tjenester er åpne nå')).toBeInTheDocument();
    expect(screen.getAllByText('1 åpen').length).toBeGreaterThan(0);
    expect(screen.getAllByText('1 ustabil').length).toBeGreaterThan(0);
    expect(screen.getAllByText('1 stengt').length).toBeGreaterThan(0);
    expect(screen.getAllByText('1 uten åpningstider').length).toBeGreaterThan(0);
  });

  it('løfter fram tjenestene som krever oppmerksomhet, og utelater de åpne', async () => {
    renderAt('/');

    expect(await screen.findByText('mangler åpningstider')).toBeInTheDocument();
    expect(screen.getByText('er ustabil 08:00–15:30')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sykepengesøknad' })).toBeInTheDocument();
    // Dagpengesøknad er åpen som normalt og skal ikke stå i avvikslisten.
    expect(screen.queryByRole('link', { name: 'Dagpengesøknad' })).not.toBeInTheDocument();
  });

  it('teller «åpne nå», ikke «åpne en gang i dag»', async () => {
    // Samme data, men etter stengetid: da er ingen av dem åpne.
    vi.setSystemTime(new Date('2026-05-12T20:00:00+02:00'));
    renderAt('/');

    expect(await screen.findByText('0 av 4 tjenester er åpne nå')).toBeInTheDocument();
    expect(screen.getByText('stengte 15:30')).toBeInTheDocument();
  });

  it('viser de seks neste dagene, med rød dag der den finnes', async () => {
    renderAt('/');

    expect(await screen.findByRole('link', { name: /onsdag 13\. mai/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /søndag 17\. mai/i })).toBeInTheDocument();
    // 18. mai er dag seks — stripen slutter der, og 19. skal ikke være med.
    expect(screen.getByRole('link', { name: /mandag 18\. mai/i })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /19\. mai/i })).not.toBeInTheDocument();
    expect(await screen.findByText('Grunnlovsdagen')).toBeInTheDocument();
  });

  it('blar seks dager fram om gangen', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderAt('/');

    await screen.findByRole('link', { name: /onsdag 13\. mai/i });
    await user.click(screen.getByRole('button', { name: 'Neste seks dager' }));

    expect(await screen.findByRole('link', { name: /tirsdag 19\. mai/i })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /13\. mai/i })).not.toBeInTheDocument();
  });
});

describe('DayPage', () => {
  it('sorterer avvik øverst og åpne nederst', async () => {
    renderAt(`/dag/${TODAY}`);

    const rows = await screen.findAllByRole('row');
    // Første rad er tabellhodet.
    const names = rows.slice(1).map((row) => within(row).getByRole('link').textContent);
    expect(names).toEqual(['Sykepengesøknad', 'Meldekort', 'Skriv til oss', 'Dagpengesøknad']);
  });

  it('viser meldingen til brukeren og regelen for innloggede', async () => {
    renderAt(`/dag/${TODAY}`);

    expect(await screen.findByText('Tjenesten åpner igjen i morgen 09:00.')).toBeInTheDocument();
    expect(screen.getAllByText(/Regel: Ordinær åpningstid/).length).toBeGreaterThan(0);
    expect(screen.getByText('Regel: ingen regel traff')).toBeInTheDocument();
  });

  it('gir statuskolonnen en tekst skjermlesere faktisk får lest opp', async () => {
    renderAt(`/dag/${TODAY}`);

    // Selve merket er dekorativt; teksten må stå som ekte innhold i cellen.
    expect(
      (await screen.findAllByText(/tirsdag 12\. mai 2026, åpen 08:00 til 15:30/i)).length,
    ).toBeGreaterThan(0);
    expect(screen.getByText(/åpningstider ikke satt opp/i)).toBeInTheDocument();
  });

  it('filtrerer til én tilstand når man velger en av dem', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    renderAt(`/dag/${TODAY}`);

    await screen.findByRole('link', { name: 'Meldekort' });
    await user.click(screen.getByRole('button', { name: '1 ustabil' }));

    await waitFor(() => {
      expect(screen.queryByRole('link', { name: 'Dagpengesøknad' })).not.toBeInTheDocument();
    });
    expect(screen.getByRole('link', { name: 'Meldekort' })).toBeInTheDocument();
  });

  it('henter en annen dato fra periodeendepunktet framfor dagens cache', async () => {
    renderAt('/dag/2026-05-17');

    expect(await screen.findByText('17. mai 2026 · søndag')).toBeInTheDocument();
    await waitFor(() => expect(screen.getAllByText('Rød dag').length).toBeGreaterThan(0));
    expect(screen.queryByText('I dag')).not.toBeInTheDocument();
  });
});
