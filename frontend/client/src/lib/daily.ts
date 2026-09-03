import type { DailyCacheResponse } from '../api/types';

/** `OpeningHoursEvaluator.DEFAULT_DISPLAY_DATA.ruleName` i backend. */
export const NO_RULES_SENTINEL = 'No Rules stated';

/**
 * Traff ingen regel?
 *
 * Periodeendepunktet sier dette rett ut med `warningMessage`, men dagcachen
 * gjør det ikke: der får en tjeneste uten gruppe — eller uten regel som treffer
 * — backendens `DEFAULT_DISPLAY_DATA`, som er *døgnåpent*. Uten sjekken her
 * ville nettopp de tjenestene som mangler oppsett stått som «åpent nå», og
 * problemet vært usynlig akkurat den dagen noen så etter det.
 *
 * Vi kjenner dem igjen på regelnavnet, som er en fast sentinel backend aldri
 * bruker til noe annet.
 */
export function hasNoRule(daily: Pick<DailyCacheResponse, 'openingHours' | 'ruleName'>): boolean {
  return daily.openingHours === null || daily.ruleName === NO_RULES_SENTINEL;
}
