import { BodyLong, Heading } from '@navikt/ds-react';
import { AppLink } from '../components/common/AppLink';

export function NotFoundPage() {
  return (
    <div>
      <Heading level="1" size="large" spacing>
        Fant ikke siden
      </Heading>
      <BodyLong spacing>Adressen finnes ikke, eller tjenesten kan ha blitt slettet.</BodyLong>
      <AppLink to="/">Gå til oversikten</AppLink>
    </div>
  );
}
