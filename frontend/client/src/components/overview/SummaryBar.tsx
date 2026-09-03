import { Label } from '@navikt/ds-react';
import type { Bucket, DayCounts } from '../../lib/summary';
import { BUCKETS, countLabel, presentBuckets } from '../../lib/summary';
import './SummaryBar.css';

const LEGEND_LABELS: Record<Bucket, string> = {
  open: 'Åpne',
  unstable: 'Ustabile',
  closed: 'Stengte',
  missing: 'Uten åpningstider',
};

/** Fast visningsrekkefølge: normalen først, avvikene sist. */
const DISPLAY_ORDER: Bucket[] = ['open', 'unstable', 'closed', 'missing'];

interface Props {
  counts: DayCounts;
  total: number;
  size?: 'small' | 'medium';
  /** Overstyrer den utledede beskrivelsen for skjermlesere. */
  label?: string;
}

/**
 * Stablet andelsstrek — hvor stor del av tjenestene som er i hver tilstand.
 *
 * Tallene streken viser står alltid som tekst ved siden av: en andel avlest på
 * øyemål er ikke noe å handle på, og farge får ikke bære betydning alene
 * (WCAG 2.1 AA). Rekkefølgen er fast, slik at to strek kan sammenlignes på tvers
 * av dager uten å lese etikettene på nytt.
 */
export function SummaryBar({ counts, total, size = 'medium', label }: Props) {
  const segments = DISPLAY_ORDER.filter((bucket) => counts[bucket] > 0).map((bucket) => ({
    bucket,
    percent: (counts[bucket] / Math.max(total, 1)) * 100,
  }));

  return (
    <div
      className={`oh-summarybar oh-summarybar--${size}`}
      role="img"
      aria-label={label ?? describe(counts, total)}
    >
      {segments.length === 0 ? (
        <div className="oh-summarybar__segment oh-summarybar__segment--empty" />
      ) : (
        segments.map(({ bucket, percent }) => (
          <div
            key={bucket}
            className={`oh-summarybar__segment oh-summarybar__segment--${bucket}`}
            style={{ width: `${percent}%` }}
          />
        ))
      )}
    </div>
  );
}

function describe(counts: DayCounts, total: number): string {
  const present = presentBuckets(counts);
  if (present.length === 0) return 'Ingen tjenester';
  return `${total} tjenester: ${present.map((b) => countLabel(b, counts[b])).join(', ')}`;
}

/**
 * Tallene under streken.
 *
 * Bøtter uten treff utelates: «0 ustabile» er støy på en dag der ingenting er
 * ustabilt, og gjør de tallene som faktisk er der vanskeligere å få øye på.
 */
export function SummaryCounts({
  counts,
  size = 'medium',
}: {
  counts: DayCounts;
  size?: 'small' | 'medium';
}) {
  const present = DISPLAY_ORDER.filter((bucket) => counts[bucket] > 0);

  return (
    <ul className={`oh-summarycounts oh-summarycounts--${size}`}>
      {present.map((bucket) => (
        <li key={bucket} className="oh-summarycounts__item">
          <span className={`oh-swatch oh-swatch--${bucket}`} aria-hidden />
          {countLabel(bucket, counts[bucket])}
        </li>
      ))}
    </ul>
  );
}

/** Tegnforklaring for fargene i streken. Vises én gang per side, ikke per dag. */
export function SummaryLegend() {
  return (
    <div className="oh-summarylegend">
      <Label size="small" textColor="subtle">
        Tegnforklaring
      </Label>
      <ul className="oh-summarylegend__items">
        {BUCKETS.map((bucket) => (
          <li key={bucket} className="oh-summarylegend__item">
            <span className={`oh-swatch oh-swatch--${bucket}`} aria-hidden />
            {LEGEND_LABELS[bucket]}
          </li>
        ))}
      </ul>
    </div>
  );
}
