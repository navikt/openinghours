import { NavLink, Outlet } from 'react-router-dom';
import { Alert, BodyShort, Heading, Link } from '@navikt/ds-react';
import { useSession } from '../../hooks/queries';
import { DelayedLoader } from '../common/DelayedLoader';
import './AdminLayout.css';

const LINKS = [
  { to: '/admin', label: 'Oversikt', end: true },
  { to: '/admin/regler', label: 'Regler', end: false },
  { to: '/admin/grupper', label: 'Grupper', end: false },
  { to: '/admin/tjenester', label: 'Tjenester', end: false },
];

/**
 * Adminskallet: sidemeny og innloggingssperre.
 *
 * Sperren her er en høflighet, ikke en sikkerhetsmekanisme. BFF-en avviser alle
 * mutasjoner fra uinnloggede uansett — dette gjør bare at brukeren får en
 * forståelig beskjed framfor en rekke 401-er.
 */
export function AdminLayout() {
  const session = useSession();

  if (session.isPending) return <DelayedLoader />;

  if (!session.data?.loggedIn) {
    return (
      <Alert variant="info">
        <Heading level="2" size="small" spacing>
          Du må være innlogget for å administrere åpningstider
        </Heading>
        <BodyShort spacing>
          Kalenderen er åpen for alle, men endringer krever at du logger inn som Nav-ansatt.
        </BodyShort>
        <Link href="/oauth2/login">Logg inn som ansatt</Link>
      </Alert>
    );
  }

  return (
    <div className="oh-admin">
      <nav className="oh-admin__nav" aria-label="Administrasjon">
        <ul>
          {LINKS.map((link) => (
            <li key={link.to}>
              <NavLink
                to={link.to}
                end={link.end}
                className={({ isActive }) =>
                  isActive ? 'oh-admin__link oh-admin__link--active' : 'oh-admin__link'
                }
              >
                {link.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <div className="oh-admin__content">
        <Outlet />
      </div>
    </div>
  );
}
