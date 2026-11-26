import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { GroupsPage } from './GroupsPage';

const GROUPS = [
  { id: 'g1', name: 'Basic Mainframe group', ruleGroupIds: ['r1', 'r2', 'r3', 'g2'] },
  { id: 'g2', name: 'Fixed helligdager', ruleGroupIds: ['r1'] },
  { id: 'g3', name: 'Tom gruppe', ruleGroupIds: [] },
];
const RULES = ['r1', 'r2', 'r3'].map((id, i) => ({
  id,
  name: `Regel ${i + 1}`,
  rule: '??.??.???? ? 1-5 08:00-15:30',
  header: null,
  text: null,
  onlyShowForNavEmployees: false,
}));

function mockApi() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      const json = (body: unknown) =>
        new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' } });
      if (url.endsWith('/g1/associations')) return json({ services: [{ id: 's1' }] });
      if (url.includes('/associations')) return json({ services: [] });
      if (url.includes('/group')) return json(GROUPS);
      if (url.includes('/rule')) return json(RULES);
      if (url.includes('/service')) return json([{ id: 's1', name: 'Dagpenger' }]);
      return json({});
    }),
  );
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <GroupsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('GroupsPage', () => {
  it('viser hver gruppe som et kort med innhold, status og bruk', async () => {
    mockApi();
    renderPage();

    const link = await screen.findByRole('link', { name: 'Basic Mainframe group' });
    expect(link).toHaveAttribute('href', '/admin/grupper/g1');
    const card = within(link.closest('article')!);
    expect(await card.findByText('I bruk')).toBeInTheDocument();
    expect(card.getByText('3 regler')).toBeInTheDocument();
    expect(card.getByText('1 undergruppe')).toBeInTheDocument();
    expect(card.getByText('+1 til')).toBeInTheDocument();
    expect(card.getByText('Brukt av Dagpenger')).toBeInTheDocument();

    const empty = within(screen.getByRole('link', { name: 'Tom gruppe' }).closest('article')!);
    expect(empty.getByText('Tom')).toBeInTheDocument();
  });

  it('filtrerer på status', async () => {
    mockApi();
    renderPage();
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Ikke i bruk (1)' }));
    expect(screen.getByRole('link', { name: 'Fixed helligdager' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Basic Mainframe group' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Tom gruppe' })).not.toBeInTheDocument();
  });
});
