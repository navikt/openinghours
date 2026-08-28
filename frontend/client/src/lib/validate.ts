/**
 * Validering av regeluttrykk — speiler backendens `RuleValidator.kt`.
 *
 * Backend svarer kun «Invalid rule format» (`RuleService.kt`), som ikke forteller
 * brukeren hva som er galt. Derfor validerer vi i klienten med konkrete meldinger.
 * Backend er fortsatt siste port: alt vi slipper gjennom blir validert der igjen.
 *
 * Reglene under er kopiert felt for felt fra `RuleValidator.kt`. Endres den, må
 * denne følge etter — testene dokumenterer grensetilfellene.
 */

import { parseRule } from './rule';

export type RuleField = 'date' | 'dayOfMonth' | 'weekday' | 'time';

export interface ValidationError {
  field: RuleField | null;
  message: string;
}

const DATE_ANY = '??.??.????';

function isValidDate(part: string): ValidationError | null {
  if (part === DATE_ANY) return null;

  const parts = part.split('.');
  if (parts.length !== 3) {
    return { field: 'date', message: 'Datoen må skrives som 17.05.2026, 17.05 eller ??.05.' };
  }

  const [dd, mm, yyyy] = parts;

  if (yyyy === '????') {
    if (dd === '??') {
      return /^(0?[1-9]|1[0-2])$/.test(mm)
        ? null
        : { field: 'date', message: 'Måneden må være et tall mellom 1 og 12.' };
    }
    return /^(0?[1-9]|[12][0-9]|3[01])\.(0?[1-9]|1[0-2])$/.test(`${dd}.${mm}`)
      ? null
      : { field: 'date', message: 'Datoen må skrives som 17.05 — dag mellom 1 og 31, måned mellom 1 og 12.' };
  }

  if (!/^\d{2}\.\d{2}\.\d{4}$/.test(part)) {
    return { field: 'date', message: 'Datoen må skrives som 17.05.2026, med to siffer på dag og måned.' };
  }

  const day = Number(dd);
  const month = Number(mm);
  const year = Number(yyyy);
  if (month < 1 || month > 12) {
    return { field: 'date', message: 'Måneden må være et tall mellom 1 og 12.' };
  }
  if (day < 1 || day > daysInMonth(year, month)) {
    return {
      field: 'date',
      message: `${monthName(month)} ${year} har ikke ${day} dager. Velg en dato som finnes.`,
    };
  }
  return null;
}

function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

const MONTH_NAMES = [
  '',
  'Januar',
  'Februar',
  'Mars',
  'April',
  'Mai',
  'Juni',
  'Juli',
  'August',
  'September',
  'Oktober',
  'November',
  'Desember',
];

function monthName(month: number): string {
  return MONTH_NAMES[month] ?? String(month);
}

function isValidDayOfMonth(part: string): ValidationError | null {
  if (part === '?' || part === 'L') return null;

  const tokens = part.split(',');
  const invalid = {
    field: 'dayOfMonth' as const,
    message: 'Dag i måneden må være tall mellom 1 og 31, i stigende rekkefølge — for eksempel 1, 15 eller 20-L.',
  };

  let previousMax = 0;
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];

    if (token === 'L') {
      // «L» betyr siste dag i måneden og gir ikke mening før andre tall.
      if (i !== tokens.length - 1) {
        return { field: 'dayOfMonth', message: 'L må stå sist, siden det betyr siste dag i måneden.' };
      }
      continue;
    }

    const rangeParts = token.split('-');
    if (rangeParts.length === 1) {
      const value = toInt(rangeParts[0]);
      if (value === null || value < 1 || value > 31 || value <= previousMax) return invalid;
      previousMax = value;
    } else if (rangeParts.length === 2) {
      const lower = toInt(rangeParts[0]);
      if (lower === null || lower < 1 || lower > 31 || lower <= previousMax) return invalid;

      if (rangeParts[1] === 'L') {
        if (i !== tokens.length - 1) {
          return { field: 'dayOfMonth', message: 'L må stå sist, siden det betyr siste dag i måneden.' };
        }
        previousMax = 31;
      } else {
        const upper = toInt(rangeParts[1]);
        if (upper === null || upper < 1 || upper > 31 || lower > upper) return invalid;
        previousMax = upper;
      }
    } else {
      return invalid;
    }
  }
  return null;
}

function isValidWeekday(part: string): ValidationError | null {
  if (part === '?') return null;

  const tokens = part.split(',');
  const invalid = {
    field: 'weekday' as const,
    message:
      'Ukedag må være tall mellom 1 og 7, der 1 er mandag, i stigende rekkefølge — for eksempel 1-5 eller 6,7.',
  };
  if (tokens.some((t) => t.trim() === '')) return invalid;

  let previousMax = 0;
  for (const token of tokens) {
    const rangeParts = token.split('-');
    if (rangeParts.length === 1) {
      const value = toInt(rangeParts[0]);
      if (value === null || value < 1 || value > 7 || value <= previousMax) return invalid;
      previousMax = value;
    } else if (rangeParts.length === 2) {
      const lo = toInt(rangeParts[0]);
      const hi = toInt(rangeParts[1]);
      if (lo === null || hi === null) return invalid;
      if (lo < 1 || lo > 7 || hi < 1 || hi > 7) return invalid;
      if (lo > hi || lo <= previousMax) return invalid;
      previousMax = hi;
    } else {
      return invalid;
    }
  }
  return null;
}

const TIME_PATTERN = /^([0-9]|0[0-9]|1[0-9]|2[0-3]):([0-9]|[0-5][0-9])-([0-9]|0[0-9]|1[0-9]|2[0-3]):([0-9]|[0-5][0-9])$/;

function isValidTime(part: string): ValidationError | null {
  // Designet foreslår flere intervaller adskilt med komma. Backend støtter det ikke,
  // så vi sier det rett ut i stedet for å la kallet feile.
  if (part.includes(',')) {
    return {
      field: 'time',
      message: 'Bare ett tidsrom per regel. Lag en regel til for det andre tidsrommet.',
    };
  }
  if (!TIME_PATTERN.test(part)) {
    return { field: 'time', message: 'Klokkeslettet må skrives som 08:00-15:30.' };
  }

  const [open, close] = part.split('-');
  if (toMinutes(close) < toMinutes(open)) {
    return { field: 'time', message: 'Sluttidspunktet må være etter starttidspunktet.' };
  }
  return null;
}

function toMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

function toInt(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  return Number(value);
}

/**
 * Validerer et helt regeluttrykk. Returnerer `null` når uttrykket er gyldig,
 * ellers den **første** feilen — én konkret melding er mer til hjelp enn en liste.
 */
export function validateRule(rule: string): ValidationError | null {
  const trimmed = rule.trim();
  if (trimmed === '') {
    return { field: null, message: 'Regeluttrykket kan ikke være tomt.' };
  }

  const parts = trimmed.split(/\s+/);
  if (parts.length !== 4) {
    return {
      field: null,
      message:
        'Uttrykket må ha fire deler adskilt med mellomrom: dato, dag i måneden, ukedag og klokkeslett.',
    };
  }

  const parsed = parseRule(trimmed);
  if (!parsed) {
    return { field: null, message: 'Uttrykket kunne ikke tolkes.' };
  }

  // Rekkefølgen følger feltene i skjemaet, slik at feilen peker på det første
  // feltet brukeren kan rette.
  return (
    isValidDate(parsed.date) ??
    isValidDayOfMonth(parsed.dayOfMonth) ??
    isValidWeekday(parsed.weekday) ??
    isValidTime(parsed.time)
  );
}

export function isValidRule(rule: string): boolean {
  return validateRule(rule) === null;
}
