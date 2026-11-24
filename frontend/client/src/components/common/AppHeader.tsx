import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { Button, Link as AkselLink, Page } from '@navikt/ds-react';
import { MenuHamburgerIcon, XMarkIcon } from '@navikt/aksel-icons';
import { useSession } from '../../hooks/queries';
import './AppHeader.css';

const LINKS = [
  { to: '/', label: 'Avvikskalender', end: true },
  { to: '/tjenester', label: 'Tjenester', end: false },
  { to: '/sammenlign', label: 'Sammenlign', end: false },
];

export function AppHeader() {
  const session = useSession();
  const loggedIn = session.data?.loggedIn ?? false;
  const isAdmin = session.data?.isAdmin ?? false;
  const location = useLocation();
  const [openLocationKey, setOpenLocationKey] = useState<string | null>(null);
  const menuOpen = openLocationKey === location.key;
  const menuButton = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    setOpenLocationKey(null);
  }, [location.key]);

  useEffect(() => {
    const desktop = window.matchMedia('(min-width: 64rem)');
    const closeOnDesktop = () => {
      if (desktop.matches) setOpenLocationKey(null);
    };
    desktop.addEventListener('change', closeOnDesktop);
    return () => desktop.removeEventListener('change', closeOnDesktop);
  }, []);

  return (
    <header
      className="oh-header"
      onKeyDown={(event) => {
        if (event.key === 'Escape' && menuOpen) {
          setOpenLocationKey(null);
          menuButton.current?.focus();
        }
      }}
    >
      <Page.Block width="xl" gutters className="oh-header__inner">
        <AkselLink
          as={Link}
          to="/"
          underline={false}
          className="oh-header__brand"
          onClick={() => setOpenLocationKey(null)}
        >
          Åpningstider
        </AkselLink>
        <Button
          ref={menuButton}
          variant="tertiary-neutral"
          icon={menuOpen ? <XMarkIcon aria-hidden /> : <MenuHamburgerIcon aria-hidden />}
          aria-expanded={menuOpen}
          aria-controls="oh-header-navigation"
          className="oh-header__menu-button"
          onClick={() => setOpenLocationKey(menuOpen ? null : location.key)}
        >
          Meny
        </Button>
        <nav
          id="oh-header-navigation"
          aria-label="Hovednavigasjon"
          className={`oh-header__navigation${menuOpen ? ' oh-header__navigation--open' : ''}`}
        >
          <ul className="oh-header__links">
            {LINKS.map((link) => (
              <li key={link.to}>
                <AkselLink
                  as={NavLink}
                  to={link.to}
                  end={link.end}
                  underline={false}
                  className="oh-header__link"
                  onClick={() => setOpenLocationKey(null)}
                >
                  {link.label}
                </AkselLink>
              </li>
            ))}
            {loggedIn && isAdmin && (
              <li>
                <AkselLink
                  as={NavLink}
                  to="/admin"
                  underline={false}
                  className="oh-header__link"
                  onClick={() => setOpenLocationKey(null)}
                >
                  Administrasjon
                </AkselLink>
              </li>
            )}
          </ul>
          <div className="oh-header__account">
            {loggedIn ? (
              <>
                <span className="oh-header__user">{session.data?.name ?? 'Innlogget'}</span>
                {/* Wonderwall eier sesjonen, så utlogging går via sidecaren. */}
                <AkselLink href="/oauth2/logout" className="oh-header__link" underline={false}>
                  Logg ut
                </AkselLink>
              </>
            ) : (
              <AkselLink href="/oauth2/login" className="oh-header__link" underline={false}>
                Logg inn som ansatt
              </AkselLink>
            )}
          </div>
        </nav>
      </Page.Block>
    </header>
  );
}
