/**
 * Avvik i oppsettet — «er noe galt?» før «hva vil du endre?».
 *
 * Alt her er ren funksjon av data vi allerede henter, slik at det kan testes uten
 * DOM og uten nettverk. Merk at gruppene bærer hele koblingsgrafen selv:
 * `ruleGroupIds` peker på både regler og undergrupper, så «brukes denne regelen?»
 * og «brukes denne gruppen?» kan avgjøres uten et eneste ekstra kall.
 *
 * Den eneste koblingen som ikke ligger i gruppene er tjeneste → gruppe. Den må
 * hentes særskilt, og sendes inn som `linkedServiceIds`.
 */

import type { OhGroup, Rule, Service } from '../api/types';
import { validateRule } from './validate';

export type Severity = 'warning' | 'info';

export interface Issue {
  id: string;
  severity: Severity;
  title: string;
  description: string;
  href: string;
  actionLabel: string;
}

export interface HealthInput {
  services: Service[];
  groups: OhGroup[];
  rules: Rule[];
  /** Tjenester som er koblet til en gruppe. Alt utenfor settet mangler kobling. */
  linkedServiceIds: Set<string>;
}

/** Alle id-er som er nevnt som medlem i en gruppe — regel eller undergruppe. */
function referencedIds(groups: OhGroup[]): Set<string> {
  const seen = new Set<string>();
  for (const group of groups) {
    for (const id of group.ruleGroupIds ?? []) seen.add(id);
  }
  return seen;
}

export function unusedRules(groups: OhGroup[], rules: Rule[]): Rule[] {
  const used = referencedIds(groups);
  return rules.filter((rule) => !used.has(rule.id));
}

/** En gruppe er i bruk hvis en tjeneste peker på den, eller en annen gruppe gjør det. */
export function unusedGroups(groups: OhGroup[], linkedGroupIds: Set<string>): OhGroup[] {
  const used = referencedIds(groups);
  return groups.filter((group) => !used.has(group.id) && !linkedGroupIds.has(group.id));
}

export function emptyGroups(groups: OhGroup[]): OhGroup[] {
  return groups.filter((group) => (group.ruleGroupIds ?? []).length === 0);
}

/**
 * Regler backend en gang godtok, men som validatoren nå avviser. Sjeldent, men
 * verdt å vise: en regel som ikke lar seg tolke treffer aldri, og feilen er
 * usynlig i kalenderen fordi neste regel bare overtar.
 */
export function invalidRules(rules: Rule[]): Rule[] {
  return rules.filter((rule) => validateRule(rule.rule) !== null);
}

function plural(n: number, one: string, many: string): string {
  return n === 1 ? one : many;
}

export function findIssues({ services, groups, rules, linkedServiceIds }: HealthInput): Issue[] {
  const issues: Issue[] = [];

  const unlinked = services.filter((service) => !linkedServiceIds.has(service.id));
  if (unlinked.length > 0) {
    issues.push({
      id: 'tjenester-uten-gruppe',
      severity: 'warning',
      title: `${unlinked.length} ${plural(unlinked.length, 'tjeneste mangler', 'tjenester mangler')} åpningstidsgruppe`,
      description: `${unlinked
        .slice(0, 3)
        .map((s) => s.name)
        .join(', ')}${unlinked.length > 3 ? ' med flere' : ''}. Uten gruppe har tjenesten ingen åpningstider, og kalenderen står tom.`,
      href: '/admin/tjenester',
      actionLabel: 'Koble til gruppe',
    });
  }

  const broken = invalidRules(rules);
  if (broken.length > 0) {
    issues.push({
      id: 'ugyldige-regler',
      severity: 'warning',
      title: `${broken.length} ${plural(broken.length, 'regel har', 'regler har')} et uttrykk som ikke kan tolkes`,
      description: `${broken.map((r) => r.name).join(', ')}. En regel som ikke kan tolkes treffer aldri, og neste regel i gruppen overtar i stillhet.`,
      href: '/admin/regler',
      actionLabel: 'Rett opp regelen',
    });
  }

  const empty = emptyGroups(groups);
  if (empty.length > 0) {
    issues.push({
      id: 'tomme-grupper',
      severity: 'warning',
      title: `${empty.length} ${plural(empty.length, 'gruppe er', 'grupper er')} tom`,
      description: `${empty.map((g) => g.name).join(', ')}. Tjenester som bruker en tom gruppe får ingen åpningstider.`,
      href: '/admin/grupper',
      actionLabel: 'Legg til regler',
    });
  }

  const unused = unusedRules(groups, rules);
  if (unused.length > 0) {
    issues.push({
      id: 'ubrukte-regler',
      severity: 'info',
      title: `${unused.length} ${plural(unused.length, 'regel er', 'regler er')} ikke i bruk`,
      description:
        'Regelen ligger ikke i noen gruppe. Endrer du den, skjer det ingenting for brukerne.',
      href: '/admin/regler?flagg=ubrukt',
      actionLabel: 'Se reglene',
    });
  }

  return issues;
}

/** Kort oppsummering øverst — ett varsel framfor fire. */
export function summarize(issues: Issue[]): string | null {
  const warnings = issues.filter((i) => i.severity === 'warning');
  if (warnings.length === 0) return null;
  return `${warnings.map((i) => i.title).join(', og ')}.`;
}
