import type { QueryResponse } from '../../api/types';
import { monthGrid, WEEKDAY_HEADERS } from '../../lib/date';
import { useGridNavigation } from '../../hooks/useGridNavigation';
import { deriveStatus, statusAriaLabel } from '../../lib/status';
import { DayCell } from './DayCell';
import './MonthGrid.css';

interface Props {
  month: string;
  days: Map<string, QueryResponse>;
  today: string;
  nowIsOpen: boolean;
  selected: string | null;
  onSelect: (date: string) => void;
  /** Piltast krysset månedsgrensen — kalleren laster ny måned og beholder fokus. */
  onNavigateMonth: (month: string, focusDate: string) => void;
}

/**
 * Månedsrutenett.
 *
 * Aksels `DatePicker` velger en dato, men viser ikke innhold per dag — rutenettet
 * må bygges. Det arver `DatePicker`s tastaturmønster og fokusramme.
 *
 * Rutenettet er én tabulatorstopp (roving tabindex): kun den fokuserte dagen har
 * `tabindex=0`. Piltaster flytter fokus, Enter/mellomrom åpner detaljpanelet.
 */
export function MonthGrid({
  month,
  days,
  today,
  nowIsOpen,
  selected,
  onSelect,
  onNavigateMonth,
}: Props) {
  const grid = monthGrid(month);
  const { containerRef, onKeyDown } = useGridNavigation({
    month,
    dates: grid.map((d) => d.date),
    onNavigateMonth,
    onSelect,
  });

  // Nøyaktig én celle skal være tabulerbar.
  const tabbable = selected && grid.some((d) => d.date === selected) ? selected : today;
  const tabbableInGrid = grid.some((d) => d.date === tabbable)
    ? tabbable
    : (grid.find((d) => d.inMonth)?.date ?? grid[0].date);

  return (
    <div className="oh-grid-wrapper">
      <div className="oh-grid__headers" aria-hidden>
        {WEEKDAY_HEADERS.map((name) => (
          <span key={name} className="oh-grid__header">
            {name}
          </span>
        ))}
      </div>
      <div
        className="oh-grid"
        role="grid"
        aria-label="Åpningstider per dag"
        ref={containerRef}
      >
        {grid.map(({ date, inMonth }) => {
          const day = inMonth ? days.get(date) : undefined;
          const status = day ? deriveStatus(day) : null;
          return (
            <DayCell
              key={date}
              date={date}
              inMonth={inMonth}
              status={status}
              isToday={date === today}
              nowIsOpen={nowIsOpen}
              selected={date === selected}
              tabbable={date === tabbableInGrid}
              ariaLabel={day && status ? statusAriaLabel(day, status) : undefined}
              onSelect={onSelect}
              onKeyDown={onKeyDown}
            />
          );
        })}
      </div>
    </div>
  );
}
