import { addDays, firstOfMonth, formatMonth, formatWeek, lastOfMonth, monthOf, shiftMonth, shiftWeek, startOfWeek, yearOf } from './date';

/**
 * De tre kalendervisningene deler samme datakilde (range-endepunktet) og skiller
 * seg bare i hvilket tidsrom de spør om og hvordan de flyttes fram og tilbake.
 *
 * Logikken ligger her, ikke i komponenten, slik at den kan testes uten DOM.
 */
export type ViewKind = 'maned' | 'uke' | 'aar';

const VIEWS: ViewKind[] = ['maned', 'uke', 'aar'];

export function parseView(value: string | null): ViewKind {
  return VIEWS.includes(value as ViewKind) ? (value as ViewKind) : 'maned';
}

export interface ViewRange {
  from: string;
  to: string;
  /** Overskriften over kalenderen, f.eks. «August 2025» eller «Uke 34 · …». */
  title: string;
  /** Tekst på forrige/neste-knappene, f.eks. «måned». */
  unit: string;
}

/**
 * `anchor` er en dato som ligger i perioden. Måneds- og årsvisning normaliserer
 * den til periodens start, slik at samme dato gir samme spørring uansett hvilken
 * visning brukeren kom fra.
 */
export function viewRange(view: ViewKind, anchor: string): ViewRange {
  switch (view) {
    case 'uke': {
      const monday = startOfWeek(anchor);
      return { from: monday, to: addDays(monday, 6), title: formatWeek(monday), unit: 'uke' };
    }
    case 'aar': {
      const year = yearOf(anchor);
      return { from: `${year}-01-01`, to: `${year}-12-31`, title: year, unit: 'år' };
    }
    default: {
      const month = monthOf(anchor);
      return {
        from: firstOfMonth(month),
        to: lastOfMonth(month),
        title: formatMonth(month),
        unit: 'måned',
      };
    }
  }
}

/** Flytter ankeret én periode fram (+1) eller tilbake (−1) i gjeldende visning. */
export function shiftAnchor(view: ViewKind, anchor: string, delta: number): string {
  switch (view) {
    case 'uke':
      return shiftWeek(startOfWeek(anchor), delta);
    case 'aar':
      return `${Number(yearOf(anchor)) + delta}-01-01`;
    default:
      return firstOfMonth(shiftMonth(monthOf(anchor), delta));
  }
}
