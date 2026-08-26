import type { QueryResponse } from '../api/types';
import { holidayName } from './holidays';
import { HOURS_ALWAYS_OPEN, HOURS_CLOSED, trimSeconds } from './rule';

/**
 * Statussystemet fra designet: fem tilstander, hver med bakgrunn + ikon + tekst.
 * Farge bærer aldri betydning alene.
 */
export type StatusKind = 'open' | 'closed' | 'redDay' | 'warning' | 'masked';

export interface DayStatus {
  kind: StatusKind;
  /** Kort status, f.eks. «Åpen 08:00–15:30». Alltid satt. */
  label: string;
  /** Utdypning under statuslinjen, f.eks. «Grunnlovsdagen» eller «Helg». */
  detail: string | null;
  /** Åpningsintervaller for tidsstreken. Tom liste = ingen strek. */
  intervals: Interval[];
  /** Døgnåpen — tidsstreken fylles helt. */
  allDay: boolean;
  /** Navnet på helligdagen, når datoen er en offisiell rød dag. */
  holiday: string | null;
}

export interface Interval {
  /** Minutter etter midnatt. */
  from: number;
  to: number;
}

function toMinutes(time: string): number | null {
  const t = trimSeconds(time);
  if (!t) return null;
  const [h, m] = t.split(':').map(Number);
  return h * 60 + m;
}

/**
 * Åpningsintervaller for en dag.
 *
 * Backend har kun ett intervall per regel, så dette gir alltid 0 eller 1 element.
 * Signaturen returnerer likevel en liste, slik at tidsstreken kan tegne flere
 * segmenter uendret den dagen backend får støtte for lunsjpauser.
 */
export function toIntervals(day: QueryResponse): Interval[] {
  const hours = `${trimSeconds(day.openingTime)}-${trimSeconds(day.closingTime)}`;
  if (hours === HOURS_CLOSED) return [];
  const from = toMinutes(day.openingTime);
  const to = toMinutes(day.closingTime);
  if (from === null || to === null || to <= from) return [];
  return [{ from, to }];
}

function weekendDetail(dateIso: string): string | null {
  const dow = new Date(`${dateIso}T00:00:00Z`).getUTCDay();
  return dow === 0 || dow === 6 ? 'Helg' : null;
}

export function deriveStatus(day: QueryResponse): DayStatus {
  const holiday = day.redDay ? holidayName(day.date) : null;
  const intervals = toIntervals(day);
  const hours = `${trimSeconds(day.openingTime)}-${trimSeconds(day.closingTime)}`;
  const allDay = hours === HOURS_ALWAYS_OPEN;

  // Rekkefølgen er bevisst: maskering og manglende oppsett er viktigere å formidle
  // enn åpningstiden, fordi de betyr at tallet i cellen ikke kan stoles på.
  //
  // `masked` settes bare når kalenderen er åpen for uinnloggede. I dag ligger
  // hele appen bak ansatt.nav.no, så denne grenen er hvilende.
  if (day.masked) {
    return {
      kind: 'masked',
      label: 'Intern åpningstid',
      detail: 'Logg inn som ansatt for å se',
      intervals: [],
      allDay: false,
      holiday,
    };
  }

  if (day.warningMessage) {
    return {
      kind: 'warning',
      label: 'Ikke satt opp',
      detail: 'Ingen regel treffer',
      intervals: [],
      allDay: false,
      holiday,
    };
  }

  if (day.redDay) {
    return {
      kind: 'redDay',
      label: 'Rød dag',
      detail: holiday ? `${holiday} · stengt` : 'Stengt',
      intervals: allDay ? [] : intervals,
      allDay: false,
      holiday,
    };
  }

  if (allDay) {
    return { kind: 'open', label: 'Døgnåpen', detail: null, intervals: [], allDay: true, holiday };
  }

  if (intervals.length === 0) {
    return {
      kind: 'closed',
      label: 'Stengt',
      detail: weekendDetail(day.date) ?? 'Stengt hele dagen',
      intervals: [],
      allDay: false,
      holiday,
    };
  }

  const first = intervals[0];
  const last = intervals[intervals.length - 1];
  return {
    kind: 'open',
    label: `Åpen ${fmt(first.from)}–${fmt(last.to)}`,
    detail: null,
    intervals,
    allDay: false,
    holiday,
  };
}

function fmt(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

export { fmt as formatMinutes };

/** Full status som én setning for `aria-label`. Skjermlesere får aldri bare farge. */
export function statusAriaLabel(day: QueryResponse, status: DayStatus): string {
  const date = new Date(`${day.date}T00:00:00Z`);
  const dateText = new Intl.DateTimeFormat('nb-NO', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    weekday: 'long',
    timeZone: 'UTC',
  }).format(date);

  const parts = [dateText];
  switch (status.kind) {
    case 'open':
      parts.push(status.allDay ? 'døgnåpen' : status.label.toLowerCase().replace('–', ' til '));
      break;
    case 'redDay':
      parts.push(status.holiday ? `rød dag, ${status.holiday.toLowerCase()}, stengt` : 'rød dag, stengt');
      break;
    case 'closed':
      parts.push('stengt hele dagen');
      break;
    case 'warning':
      parts.push('åpningstider ikke satt opp');
      break;
    case 'masked':
      parts.push('intern åpningstid, krever innlogging');
      break;
  }
  return parts.join(', ');
}
