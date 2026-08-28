import { Heading } from '@navikt/ds-react';
import type { QueryResponse } from '../../api/types';
import { deriveStatus, statusAriaLabel } from '../../lib/status';
import { formatMonthName, monthGrid, monthsOfYear } from '../../lib/date';
import './YearView.css';

interface Props {
  year: string;
  days: Map<string, QueryResponse>;
  today: string;
  selected: string | null;
  onSelect: (date: string) => void;
}

/**
 * Årsoversikt — tolv minimåneder, laget for å kontrollere røde dager i ett blikk.
 *
 * Statusen bæres av tre signaler samtidig: bakgrunnsfarge, tekstvekt og understrek.
 * Rutene er for små til ikoner, så uten de to siste ville fargen stått alene.
 */
export function YearView({ year, days, today, selected, onSelect }: Props) {
  return (
    <div className="oh-year">
      {monthsOfYear(year).map((month) => (
        <section className="oh-year__month" key={month}>
          <Heading level="3" size="xsmall" className="oh-year__title">
            {formatMonthName(month)}
          </Heading>
          <div className="oh-year__grid">
            {['M', 'T', 'O', 'T', 'F', 'L', 'S'].map((letter, i) => (
              <abbr className="oh-year__weekday" key={i} title={WEEKDAYS[i]}>
                {letter}
              </abbr>
            ))}
            {monthGrid(month).map(({ date, inMonth }) => {
              if (!inMonth) return <span className="oh-year__blank" key={date} />;

              const day = days.get(date);
              const status = day ? deriveStatus(day) : null;
              const kind = status?.kind ?? 'unknown';

              return (
                <button
                  type="button"
                  key={date}
                  data-date={date}
                  className={[
                    'oh-year__day',
                    `oh-year__day--${kind}`,
                    date === today ? 'oh-year__day--today' : '',
                    date === selected ? 'oh-year__day--selected' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  aria-current={date === today ? 'date' : undefined}
                  aria-label={day && status ? statusAriaLabel(day, status) : date}
                  onClick={() => onSelect(date)}
                >
                  {Number(date.slice(8, 10))}
                </button>
              );
            })}
          </div>
        </section>
      ))}
    </div>
  );
}

const WEEKDAYS = ['Mandag', 'Tirsdag', 'Onsdag', 'Torsdag', 'Fredag', 'Lørdag', 'Søndag'];
