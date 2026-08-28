/**
 * Dato- og kalenderhjelpere.
 *
 * Alle beregninger gjøres i `Europe/Oslo`, uavhengig av klientens tidssone.
 * Datoer representeres som ISO-strenger (`yyyy-MM-dd`) — ikke `Date` — for å unngå
 * at tidssonen sniker seg inn via `Date`-konstruktøren.
 */

export const TIMEZONE = 'Europe/Oslo';

const MS_PER_DAY = 86_400_000;

/** Dagens dato i Oslo, som ISO-streng. */
export function todayIso(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}

/** Klokkeslettet i Oslo som minutter etter midnatt. */
export function nowMinutes(now: Date = new Date()): number {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: TIMEZONE,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(now);
  const [h, m] = parts.split(':').map(Number);
  return h * 60 + m;
}

export function addDays(dateIso: string, days: number): string {
  const d = new Date(`${dateIso}T00:00:00Z`);
  return new Date(d.getTime() + days * MS_PER_DAY).toISOString().slice(0, 10);
}

/**
 * Bro mot Aksels `DatePicker`, som er den ene komponenten vi ikke kan mate med
 * ISO-strenger. Konverteringen går via lokale datofelter, ikke `toISOString()`:
 * sistnevnte regner om til UTC og ville flyttet datoen én dag bakover for enhver
 * klient vest for Greenwich.
 */
export function isoToDate(dateIso: string): Date {
  const [y, m, d] = dateIso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function dateToIso(date: Date): string {
  const y = String(date.getFullYear()).padStart(4, '0');
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Ukedag der 1 = mandag og 7 = søndag, i tråd med regelsyntaksen. */
export function isoWeekday(dateIso: string): number {
  const dow = new Date(`${dateIso}T00:00:00Z`).getUTCDay();
  return dow === 0 ? 7 : dow;
}

export function firstOfMonth(month: string): string {
  return `${month}-01`;
}

export function lastOfMonth(month: string): string {
  const [y, m] = month.split('-').map(Number);
  const days = new Date(Date.UTC(y, m, 0)).getUTCDate();
  return `${month}-${String(days).padStart(2, '0')}`;
}

/** `"2026-05"` → `"2026-06"`. Håndterer årsskifte. */
export function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split('-').map(Number);
  const total = y * 12 + (m - 1) + delta;
  const year = Math.floor(total / 12);
  const monthIndex = total - year * 12;
  return `${year}-${String(monthIndex + 1).padStart(2, '0')}`;
}

export function monthOf(dateIso: string): string {
  return dateIso.slice(0, 7);
}

export function yearOf(dateIso: string): string {
  return dateIso.slice(0, 4);
}

/** De tolv månedene i et år, som `yyyy-MM`. */
export function monthsOfYear(year: string): string[] {
  return Array.from({ length: 12 }, (_, i) => `${year}-${String(i + 1).padStart(2, '0')}`);
}

/** Mandagen i uka som datoen ligger i. */
export function startOfWeek(dateIso: string): string {
  return addDays(dateIso, -(isoWeekday(dateIso) - 1));
}

export function shiftWeek(dateIso: string, delta: number): string {
  return addDays(dateIso, delta * 7);
}

/** De sju datoene i uka som starter på `monday`. */
export function weekDays(monday: string): string[] {
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i));
}

/**
 * ISO-8601 ukenummer. Uke 1 er uka som inneholder årets første torsdag,
 * så nyttårsuka kan tilhøre foregående år.
 */
export function isoWeekNumber(dateIso: string): number {
  const date = new Date(`${dateIso}T00:00:00Z`);
  // Flytt til torsdagen i samme uke: da bestemmer året til torsdagen ukenummeret.
  date.setUTCDate(date.getUTCDate() + 4 - (date.getUTCDay() || 7));
  const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
  return Math.ceil(((date.getTime() - yearStart.getTime()) / MS_PER_DAY + 1) / 7);
}

/** «Uke 34 · 18.–24. august 2025», eller med begge månedene når uka krysser et skille. */
export function formatWeek(monday: string): string {
  const sunday = addDays(monday, 6);
  const week = isoWeekNumber(monday);
  const day = (iso: string) => Number(iso.slice(8, 10));
  const year = sunday.slice(0, 4);

  if (monthOf(monday) === monthOf(sunday)) {
    return `Uke ${week} · ${day(monday)}.–${day(sunday)}. ${MONTH_NAMES[Number(monday.slice(5, 7)) - 1]} ${year}`;
  }
  const fromMonth = MONTH_NAMES[Number(monday.slice(5, 7)) - 1];
  const toMonth = MONTH_NAMES[Number(sunday.slice(5, 7)) - 1];
  return `Uke ${week} · ${day(monday)}. ${fromMonth}–${day(sunday)}. ${toMonth} ${year}`;
}

export interface GridDay {
  date: string;
  /** Dager fra forrige/neste måned vises dempet og uten status. */
  inMonth: boolean;
}

/**
 * Bygger rutenettet for en måned: hele uker, mandag først.
 * Alltid et helt antall uker, slik at rutenettet ikke endrer høyde mellom måneder.
 */
export function monthGrid(month: string): GridDay[] {
  const first = firstOfMonth(month);
  const last = lastOfMonth(month);
  const leading = isoWeekday(first) - 1;
  const trailing = 7 - isoWeekday(last);

  const days: GridDay[] = [];
  for (let i = leading; i > 0; i--) days.push({ date: addDays(first, -i), inMonth: false });
  for (let d = first; d <= last; d = addDays(d, 1)) days.push({ date: d, inMonth: true });
  for (let i = 1; i <= trailing; i++) days.push({ date: addDays(last, i), inMonth: false });
  return days;
}

const MONTH_NAMES = [
  'januar',
  'februar',
  'mars',
  'april',
  'mai',
  'juni',
  'juli',
  'august',
  'september',
  'oktober',
  'november',
  'desember',
];

export function formatMonth(month: string): string {
  const [y, m] = month.split('-').map(Number);
  const name = MONTH_NAMES[m - 1];
  return `${name.charAt(0).toUpperCase()}${name.slice(1)} ${y}`;
}

/** Bare månedsnavnet, med stor forbokstav — brukes i årsoversiktens minimåneder. */
export function formatMonthName(month: string): string {
  const name = MONTH_NAMES[Number(month.slice(5, 7)) - 1];
  return `${name.charAt(0).toUpperCase()}${name.slice(1)}`;
}

export function formatDateLong(dateIso: string): string {
  return new Intl.DateTimeFormat('nb-NO', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${dateIso}T00:00:00Z`));
}

export function weekdayName(dateIso: string): string {
  return new Intl.DateTimeFormat('nb-NO', { weekday: 'long', timeZone: 'UTC' }).format(
    new Date(`${dateIso}T00:00:00Z`),
  );
}

export function weekdayShort(dateIso: string): string {
  return new Intl.DateTimeFormat('nb-NO', { weekday: 'short', timeZone: 'UTC' }).format(
    new Date(`${dateIso}T00:00:00Z`),
  );
}

export const WEEKDAY_HEADERS = [
  'Mandag',
  'Tirsdag',
  'Onsdag',
  'Torsdag',
  'Fredag',
  'Lørdag',
  'Søndag',
];
