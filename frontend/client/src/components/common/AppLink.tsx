import { forwardRef } from 'react';
import { Link as RouterLink, type LinkProps as RouterLinkProps } from 'react-router-dom';
import { Link as AkselLink, type LinkProps as AkselLinkProps } from '@navikt/ds-react';

type AppLinkProps = Omit<AkselLinkProps, 'href'> & Pick<RouterLinkProps, 'to' | 'replace'>;

/**
 * Intern lenke med Aksels utseende.
 *
 * React Routers `Link` rendrer en naken `<a>` uten understrek, fokusramme eller
 * riktig farge, mens Aksels `Link` ikke kan rute på klientsiden. Å skrive
 * `as={RouterLink}` på hvert brukssted er lett å glemme, og en glemt lenke er
 * usynlig i kodegjennomgang men tydelig på skjermen. Derfor denne.
 */
export const AppLink = forwardRef<HTMLAnchorElement, AppLinkProps>(function AppLink(props, ref) {
  return <AkselLink ref={ref} as={RouterLink} {...props} />;
});
