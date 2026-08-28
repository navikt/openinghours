import { Link } from 'react-router-dom';
import { BodyLong, Heading, Link as AkselLink } from '@navikt/ds-react';

export function NotFoundPage() {
  return (
    <div>
      <Heading level="1" size="large" spacing>
        Fant ikke siden
      </Heading>
      <BodyLong spacing>Adressen finnes ikke, eller tjenesten kan ha blitt slettet.</BodyLong>
      <AkselLink as={Link} to="/">
        Gå til oversikten over tjenester
      </AkselLink>
    </div>
  );
}
