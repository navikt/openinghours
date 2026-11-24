import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, apiFetch, query } from '../api/client';
import type { Activity, ActivityInput } from '../api/types';

const BASE = '/api/openinghours/activity';
const FEED_REFRESH = 60 * 1000;

export function useActivitiesRange(from: string, to: string, enabled = true) {
  return useQuery({
    queryKey: ['activities', 'range', from, to],
    queryFn: () =>
      apiFetch<Activity[]>(`${BASE}/range${query({ from, to })}`),
    enabled,
    staleTime: FEED_REFRESH,
    refetchInterval: FEED_REFRESH,
  });
}

export function useActivity(id: string | undefined) {
  return useQuery({
    queryKey: ['activities', 'detail', id],
    queryFn: () => apiFetch<Activity>(`${BASE}/${id}`),
    enabled: Boolean(id),
  });
}

export function useCreateActivity() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ActivityInput) =>
      apiFetch<Activity>(BASE, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['activities'] }),
  });
}

export function useUpdateActivity() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...input }: ActivityInput & { id: string }) =>
      apiFetch<Activity>(`${BASE}/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['activities'] }),
  });
}

export function useDeleteActivity() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: string) => {
      const deleted = await apiFetch<boolean>(`${BASE}/${id}`, { method: 'DELETE' });
      if (!deleted) throw new ApiError('Aktiviteten ble ikke slettet.', 404);
      return deleted;
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['activities'] }),
  });
}
