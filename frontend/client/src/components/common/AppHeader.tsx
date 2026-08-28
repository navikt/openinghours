import { Link } from 'react-router-dom';
import { Button, InternalHeader, Spacer } from '@navikt/ds-react';
import { useSession } from '../../hooks/queries';
import './AppHeader.css';

/**
 * Adminlenker og interne snarveier rendres ikke når brukeren er uinnlogget.
 * De skjules ikke med CSS — de finnes ikke i DOM-en.
 */
export function AppHeader() {
  const session = useSession();
  const loggedIn = session.data?.loggedIn ?? false;

  return (
    <InternalHeader>
      <InternalHeader.Title as={Link} to="/">
        Åpningstider
      </InternalHeader.Title>
      <Spacer />
      {loggedIn ? (
        <>
          <InternalHeader.Button as={Link} to="/admin">
            Administrasjon
          </InternalHeader.Button>
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
