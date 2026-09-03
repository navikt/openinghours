import {
  CheckmarkCircleIcon,
  ClockIcon,
  ExclamationmarkTriangleIcon,
  ExclamationmarkTriangleFillIcon,
  MinusCircleIcon,
  PadlockLockedIcon,
  StarIcon,
} from '@navikt/aksel-icons';
import type { StatusKind } from '../../lib/status';
import './StatusBadge.css';

const ICONS = {
  open: CheckmarkCircleIcon,
  closed: MinusCircleIcon,
  // Aksel har ingen flagg-ikon; stjernen skiller rød dag fra vanlig stengt.
  redDay: StarIcon,
  warning: ExclamationmarkTriangleIcon,
  masked: PadlockLockedIcon,
} as const;

interface Props {
  kind: StatusKind;
  label: string;
  size?: 'small' | 'medium';
  /** Statusen er allerede beskrevet av forelderens aria-label. */
  decorative?: boolean;
}

/**
 * Statusmerke — ikon + tekst + bakgrunn.
 *
 * Aksels `Tag` tar ikke ikon, og statusen kan ikke hvile på farge alene (WCAG 2.1 AA).
 * Bygget utelukkende på Aksel-tokens; ingen nye farger.
 */
export function StatusBadge({ kind, label, size = 'medium', decorative = false }: Props) {
  const Icon = ICONS[kind];
  return (
    <span
      className={`oh-status oh-status--${kind} oh-status--${size}`}
      aria-hidden={decorative || undefined}
    >
      <Icon aria-hidden fontSize={size === 'small' ? '0.9375rem' : '1.125rem'} />
      <span className="oh-status__label">{label}</span>
    </span>
  );
}

/** Nå-indikator for dagens dato. Annonseres ikke på nytt av skjermleser. */
export function NowIndicator({ isOpen }: { isOpen: boolean }) {
  return (
    <span className={`oh-now ${isOpen ? 'oh-now--open' : 'oh-now--closed'}`} aria-hidden>
      <ClockIcon aria-hidden fontSize="0.875rem" />
      {isOpen ? 'Åpent nå' : 'Stengt nå'}
    </span>
  );
}

/**
 * Merke for perioder fagansvarlig har flagget som ustabile.
 *
 * Står ved siden av statusmerket, ikke i stedet for det: dagen kan være både
 * åpen og ustabil. Alltid dekorativt — hele statusen, ustabiliteten inkludert,
 * ligger i cellens `aria-label` via `statusAriaLabel`, så et eget
 * skjermlesermerke ville blitt lest opp to ganger.
 */
export function UnstableMark({ size = 'small' }: { size?: 'small' | 'medium' }) {
  return (
    <span className={`oh-unstable oh-unstable--${size}`} aria-hidden>
      <ExclamationmarkTriangleFillIcon
        aria-hidden
        fontSize={size === 'small' ? '0.9375rem' : '1.125rem'}
      />
      Ustabil
    </span>
  );
}
