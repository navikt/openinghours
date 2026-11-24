import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Service } from '../api/types';
import { ServiceOverviewPage } from './ServiceOverviewPage';

const service: Service = {
  id: 'ac8e0f53-b71a-4a65-8184-1ec651459bff',
  name: 'Testtjeneste',
  type: 'TJENESTE',
  team: 'Testteam',
  monitorlink: null,
  logglink: null,
  description: null,
  createdAt: '2026-10-13T09:45:06Z',
  updatedAt: null,
};

function setup(url: string) {
  vi.stubGlobal('fetch', vi.fn(async (path: string) => {
    const body = path === '/api/openinghours/service?type=TJENESTE'
      ? [service]
      : path === '/api/openinghours/daily'
        ? {}
        : path === '/me'
          ? { loggedIn: false, isAdmin: false }
          : undefined;
    if (body === undefined) throw new Error(`Unexpected request: ${path}`);
    return new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' } });
  }));
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[url]}>
        <ServiceOverviewPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ServiceOverviewPage', () => {
  it('shows filtered services without offering a component type filter', async () => {
    setup('/tjenester');

    expect(await screen.findByRole('link', { name: 'Testtjeneste' })).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Type' })).not.toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Team' })).toBeInTheDocument();
  });

  it('does not hide services when opening an old component-filtered link', async () => {
    setup('/tjenester?type=KOMPONENT');

    expect(await screen.findByRole('link', { name: 'Testtjeneste' })).toBeInTheDocument();
    expect(screen.queryByText('Ingen tjenester matcher søket')).not.toBeInTheDocument();
  });

  it('still applies search and team filters', async () => {
    setup('/tjenester?sok=annen&team=Testteam');

    expect(await screen.findByText('Ingen tjenester matcher søket')).toBeInTheDocument();
    expect(screen.getByText('Prøv et kortere søkeord, eller nullstill filtrene for søk og team.'))
      .toBeInTheDocument();
  });
});
