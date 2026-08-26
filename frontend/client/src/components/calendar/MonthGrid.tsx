import { useEffect, useRef } from 'react';
import type { QueryResponse } from '../../api/types';
import { addDays, monthGrid, monthOf, WEEKDAY_HEADERS } from '../../lib/date';
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
  const focusRef = useRef<string | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Fokusert dag styres av DOM-en, ikke av state: å re-rendre hele rutenettet
  // for hvert piltastetrykk gjør navigasjonen merkbart treg.
  const focusDate = (date: string) => {
    focusRef.current = date;
    const el = containerRef.current?.querySelector<HTMLElement>(`[data-date="${date}"]`);
    el?.focus();
  };

  useEffect(() => {
    const pending = focusRef.current;
    if (pending && monthOf(pending) === month) focusDate(pending);
  }, [month]);

  const move = (from: string, delta: number) => {
    const target = addDays(from, delta);
    if (monthOf(target) !== month) {
      focusRef.current = target;
      onNavigateMonth(monthOf(target), target);
      return;
    }
    focusDate(target);
  };

  const handleKeyDown = (event: React.KeyboardEvent, date: string) => {
    const handlers: Record<string, () => void> = {
      ArrowLeft: () => move(date, -1),
      ArrowRight: () => move(date, 1),
      ArrowUp: () => move(date, -7),
      ArrowDown: () => move(date, 7),
      Home: () => {
        const dow = grid.findIndex((d) => d.date === date) % 7;
        move(date, -dow);
      },
      End: () => {
        const dow = grid.findIndex((d) => d.date === date) % 7;
        move(date, 6 - dow);
      },
      PageUp: () => move(date, -28),
      PageDown: () => move(date, 28),
      Enter: () => onSelect(date),
      ' ': () => onSelect(date),
    };
    const handler = handlers[event.key];
    if (!handler) return;
    event.preventDefault();
    handler();
  };

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
              onKeyDown={handleKeyDown}
            />
          );
        })}
      </div>
    </div>
  );
}
