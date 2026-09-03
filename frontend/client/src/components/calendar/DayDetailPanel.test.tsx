import { render, screen } from '@testing-library/react';
import { Theme } from '@navikt/ds-react';
import { describe, expect, it } from 'vitest';
import type { QueryResponse } from '../../api/types';
import { DayDetailPanel } from './DayDetailPanel';

/**
 * Vaktpost mot `Box`-fellen.
 *
 * Aksels gamle `Box` kaster en feil når den får `background`, `borderColor` eller
 * `shadow` inne i darkside-`<Theme>` — og hele appen ligger inne i én. Feilen
 * dukker ikke opp før panelet faktisk rendres, altså først når noen klikker på
 * en dag i kalenderen. Denne testen rendrer det, slik at fellen fanges her.
 */
const DAY: QueryResponse = {
  resourceId: 's1',
  date: '2026-05-12',
  isOpen: true,
  openingTime: '08:00',
  closingTime: '15:30',
  displayHeader: null,
  displayText: null,
  onlyShowForNavEmployees: false,
  unstableOpeningHours: false,
  redDay: false,
};

describe('DayDetailPanel', () => {
  it('rendres inne i darkside-temaet uten å kaste', () => {
    render(
      <Theme theme="light">
        <DayDetailPanel day={DAY} loggedIn={false} onClose={() => {}} />
      </Theme>,
    );

    expect(screen.getByRole('heading', { name: /12\. mai 2026 · tirsdag/ })).toBeInTheDocument();
    expect(screen.getByText('Åpen 08:00–15:30')).toBeInTheDocument();
  });
});
