import { Link } from 'react-router-dom';
import { Button, InternalHeader, Spacer } from '@navikt/ds-react';
import { useSession } from '../../hooks/queries';

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
        </>
      ) : (
        <Button
          variant="secondary-neutral"
          size="small"
          as="a"
          href="/oauth2/login"
          style={{ alignSelf: 'center', marginRight: '0.5rem' }}
        >
          Logg inn som ansatt
        </Button>
      )}
    </InternalHeader>
  );
}
