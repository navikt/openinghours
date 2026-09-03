/**
 * Avvik fra tjenestens normale timeplan.
 *
 * Premisset for hele forsiden: den som åpner siden kjenner allerede grovt til
 * åpningstidene for tjenestene sine. Det de leter etter er dagene som *bryter*
 * med den kjennskapen — tidlig stenging, ekstra åpning, en rød dag. En
 * visning som markerer «stengt» hver lørdag drukner nettopp de dagene i støy,
 * fordi lørdag stengt ikke er nyheter for noen.
 *
 * Modulen er ren: ingen DOM, ingen nettverk, ingen `Date.now()`. Normalplanen
 * utledes av de samme dagene som allerede er hentet for å tegne kalenderen.
 */

import type { QueryResponse } from '../api/types';
import { isoWeekday } from './date';
import { holidayName } from './holidays';
import { HOURS_ALWAYS_OPEN, HOURS_CLOSED, parseRule, trimSeconds } from './rule';
import { formatMinutes } from './status';

/**
 * Hva som skjedde en dag, som én sammenlignbar verdi.
 *
 * To dager er «like» når signaturene er like. Åpningstiden er med i signaturen,
 * så 08:00–15:30 og 08:00–12:00 er forskjellige dager selv om begge er «åpne».
 */
export type DaySignature =
  | { kind: 'closed' }
  | { kind: 'allDay' }
  | { kind: 'open'; from: number; to: number };

export function signatureOf(day: QueryResponse): DaySignature {
  /*
   * `warningMessage` gir ingen egen signatur. Traff ingen regel, svarer backend
   * med døgnåpent, og det er den avtalte betydningen — se `lib/status.ts`.
   * Dagen får dermed signaturen `allDay` fra klokkeslettene under, akkurat som
   * en tjeneste som er satt opp døgnåpent med vilje. Det er poenget: er
   * tjenesten uten regler hele måneden, er *hver* dag døgnåpen, normalen blir
   * døgnåpen, og kalenderen tier — slik den skal når ingenting avviker.
   */
  /*
   * Rød dag er ikke en egen signatur. En helligdag der tjenesten er stengt har
   * samme signatur som enhver annen stengt dag — det er sammenligningen med
   * normalplanen som avgjør om den er verdt å nevne, ikke flagget i seg selv.
   */
  const hours = `${trimSeconds(day.openingTime)}-${trimSeconds(day.closingTime)}`;
  if (hours === HOURS_ALWAYS_OPEN) return { kind: 'allDay' };
  if (hours === HOURS_CLOSED) return { kind: 'closed' };

  /*
   * Bevisst ikke `toIntervals`: den forkaster åpningstider som krysser midnatt
   * (`to <= from`) og gir tom liste. For tidsstreken er det en skjønnhetsfeil,
   * men her ville en tjeneste med 22:00–02:00 fått signaturen «stengt» — og da
   * ville en dag der den *faktisk* er stengt sett identisk ut, og en ekte
   * stenging aldri blitt meldt.
   */
  const from = toMinutes(day.openingTime);
  const to = toMinutes(day.closingTime);
  if (from === null || to === null || from === to) return { kind: 'closed' };
  return { kind: 'open', from, to };
}

function toMinutes(time: string): number | null {
  const t = trimSeconds(time);
  if (!t) return null;
  const [h, m] = t.split(':').map(Number);
  return h * 60 + m;
}

/**
 * Sluttidspunktet på en akse der det alltid ligger etter starten.
 *
 * 22:00–02:00 betyr klokken to *neste* døgn. Uten dette ville enhver
 * sammenligning av start og slutt gitt motsatt svar for slike åpningstider.
 */
function endOf(sig: { from: number; to: number }): number {
  return sig.to < sig.from ? sig.to + 1440 : sig.to;
}

export function signatureKey(sig: DaySignature): string {
  return sig.kind === 'open' ? `open:${sig.from}-${sig.to}` : sig.kind;
}

export function sameSignature(a: DaySignature, b: DaySignature): boolean {
  return signatureKey(a) === signatureKey(b);
}

/** «08:00–15:30», «stengt», «døgnåpent» — normalplanen skrevet ut. */
export function describeSignature(sig: DaySignature): string {
  switch (sig.kind) {
    case 'closed':
      return 'stengt';
    case 'allDay':
      return 'døgnåpent';
    case 'open':
      return `${formatMinutes(sig.from)}–${formatMinutes(sig.to)}`;
  }
}

/**
 * Er dette en regel som gjelder hver eneste uke, for alltid?
 *
 * Regel-DSL-en er `<dato> <dagIMåned> <ukedag> <tid>`. Er både dato og dag i
 * måneden jokere, gjentar regelen seg på faste ukedager uten ende — det er
 * nettopp den timeplanen brukeren har i hodet. Alt som er festet til en dato
 * (`24.12.????`) eller til en dag i måneden (`L` = siste) er per definisjon et
 * unntak fra den, uansett hva klokkeslettene skulle være.
 */
export function isBaselineRule(dsl: string): boolean {
  const parsed = parseRule(dsl);
  if (!parsed) return false;
  return parsed.date === '??.??.????' && parsed.dayOfMonth === '?';
}

/** Normalplanen for én tjeneste: ukedag (1 = mandag) → signatur. */
export type Baseline = Map<number, DaySignature>;

export interface ServiceBaseline {
  byWeekday: Baseline;
}

/**
 * Utleder normalplanen fra dagene vi allerede har hentet.
 *
 * To kilder, i prioritert rekkefølge:
 *
 * 1. **Grunnregelen.** Traff en ukesregel (`??.??.???? ?`) på en gitt ukedag,
 *    er det den timeplanen som gjelder når ingenting spesielt skjer. Én slik
 *    dag holder — regelen sier selv at den gjentar seg.
 * 2. **Det som skjer oftest.** Har ingen ukesregel truffet den ukedagen, er
 *    den hyppigste signaturen beste gjetning. Dette fanger tilfellet der en
 *    tjeneste ikke har regler for helg i det hele tatt: «ingen regel treffer»
 *    blir da normalen for lørdag, ikke et avvik som ropes ut hver uke.
 *
 * Er begge kilder tomme for en ukedag — typisk når vinduet er for kort —
 * finnes ingen normal å måle mot, og dagen får være i fred. Å gjette ville gitt
 * falske avvik, og et falskt avvik koster mer enn et uteglemt: det lærer
 * brukeren å ignorere markeringene.
 */
export function deriveBaseline(days: QueryResponse[], primaryMonth?: string): ServiceBaseline {
  const fromRule = new Map<number, DaySignature>();
  const tally = new Map<number, Map<string, { sig: DaySignature; count: number }>>();

  for (const day of days) {
    const weekday = isoWeekday(day.date);
    const sig = signatureOf(day);

    /*
     * Kun dager i måneden vi faktisk viser får definere normalen.
     *
     * Rutenettet drar med seg opptil tolv dager fra nabomånedene, og en
     * sesongregel som `??.07.???? ? 1-5 09:00-14:00` gjelder ikke der. Uten
     * denne avgrensningen ville mandag 30. juni satt normalen for alle julis
     * mandager til junitidene, mens tirsdag til fredag — som ikke har noen
     * junidag i rutenettet — fant julitidene selv. Samme tjeneste, samme
     * måned, og avvik kun på mandager, avgjort av hvor rutenettkanten falt.
     */
    const authoritative = !primaryMonth || day.date.startsWith(primaryMonth);
    if (
      authoritative &&
      day.matchedRule &&
      isBaselineRule(day.matchedRule.rule) &&
      !fromRule.has(weekday)
    ) {
      fromRule.set(weekday, sig);
    }

    const bucket = tally.get(weekday) ?? new Map();
    const key = signatureKey(sig);
    const seen = bucket.get(key);
    if (seen) seen.count += 1;
    else bucket.set(key, { sig, count: 1 });
    tally.set(weekday, bucket);
  }

  const byWeekday: Baseline = new Map();
  for (const [weekday, bucket] of tally) {
    const preferred = fromRule.get(weekday);
    if (preferred) {
      byWeekday.set(weekday, preferred);
      continue;
    }
    const winner = [...bucket.values()].sort((a, b) => b.count - a.count)[0];
    /*
     * Én observasjon er ingen normal. Skjer noe bare én gang i vinduet, er det
     * like sannsynlig at det *er* avviket vi leter etter.
     */
    if (winner && winner.count > 1) byWeekday.set(weekday, winner.sig);
  }

  return { byWeekday };
}

/**
 * Avvikstyper, sortert etter hvor mye de haster.
 *
 * `closed` først fordi en stengt dør er det som faktisk stopper noen; `extra`
 * sist fordi ekstra åpningstid sjelden ødelegger noens dag.
 */
export const DEVIATION_KINDS = ['closed', 'shorter', 'moved', 'longer', 'extra', 'unstable'] as const;

export type DeviationKind = (typeof DEVIATION_KINDS)[number];

export const DEVIATION_LABELS: Record<DeviationKind, string> = {
  closed: 'Stengt',
  shorter: 'Kortere åpent',
  moved: 'Endret åpningstid',
  longer: 'Lenger åpent',
  extra: 'Ekstra åpent',
  unstable: 'Ustabil',
};

export interface Deviation {
  kind: DeviationKind;
  /**
  * Åpningstiden denne dagen, f.eks. «Åpent 08:00–12:00».
  *
  * Setningen står på egne bein. Normalplanen er *filteret* som avgjør om dagen
  * er verdt å vise, men den nevnes ikke: den som leser vil vite om hun rekker
  * innom fredag, ikke hva regelen pleier å si.
  */
  summary: string;
  /** Ustabilitet er et tillegg til avviket, ikke et alternativ til det. */
  unstable: boolean;
  /** Navnet på helligdagen, når dagen er rød. */
  holiday: string | null;
}

/**
 * Skiller denne dagen seg fra normalen for tjenesten?
 *
 * `null` betyr «som vanlig» — og det er svaret vi håper på for de aller fleste
 * dager. Kjenner vi ikke normalen for ukedagen, svarer vi også `null`: uten et
 * sammenligningsgrunnlag finnes det ikke noe avvik å påstå.
 */
export function deviationOf(day: QueryResponse, baseline: Baseline): Deviation | null {
  const actual = signatureOf(day);
  const normal = baseline.get(isoWeekday(day.date));
  const unstable = day.unstableOpeningHours === true;
  const holiday = day.redDay ? holidayName(day.date) : null;

  if (!normal || sameSignature(actual, normal)) {
    /*
     * Ustabilitet er ikke en endring i åpningstiden, men i hvor mye den kan
     * stoles på. Den skal derfor fram selv om klokkeslettene er som vanlig.
     */
    if (unstable) {
      return {
        kind: 'unstable',
        summary: 'Ustabile åpningstider',
        unstable: true,
        holiday,
      };
    }
    return null;
  }

  const kind = classify(actual, normal);
  return {
    kind,
    summary: summarize(actual, holiday),
    unstable,
    holiday,
  };
}

function classify(actual: DaySignature, normal: DaySignature): DeviationKind {
  if (actual.kind === 'closed') return 'closed';
  if (normal.kind !== 'open' && normal.kind !== 'allDay') return 'extra';
  if (actual.kind === 'allDay') return 'longer';
  if (normal.kind === 'allDay') return 'shorter';

  const actualEnd = endOf(actual);
  const normalEnd = endOf(normal);
  const opensLater = actual.from > normal.from;
  const closesEarlier = actualEnd < normalEnd;
  const opensEarlier = actual.from < normal.from;
  const closesLater = actualEnd > normalEnd;

  if ((opensLater || closesEarlier) && !opensEarlier && !closesLater) return 'shorter';
  if ((opensEarlier || closesLater) && !opensLater && !closesEarlier) return 'longer';
  return 'moved';
}

/**
 * Setningen som står i kalendercellen.
 *
 * Den sier hva som gjelder denne dagen, ikke hva som er endret. «Stenger 12:00»
 * er kortere, men krever at leseren husker at normalen er 08:00–15:30 — og da
 * må hun regne ut svaret selv. «Åpent 08:00–12:00» er hele svaret med én gang.
 */
function summarize(actual: DaySignature, holiday: string | null): string {
  if (actual.kind === 'closed') return holiday ? `Stengt · ${holiday}` : 'Stengt hele dagen';
  if (actual.kind === 'allDay') return 'Døgnåpent';
  return `Åpent ${describeSignature(actual)}`;
}

/** Alle dagene vi har hentet for én tjeneste, i vinduet kalenderen viser. */
export interface ServiceDays {
  serviceId: string;
  serviceName: string;
  team: string;
  days: QueryResponse[];
}

export interface DeviationEntry {
  serviceId: string;
  serviceName: string;
  team: string;
  date: string;
  deviation: Deviation;
  day: QueryResponse;
}

export interface DeviationCalendar {
  /** Dato → avvikene den dagen, viktigste først. Datoer uten avvik mangler helt. */
  byDate: Map<string, DeviationEntry[]>;
  total: number;
}

/**
 * Bygger avvikskalenderen for hele vinduet.
 *
 * Normalplanen utledes per tjeneste fra tjenestens egne dager i det samme
 * vinduet. Det gjør visningen selvforsynt: den trenger ingen ekstra kall og
 * ingen kjennskap til reglene bak, som uinnloggede uansett ikke får se.
 *
 * `month` (`yyyy-MM`) skiller dagene som skal *vises* fra dagene som bare er
 * med for å gi normalen nok observasjoner. Utelates den, teller alle dagene.
 */
export function buildCalendar(services: ServiceDays[], month?: string): DeviationCalendar {
  const byDate = new Map<string, DeviationEntry[]>();
  let total = 0;

  for (const service of services) {
    const baseline = deriveBaseline(service.days, month);

    for (const day of service.days) {
      /*
       * Dagene fra nabomånedene er hentet for å utlede normalen, ikke for å
       * vises. Talte vi dem med, ville «47 avvik denne måneden» inkludert et
       * nyttårsaften som ikke står i en eneste celle.
       */
      if (month && !day.date.startsWith(month)) continue;
      const deviation = deviationOf(day, baseline.byWeekday);
      if (!deviation) continue;
      const list = byDate.get(day.date) ?? [];
      list.push({
        serviceId: service.serviceId,
        serviceName: service.serviceName,
        team: service.team,
        date: day.date,
        deviation,
        day,
      });
      byDate.set(day.date, list);
      total += 1;
    }
  }

  for (const list of byDate.values()) list.sort(compareEntries);
  return { byDate, total };
}

export function compareEntries(a: DeviationEntry, b: DeviationEntry): number {
  const order =
    DEVIATION_KINDS.indexOf(a.deviation.kind) - DEVIATION_KINDS.indexOf(b.deviation.kind);
  if (order !== 0) return order;
  return a.serviceName.localeCompare(b.serviceName, 'nb');
}

/**
 * Avvikene som kommer, i kronologisk rekkefølge.
 *
 * Kalenderen svarer på «hva skjer i mai?». Denne svarer på «hva er det neste
 * som skjer?» — spørsmålet folk faktisk kom for å få svar på.
 */
export function upcoming(
  calendar: DeviationCalendar,
  fromDate: string,
  limit = 8,
): DeviationEntry[] {
  return [...calendar.byDate.entries()]
    .filter(([date]) => date >= fromDate)
    .sort(([a], [b]) => a.localeCompare(b))
    .flatMap(([, entries]) => entries)
    .slice(0, limit);
}
