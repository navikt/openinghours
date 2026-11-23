import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Activity, ActivityInput } from '../api/types';
import {
  useActivitiesRange,
  useActivity,
  useCreateActivity,
  useDeleteActivity,
  useUpdateActivity,
} from './activities';

const BASE = '/api/openinghours/activity';

const input: ActivityInput = {
  serviceIds: [],
  header: 'Planlagt vedlikehold',
  message: 'Tjenesten kan være utilgjengelig.',
  start: null,
  end: null,
  onlyShowForNavEmployees: false,
};

const activity: Activity = { id: 'a1', ...input, ongoing: true };

function setup() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { queryClient, wrapper };
}

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    headers: { 'content-type': 'application/json' },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('activity queries', () => {
  it('should fetch a range with its parameters and cache key', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([activity]));
    vi.stubGlobal('fetch', fetchMock);
    const { queryClient, wrapper } = setup();

    const { result } = renderHook(() => useActivitiesRange('2025-03-01', '2025-03-31'), {
      wrapper,
    });

    await waitFor(() => expect(result.current.data).toEqual([activity]));
    expect(fetchMock).toHaveBeenCalledWith(
      `${BASE}/range?from=2025-03-01&to=2025-03-31`,
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    );
    expect(queryClient.getQueryData(['activities', 'range', '2025-03-01', '2025-03-31'])).toEqual([
      activity,
    ]);
  });

  it('should not fetch a disabled range or an activity without an id', () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const { wrapper } = setup();

    renderHook(() => useActivitiesRange('2025-03-01', '2025-03-31', false), { wrapper });
    renderHook(() => useActivity(undefined), { wrapper });

    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('should fetch an activity by id', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(activity));
    vi.stubGlobal('fetch', fetchMock);
    const { wrapper } = setup();

    const { result } = renderHook(() => useActivity('a1'), { wrapper });

    await waitFor(() => expect(result.current.data).toEqual(activity));
    expect(fetchMock).toHaveBeenCalledWith(`${BASE}/a1`, expect.any(Object));
  });
});

describe('activity mutations', () => {
  it('should create an activity, preserve null optional fields, and invalidate only activity queries', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(activity));
    vi.stubGlobal('fetch', fetchMock);
    const { queryClient, wrapper } = setup();
    queryClient.setQueryData(['activities', 'range', 'from', 'to'], []);
    queryClient.setQueryData(['services'], []);

    const { result } = renderHook(() => useCreateActivity(), { wrapper });
    await act(async () => result.current.mutateAsync(input));

    expect(fetchMock).toHaveBeenCalledWith(
      BASE,
      expect.objectContaining({
        method: 'POST',
        headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      }),
    );
    expect(queryClient.getQueryState(['activities', 'range', 'from', 'to'])?.isInvalidated).toBe(
      true,
    );
    expect(queryClient.getQueryState(['services'])?.isInvalidated).toBe(false);
  });

  it('should update an activity with its id in the URL and no id in the payload', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(activity));
    vi.stubGlobal('fetch', fetchMock);
    const { wrapper } = setup();
    const { result } = renderHook(() => useUpdateActivity(), { wrapper });

    await act(async () => result.current.mutateAsync({ id: 'a1', ...input }));

    expect(fetchMock).toHaveBeenCalledWith(
      `${BASE}/a1`,
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(input) }),
    );
  });

  it('should invalidate activity queries after a successful delete', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(true));
    vi.stubGlobal('fetch', fetchMock);
    const { queryClient, wrapper } = setup();
    queryClient.setQueryData(['activities', 'detail', 'a1'], activity);

    const { result } = renderHook(() => useDeleteActivity(), { wrapper });
    await act(async () => result.current.mutateAsync('a1'));

    expect(fetchMock).toHaveBeenCalledWith(`${BASE}/a1`, expect.objectContaining({ method: 'DELETE' }));
    expect(queryClient.getQueryState(['activities', 'detail', 'a1'])?.isInvalidated).toBe(true);
  });

  it('should reject a delete that returns false instead of reporting success', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(false));
    vi.stubGlobal('fetch', fetchMock);
    const { queryClient, wrapper } = setup();
    queryClient.setQueryData(['activities', 'detail', 'a1'], activity);

    const { result } = renderHook(() => useDeleteActivity(), { wrapper });
    await expect(act(async () => result.current.mutateAsync('a1'))).rejects.toThrow(
      'Driftsmeldingen ble ikke slettet.',
    );
    expect(queryClient.getQueryState(['activities', 'detail', 'a1'])?.isInvalidated).toBe(false);
  });

  it('should surface API errors from mutation requests', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 500 }));
    vi.stubGlobal('fetch', fetchMock);
    const { wrapper } = setup();
    const { result } = renderHook(() => useCreateActivity(), { wrapper });

    await expect(act(async () => result.current.mutateAsync(input))).rejects.toThrow(
      'Noe gikk galt hos oss.',
    );
  });
});
