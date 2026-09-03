import { Link } from 'react-router-dom';
import { Button, InternalHeader, Spacer } from '@navikt/ds-react';
import { useSession } from '../../hooks/queries';
import { AppNav } from './AppNav';
import './AppHeader.css';

/**
 * Adminlenker og interne snarveier rendres ikke når brukeren mangler tilgang.
 * De skjules ikke med CSS — de finnes ikke i DOM-en.
 *
 * «Administrasjon» krever medlemskap i admingruppen, ikke bare innlogging: en
 * lenke som alltid ender i «du har ikke tilgang» er verre enn ingen lenke.
 */
export function AppHeader() {
  const session = useSession();
  const loggedIn = session.data?.loggedIn ?? false;
  const isAdmin = session.data?.isAdmin ?? false;

  return (
    <InternalHeader>
      <InternalHeader.Title as={Link} to="/">
        Åpningstider
      </InternalHeader.Title>
      <AppNav isAdmin={isAdmin} />
      <Spacer />
      {loggedIn ? (
        <>
          <InternalHeader.User name={session.data?.name ?? 'Innlogget'} />
          {/* Wonderwall eier sesjonen, så utlogging må gå via sidecaren og kan
              ikke være en ren klientrute. */}
          <InternalHeader.Button as="a" href="/oauth2/logout">
            Logg ut
          </InternalHeader.Button>
        </>
      ) : (
        <Button variant="secondary-neutral" size="small" as="a" href="/oauth2/login" className="oh-header__login">
          Logg inn som ansatt
        </Button>
      )}
    </InternalHeader>
  );
}
