import type { Interval } from '../../lib/status';
import './OpeningBar.css';

/** Fast vindu 07:00–17:00 slik at celler kan sammenlignes på tvers av rader. */
export const WINDOW_START = 7 * 60;
export const WINDOW_END = 17 * 60;
const WINDOW_SPAN = WINDOW_END - WINDOW_START;

interface Props {
  intervals: Interval[];
  /** Døgnåpen fyller hele sporet. */
  allDay?: boolean;
  /** `cell` er den tynne streken i dagcellen, `track` er det høye sporet i ukevisningen. */
  variant?: 'cell' | 'track';
  /** Klokkeslett inni segmentet — kun plass til dette i `track`. */
  showTimes?: boolean;
}

function position(interval: Interval) {
  const from = Math.max(interval.from, WINDOW_START);
  const to = Math.min(interval.to, WINDOW_END);
  if (to <= from) return null;
  return {
    left: `${((from - WINDOW_START) / WINDOW_SPAN) * 100}%`,
    width: `${((to - from) / WINDOW_SPAN) * 100}%`,
  };
}

function label(minutes: number): string {
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
}

/**
 * Åpningsstrek — grafisk fremstilling av når på døgnet det er åpent.
 *
 * Ingen motpart i Aksel. Samme geometri gjenbrukes i dagcelle og ukevisning,
 * slik at et segment betyr det samme uansett hvor det vises.
 */
export function OpeningBar({ intervals, allDay, variant = 'cell', showTimes }: Props) {
  const segments = allDay
    ? [{ left: '0%', width: '100%', key: 'all', from: WINDOW_START, to: WINDOW_END }]
    : intervals
        .map((interval) => {
          const pos = position(interval);
          return pos ? { ...pos, key: `${interval.from}`, ...interval } : null;
        })
        .filter((s): s is NonNullable<typeof s> => s !== null);

  return (
    <div className={`oh-bar oh-bar--${variant}`} aria-hidden>
      {segments.map((segment) => (
        <div
          className="oh-bar__segment"
          key={segment.key}
          style={{ left: segment.left, width: segment.width }}
        >
          {showTimes && (
            <span className="oh-bar__time">
              {label(segment.from)}–{label(segment.to)}
            </span>
          )}
        </div>
      ))}
    </div>
  );
}

/** Timeetikettene over sporet. Absolutt posisjonert — samme geometri som segmentene. */
export function TimeAxis() {
  const hours = [];
  for (let h = 7; h <= 17; h++) hours.push(h);
  return (
    <div className="oh-axis" aria-hidden>
      {hours.map((h) => (
        <span
          className="oh-axis__label"
          key={h}
          style={{ left: `${((h * 60 - WINDOW_START) / WINDOW_SPAN) * 100}%` }}
        >
          {String(h).padStart(2, '0')}:00
        </span>
      ))}
    </div>
  );
}
