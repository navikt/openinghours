/** Speiler backendens `no.nav.openinghours` API. Ett felt per felt i QueryResponse. */

export type ServiceType = 'TJENESTE' | 'KOMPONENT';

export interface Service {
  id: string;
  name: string;
  type: ServiceType;
  team: string;
  monitorlink: string | null;
  logglink: string | null;
  description: string | null;
  createdAt: string;
  updatedAt: string | null;
}

export interface MatchedRule {
  name: string;
  rule: string;
}

/**
 * Én dag med åpningstider.
 *
 * NB: backend har kun ETT intervall per dag (`openingTime`–`closingTime`).
 * Flere intervaller (lunsjpause) støttes ikke i datamodellen — se `toIntervals()`.
 */
export interface QueryResponse {
  resourceId: string;
  serviceName?: string;
  date: string; // ISO yyyy-MM-dd
  isOpen: boolean;
  openingTime: string; // HH:mm(:ss)
  closingTime: string;
  displayHeader: string | null;
  displayText: string | null;
  onlyShowForNavEmployees: boolean;
  /** Fagansvarlig har flagget perioden som ustabil. Åpningstiden gjelder, men kan svikte. */
  unstableOpeningHours: boolean;
  redDay: boolean;
  matchedRule?: MatchedRule;
  warningMessage?: string;
  /**
   * Satt av BFF-en når regelen kun er for Nav-ansatte og brukeren er uinnlogget.
   * Hvilende så lenge appen ligger bak ansatt.nav.no — se `PUBLIC_ACCESS` i BFF-en.
   */
  masked?: boolean;
}

export interface DailyCacheResponse {
  serviceId: string;
  serviceName: string;
  isOpen: boolean;
  openingHours: string | null;
  displayHeader: string | null;
  displayText: string | null;
  onlyShowForNavEmployees: boolean;
  unstableOpeningHours: boolean;
  redDay: boolean;
  ruleName: string | null;
  rule: string | null;
  masked?: boolean;
}

export interface OhGroup {
  id: string;
  name: string;
  ruleGroupIds: string[] | null;
}

export interface Rule {
  id: string;
  name: string;
  rule: string;
  header: string | null;
  text: string | null;
  onlyShowForNavEmployees: boolean;
  unstableOpeningHours: boolean;
  redDay: boolean;
  createdAt: string;
  /** `null` betyr at regelen aldri har blitt endret etter opprettelsen. */
  updatedAt: string | null;
}

export interface GroupAssociations {
  services: Service[];
  groups: OhGroup[];
}

export interface Session {
  loggedIn: boolean;
  name?: string;
}
