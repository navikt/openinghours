/**
 * Admin: spørringer og mutasjoner.
 *
 * To ting skiller seg fra resten av API-laget, og begge er backendens valg:
 *
 * 1. **Regel-endepunktene tar query-parametre**, ikke JSON-body (avvik 6).
 * 2. **`PUT /rule` er en upsert på navn** (avvik 7). Et eksisterende navn
 *    overskriver den regelen i stillhet, så `findRuleByName` finnes for at
 *    skjemaet kan advare før lagring.
 *
 * Vi sender aldri `?confirm=true`. 409 skal føre til at brukeren rydder opp,
 * ikke til en knapp som overkjører vernet — BFF-en filtrerer også bort
 * parameteren, så et forsøk her ville uansett ikke nådd fram.
 */

import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, query } from '../api/client';
import type {
  GroupAssociations,
  OhGroup,
  QueryResponse,
  Rule,
  Service,
  ServiceType,
} from '../api/types';

const BASE = '/api/openinghours';
const STALE = 30 * 1000;

/** Query-parametre for regel-endepunktene. `undefined` utelates helt. */
function ruleParams(input: RuleInput): Record<string, string | undefined> {
  return {
    name: input.name,
    rule: input.rule,
    header: input.header || undefined,
    text: input.text || undefined,
    onlyShowForNavEmployees:
      input.onlyShowForNavEmployees === undefined
        ? undefined
        : String(input.onlyShowForNavEmployees),
    unstableOpeningHours:
      input.unstableOpeningHours === undefined
        ? undefined
        : String(input.unstableOpeningHours),
  };
}

export interface RuleInput {
  name?: string;
  rule?: string;
  header?: string | null;
  text?: string | null;
  onlyShowForNavEmployees?: boolean;
  unstableOpeningHours?: boolean;
}

export interface ServiceInput {
  name: string;
  type: ServiceType;
  team: string;
  monitorlink?: string | null;
  logglink?: string | null;
  description?: string | null;
  ohGroupId?: string | null;
}

/* ── Regler ──────────────────────────────────────────────────────────────── */

export function useRules() {
  return useQuery({
    queryKey: ['rules'],
    queryFn: () => apiFetch<Rule[]>(`${BASE}/rule`),
    staleTime: STALE,
  });
}

export function useRule(id: string | undefined) {
  return useQuery({
    queryKey: ['rule', id],
    queryFn: () => apiFetch<Rule>(`${BASE}/rule/${id}`),
    enabled: Boolean(id),
    staleTime: STALE,
  });
}

/** Gruppene som bruker regelen — «Brukt i N grupper» og konsekvensen ved lagring. */
export function useRuleGroups(id: string | undefined) {
  return useQuery({
    queryKey: ['rule-groups', id],
    queryFn: () => apiFetch<OhGroup[]>(`${BASE}/rule/${id}/groups`),
    enabled: Boolean(id),
    staleTime: STALE,
  });
}

/** Oppretter — eller overskriver en regel med samme navn. Advar først. */
export function useUpsertRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: RuleInput) =>
      apiFetch<Rule>(`${BASE}/rule${query(ruleParams(input))}`, { method: 'PUT' }),
    onSuccess: () => invalidateAdmin(qc),
  });
}

export function useUpdateRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...input }: RuleInput & { id: string }) =>
      apiFetch<Rule>(`${BASE}/rule/${id}${query(ruleParams(input))}`, { method: 'PATCH' }),
    onSuccess: () => invalidateAdmin(qc),
  });
}

export function useDeleteRule() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiFetch<boolean>(`${BASE}/rule/${id}`, { method: 'DELETE' }),
    onSuccess: () => invalidateAdmin(qc),
  });
}

/* ── Grupper ─────────────────────────────────────────────────────────────── */

export function useGroups() {
  return useQuery({
    queryKey: ['groups'],
    queryFn: () => apiFetch<OhGroup[]>(`${BASE}/group`),
    staleTime: STALE,
  });
}

export function useGroup(id: string | undefined) {
  return useQuery({
    queryKey: ['group', id],
    queryFn: () => apiFetch<OhGroup>(`${BASE}/group/${id}`),
    enabled: Boolean(id),
    staleTime: STALE,
  });
}

/** Hvor gruppa er i bruk — vises før sletting, og i konfliktlisten ved 409. */
export function useGroupAssociations(id: string | undefined) {
  return useQuery({
    queryKey: ['group-associations', id],
    queryFn: () => apiFetch<GroupAssociations>(`${BASE}/group/${id}/associations`),
    enabled: Boolean(id),
    staleTime: STALE,
  });
}

/**
 * Autoritativ test av en gruppe mot en dato. I motsetning til regelskjemaets
 * lokale forhåndsvisning går denne mot backendens egen evaluator, så den viser
 * også hvilken regel som vant i rekkefølgen.
 */
export function useGroupOnDate(groupId: string | undefined, date: string) {
  return useQuery({
    queryKey: ['group-query', groupId, date],
    queryFn: () => apiFetch<QueryResponse>(`${BASE}/group/${groupId}${query({ date })}`),
    enabled: Boolean(groupId) && Boolean(date),
    staleTime: STALE,
    retry: false,
  });
}

/**
 * Koblingen tjeneste → gruppe for alle tjenester.
 *
 * Backend har ingen samlet oversikt, og `Service` bærer ikke gruppe-id-en. Vi
 * henter derfor `associations` for hver gruppe og snur oppslaget. Det er ett
 * kall per **gruppe**, ikke per tjeneste, og grupper er det klart færrest av.
 */
export function useServiceGroupLinks(groups: OhGroup[] | undefined) {
  const results = useQueries({
    queries: (groups ?? []).map((group) => ({
      queryKey: ['group-associations', group.id],
      queryFn: () => apiFetch<GroupAssociations>(`${BASE}/group/${group.id}/associations`),
      staleTime: STALE,
    })),
  });

  const byService = new Map<string, OhGroup>();
  results.forEach((result, index) => {
    const group = (groups ?? [])[index];
    for (const service of result.data?.services ?? []) byService.set(service.id, group);
  });

  return {
    byService,
    isPending: results.some((r) => r.isPending),
    isError: results.some((r) => r.isError),
  };
}

export function useCreateGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ name, ruleGroupIds = [] }: { name: string; ruleGroupIds?: string[] }) =>
      apiFetch<OhGroup>(`${BASE}/group${query({ name })}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(ruleGroupIds),
      }),
    onSuccess: () => invalidateAdmin(qc),
  });
}

/**
 * Erstatter hele medlemslisten. Dette er også mekanismen bak «Lagre rekkefølgen»
 * — rekkefølgen i `ruleGroupIds` *er* prioriteten, og backend bevarer den.
 */
export function useUpdateGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      name,
      ruleGroupIds,
    }: {
      id: string;
      name?: string | null;
      ruleGroupIds?: string[] | null;
    }) =>
      apiFetch<OhGroup>(`${BASE}/group/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name: name ?? null, ruleGroupIds: ruleGroupIds ?? null }),
      }),
    onSuccess: () => invalidateAdmin(qc),
  });
}

export function useDeleteGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiFetch<boolean>(`${BASE}/group/${id}`, { method: 'DELETE' }),
    onSuccess: () => invalidateAdmin(qc),
  });
}

/** Fjerner ett medlem uten å røre resten av rekkefølgen. */
export function useRemoveGroupMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      groupId,
      memberId,
      kind,
    }: {
      groupId: string;
      memberId: string;
      kind: 'rule' | 'group';
    }) =>
      apiFetch<OhGroup>(
        kind === 'rule'
          ? `${BASE}/group/${groupId}/rules/${memberId}`
          : `${BASE}/group/${groupId}/groups/${memberId}`,
        { method: 'DELETE' },
      ),
    onSuccess: () => invalidateAdmin(qc),
  });
}

/* ── Tjenester ───────────────────────────────────────────────────────────── */

export function useCreateService() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: ServiceInput) =>
      apiFetch<Service>(`${BASE}/service`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      }),
    onSuccess: () => invalidateAdmin(qc),
  });
}

export function useUpdateService() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...input }: ServiceInput & { id: string }) =>
      apiFetch<Service>(`${BASE}/service/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      }),
    onSuccess: () => invalidateAdmin(qc),
  });
}

export function useDeleteService() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiFetch<boolean>(`${BASE}/service/${id}`, { method: 'DELETE' }),
    onSuccess: () => invalidateAdmin(qc),
  });
}

/** Én tjeneste har alltid nøyaktig én gruppe, eller ingen. */
export function useSetServiceGroup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ serviceId, groupId }: { serviceId: string; groupId: string | null }) =>
      groupId === null
        ? apiFetch<void>(`${BASE}/service/${serviceId}/oh-group`, { method: 'DELETE' })
        : apiFetch<void>(`${BASE}/service/${serviceId}/oh-group/${groupId}`, { method: 'PUT' }),
    onSuccess: () => invalidateAdmin(qc),
  });
}

/**
 * Alt admin endrer kan påvirke alt annet: en regel ligger i grupper, grupper
 * ligger i grupper, og tjenester peker på grupper. Å invalidere bredt er
 * billigere enn å gjette hvilke nøkler som ble berørt.
 */
function invalidateAdmin(qc: ReturnType<typeof useQueryClient>): void {
  for (const key of [
    'rules',
    'rule',
    'rule-groups',
    'groups',
    'group',
    'group-associations',
    'group-query',
    'services',
    'service',
    'daily',
    'range',
    'day',
  ]) {
    void qc.invalidateQueries({ queryKey: [key] });
  }
}
