import { Link } from 'react-router-dom';
import { BodyShort, Skeleton, Tag } from '@navikt/ds-react';
import type { DeviationCalendar, DeviationEntry } from '../../lib/deviation';
import { DEVIATION_LABELS } from '../../lib/deviation';
import { monthGrid, WEEKDAY_HEADERS } from '../../lib/date';
import { useGridNavigation } from '../../hooks/useGridNavigation';
import './DeviationMark.css';
import './DeviationMonth.css';

interface Props {
  month: string;
  calendar: DeviationCalendar;
  today: string;
  pending: boolean;
  onNavigateMonth: (month: string, focusDate: string) => void;
  onSelect: (date: string) => void;
}

/** Flere enn dette i én celle, og cellen slutter å kunne leses på et blikk. */
const MAX_PER_CELL = 3;

/**
 * Månedskalender som kun viser avvik.
 *
 * Dager der alt er som normalt står tomme — det er selve poenget. Kalenderen er
 * bygget for å skummes: er en celle tom, er det ingenting å vite om den dagen.
 * En visning som skrev «åpen 08:00–15:30» i alle 42 cellene ville formidlet
 * nøyaktig like mye som en tom kalender, bare tregere.
 */
export function DeviationMonth({
  month,
  calendar,
  today,
  pending,
  onNavigateMonth,
  onSelect,
}: Props) {
  const grid = monthGrid(month);
  const { containerRef, onKeyDown } = useGridNavigation({
    month,
    dates: grid.map((d) => d.date),
    onNavigateMonth,
    onSelect,
  });

  /* Nøyaktig én celle skal være tabulerbar — se `useGridNavigation`. */
  const tabbable = grid.some((d) => d.date === today)
    ? today
    : (grid.find((d) => d.inMonth)?.date ?? grid[0].date);

  return (
    <div className="oh-devmonth">
      <div className="oh-devmonth__headers" aria-hidden>
        {WEEKDAY_HEADERS.map((name) => (
          <span key={name} className="oh-devmonth__header">
            {name}
          </span>
        ))}
      </div>
      <div className="oh-devmonth__grid" role="grid" aria-label="Avvik per dag" ref={containerRef}>
        {grid.map(({ date, inMonth }) =>
          inMonth ? (
            <DayCell
              key={date}
              date={date}
              entries={calendar.byDate.get(date) ?? []}
              isToday={date === today}
              pending={pending}
              tabbable={date === tabbable}
              onKeyDown={onKeyDown}
            />
          ) : (
            /*
             * Dagene fra nabomånedene fyller ut rutenettet og skal ikke leses
             * opp eller tabuleres til. De er heller ikke tomme for avvik — de
             * er bare ikke denne månedens ansvar, og en celle som sa «ingen
             * avvik» ville påstått noe vi ikke har sjekket.
             */
            <div key={date} className="oh-devcell oh-devcell--outside" aria-hidden />
          ),
        )}
      </div>
    </div>
  );
}

interface CellProps {
  date: string;
  entries: DeviationEntry[];
  isToday: boolean;
  pending: boolean;
  tabbable: boolean;
  onKeyDown: (event: React.KeyboardEvent, date: string) => void;
}

function DayCell({ date, entries, isToday, pending, tabbable, onKeyDown }: CellProps) {
  const dayNumber = Number(date.slice(8, 10));
  const shown = entries.slice(0, MAX_PER_CELL);
  const rest = entries.length - shown.length;
  /* Rød dag er tjenestenes eget flagg, ikke kalenderens: er den satt for
     dagens avvik, gjelder den hele dagen og fortjener egen merking. */
  const holiday = entries.find((e) => e.deviation.holiday)?.deviation.holiday ?? null;

  const classes = [
    'oh-devcell',
    isToday && 'oh-devcell--today',
    entries.length > 0 && 'oh-devcell--has',
    holiday && 'oh-devcell--red',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classes} role="gridcell">
      <Link
        to={`/dag/${date}`}
        className="oh-devcell__link"
        data-date={date}
        tabIndex={tabbable ? 0 : -1}
        aria-label={cellLabel(date, entries, isToday, holiday)}
        onKeyDown={(event) => onKeyDown(event, date)}
      >
        <span className="oh-devcell__num" aria-hidden>
          {dayNumber}
          {isToday && <span className="oh-devcell__today">i dag</span>}
        </span>

        {holiday && (
          <span className="oh-devcell__holiday" aria-hidden>
            {holiday}
          </span>
        )}

        {pending ? (
          <Skeleton variant="text" width="80%" />
        ) : (
          <span className="oh-devcell__list" aria-hidden>
            {shown.map((entry) => (
              <span
                key={entry.serviceId}
                className={`oh-devchip oh-devchip--${entry.deviation.kind}`}
              >
                <span className="oh-devchip__name">{entry.serviceName}</span>
                <span className="oh-devchip__what">{entry.deviation.summary}</span>
              </span>
            ))}
            {rest > 0 && <span className="oh-devcell__more">+{rest} til</span>}
          </span>
        )}
      </Link>
    </div>
  );
}

/**
 * Hele cellen som én setning.
 *
 * Chipsene er `aria-hidden` fordi de er visuelt komprimerte fragmenter —
 * «Dagpenger» og «Stenger 12:00» i hver sin `<span>` leses som to løsrevne
 * biter. Setningen her er den samme informasjonen, formulert for å høres.
 */
function cellLabel(
  date: string,
  entries: DeviationEntry[],
  isToday: boolean,
  holiday: string | null,
): string {
  const dateText = new Intl.DateTimeFormat('nb-NO', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    timeZone: 'UTC',
  }).format(new Date(`${date}T00:00:00Z`));

  const parts = [isToday ? `${dateText}, i dag` : dateText];
  if (holiday) parts.push(holiday);
  if (entries.length === 0) {
    parts.push('ingen avvik');
  } else {
    parts.push(entries.length === 1 ? 'ett avvik' : `${entries.length} avvik`);
    for (const entry of entries) {
      parts.push(`${entry.serviceName}: ${entry.deviation.summary.replace('·', '')}`);
    }
  }
  return parts.join('. ');
}

/** Tegnforklaring — fargene i cellene skal aldri bære betydningen alene. */
export function DeviationLegend({ kinds }: { kinds: readonly string[] }) {
  if (kinds.length === 0) return null;
  return (
    <div className="oh-devlegend">
      <BodyShort size="small" textColor="subtle" as="span">
        Avvikstyper:
      </BodyShort>
      {kinds.map((kind) => (
        <Tag key={kind} variant="neutral" size="small" className={`oh-devchip--${kind}`}>
          {DEVIATION_LABELS[kind as keyof typeof DEVIATION_LABELS]}
        </Tag>
      ))}
    </div>
  );
}
