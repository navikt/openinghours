import { ChevronRightIcon } from '@navikt/aksel-icons';
import type { QueryResponse } from '../../api/types';
import { weekdayShort } from '../../lib/date';
import { deriveStatus, statusAriaLabel } from '../../lib/status';
import { NowIndicator, StatusBadge } from './StatusBadge';
import './AgendaList.css';

interface Props {
  days: QueryResponse[];
  today: string;
  nowIsOpen: boolean;
  onSelect: (date: string) => void;
}

/**
 * Mobilvisning av måneden.
 *
 * Rutenettet er ikke lesbart under 720 px. Listen starter på dagens dato,
 * fordi det nesten alltid er det brukeren er ute etter.
 */
export function AgendaList({ days, today, nowIsOpen, onSelect }: Props) {
  const fromToday = days.filter((d) => d.date >= today);
  const visible = fromToday.length > 0 ? fromToday : days;

  return (
    <ul className="oh-agenda">
      {visible.map((day) => {
        const status = deriveStatus(day);
        const isToday = day.date === today;
        return (
          <li key={day.date}>
            <button
              type="button"
              className={`oh-agenda__row oh-agenda__row--${status.kind}`}
              onClick={() => onSelect(day.date)}
              aria-label={`${statusAriaLabel(day, status)}${isToday ? ', i dag' : ''}`}
            >
              <span
                className={`oh-agenda__date ${isToday ? 'oh-agenda__date--today' : ''}`}
                aria-hidden
              >
                <span className="oh-agenda__weekday">{weekdayShort(day.date)}</span>
                <span className="oh-agenda__number">{Number(day.date.slice(-2))}</span>
              </span>
              <span className="oh-agenda__body">
                <StatusBadge kind={status.kind} label={status.label} decorative />
                {status.detail && <span className="oh-agenda__detail">{status.detail}</span>}
                {isToday && <NowIndicator isOpen={nowIsOpen} />}
              </span>
              <ChevronRightIcon aria-hidden fontSize="1.25rem" />
            </button>
          </li>
        );
      })}
    </ul>
  );
}
