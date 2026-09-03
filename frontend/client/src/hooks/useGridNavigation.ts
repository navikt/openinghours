import { useEffect, useRef } from 'react';
import { addDays, monthOf, shiftMonthKeepingDay } from '../lib/date';

interface Options {
  /** Måneden rutenettet viser, `yyyy-MM`. */
  month: string;
  /** Datoene i rutenettet, i visningsrekkefølge — brukes til Home/End. */
  dates: string[];
  /** Piltast krysset månedsgrensen: kalleren laster ny måned og beholder fokus. */
  onNavigateMonth: (month: string, focusDate: string) => void;
  /** Enter/mellomrom på en dag. */
  onSelect?: (date: string) => void;
}

/**
 * Tastaturnavigasjon i et månedsrutenett, delt av kalenderne.
 *
 * Rutenettet er én tabulatorstopp (roving tabindex): uten det ville en måned
 * kostet 42 tabulatortrykk å passere. Piltaster flytter fokus mellom dager,
 * PageUp/PageDown bytter måned.
 *
 * Fokus styres gjennom DOM-en framfor React-state med vilje: å re-rendre hele
 * rutenettet for hvert piltastetrykk gjør navigasjonen merkbart treg. Cellene
 * må derfor merkes med `data-date`.
 */
export function useGridNavigation({ month, dates, onNavigateMonth, onSelect }: Options) {
  const focusRef = useRef<string | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const focusDate = (date: string) => {
    focusRef.current = date;
    containerRef.current?.querySelector<HTMLElement>(`[data-date="${date}"]`)?.focus();
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

  /*
   * PageUp/PageDown skal bytte måned, ikke flytte et fast antall dager.
   * Dagnummeret klemmes mot månedens lengde, slik at 31. mars havner på
   * 28. februar i stedet for å renne over i mars igjen.
   */
  const moveMonth = (from: string, delta: number) => {
    const target = shiftMonthKeepingDay(from, delta);
    focusRef.current = target;
    onNavigateMonth(monthOf(target), target);
  };

  const onKeyDown = (event: React.KeyboardEvent, date: string) => {
    const handlers: Record<string, () => void> = {
      ArrowLeft: () => move(date, -1),
      ArrowRight: () => move(date, 1),
      ArrowUp: () => move(date, -7),
      ArrowDown: () => move(date, 7),
      Home: () => move(date, -weekdayIndex(dates, date)),
      End: () => move(date, 6 - weekdayIndex(dates, date)),
      PageUp: () => moveMonth(date, -1),
      PageDown: () => moveMonth(date, 1),
      Enter: () => onSelect?.(date),
      ' ': () => onSelect?.(date),
    };
    const handler = handlers[event.key];
    if (!handler) return;
    event.preventDefault();
    handler();
  };

  return { containerRef, onKeyDown };
}

function weekdayIndex(dates: string[], date: string): number {
  const index = dates.indexOf(date);
  return index < 0 ? 0 : index % 7;
}
