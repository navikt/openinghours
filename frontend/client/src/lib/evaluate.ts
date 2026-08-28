/**
 * Evaluering av én regel mot én dato — speiler backendens `OpeningHoursEvaluator.kt`
 * (`matchesDate`, `matchesDayOfMonth`, `matchesWeekday`, `matchesNumberToken`).
 *
 * Backend har **ingen** endepunkt som evaluerer en enkelt regel, og regelskjemaets
 * forhåndsvisning trenger et svar mens du skriver. Derfor er logikken speilet her,
 * på samme måte som helligdagsalgoritmen i `holidays.ts`.
 *
 * To ting begrenser konsekvensen av at dette er en kopi:
 * 1. Forhåndsvisningen viser regelen **alene**. Hva brukeren faktisk ser avgjøres av
 *    rekkefølgen i gruppen, og den testes mot det ekte `query/group`-endepunktet.
 * 2. Panelet merkes som lokalt beregnet, så ingen tror det er et fasitsvar fra API-et.
 */

import { isoWeekday } from './date';
import { HOURS_ALWAYS_OPEN, HOURS_CLOSED, parseRule } from './rule';

export interface MatchResult {
  matches: boolean;
  /** Hvilket felt som avgjorde — brukes til å forklare hvorfor regelen ikke traff. */
  blockedBy: 'date' | 'dayOfMonth' | 'weekday' | null;
  /** Én setning på norsk som forklarer utfallet. */
  reason: string;
}

function matchesNumberToken(token: string, value: number): boolean {
  if (token.includes('-')) {
    const [lo, hi] = token.split('-').map(Number);
    if (Number.isNaN(lo) || Number.isNaN(hi)) return false;
    return value >= lo && value <= hi;
  }
  const parsed = Number(token);
  return Number.isInteger(parsed) && parsed === value;
}

function matchesDate(dateIso: string, part: string): boolean {
  if (part === '??.??.????') return true;
  const p = part.split('.');
  if (p.length !== 3) return false;

  const day = Number(dateIso.slice(8, 10));
  const month = Number(dateIso.slice(5, 7));

  if (p[2] === '????') {
    if (p[0] === '??') return Number(p[1]) === month;
    return Number(p[0]) === day && Number(p[1]) === month;
  }

  // Full dato: sammenlign normalisert, slik at «01.05.2026» og «1.5.2026» er like.
  const y = Number(p[2]);
  const m = Number(p[1]);
  const d = Number(p[0]);
  if (!Number.isInteger(y) || !Number.isInteger(m) || !Number.isInteger(d)) return false;
  return y === Number(dateIso.slice(0, 4)) && m === month && d === day;
}

function matchesDayOfMonth(dateIso: string, part: string): boolean {
  if (part === '?') return true;
  const year = Number(dateIso.slice(0, 4));
  const month = Number(dateIso.slice(5, 7));
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const expanded = part.replaceAll('L', String(lastDay));
  const day = Number(dateIso.slice(8, 10));
  return expanded.split(',').some((token) => matchesNumberToken(token, day));
}

function matchesWeekday(dateIso: string, part: string): boolean {
  if (part === '?') return true;
  const dow = isoWeekday(dateIso); // 1 = mandag … 7 = søndag
  return part.split(',').some((token) => matchesNumberToken(token, dow));
}

const WEEKDAY_NAMES = ['', 'mandag', 'tirsdag', 'onsdag', 'torsdag', 'fredag', 'lørdag', 'søndag'];

/**
 * Treffer regelen datoen?
 *
 * Merk at tidsfeltet ikke er med: det avgjør *når på dagen* det er åpent, ikke
 * *om* regelen gjelder. Det matcher backendens traversering, som velger regel
 * på dato og deretter leser klokkeslettene fra den.
 */
export function ruleMatchesDate(rule: string, dateIso: string): MatchResult {
  const parsed = parseRule(rule);
  if (!parsed) {
    return { matches: false, blockedBy: null, reason: 'Uttrykket kunne ikke tolkes.' };
  }

  if (!matchesDate(dateIso, parsed.date)) {
    return {
      matches: false,
      blockedBy: 'date',
      reason: 'Datofeltet utelukker denne datoen.',
    };
  }

  if (!matchesDayOfMonth(dateIso, parsed.dayOfMonth)) {
    return {
      matches: false,
      blockedBy: 'dayOfMonth',
      reason: `Feltet «dag i måneden» utelukker den ${Number(dateIso.slice(8, 10))}.`,
    };
  }

  if (!matchesWeekday(dateIso, parsed.weekday)) {
    return {
      matches: false,
      blockedBy: 'weekday',
      reason: `Ukedagsfeltet utelukker ${WEEKDAY_NAMES[isoWeekday(dateIso)]}.`,
    };
  }

  return { matches: true, blockedBy: null, reason: 'Regelen treffer denne datoen.' };
}

export interface RulePreview extends MatchResult {
  /** `null` når regelen ikke traff. */
  hours: { open: string; close: string } | null;
  allDay: boolean;
  closed: boolean;
}

/** Fullt forhåndsvisningsresultat: treffer regelen, og hva blir åpningstiden? */
export function previewRule(rule: string, dateIso: string): RulePreview {
  const match = ruleMatchesDate(rule, dateIso);
  const parsed = parseRule(rule);

  if (!match.matches || !parsed) {
    return { ...match, hours: null, allDay: false, closed: false };
  }

  const time = parsed.time;
  if (time === HOURS_CLOSED) {
    return { ...match, hours: null, allDay: false, closed: true };
  }
  if (time === HOURS_ALWAYS_OPEN) {
    return { ...match, hours: null, allDay: true, closed: false };
  }

  const [open, close] = time.split('-');
  return { ...match, hours: { open, close }, allDay: false, closed: false };
}
