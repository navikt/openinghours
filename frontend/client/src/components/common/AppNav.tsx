import { useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ActionMenu, InternalHeader } from '@navikt/ds-react';
import {
  Buildings3Icon,
  CalendarIcon,
  ChevronDownIcon,
  HouseIcon,
  LayersIcon,
  MenuHamburgerIcon,
  TableIcon,
} from '@navikt/aksel-icons';
import { todayIso } from '../../lib/date';

type NavItem = {
  to: string;
  label: string;
  icon: React.ReactNode;
  /** Ruter som skal få denne oppføringen markert som gjeldende side. */
  match: (pathname: string) => boolean;
};

/**
 * Menyen i headeren.
 *
 * Uten den var dagsvisningen bare tilgjengelig ved å klikke seg inn fra
 * forsiden. En side man må finne veien til, finner folk ikke — og
 * `/dag/<dato>` er ikke en URL noen gjetter seg fram til.
 */
export function AppNav({ isAdmin }: { isAdmin: boolean }) {
  const { pathname } = useLocation();
  const [open, setOpen] = useState(false);

  /*
   * «I dag» regnes ut når menyen åpnes, ikke når headeren rendres. Headeren
   * rendres én gang og blir stående; i en fane som står åpen over midnatt ville
   * lenken ellers pekt på gårsdagen.
   */
  const today = useMemo(() => todayIso(), [open]);

  const items: NavItem[] = [
    {
      to: '/',
      label: 'Forsiden',
      icon: <HouseIcon aria-hidden />,
      match: (p) => p === '/',
    },
    {
      to: `/dag/${today}`,
      label: 'Dag for dag',
      icon: <CalendarIcon aria-hidden />,
      match: (p) => p.startsWith('/dag/'),
    },
    {
      to: '/tjenester',
      label: 'Alle tjenester',
      icon: <TableIcon aria-hidden />,
      match: (p) => p === '/tjenester' || p.startsWith('/t/'),
    },
    {
      to: '/sammenlign',
      label: 'Sammenlign tjenester',
      icon: <LayersIcon aria-hidden />,
      match: (p) => p === '/sammenlign',
    },
  ];

  if (isAdmin) {
    items.push({
      to: '/admin',
      label: 'Administrasjon',
      icon: <Buildings3Icon aria-hidden />,
      match: (p) => p.startsWith('/admin'),
    });
  }

  return (
    <ActionMenu open={open} onOpenChange={setOpen}>
      <ActionMenu.Trigger>
        <InternalHeader.Button aria-label="Meny">
          <MenuHamburgerIcon aria-hidden fontSize="1.25rem" />
          Meny
          <ChevronDownIcon aria-hidden fontSize="1.25rem" />
        </InternalHeader.Button>
      </ActionMenu.Trigger>
      <ActionMenu.Content align="start">
        <ActionMenu.Group label="Åpningstider">
          {items.map((item) => {
            const current = item.match(pathname);
            return (
              <ActionMenu.Item
                key={item.label}
                as={Link}
                to={item.to}
                icon={item.icon}
                /* Markerer siden man allerede står på, både visuelt og for skjermlesere. */
                aria-current={current ? 'page' : undefined}
              >
                {item.label}
              </ActionMenu.Item>
            );
          })}
        </ActionMenu.Group>
      </ActionMenu.Content>
    </ActionMenu>
  );
}
