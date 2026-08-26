/**
 * Regeluttrykk — parsing og norsk formulering.
 *
 * Faktisk syntaks fra backendens `RuleValidator`/`OpeningHoursEvaluator`:
 *
 *     <dato>  <dagIMåned>  <ukedag>  <tid>
 *
 * - `dato`       `??.??.????` (alle) | `dd.MM.????` | `??.MM.????` | `dd.MM.yyyy`
 * - `dagIMåned`  `?` (alle) | `L` (siste dag) | `5` | `1-15` | `1,8,20-L`
 * - `ukedag`     `?` (alle) | tall 1–7 der 1 = mandag | `1-5` | `1-4,6`
 * - `tid`        `HH:mm-HH:mm` — **kun ett intervall**
 *
 * Merk at designforslaget bruker `*` og `man-tor`. Det er ikke gyldig syntaks;
 * denne modulen er sannheten, og all visning av regler går gjennom `formatRule`.
 */

export const HOURS_ALWAYS_OPEN = '00:00-23:59';
export const HOURS_CLOSED = '00:00-00:00';

const WEEKDAYS = ['', 'mandag', 'tirsdag', 'onsdag', 'torsdag', 'fredag', 'lørdag', 'søndag'];
const MONTHS = [
  '',
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

export interface ParsedRule {
  date: string;
  dayOfMonth: string;
  weekday: string;
  time: string;
}

export function parseRule(rule: string): ParsedRule | null {
  const parts = rule.trim().split(/\s+/);
  if (parts.length !== 4) return null;
  return { date: parts[0], dayOfMonth: parts[1], weekday: parts[2], time: parts[3] };
}

/** `"08:00-15:30"` → `{ open: "08:00", close: "15:30" }`, eller `null` ved ugyldig format. */
export function parseHoursRange(hours: string): { open: string; close: string } | null {
  const normalized = hours.trim().replace(/\s*-\s*/, '-');
  const parts = normalized.split('-');
  if (parts.length !== 2) return null;
  const [open, close] = parts.map(trimSeconds);
  if (!open || !close) return null;
  return { open, close };
}

/** Backend sender av og til `HH:mm:ss`. Vi viser alltid `HH:mm`. */
export function trimSeconds(time: string): string {
  const m = /^(\d{1,2}):(\d{2})/.exec(time.trim());
  if (!m) return '';
  return `${m[1].padStart(2, '0')}:${m[2]}`;
}

function formatNumericTokens(part: string, label: (n: number) => string): string {
  return part
    .split(',')
    .map((token) => {
      const [lo, hi] = token.split('-');
      if (hi === undefined) return label(Number(lo));
      if (hi === 'L') return `${lo}. til siste dag i måneden`;
      return `${label(Number(lo))}–${label(Number(hi))}`;
    })
    .join(', ');
}

function formatDatePart(part: string): string | null {
  if (part === '??.??.????') return null;
  const [dd, mm, yyyy] = part.split('.');
  if (dd === '??' && yyyy === '????') return `hver ${MONTHS[Number(mm)] ?? mm}`;
  if (yyyy === '????') return `${Number(dd)}. ${MONTHS[Number(mm)] ?? mm} hvert år`;
  return `${Number(dd)}. ${MONTHS[Number(mm)] ?? mm} ${yyyy}`;
}

function formatDayOfMonthPart(part: string): string | null {
  if (part === '?') return null;
  if (part === 'L') return 'siste dag i måneden';
  return `dag ${formatNumericTokens(part, (n) => String(n))} i måneden`;
}

function formatWeekdayPart(part: string): string | null {
  if (part === '?') return null;
  return formatNumericTokens(part, (n) => WEEKDAYS[n] ?? String(n));
}

export function formatHours(time: string): string {
  const normalized = time.trim().replace(/\s*-\s*/, '-');
  if (normalized === HOURS_ALWAYS_OPEN) return 'døgnåpent';
  if (normalized === HOURS_CLOSED) return 'stengt';
  const range = parseHoursRange(normalized);
  return range ? `${range.open}–${range.close}` : normalized;
}

/**
 * Gjør et regeluttrykk om til en setning på norsk, f.eks.
 * `"??.??.???? ? 1-5 08:00-15:30"` → `«Mandag–fredag, 08:00–15:30»`.
 *
 * Returnerer det rå uttrykket hvis det ikke lar seg parse — vi skjuler aldri data.
 */
export function formatRule(rule: string): string {
  const parsed = parseRule(rule);
  if (!parsed) return rule;

  const scope = [
    formatDatePart(parsed.date),
    formatDayOfMonthPart(parsed.dayOfMonth),
    formatWeekdayPart(parsed.weekday),
  ].filter((s): s is string => s !== null);

  const when = scope.length > 0 ? scope.join(', ') : 'alle dager';
  const sentence = `${when}, ${formatHours(parsed.time)}`;
  return sentence.charAt(0).toUpperCase() + sentence.slice(1);
}
