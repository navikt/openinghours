import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import type { Service } from '../api/types';
import { useAllServicesRange, useServices } from './queries';

const service: Service = {
  id: 'ac8e0f53-b71a-4a65-8184-1ec651459bff',
  name: 'Testtjeneste',
  type: 'TJENESTE',
  team: 'Testteam',
  monitorlink: null,
  logglink: null,
  description: null,
  createdAt: '2026-10-13T09:45:06Z',
  updatedAt: null,
};

function setup() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { queryClient, wrapper };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('useServices', () => {
  it('requests only TJENESTE and does not reuse an unfiltered service cache', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([service]));
    vi.stubGlobal('fetch', fetchMock);
    const { queryClient, wrapper } = setup();
    queryClient.setQueryData(['services'], [{ ...service, id: 'component-id', type: 'KOMPONENT' }]);

    const { result } = renderHook(() => useServices(), { wrapper });

    await waitFor(() => expect(result.current.data).toEqual([service]));
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/openinghours/service?type=TJENESTE',
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    );
    expect(queryClient.getQueryData(['services', 'TJENESTE'])).toEqual([service]);
  });

  it('queries calendars only for the filtered service list', async () => {
    const fetchMock = vi.fn(async (path: string) => {
      if (path === '/api/openinghours/service?type=TJENESTE') return jsonResponse([service]);
      if (path === `/api/openinghours/query/service/${service.id}/range?from=2026-11-01&to=2026-11-30`) {
        return jsonResponse([]);
      }
      throw new Error(`Unexpected request: ${path}`);
    });
    vi.stubGlobal('fetch', fetchMock);
    const { wrapper } = setup();
    const { result } = renderHook(() => {
      const services = useServices();
      const calendars = useAllServicesRange(
        (services.data ?? []).map((item) => item.id), '2026-11-01', '2026-11-30',
      );
      return { services, calendars };
    }, { wrapper });

    await waitFor(() => expect(result.current.calendars[0]?.isSuccess).toBe(true));
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(result.current.calendars).toHaveLength(1);
  });

  it('keeps the service cache invalidation used by admin mutations', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => jsonResponse([service]));
    vi.stubGlobal('fetch', fetchMock);
    const { queryClient, wrapper } = setup();
    const { result } = renderHook(() => useServices(), { wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    await act(async () => queryClient.invalidateQueries({ queryKey: ['services'] }));

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/openinghours/service?type=TJENESTE', expect.any(Object),
    );
  });

  it('surfaces service endpoint errors instead of reporting an empty service list', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ message: 'Unavailable' }, 503)));
    const { wrapper } = setup();
    const { result } = renderHook(() => useServices(), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(ApiError);
    expect(result.current.data).toBeUndefined();
  });
});
