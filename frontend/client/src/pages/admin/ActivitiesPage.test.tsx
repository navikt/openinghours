import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import type { Activity, Service } from '../../api/types';
import { ActivitiesPage } from './ActivitiesPage';

const ACTIVITY: Activity = {
  id: 'activity-1',
  serviceIds: ['service-1'],
  header: 'Planlagt vedlikehold',
  message: 'Tjenesten kan være utilgjengelig.',
  start: '2025-04-10T09:30:00Z',
  end: '2025-04-10T10:00:00Z',
  onlyShowForNavEmployees: true,
  ongoing: true,
};

const SERVICE: Service = {
  id: 'service-1',
  name: 'Dagpenger',
  type: 'TJENESTE',
  team: 'Team A',
  monitorlink: null,
  logglink: null,
  description: null,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: null,
};

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

interface ApiOptions {
  session?: unknown;
  activities?: () => Response;
  services?: () => Response;
  post?: () => Response | Promise<Response>;
  put?: () => Response;
  delete?: () => Response | Promise<Response>;
}

function mockApi(options: ApiOptions = {}) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';

    if (url === '/me') {
      return jsonResponse(options.session ?? { loggedIn: true, isAdmin: true });
    }
    if (url.startsWith('/api/openinghours/service')) {
      return options.services?.() ?? jsonResponse([SERVICE]);
    }
    if (url.includes('/api/openinghours/activity/range')) {
      return options.activities?.() ?? jsonResponse([ACTIVITY]);
    }
    if (url === '/api/openinghours/activity' && method === 'POST') {
      return options.post?.() ?? jsonResponse({ ...ACTIVITY, id: 'activity-created' });
    }
    if (url === '/api/openinghours/activity/activity-1' && method === 'PUT') {
      return options.put?.() ?? jsonResponse(ACTIVITY);
    }
    if (url === '/api/openinghours/activity/activity-1' && method === 'DELETE') {
      return options.delete?.() ?? jsonResponse(true);
    }
    return jsonResponse({}, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderPage(path = '/admin/aktiviteter') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <ActivitiesPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeAll(() => {
  if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
    HTMLDialogElement.prototype.showModal = function showModal() {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function close() {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  }
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ActivitiesPage', () => {
  it('shows activities, service names, period, and ongoing status', async () => {
    mockApi();
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Planlagt vedlikehold' })).toBeInTheDocument();
    expect(screen.getByText('Dagpenger')).toBeInTheDocument();
    expect(screen.getByText('Pågår')).toBeInTheDocument();
    expect(screen.getByText(/Fra 10\. apr\./)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Rediger aktiviteten «Planlagt vedlikehold»' }))
      .toBeInTheDocument();
  });

  it('shows the empty state when the selected range has no activities', async () => {
    mockApi({ activities: () => jsonResponse([]) });
    renderPage();

    expect(await screen.findByText('Ingen aktiviteter i perioden')).toBeInTheDocument();
  });

  it('shows an activity-list error with a retry action', async () => {
    let attempts = 0;
    mockApi({
      activities: () => (++attempts === 1 ? jsonResponse({ message: 'unavailable' }, 503) : jsonResponse([])),
    });
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText(/Vi klarte ikke å hente aktivitetene/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Prøv igjen' }));
    expect(await screen.findByText('Ingen aktiviteter i perioden')).toBeInTheDocument();
  });

  it('keeps the activity list usable when service loading fails', async () => {
    mockApi({ services: () => jsonResponse({ message: 'unavailable' }, 503) });
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Planlagt vedlikehold' })).toBeInTheDocument();
    expect(screen.getByText(/Vi klarte ikke å hente tjenestene/)).toBeInTheDocument();
    expect(screen.getByText('Tjenestelisten er utilgjengelig')).toBeInTheDocument();
  });

  it('uses dates from the URL and updates the range query when a date changes', async () => {
    const fetchMock = mockApi({ activities: () => jsonResponse([]) });
    renderPage('/admin/aktiviteter?fra=2025-02-01&til=2025-03-01');

    const from = (await screen.findByLabelText('Fra')) as HTMLInputElement;
    expect(from.value).toBe('2025-02-01');
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/openinghours/activity/range?from=2025-02-01&to=2025-03-01',
        expect.any(Object),
      );
    });

    fireEvent.change(from, { target: { value: '2025-02-10' } });
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/openinghours/activity/range?from=2025-02-10&to=2025-03-01',
        expect.any(Object),
      );
    });
  });

  it('creates a general activity with null times and retains values after an API error', async () => {
    let postAttempts = 0;
    const fetchMock = mockApi({
      activities: () => jsonResponse([]),
      post: () =>
        (++postAttempts === 1
          ? jsonResponse({ message: 'invalid' }, 400)
          : jsonResponse({ ...ACTIVITY, id: 'activity-created', serviceIds: [] })),
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Ny aktivitet' }));
    await user.type(screen.getByLabelText('Tittel'), 'Planlagt vedlikehold');
    await user.type(screen.getByLabelText('Melding'), 'Tjenesten kan være utilgjengelig.');
    await user.click(screen.getByRole('button', { name: 'Lagre aktiviteten' }));

    expect(await screen.findByText('Forespørselen var ugyldig. Kontroller datoene og prøv igjen.'))
      .toBeInTheDocument();
    expect(screen.getByLabelText('Tittel')).toHaveValue('Planlagt vedlikehold');
    expect(screen.getByLabelText('Melding')).toHaveValue('Tjenesten kan være utilgjengelig.');

    await user.click(screen.getByRole('button', { name: 'Lagre aktiviteten' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({
      serviceIds: [],
      header: 'Planlagt vedlikehold',
      message: 'Tjenesten kan være utilgjengelig.',
      start: null,
      end: null,
      onlyShowForNavEmployees: false,
    });
  });

  it('edits Oslo-local date times while preserving services and employee visibility', async () => {
    const fetchMock = mockApi();
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole('button', { name: 'Rediger aktiviteten «Planlagt vedlikehold»' }),
    );
    const form = within(screen.getByRole('dialog'));
    expect(form.getByLabelText('Fra')).toHaveValue('2025-04-10T11:30');
    expect(form.getByLabelText('Til')).toHaveValue('2025-04-10T12:00');
    expect(form.getByLabelText('Dagpenger')).toBeChecked();
    expect(form.getByLabelText('Vis kun for Nav-ansatte')).toBeChecked();

    await user.click(screen.getByRole('button', { name: 'Lagre aktiviteten' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT');
    expect(put?.[0]).toBe('/api/openinghours/activity/activity-1');
    expect(JSON.parse(String(put?.[1]?.body))).toEqual({
      serviceIds: ['service-1'],
      header: 'Planlagt vedlikehold',
      message: 'Tjenesten kan være utilgjengelig.',
      start: '2025-04-10T11:30',
      end: '2025-04-10T12:00',
      onlyShowForNavEmployees: true,
    });
  });

  it('keeps the delete dialog open for false and failed deletes', async () => {
    let attempts = 0;
    mockApi({
      delete: () => {
        attempts += 1;
        if (attempts === 1) return jsonResponse(false);
        if (attempts === 2) return jsonResponse({ message: 'unavailable' }, 503);
        return jsonResponse(true);
      },
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole('button', { name: 'Slett aktiviteten «Planlagt vedlikehold»' }),
    );
    expect(screen.getByText('Aktiviteten fjernes fra forsiden. Åpningstidene endres ikke.'))
      .toBeInTheDocument();
    const confirm = screen.getByRole('button', { name: 'Slett aktiviteten' });

    await user.click(confirm);
    expect(await screen.findByText('Aktiviteten ble ikke slettet.'))
      .toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await user.click(confirm);
    expect(
      await screen.findByText(/Tjenesten er utilgjengelig akkurat nå/),
    ).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    await user.click(confirm);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByText('Aktiviteten er slettet.')).toBeInTheDocument();
  });

  it('does not fetch activity or service data for non-admin users', async () => {
    const fetchMock = mockApi({ session: { loggedIn: true, isAdmin: false } });
    renderPage();

    expect(await screen.findByText('Du har ikke tilgang til aktiviteter.')).toBeInTheDocument();
    expect(fetchMock.mock.calls.map(([url]) => String(url))).toEqual(['/me']);
    expect(screen.queryByRole('button', { name: 'Ny aktivitet' })).not.toBeInTheDocument();
  });

  it.each(['save', 'delete'] as const)('keeps the dialog open during %s and after an API error', async (operation) => {
    let finishRequest: ((response: Response) => void) | undefined;
    const response = new Promise<Response>((resolve) => { finishRequest = resolve; });
    mockApi({ post: () => response, delete: () => response });
    const user = userEvent.setup();
    renderPage();

    if (operation === 'save') {
      await user.click(await screen.findByRole('button', { name: 'Ny aktivitet' }));
      await user.type(screen.getByLabelText('Tittel'), 'Aktivitet');
      await user.type(screen.getByLabelText('Melding'), 'Arbeid pågår.');
      await user.click(screen.getByRole('button', { name: 'Lagre aktiviteten' }));
    } else {
      await user.click(await screen.findByRole('button', { name: 'Slett aktiviteten «Planlagt vedlikehold»' }));
      await user.click(screen.getByRole('button', { name: 'Slett aktiviteten' }));
    }

    const dialog = screen.getByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: /lukk/i }));
    expect(dialog).toHaveAttribute('open');
    expect(fireEvent(dialog, new Event('cancel', { cancelable: true, bubbles: true }))).toBe(false);
    expect(dialog).toHaveAttribute('open');

    finishRequest?.(jsonResponse({ message: 'unavailable' }, 503));
    expect(await within(dialog).findByText(/Tjenesten er utilgjengelig akkurat nå/)).toBeInTheDocument();
    expect(dialog).toHaveAttribute('open');
    if (operation === 'save') {
      expect(screen.getByLabelText('Tittel')).toHaveValue('Aktivitet');
    }
  });
});
