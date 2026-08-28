/**
 * Norske helligdager med navn.
 *
 * Backend (`NorwegianPublicHolidays`) beregner de samme datoene, men eksponerer kun
 * `redDay: boolean` i API-et — ingen navn. Designet krever navnet («Grunnlovsdagen · stengt»),
 * så vi speiler backendens algoritme her og bruker den *kun* til å sette navn på en dag
 * som backend allerede har flagget som rød.
 *
 * Kilden til sannhet for OM en dag er rød er fortsatt `QueryResponse.redDay`.
 * Denne modulen svarer bare på HVA dagen heter.
 *
 * Algoritmen er identisk med backendens (Meeus/Jones/Butcher). Holdes de to i utakt,
 * vil en dag være rød uten navn — det degraderer pent til «Rød dag».
 */

const MS_PER_DAY = 86_400_000;

function iso(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

function shift(dateIso: string, days: number): string {
  const d = new Date(`${dateIso}T00:00:00Z`);
  return new Date(d.getTime() + days * MS_PER_DAY).toISOString().slice(0, 10);
}

/** Anonymous Gregorian algorithm — identisk med backendens `easterSunday`. */
export function easterSunday(year: number): string {
  const a = year % 19;
  const b = Math.floor(year / 100);
  const c = year % 100;
  const d = Math.floor(b / 4);
  const e = b % 4;
  const f = Math.floor((b + 8) / 25);
  const g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4);
  const k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const month = Math.floor((h + l - 7 * m + 114) / 31);
  const day = ((h + l - 7 * m + 114) % 31) + 1;
  return iso(year, month, day);
}

const cache = new Map<number, Map<string, string>>();

function buildYear(year: number): Map<string, string> {
  const easter = easterSunday(year);
  return new Map<string, string>([
    [iso(year, 1, 1), 'Første nyttårsdag'],
    [iso(year, 5, 1), 'Arbeidernes dag'],
    [iso(year, 5, 17), 'Grunnlovsdagen'],
    [iso(year, 12, 25), 'Første juledag'],
    [iso(year, 12, 26), 'Andre juledag'],
    [shift(easter, -3), 'Skjærtorsdag'],
    [shift(easter, -2), 'Langfredag'],
    [easter, 'Første påskedag'],
    [shift(easter, 1), 'Andre påskedag'],
    [shift(easter, 39), 'Kristi himmelfartsdag'],
    [shift(easter, 49), 'Første pinsedag'],
    [shift(easter, 50), 'Andre pinsedag'],
  ]);
}

export function holidaysForYear(year: number): Map<string, string> {
  let cached = cache.get(year);
  if (!cached) {
    cached = buildYear(year);
    cache.set(year, cached);
  }
  return cached;
}

/** Navnet på helligdagen, eller `null` hvis datoen ikke er en offisiell helligdag. */
export function holidayName(dateIso: string): string | null {
  const year = Number(dateIso.slice(0, 4));
  if (!Number.isFinite(year)) return null;
  return holidaysForYear(year).get(dateIso) ?? null;
}
