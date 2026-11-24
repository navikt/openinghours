import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Theme } from '@navikt/ds-react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Service } from '../../api/types';
import { useActivitiesRange } from '../../hooks/activities';
import { useSession } from '../../hooks/queries';
import { ActivityFeed } from './ActivityFeed';

vi.mock('../../hooks/queries', () => ({ useSession: vi.fn() }));
vi.mock('../../hooks/activities', () => ({ useActivitiesRange: vi.fn() }));

const TODAY = '2025-05-13';
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

const sessionResult = (overrides: Record<string, unknown> = {}) =>
  ({
    data: { loggedIn: true, isAdmin: false },
    isPending: false,
    isError: false,
    ...overrides,
  }) as ReturnType<typeof useSession>;

const activitiesResult = (overrides: Record<string, unknown> = {}) =>
  ({
    data: [],
    isPending: false,
    isError: false,
    refetch: vi.fn(),
    ...overrides,
  }) as unknown as ReturnType<typeof useActivitiesRange>;

function renderFeed() {
  return render(
    <MemoryRouter>
      <Theme theme="light" hasBackground={false}>
        <ActivityFeed today={TODAY} services={SERVICES} />
      </Theme>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(useSession).mockReturnValue(sessionResult());
  vi.mocked(useActivitiesRange).mockReturnValue(activitiesResult());
});

describe('ActivityFeed', () => {
  it('viser en tilgjengelig ventetilstand mens aktivitetene hentes', () => {
    vi.mocked(useActivitiesRange).mockReturnValue(activitiesResult({ isPending: true }));

    renderFeed();

    expect(screen.getByRole('status')).toHaveTextContent('Henter aktiviteter');
    expect(screen.getByRole('heading', { name: 'Aktiviteter' })).toBeInTheDocument();
  });

  it('viser en kort beskjed når perioden er tom', () => {
    renderFeed();

    expect(screen.getByText('Ingen aktiviteter i denne perioden.')).toBeInTheDocument();
  });

  it('viser en lokal feil og kan prøve henting på nytt', async () => {
    const refetch = vi.fn();
    vi.mocked(useActivitiesRange).mockReturnValue(
      activitiesResult({ isError: true, refetch }),
    );
    const user = userEvent.setup();

    renderFeed();

    expect(screen.getByRole('alert')).toHaveTextContent('Vi klarte ikke å hente aktivitetene.');
    await user.click(screen.getByRole('button', { name: 'Prøv igjen' }));
    expect(refetch).toHaveBeenCalledOnce();
  });

  it('viser aktiviteter i backend-rekkefølge med tider, tjenester og ren meldingstekst', () => {
    vi.mocked(useActivitiesRange).mockReturnValue(
      activitiesResult({
        data: [
          {
            id: 'scheduled',
            serviceIds: ['s1', 's2', 'ukjent'],
            header: 'Planlagt vedlikehold',
            message: 'Vedlikehold <img src=x onerror=alert(1)>.',
            start: '2025-05-14T08:00:00Z',
            end: '2025-05-14T10:00:00Z',
            onlyShowForNavEmployees: false,
            ongoing: false,
          },
          {
            id: 'ongoing',
            serviceIds: [],
            header: 'Pågående feil',
            message: 'Vi undersøker feilen.',
            start: null,
            end: '2025-05-14T11:00:00Z',
            onlyShowForNavEmployees: true,
            ongoing: true,
          },
          {
            id: 'general',
            serviceIds: [],
            header: 'Generell melding',
            message: 'Tjenestene er tilgjengelige.',
            start: null,
            end: null,
            onlyShowForNavEmployees: false,
            ongoing: false,
          },
        ],
      }),
    );
    const { container } = renderFeed();
    const articles = screen.getAllByRole('article');

    expect(articles.map((article) => within(article).getByRole('heading').textContent)).toEqual([
      'Planlagt vedlikehold',
      'Pågående feil',
      'Generell melding',
    ]);

    const scheduled = articles[0];
    expect(
      within(scheduled).getByText('Vedlikehold <img src=x onerror=alert(1)>.'),
    ).toBeInTheDocument();
    expect(scheduled.querySelector('img')).not.toBeInTheDocument();
    expect(within(scheduled).getAllByRole('link')).toHaveLength(3);
    expect(within(scheduled).getByRole('link', { name: 'Dagpenger' })).toHaveAttribute(
      'href',
      '/t/s1',
    );
    expect(within(scheduled).getByRole('link', { name: 'Sykepenger' })).toHaveAttribute(
      'href',
      '/t/s2',
    );
    expect(within(scheduled).getByRole('link', { name: 'ukjent' })).toHaveAttribute(
      'href',
      '/t/ukjent',
    );
    expect(within(scheduled).getAllByText((_, element) => element?.tagName === 'TIME')).toHaveLength(2);
    expect(within(scheduled).queryByText('Pågår')).not.toBeInTheDocument();
    expect(within(scheduled).queryByText('Kun for Nav-ansatte')).not.toBeInTheDocument();

    const ongoing = articles[1];
    expect(within(ongoing).getByText('Pågår')).toBeInTheDocument();
    expect(within(ongoing).getByText('Kun for Nav-ansatte')).toBeInTheDocument();
    expect(within(ongoing).getByText('Til')).toBeInTheDocument();
    expect(within(ongoing).getByText((_, element) => element?.tagName === 'TIME')).toHaveAttribute(
      'dateTime',
      '2025-05-14T11:00:00Z',
    );
    expect(ongoing.querySelector('.oh-activity-feed__services')).not.toBeInTheDocument();

    const general = articles[2];
    expect(general.querySelector('time')).not.toBeInTheDocument();
    expect(general.querySelector('.oh-activity-feed__services')).not.toBeInTheDocument();
    expect(container.querySelector('img')).not.toBeInTheDocument();
  });

  it('viser ikke feed eller henter aktiviteter for utloggede', () => {
    vi.mocked(useSession).mockReturnValue(
      sessionResult({ data: { loggedIn: false, isAdmin: false } }),
    );

    renderFeed();

    expect(screen.queryByRole('heading', { name: 'Aktiviteter' })).not.toBeInTheDocument();
    expect(useActivitiesRange).toHaveBeenCalledWith(TODAY, '2025-06-12', false);
  });

  it('viser administrasjonslenke bare for administratorer', () => {
    vi.mocked(useSession).mockReturnValue(
      sessionResult({ data: { loggedIn: true, isAdmin: true } }),
    );

    const { rerender } = render(
      <MemoryRouter>
        <Theme theme="light" hasBackground={false}>
          <ActivityFeed today={TODAY} services={SERVICES} />
        </Theme>
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Administrer aktiviteter' })).toHaveAttribute(
      'href',
      '/admin/aktiviteter',
    );

    vi.mocked(useSession).mockReturnValue(sessionResult());
    rerender(
      <MemoryRouter>
        <Theme theme="light" hasBackground={false}>
          <ActivityFeed today={TODAY} services={SERVICES} />
        </Theme>
      </MemoryRouter>,
    );
    expect(screen.queryByRole('link', { name: 'Administrer aktiviteter' })).not.toBeInTheDocument();
  });
});
