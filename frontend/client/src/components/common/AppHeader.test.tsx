import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Theme } from '@navikt/ds-react';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Session } from '../../api/types';
import { AppHeader } from './AppHeader';

let desktop: MediaQueryList;

function HistoryControls() {
  const navigate = useNavigate();
  return (
    <>
      <button onClick={() => navigate('/tjenester')}>Åpne tjenester utenfra</button>
      <button onClick={() => navigate(-1)}>Tilbake</button>
    </>
  );
}

function setup(session: Session = { loggedIn: false }, path = '/') {
  const queryClient = new QueryClient();
  queryClient.setQueryData(['session'], session);
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Theme theme="light">
          <AppHeader />
          <HistoryControls />
        </Theme>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  desktop = Object.assign(new EventTarget(), {
    matches: false,
    media: '(min-width: 64rem)',
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
  });
  vi.stubGlobal('matchMedia', vi.fn(() => desktop));
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('AppHeader', () => {
  it('tilbyr hovedsidene og ansattinnlogging', () => {
    setup();
    const navigation = within(screen.getByRole('navigation', { name: 'Hovednavigasjon' }));

    expect(screen.getByRole('link', { name: 'Åpningstider' })).toHaveAttribute('href', '/');
    expect(navigation.getByRole('link', { name: 'Avvikskalender' })).toHaveAttribute('href', '/');
    expect(navigation.getByRole('link', { name: 'Tjenester' })).toHaveAttribute('href', '/tjenester');
    expect(navigation.getByRole('link', { name: 'Sammenlign' })).toHaveAttribute('href', '/sammenlign');
    expect(navigation.getByRole('link', { name: 'Logg inn som ansatt' }))
      .toHaveAttribute('href', '/oauth2/login');
    expect(screen.queryByRole('link', { name: 'Administrasjon' })).not.toBeInTheDocument();
  });

  it('åpner og lukker mobilmenyen med samme knapp', async () => {
    const user = userEvent.setup();
    setup();
    const button = screen.getByRole('button', { name: 'Meny' });

    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(button).toHaveAttribute('aria-controls', 'oh-header-navigation');
    await user.click(button);
    expect(button).toHaveAttribute('aria-expanded', 'true');
    await user.click(button);
    expect(button).toHaveAttribute('aria-expanded', 'false');
  });

  it('lukker med Escape og gir fokus tilbake til menyknappen', async () => {
    const user = userEvent.setup();
    setup();
    const button = screen.getByRole('button', { name: 'Meny' });

    await user.click(button);
    screen.getByRole('link', { name: 'Tjenester' }).focus();
    await user.keyboard('{Escape}');

    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(button).toHaveFocus();
  });

  it('lukker ved navigasjon og markerer siden du står på', async () => {
    const user = userEvent.setup();
    setup();
    const button = screen.getByRole('button', { name: 'Meny' });

    await user.click(button);
    await user.click(screen.getByRole('link', { name: 'Tjenester' }));

    expect(button).toHaveAttribute('aria-expanded', 'false');
    expect(screen.getByRole('link', { name: 'Tjenester' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Avvikskalender' })).not.toHaveAttribute('aria-current');
  });

  it('åpner ikke en gammel meny når du går tilbake i nettleserhistorikken', async () => {
    const user = userEvent.setup();
    setup();
    const button = screen.getByRole('button', { name: 'Meny' });

    await user.click(button);
    await user.click(screen.getByRole('button', { name: 'Åpne tjenester utenfra' }));
    await user.click(screen.getByRole('button', { name: 'Tilbake' }));

    expect(button).toHaveAttribute('aria-expanded', 'false');
  });

  it('lukker mobilmenyen når skjermen blir større', async () => {
    const user = userEvent.setup();
    setup();
    const button = screen.getByRole('button', { name: 'Meny' });
    await user.click(button);

    act(() => {
      Object.assign(desktop, { matches: true });
      desktop.dispatchEvent(new Event('change'));
    });

    expect(button).toHaveAttribute('aria-expanded', 'false');
  });

  it('viser utlogging, men ikke administrasjon for en ansatt uten admintilgang', () => {
    setup({ loggedIn: true, isAdmin: false });

    expect(screen.getByText('Innlogget')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Logg ut' })).toHaveAttribute('href', '/oauth2/logout');
    expect(screen.queryByRole('link', { name: 'Administrasjon' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Logg inn som ansatt' })).not.toBeInTheDocument();
  });

  it('viser administrasjon og markerer også undersidene som aktive', () => {
    setup({ loggedIn: true, isAdmin: true }, '/admin/regler');

    expect(screen.getByRole('link', { name: 'Administrasjon' }))
      .toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Administrasjon' })).toHaveAttribute('href', '/admin');
  });

  it('viser ikke administrasjon uten innlogging selv om adminflagget er satt', () => {
    setup({ loggedIn: false, isAdmin: true });

    expect(screen.queryByRole('link', { name: 'Administrasjon' })).not.toBeInTheDocument();
  });
});
