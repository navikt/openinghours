/**
 * Samlet status for mange tjenester på én dato.
 *
 * Landingssiden og dagsvisningen svarer på det samme spørsmålet — «hvordan står
 * det til med driften?» — bare på hvert sitt detaljnivå. Bøttene, rekkefølgen og
 * opptellingen bor derfor her, som rene funksjoner uten DOM og uten nettverk.
 *
 * Merk at bøttene er *gjensidig utelukkende*, i motsetning til `DayStatus`, der
 * ustabilitet er et tillegg til statusen. En tjeneste som er åpen og ustabil
 * telles kun som ustabil: det er avviket som skal fanges opp, ikke normalen.
 */

import type { DailyCacheResponse, QueryResponse } from '../api/types';
import type { DayStatus } from './status';
import { deriveStatus, formatMinutes } from './status';
import { HOURS_CLOSED, parseHoursRange } from './rule';

/** Rekkefølgen er prioritet: det som krever oppmerksomhet først. */
export const BUCKETS = ['missing', 'unstable', 'closed', 'open'] as const;

export type Bucket = (typeof BUCKETS)[number];

export const BUCKET_LABELS: Record<Bucket, { one: string; many: string }> = {
  missing: { one: 'uten åpningstider', many: 'uten åpningstider' },
  unstable: { one: 'ustabil', many: 'ustabile' },
  closed: { one: 'stengt', many: 'stengte' },
  open: { one: 'åpen', many: 'åpne' },
};

export interface ServiceDay {
  serviceId: string;
  serviceName: string;
  team: string;
  /** `null` når kallet for tjenesten feilet — da vet vi ikke noe om dagen. */
  day: QueryResponse | null;
}

export interface DayEntry extends ServiceDay {
  bucket: Bucket;
  status: DayStatus | null;
}

export type DayCounts = Record<Bucket, number>;

export interface DaySummary {
  date: string;
  counts: DayCounts;
  total: number;
  /** Dagen er rød for et flertall av tjenestene — brukes til «Rød dag»-merket. */
  redDay: boolean;
  /** Navnet på helligdagen, når dagen er rød. */
  holiday: string | null;
  entries: DayEntry[];
}

/**
 * Hvilken bøtte en dag havner i.
 *
 * En tjeneste vi ikke har svar for behandles som «uten åpningstider»: for den som
 * leser siden er «vi vet ikke» og «ingen regel treffer» samme problem.
 *
 * `nowMinutes` skiller «åpen i dag» fra «åpen nå». Uten den ville en tjeneste med
 * åpningstid 08:00–15:30 blitt talt som åpen klokken 20:00, og forsidens
 * «41 av 47 tjenester er åpne nå» vært direkte feil hver kveld. Sendes den ikke
 * inn — som for en framtidig dato, der det ikke finnes noe «nå» — teller dagen
 * som åpen hvis den er åpen på et tidspunkt.
 */
export function bucketOf(status: DayStatus | null, nowMinutes?: number): Bucket {
  if (!status) return 'missing';
  if (status.kind === 'warning') return 'missing';
  if (status.unstable) return 'unstable';
  if (status.kind !== 'open') return 'closed';
  return nowMinutes === undefined || isOpenAt(status, nowMinutes) ? 'open' : 'closed';
}

/** Er tjenesten åpen på dette klokkeslettet? Døgnåpent er alltid åpent. */
export function isOpenAt(status: DayStatus, minutes: number): boolean {
  if (status.allDay) return true;
  return status.intervals.some((i) => minutes >= i.from && minutes < i.to);
}

export function emptyCounts(): DayCounts {
  return { missing: 0, unstable: 0, closed: 0, open: 0 };
}

export function toEntry(item: ServiceDay, nowMinutes?: number): DayEntry {
  const status = item.day ? deriveStatus(item.day) : null;
  return { ...item, status, bucket: bucketOf(status, nowMinutes) };
}

/**
 * Sortering for dagsvisningen: avvik øverst, deretter alfabetisk.
 *
 * Alfabetisk alene ville begravd de to tjenestene uten oppsett midt i en liste
 * på 47 — og det er nettopp de som gjør at noen åpner siden.
 */
export function sortEntries(entries: DayEntry[]): DayEntry[] {
  return [...entries].sort((a, b) => {
    const order = BUCKETS.indexOf(a.bucket) - BUCKETS.indexOf(b.bucket);
    if (order !== 0) return order;
    return a.serviceName.localeCompare(b.serviceName, 'nb');
  });
}

export function summarize(date: string, items: ServiceDay[], nowMinutes?: number): DaySummary {
  const entries = sortEntries(items.map((item) => toEntry(item, nowMinutes)));
  const counts = emptyCounts();
  for (const entry of entries) counts[entry.bucket] += 1;

  const known = entries.filter((e) => e.day !== null);
  const red = known.filter((e) => e.day?.redDay === true);
  const redDay = known.length > 0 && red.length > known.length / 2;

  return {
    date,
    counts,
    total: entries.length,
    redDay,
    holiday: redDay ? (red.find((e) => e.status?.holiday)?.status?.holiday ?? null) : null,
    entries,
  };
}

/** «41 åpne», «1 ustabil» — riktig entall/flertall uten at hvert kallsted gjentar det. */
export function countLabel(bucket: Bucket, count: number): string {
  const { one, many } = BUCKET_LABELS[bucket];
  return `${count} ${count === 1 ? one : many}`;
}

/** Bøttene som faktisk forekommer, i prioritert rekkefølge. Tomme bøtter nevnes ikke. */
export function presentBuckets(counts: DayCounts): Bucket[] {
  return BUCKETS.filter((bucket) => counts[bucket] > 0);
}

/**
 * Kort oppsummering av dagen, f.eks. «41 av 47 tjenester er åpne nå».
 *
 * `nowText` skiller i dag fra andre dager: for en framtidig dato finnes det ikke
 * noe «nå», og da ville formuleringen vært direkte misvisende.
 */
export function headline(counts: DayCounts, total: number, isToday: boolean): string {
  const open = counts.open;
  return isToday
    ? `${open} av ${total} tjenester er åpne nå`
    : `${open} av ${total} tjenester er åpne`;
}

/**
 * Tjenestene som krever oppmerksomhet, med en setning hver.
 *
 * Åpne tjenester er utelatt med vilje — de er normalen, og en liste over normalen
 * skjuler avvikene den er ment å løfte fram.
 */
export interface Attention {
  serviceId: string;
  serviceName: string;
  bucket: Bucket;
  /** Setningen etter tjenestenavnet, f.eks. «mangler åpningstider». */
  detail: string;
}

export function attentionItems(entries: DayEntry[], limit = 5, nowMinutes?: number): Attention[] {
  return entries
    .filter((entry) => entry.bucket !== 'open')
    .slice(0, limit)
    .map((entry) => ({
      serviceId: entry.serviceId,
      serviceName: entry.serviceName,
      bucket: entry.bucket,
      detail: attentionDetail(entry, nowMinutes),
    }));
}

function attentionDetail(entry: DayEntry, nowMinutes?: number): string {
  const status = entry.status;
  if (!status || status.kind === 'warning') return 'mangler åpningstider';
  if (status.unstable) {
    return status.kind === 'open' && !status.allDay
      ? `er ustabil ${status.label.replace('Åpen ', '')}`
      : 'er merket som ustabil';
  }
  if (status.kind === 'redDay') return status.holiday ? `stengt · ${status.holiday}` : 'stengt · rød dag';
  // Åpen i dag, men ikke akkurat nå: klokkeslettet er hele poenget med linjen.
  if (status.kind === 'open' && nowMinutes !== undefined && status.intervals.length > 0) {
    const first = status.intervals[0];
    const last = status.intervals[status.intervals.length - 1];
    return nowMinutes < first.from
      ? `åpner ${formatMinutes(first.from)}`
      : `stengte ${formatMinutes(last.to)}`;
  }
  return status.detail === 'Helg' ? 'stengt · helg' : 'stengt hele dagen';
}

/**
 * Dagens cache til samme form som resten av appen leser.
 *
 * `/daily` er ett kall for alle tjenester og er derfor den billigste kilden til
 * dagens status, men den svarer med sin egen form. Å oversette her framfor å la
 * hver visning kjenne begge formene holder statuslogikken på ett sted.
 */
export function dailyToQuery(daily: DailyCacheResponse, date: string): QueryResponse {
  const missing = hasNoRule(daily);
  const hours = parseHoursRange(daily.openingHours ?? HOURS_CLOSED);
  return {
    resourceId: daily.serviceId,
    serviceName: daily.serviceName,
    date,
    isOpen: daily.isOpen,
    openingTime: hours?.open ?? '00:00',
    closingTime: hours?.close ?? '00:00',
    displayHeader: daily.displayHeader,
    displayText: daily.displayText,
    onlyShowForNavEmployees: daily.onlyShowForNavEmployees,
    unstableOpeningHours: daily.unstableOpeningHours === true,
    redDay: daily.redDay,
    matchedRule:
      !missing && daily.ruleName && daily.rule ? { name: daily.ruleName, rule: daily.rule } : undefined,
    warningMessage: missing ? 'Ingen regel treffer datoen' : undefined,
    masked: daily.masked,
  };
}

/**
 * Traff ingen regel?
 *
 * Periodeendepunktet sier dette rett ut med `warningMessage`, men dagcachen gjør
 * det ikke: der får en tjeneste uten gruppe — eller uten regel som treffer —
 * backendens `DEFAULT_DISPLAY_DATA`, som er *døgnåpent*. Uten sjekken her ville
 * nettopp de tjenestene som mangler oppsett sett helt friske ut i dag, og først
 * dukket opp som avvik i morgen. Vi kjenner dem igjen på regelnavnet, som er en
 * fast sentinel backend aldri bruker til noe annet.
 */
function hasNoRule(daily: DailyCacheResponse): boolean {
  return daily.openingHours === null || daily.ruleName === NO_RULES_SENTINEL;
}

/** `OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA.ruleName` i backend. */
const NO_RULES_SENTINEL = 'No Rules stated';
