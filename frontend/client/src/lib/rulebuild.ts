/**
 * Toveis kobling mellom veiviserens fire felt og regeluttrykket.
 *
 * Veiviseren lar deg fylle ut felt på norsk («17.05», «siste», «man-fre») mens
 * uttrykket skrives for deg. Uttrykket er samtidig redigerbart, så konverteringen
 * må gå begge veier: `buildRule(fields)` skriver strengen, `toFields(rule)` fyller
 * feltene igjen. Kan uttrykket ikke uttrykkes med feltene, gir `toFields` `null`,
 * og skjemaet låser feltene til brukeren nullstiller dem.
 *
 * Syntaksen er dokumentert i `rule.ts`. Merk at designet skrev wildcards som `*`;
 * backend bruker `??.??.????` og `?`.
 */

import { HOURS_CLOSED, parseRule } from './rule';

export const DATE_ANY = '??.??.????';
export const ANY = '?';

export interface RuleFields {
  /** Slik brukeren skrev den: `17.05`, `17.05.2026`, `05` eller tom. */
  date: string;
  /** `1`, `15`, `siste`, `20-siste` eller tom. */
  dayOfMonth: string;
  /** En av `WEEKDAY_OPTIONS`-verdiene, eller et rått uttrykk. */
  weekday: string;
  /** `08:00-15:30` eller tom, som betyr stengt. */
  time: string;
}

export const EMPTY_FIELDS: RuleFields = { date: '', dayOfMonth: '', weekday: ANY, time: '' };

/**
 * Ukedagsvalgene fra designet, oversatt til backendens tallsyntaks
 * (1 = mandag … 7 = søndag). Avvik 2b — oversettelsen er tapsfri.
 */
export const WEEKDAY_OPTIONS = [
  { value: ANY, label: 'Alle' },
  { value: '1-5', label: 'Mandag–fredag' },
  { value: '1-4', label: 'Mandag–torsdag' },
  { value: '5', label: 'Fredag' },
  { value: '6,7', label: 'Lørdag–søndag' },
] as const;

function pad(value: string): string {
  return value.trim().padStart(2, '0');
}

/** `"17.05"` → `"17.05.????"`, `"05"` → `"??.05.????"`, tom → `"??.??.????"`. */
function buildDate(input: string): string {
  const raw = input.trim();
  if (raw === '') return DATE_ANY;

  const parts = raw.split('.').filter((p) => p !== '');
  if (parts.length === 1) return `??.${pad(parts[0])}.????`;
  if (parts.length === 2) return `${pad(parts[0])}.${pad(parts[1])}.????`;
  if (parts.length === 3) return `${pad(parts[0])}.${pad(parts[1])}.${parts[2].trim()}`;

  // Uforståelig — send den videre urørt, så valideringen får forklare hvorfor.
  return raw;
}

function buildDayOfMonth(input: string): string {
  const raw = input.trim();
  if (raw === '') return ANY;
  return raw.replace(/siste/gi, 'L').replace(/\s+/g, '');
}

function buildTime(input: string): string {
  const raw = input.trim();
  if (raw === '') return HOURS_CLOSED;
  return raw.replace(/\s*-\s*/, '-').replace(/\s+/g, '');
}

/** Setter sammen de fire feltene til ett regeluttrykk. */
export function buildRule(fields: RuleFields): string {
  const weekday = fields.weekday.trim() === '' ? ANY : fields.weekday.trim();
  return [
    buildDate(fields.date),
    buildDayOfMonth(fields.dayOfMonth),
    weekday,
    buildTime(fields.time),
  ].join(' ');
}

function fieldsFromDate(part: string): string | null {
  if (part === DATE_ANY) return '';
  const p = part.split('.');
  if (p.length !== 3) return null;
  if (p[2] === '????') {
    return p[0] === '??' ? p[1] : `${p[0]}.${p[1]}`;
  }
  return `${p[0]}.${p[1]}.${p[2]}`;
}

/**
 * Fyller veiviserfeltene fra et uttrykk. Returnerer `null` når uttrykket ikke lar
 * seg representere i feltene — da skal skjemaet vise «skrevet manuelt» framfor å
 * late som feltene stemmer.
 */
export function toFields(rule: string): RuleFields | null {
  const parsed = parseRule(rule);
  if (!parsed) return null;

  const date = fieldsFromDate(parsed.date);
  if (date === null) return null;

  return {
    date,
    dayOfMonth: parsed.dayOfMonth === ANY ? '' : parsed.dayOfMonth.replaceAll('L', 'siste'),
    weekday: parsed.weekday,
    time: parsed.time === HOURS_CLOSED ? '' : parsed.time,
  };
}

/** Er uttrykket og feltene fortsatt i takt? Brukes til å oppdage manuell redigering. */
export function fieldsMatchRule(fields: RuleFields, rule: string): boolean {
  return buildRule(fields).trim() === rule.trim();
}

export interface RulePattern {
  id: string;
  label: string;
  fields: RuleFields;
}

/**
 * De fem startmønstrene fra designet.
 *
 * Designets «arbeidsuke med lunsjpause» er tatt ut: backend støtter bare ett
 * tidsrom per regel (avvik 2), så en lunsjpause må uansett bli to regler i en
 * gruppe. Den er byttet ut med «Døgnåpent», som er et mønster backend faktisk
 * kan uttrykke, og som er vanlig for komponenter.
 */
export const RULE_PATTERNS: RulePattern[] = [
  {
    id: 'arbeidsuke',
    label: 'Ordinær arbeidsuke',
    fields: { date: '', dayOfMonth: '', weekday: '1-5', time: '08:00-15:30' },
  },
  {
    id: 'kortere-fredag',
    label: 'Kortere fredag',
    fields: { date: '', dayOfMonth: '', weekday: '5', time: '08:00-14:00' },
  },
  {
    id: 'stengt-helg',
    label: 'Stengt i helgen',
    fields: { date: '', dayOfMonth: '', weekday: '6,7', time: '' },
  },
  {
    id: 'stengt-dato',
    label: 'Stengt en bestemt dato',
    fields: { date: '17.05', dayOfMonth: '', weekday: ANY, time: '' },
  },
  {
    id: 'dognapent',
    label: 'Døgnåpent',
    fields: { date: '', dayOfMonth: '', weekday: ANY, time: '00:00-23:59' },
  },
];
