import { NavLink, Outlet } from 'react-router-dom';
import { Alert, BodyShort, Heading, Link } from '@navikt/ds-react';
import { useSession } from '../../hooks/queries';
import { DelayedLoader } from '../common/DelayedLoader';
import { AppLink } from '../common/AppLink';
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
          Endringer i åpningstider krever at du logger inn som Nav-ansatt.
        </BodyShort>
        <Link href="/oauth2/login">Logg inn som ansatt</Link>
      </Alert>
    );
  }

  /*
   * Innlogget, men utenfor admingruppen. Vi sier hvem som har tilgang framfor
   * bare å nekte — ellers vet ikke brukeren hva neste steg er.
   */
  if (!session.data.isAdmin) {
    return (
      <Alert variant="warning">
        <Heading level="2" size="small" spacing>
          Du har ikke tilgang til administrasjon
        </Heading>
        <BodyShort spacing>
          Å endre åpningstider krever medlemskap i tilgangsgruppen for
          åpningstidsadministrasjon. Ta kontakt med teamet som eier tjenesten for å få tilgang.
        </BodyShort>
        <AppLink to="/">Gå til kalenderen</AppLink>
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
