import type { DayStatus } from '../../lib/status';
import { OpeningBar } from './OpeningBar';
import { NowIndicator, StatusBadge } from './StatusBadge';
import './DayCell.css';

interface Props {
  date: string;
  inMonth: boolean;
  status: DayStatus | null;
  isToday: boolean;
  nowIsOpen: boolean;
  selected: boolean;
  tabbable: boolean;
  ariaLabel?: string;
  onSelect: (date: string) => void;
  onKeyDown: (event: React.KeyboardEvent, date: string) => void;
}

/**
 * Dagcelle med fire faste lag: dagnummer, tidsstrek, status, underlinje.
 * Rekkefølgen er alltid den samme slik at øyet kan skanne nedover en kolonne.
 */
export function DayCell({
  date,
  inMonth,
  status,
  isToday,
  nowIsOpen,
  selected,
  tabbable,
  ariaLabel,
  onSelect,
  onKeyDown,
}: Props) {
  const dayNumber = Number(date.slice(-2));

  if (!inMonth) {
    return (
      <div className="oh-day oh-day--outside" role="gridcell" aria-hidden>
        <span className="oh-day__number">{dayNumber}</span>
      </div>
    );
  }

  const classes = [
    'oh-day',
    status ? `oh-day--${status.kind}` : 'oh-day--loading',
    isToday && 'oh-day--today',
    selected && 'oh-day--selected',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div
      role="gridcell"
      tabIndex={tabbable ? 0 : -1}
      data-date={date}
      className={classes}
      aria-selected={selected}
      aria-label={ariaLabel ? `${ariaLabel}${isToday ? ', i dag' : ''}` : undefined}
      onClick={() => onSelect(date)}
      onKeyDown={(event) => onKeyDown(event, date)}
    >
      <div className="oh-day__top">
        <span className="oh-day__number">{dayNumber}</span>
        {isToday && <span className="oh-day__today">I dag</span>}
      </div>

      <OpeningBar intervals={status?.intervals ?? []} allDay={status?.allDay} />

      {status && <StatusBadge kind={status.kind} label={status.label} size="small" decorative />}
      {status?.detail && <span className="oh-day__detail">{status.detail}</span>}
      {isToday && status && <NowIndicator isOpen={nowIsOpen} />}
    </div>
  );
}
