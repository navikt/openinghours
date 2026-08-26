/**
 * Gruppetreet: struktur, prioritet og flytting.
 *
 * En gruppes `ruleGroupIds` er en flat liste av id-er som peker på **enten** en
 * regel eller en undergruppe. Backend skiller ikke på type i listen, så vi slår
 * opp mot begge registrene for å avgjøre hva hver id er.
 *
 * Rekkefølgen *er* prioriteten: evaluatoren tar første regel som treffer, i
 * traverseringsrekkefølge. Alt her er rene funksjoner, slik at flyttelogikken
 * kan testes uten DOM.
 */

import type { OhGroup, Rule } from '../api/types';
import { parseRule } from './rule';

export type MemberKind = 'rule' | 'group';

export interface TreeNode {
  id: string;
  kind: MemberKind;
  name: string;
  /** Kun for regler. */
  rule?: string;
  /** «2» eller «2.1» — fortløpende i evalueringsrekkefølge, på tvers av nivåer. */
  priority: string;
  depth: number;
  children: TreeNode[];
  /** Antall medlemmer, for undergrupper. */
  memberCount?: number;
  /** Nivået er dypere enn vi viser utvidet; åpne undergruppen som egen side. */
  truncated?: boolean;
  /** Id-en finnes i listen, men verken som regel eller gruppe. */
  missing?: boolean;
}

export interface Registry {
  groups: Map<string, OhGroup>;
  rules: Map<string, Rule>;
}

export function buildRegistry(groups: OhGroup[], rules: Rule[]): Registry {
  return {
    groups: new Map(groups.map((g) => [g.id, g])),
    rules: new Map(rules.map((r) => [r.id, r])),
  };
}

/**
 * Bygger treet under én gruppe.
 *
 * `maxDepth` er 3 med vilje: et tre du ikke kan overskue er farligere enn ett
 * klikk mer. Dypere undergrupper vises som en rad du kan åpne som egen side.
 */
export function buildTree(rootId: string, registry: Registry, maxDepth = 3): TreeNode[] {
  const visiting = new Set<string>();

  function walk(groupId: string, depth: number, prefix: string): TreeNode[] {
    const group = registry.groups.get(groupId);
    if (!group) return [];

    // Backend har sirkelvern ved lagring, men eldre data kan være i ustand.
    // Vi stopper heller enn å henge.
    if (visiting.has(groupId)) return [];
    visiting.add(groupId);

    const nodes = (group.ruleGroupIds ?? []).map((id, index) => {
      const priority = prefix ? `${prefix}.${index + 1}` : String(index + 1);
      const rule = registry.rules.get(id);
      if (rule) {
        return {
          id,
          kind: 'rule' as const,
          name: rule.name,
          rule: rule.rule,
          priority,
          depth,
          children: [],
        };
      }

      const child = registry.groups.get(id);
      if (child) {
        const members = (child.ruleGroupIds ?? []).length;
        const truncated = depth + 1 >= maxDepth;
        return {
          id,
          kind: 'group' as const,
          name: child.name,
          priority,
          depth,
          memberCount: members,
          truncated,
          children: truncated ? [] : walk(id, depth + 1, priority),
        };
      }

      return {
        id,
        kind: 'rule' as const,
        name: 'Ukjent medlem',
        priority,
        depth,
        children: [],
        missing: true,
      };
    });

    visiting.delete(groupId);
    return nodes;
  }

  return walk(rootId, 0, '');
}

/** Flytter ett element. Utenfor listen er en no-op, ikke en feil. */
export function moveItem<T>(list: T[], from: number, to: number): T[] {
  if (from === to || from < 0 || from >= list.length || to < 0 || to >= list.length) {
    return list;
  }
  const next = [...list];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

/**
 * Ville det laget en sirkel å legge `childId` inn i `parentId`?
 *
 * Backend avviser dette ved lagring, men valget skal være utilgjengelig i
 * grensesnittet framfor å feile etterpå.
 */
export function wouldCycle(parentId: string, childId: string, registry: Registry): boolean {
  if (parentId === childId) return true;

  const seen = new Set<string>();
  const stack = [childId];
  while (stack.length > 0) {
    const current = stack.pop()!;
    if (current === parentId) return true;
    if (seen.has(current)) continue;
    seen.add(current);
    const group = registry.groups.get(current);
    for (const id of group?.ruleGroupIds ?? []) {
      if (registry.groups.has(id)) stack.push(id);
    }
  }
  return false;
}

/** Treffer regelen alle datoer? Da vinner den alltid, og alt etter den er dødt. */
function coversEverything(expr: string | undefined): boolean {
  if (!expr) return false;
  const parsed = parseRule(expr);
  if (!parsed) return false;
  return parsed.date === '??.??.????' && parsed.dayOfMonth === '?' && parsed.weekday === '?';
}

/**
 * Id-ene til medlemmer som aldri kan treffe, fordi et tidligere søsken dekker
 * alle datoer.
 *
 * Vi rapporterer bare det trygge tilfellet — en regel med bare wildcards. Å
 * avgjøre generell overlapp mellom to uttrykk er mulig, men en falsk advarsel
 * er verre enn ingen advarsel: rekkefølgen kan være tilsiktet.
 */
export function unreachableMembers(nodes: TreeNode[]): Set<string> {
  const unreachable = new Set<string>();
  let blocked = false;

  for (const node of nodes) {
    if (blocked) unreachable.add(node.id);
    else if (node.kind === 'rule' && coversEverything(node.rule)) blocked = true;

    for (const id of unreachableMembers(node.children)) unreachable.add(id);
  }

  return unreachable;
}
