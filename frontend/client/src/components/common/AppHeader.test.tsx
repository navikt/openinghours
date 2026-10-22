import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Theme } from '@navikt/ds-react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppHeader } from './AppHeader';

/**
 * Menyen er eneste vei til `/dag/<dato>` uten å gå via forsiden, og
 * `ActionMenu` rendres i en portal inne i darkside-`<Theme>`. Begge delene
 * feiler stille: en meny som ikke åpner ser ut som en knapp som ikke gjør noe.
 */
function renderHeader(session: unknown, path = '/') {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(JSON.stringify(session), { headers: { 'content-type': 'application/json' } })),
  );
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Theme theme="light" hasBackground={false}>
          <AppHeader />
        </Theme>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

async function openMenu() {
  const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  await user.click(screen.getByRole('button', { name: /meny/i }));
  return user;
}

describe('AppHeader', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date('2025-05-13T09:00:00+02:00'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('åpner menyen og lenker til dagsvisningen for i dag', async () => {
    renderHeader({ loggedIn: false, isAdmin: false });
    await openMenu();

    expect(screen.getByRole('menuitem', { name: /dag for dag/i })).toHaveAttribute(
      'href',
      '/dag/2025-05-13',
    );
    expect(screen.getByRole('menuitem', { name: /alle tjenester/i })).toHaveAttribute(
      'href',
      '/tjenester',
    );
    expect(screen.getByRole('menuitem', { name: /sammenlign/i })).toHaveAttribute(
      'href',
      '/sammenlign',
    );
  });

  it('markerer siden man står på', async () => {
    renderHeader({ loggedIn: false, isAdmin: false }, '/dag/2025-05-13');
    await openMenu();

    expect(screen.getByRole('menuitem', { name: /dag for dag/i })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByRole('menuitem', { name: /forsiden/i })).not.toHaveAttribute('aria-current');
  });

  it('viser ikke administrasjon uten tilgang', async () => {
    renderHeader({ loggedIn: true, isAdmin: false, name: 'Kari' });
    await screen.findByText('Kari');
    await openMenu();

    expect(screen.queryByRole('menuitem', { name: /administrasjon/i })).toBeNull();
  });

  it('viser administrasjon for admin', async () => {
    renderHeader({ loggedIn: true, isAdmin: true, name: 'Ola' });
    await screen.findByText('Ola');
    await openMenu();

    expect(screen.getByRole('menuitem', { name: /administrasjon/i })).toHaveAttribute(
      'href',
      '/admin',
    );
  });
});
