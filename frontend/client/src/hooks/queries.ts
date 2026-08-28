import { useQueries, useQuery } from '@tanstack/react-query';
import { apiFetch, query } from '../api/client';
import type { DailyCacheResponse, QueryResponse, Service, Session } from '../api/types';
import { firstOfMonth, lastOfMonth } from '../lib/date';

const STALE = 5 * 60 * 1000;

export function useSession() {
  return useQuery({
    queryKey: ['session'],
    queryFn: () => apiFetch<Session>('/me'),
    staleTime: Infinity,
    retry: false,
  });
}

export function useServices() {
  return useQuery({
    queryKey: ['services'],
    queryFn: () => apiFetch<Service[]>('/api/openinghours/service'),
    staleTime: STALE,
  });
}

export function useService(serviceId: string | undefined) {
  return useQuery({
    queryKey: ['service', serviceId],
    queryFn: () => apiFetch<Service>(`/api/openinghours/service/${serviceId}`),
    enabled: Boolean(serviceId),
    staleTime: STALE,
  });
}

/** «Åpen nå» for alle tjenester — én cache-oppdatert kilde, ett kall. */
export function useDailyStatus() {
  return useQuery({
    queryKey: ['daily'],
    queryFn: () => apiFetch<Record<string, DailyCacheResponse>>('/api/openinghours/daily'),
    staleTime: 60 * 1000,
    refetchInterval: 60 * 1000,
  });
}

/**
 * Hele måneden i ett kall. Backend støtter vilkårlige intervaller, så
 * uke- og årsvisning bruker samme hook med andre grenser.
 */
export function useServiceRange(serviceId: string | undefined, from: string, to: string) {
  return useQuery({
    queryKey: ['range', serviceId, from, to],
    queryFn: () =>
      apiFetch<QueryResponse[]>(
        `/api/openinghours/query/service/${serviceId}/range${query({ from, to })}`,
      ),
    enabled: Boolean(serviceId),
    staleTime: STALE,
  });
}

export function useServiceMonth(serviceId: string | undefined, month: string) {
  return useServiceRange(serviceId, firstOfMonth(month), lastOfMonth(month));
}

/**
 * Samme dato for flere tjenester. Backend har ingen samlet endepunkt for dette,
 * så vi gjør N parallelle kall og lar hver tjeneste feile for seg — én tjeneste
 * som svarer dårlig skal ikke skjule resten av sammenligningen.
 */
export function useServicesOnDate(serviceIds: string[], date: string) {
  return useQueries({
    queries: serviceIds.map((id) => ({
      queryKey: ['day', id, date],
      queryFn: () =>
        apiFetch<QueryResponse>(`/api/openinghours/query/service/${id}${query({ date })}`),
      staleTime: STALE,
    })),
  });
}
