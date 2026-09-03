/**
 * Maskering av interne åpningstider.
 *
 * Regler med `onlyShowForNavEmployees` skal ikke vises til publikum. Backend har
 * ingen «fall tilbake på neste regel»-modus, så designets ønske kan ikke innfris
 * direkte. Pragmatisk løsning: BFF-en fjerner innholdet og markerer dagen som
 * `masked`. Klienten viser da «Intern åpningstid — logg inn som ansatt for å se»
 * i stedet for å avsløre tidene.
 *
 * **Hvilende i dag.** Hele appen ligger bak `ansatt.nav.no` med `autoLogin`, så
 * alle er innlogget og `maskResponse` slipper alt gjennom urørt. Koden står
 * fordi kalenderen etter planen skal åpnes for publikum — da er dette igjen den
 * eneste tingen som skiller interne tider fra offentlige. Se `PUBLIC_ACCESS` i
 * `config.ts`.
 */

interface MaskableDay {
  onlyShowForNavEmployees?: boolean;
  unstableOpeningHours?: boolean;
  matchedRule?: unknown;
  displayHeader?: string | null;
  displayText?: string | null;
  ruleName?: string | null;
  rule?: string | null;
  openingHours?: string | null;
  openingTime?: string;
  closingTime?: string;
  isOpen?: boolean;
  masked?: boolean;
}

function maskDay<T extends MaskableDay>(day: T): T {
  const masked: T = { ...day, masked: true };
  delete masked.matchedRule;
  masked.displayHeader = null;
  masked.displayText = null;
  if ('ruleName' in masked) masked.ruleName = null;
  if ('rule' in masked) masked.rule = null;
  if ('openingHours' in masked) masked.openingHours = null;
  // Sentinelen «00:00–00:00» er backendens «stengt». Vi bruker den bevisst i
  // stedet for null, slik at en klient som ikke kjenner `masked` degraderer trygt.
  if ('openingTime' in masked) masked.openingTime = '00:00';
  if ('closingTime' in masked) masked.closingTime = '00:00';
  if ('isOpen' in masked) masked.isOpen = false;
  // Ustabilitet er en egenskap ved den skjulte regelen. Beholdt den seg, ville
  // et «Ustabil»-merke røpet noe om en åpningstid vi nettopp har maskert bort.
  if ('unstableOpeningHours' in masked) masked.unstableOpeningHours = false;
  return masked;
}

/**
 * Maskerer interne dager i et vilkårlig svar fra backend.
 *
 * Svarene har ulik form — én dag, en liste med dager, eller et oppslag fra
 * tjeneste-id til dag — så vi går rekursivt gjennom og maskerer det som ser ut
 * som en dag.
 */
export function maskResponse(body: unknown, loggedIn: boolean): unknown {
  if (loggedIn || body === null || typeof body !== 'object') return body;

  if (Array.isArray(body)) {
    return body.map((item) => maskResponse(item, loggedIn));
  }

  const day = body as MaskableDay;
  if (day.onlyShowForNavEmployees === true) {
    return maskDay(day);
  }

  const entries = Object.entries(body);
  if (entries.some(([, value]) => value !== null && typeof value === 'object')) {
    return Object.fromEntries(entries.map(([key, value]) => [key, maskResponse(value, loggedIn)]));
  }

  return body;
}
