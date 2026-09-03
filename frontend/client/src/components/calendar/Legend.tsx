import { Label } from '@navikt/ds-react';
import type { StatusKind } from '../../lib/status';
import { StatusBadge } from './StatusBadge';
import './Legend.css';

const ITEMS: Array<{ kind: StatusKind; label: string }> = [
  { kind: 'open', label: 'Åpen' },
  { kind: 'closed', label: 'Stengt' },
  { kind: 'redDay', label: 'Rød dag' },
];

interface LegendProps {
  /**
   * Vises bare når maskerte dager faktisk forekommer, altså når kalenderen er
   * åpen for uinnloggede. Å utlede det framfor å styre det med et flagg gjør at
   * tegnforklaringen blir riktig av seg selv når appen åpnes for publikum.
   */
  hasMasked?: boolean;
}

export function Legend({ hasMasked = false }: LegendProps) {
  const items = hasMasked
    ? [...ITEMS, { kind: 'masked' as StatusKind, label: 'Kun for ansatte' }]
    : ITEMS;

  return (
    <div className="oh-legend">
      <Label size="small" textColor="subtle">
        Tegnforklaring
      </Label>
      <div className="oh-legend__items">
        {items.map(({ kind, label }) => (
          <StatusBadge key={kind} kind={kind} label={label} size="small" />
        ))}
      </div>
    </div>
  );
}
