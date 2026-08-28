import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { inject } from 'light-my-request';
import { createApp } from './index.ts';
import type { Config } from './config.ts';

/**
 * Integrasjonstester for BFF-en.
 *
 * Kjøres med light-my-request i stedet for supertest: den dispatcher rett inn i
 * Express-appen uten å åpne en socket, som gjør at testene også fungerer i miljøer
 * der lytting på porter er blokkert.
 */

const config: Config = {
  port: 0,
  backendUrl: 'http://backend.test',
  apiKey: 'hemmelig-nokkel',
  staticDir: '../client/dist',
  serveStatic: false,
  // Slik appen kjører i dag: bak ansatt.nav.no, alt krever innlogging.
  publicAccess: false,
};

const app = createApp(config);

/** Slik appen skal kjøre når kalenderen åpnes for publikum. */
const publicApp = createApp({ ...config, publicAccess: true });

const SERVICE_ID = '11111111-1111-1111-1111-111111111111';

interface CallOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  headers?: Record<string, string>;
  payload?: unknown;
  query?: Record<string, string>;
  /** Kjør mot varianten der kalenderen er åpen for uinnloggede. */
  public?: boolean;
}

async function call(url: string, options: CallOptions = {}) {
  const res = await inject(options.public ? publicApp : app, {
    method: options.method ?? 'GET',
    url,
    headers: options.headers,
    query: options.query,
    payload: options.payload as never,
    remoteAddress: '127.0.0.1',
  });
  return {
    status: res.statusCode,
    headers: res.headers,
    raw: res.payload,
    body: (res.payload ? JSON.parse(res.payload) : undefined) as never,
  };
}

/** Token uten gyldig signatur — BFF-en validerer ikke selv, Wonderwall står foran. */
function authHeader(name: string): Record<string, string> {
  const payload = Buffer.from(JSON.stringify({ name }), 'utf8').toString('base64url');
  return { authorization: `Bearer header.${payload}.signature` };
}

function mockUpstream(body: unknown, status = 200) {
  const fetchMock = vi.fn(async () => new Response(JSON.stringify(body), { status }));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('infrastruktur', () => {
  it('svarer UP på health, slik at NAIS får startet poden', async () => {
    const res = await call('/internal/health');
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: 'UP' });
  });

  it('lekker ikke serverteknologi og setter sikkerhetsheadere', async () => {
    const res = await call('/internal/health');
    expect(res.headers['x-powered-by']).toBeUndefined();
    expect(res.headers['content-security-policy']).toContain("frame-ancestors 'none'");
  });

  it('slipper gjennom Aksel-fonten fra Navs CDN', async () => {
    // Uten denne kilden blokkerer CSP Source Sans 3, og hele appen faller
    // tilbake til Arial. Feilen er stille: ingenting krasjer, alt ser bare feil ut.
    const res = await call('/internal/health');
    expect(res.headers['content-security-policy']).toContain('https://cdn.nav.no');
  });
});

describe('/me', () => {
  it('rapporterer utlogget uten Authorization-header', async () => {
    const res = await call('/me');
    expect(res.body).toEqual({ loggedIn: false });
  });

  it('leser navnet ut av tokenet fra Wonderwall', async () => {
    const res = await call('/me', { headers: authHeader('Kari Nordmann') });
    expect(res.body).toEqual({ loggedIn: true, name: 'Kari Nordmann' });
  });
});

describe('proxy-tilgang', () => {
  it('slipper gjennom kalenderoppslag og legger på API-nøkkelen', async () => {
    const fetchMock = mockUpstream([{ id: SERVICE_ID, name: 'Dagpenger' }]);

    const res = await call('/api/openinghours/service', { headers: authHeader('Kari') });

    expect(res.status).toBe(200);
    expect(res.body).toEqual([{ id: SERVICE_ID, name: 'Dagpenger' }]);

    const [url, init] = fetchMock.mock.calls[0] as unknown as [URL, RequestInit];
    expect(url.toString()).toBe('http://backend.test/api/openinghours/service');
    expect((init.headers as Record<string, string>)['X-API-Key']).toBe('hemmelig-nokkel');
  });

  it('sender aldri API-nøkkelen tilbake til nettleseren', async () => {
    mockUpstream([]);
    const res = await call('/api/openinghours/service', { headers: authHeader('Kari') });
    expect(JSON.stringify(res.headers)).not.toContain('hemmelig-nokkel');
    expect(res.raw).not.toContain('hemmelig-nokkel');
  });

  it('videresender query-parametre til range-endepunktet', async () => {
    const fetchMock = mockUpstream([]);

    await call(`/api/openinghours/query/service/${SERVICE_ID}/range`, {
      headers: authHeader('Kari'),
      query: { from: '2025-08-01', to: '2025-08-31' },
    });

    const [url] = fetchMock.mock.calls[0] as unknown as [URL];
    expect(url.searchParams.get('from')).toBe('2025-08-01');
    expect(url.searchParams.get('to')).toBe('2025-08-31');
  });

  it('avviser paths utenfor whitelisten uten å kontakte backend', async () => {
    const fetchMock = mockUpstream({});

    const res = await call('/api/actuator/env');

    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('krever innlogging for admin-ruter', async () => {
    const fetchMock = mockUpstream([]);

    const res = await call('/api/openinghours/rule');

    expect(res.status).toBe(401);
    expect((res.body as { message: string }).message).toMatch(/logge inn/i);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('slipper gjennom admin-ruter for innloggede', async () => {
    const fetchMock = mockUpstream([{ id: 'r1', name: 'Ordinær åpningstid' }]);

    const res = await call('/api/openinghours/rule', { headers: authHeader('Kari') });

    expect(res.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('blokkerer alle mutasjoner for utloggede, også på offentlige ruter', async () => {
    const fetchMock = mockUpstream({});

    const res = await call('/api/openinghours/service', {
      method: 'POST',
      payload: { name: 'Ny tjeneste' },
    });

    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('svarer 404 i stedet for 401 når en innlogget bruker treffer ukjent path', async () => {
    mockUpstream({});
    const res = await call('/api/tull', { headers: authHeader('Kari') });
    expect(res.status).toBe(404);
  });
});

describe('offentlig tilgang (PUBLIC_ACCESS=true)', () => {
  const internDag = {
    resourceId: SERVICE_ID,
    date: '2025-08-21',
    isOpen: true,
    openingTime: '08:00',
    closingTime: '15:30',
    onlyShowForNavEmployees: true,
    redDay: false,
    matchedRule: { name: 'Intern drift', rule: '??.??.???? ? 1,2,3,4,5 08:00-15:30' },
  };

  it('slipper uinnloggede inn på kalenderen', async () => {
    mockUpstream([{ id: SERVICE_ID, name: 'Dagpenger' }]);
    const res = await call('/api/openinghours/service', { public: true });
    expect(res.status).toBe(200);
  });

  it('skjuler tider og regelinfo for utloggede', async () => {
    mockUpstream(internDag);

    const res = await call(`/api/openinghours/query/service/${SERVICE_ID}`, { public: true });
    const body = res.body as Record<string, unknown>;

    expect(body.masked).toBe(true);
    // Sentinelen «00:00–00:00» er backendens «stengt». Vi bruker den bevisst i stedet
    // for null, slik at en klient som ikke kjenner `masked` degraderer trygt.
    expect(body.openingTime).toBe('00:00');
    expect(body.closingTime).toBe('00:00');
    expect(body.isOpen).toBe(false);
    expect(body.matchedRule).toBeUndefined();
    expect(res.raw).not.toContain('Intern drift');
  });

  it('viser alt til innloggede', async () => {
    mockUpstream(internDag);

    const res = await call(`/api/openinghours/query/service/${SERVICE_ID}`, {
      public: true,
      headers: authHeader('Kari'),
    });
    const body = res.body as Record<string, unknown>;

    expect(body.masked).toBeFalsy();
    expect(body.openingTime).toBe('08:00');
    expect((body.matchedRule as { name: string }).name).toBe('Intern drift');
  });

  it('masker også enkeltdager inne i et range-svar', async () => {
    mockUpstream([{ ...internDag, onlyShowForNavEmployees: false }, internDag]);

    const res = await call(`/api/openinghours/query/service/${SERVICE_ID}/range`, {
      public: true,
      query: { from: '2025-08-20', to: '2025-08-21' },
    });
    const body = res.body as Array<Record<string, unknown>>;

    expect(body[0].masked).toBeFalsy();
    expect(body[0].openingTime).toBe('08:00');
    expect(body[1].masked).toBe(true);
  });

  it('holder admin stengt for uinnloggede selv når kalenderen er åpen', async () => {
    const fetchMock = mockUpstream({});
    const res = await call('/api/openinghours/rule', { public: true });
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('feilhåndtering', () => {
  it('gir en forståelig norsk melding når backend er nede', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new Error('ECONNREFUSED 10.0.0.5:8081');
      }),
    );

    const res = await call('/api/openinghours/service', { headers: authHeader('Kari') });

    expect(res.status).toBe(502);
    expect((res.body as { message: string }).message).toBe(
      'Vi fikk ikke kontakt med åpningstidstjenesten.',
    );
    expect(res.raw).not.toContain('ECONNREFUSED');
  });

  it('sender backendens feilstatus videre uendret', async () => {
    mockUpstream({ message: 'Fant ikke tjenesten' }, 404);

    const res = await call(`/api/openinghours/query/service/${SERVICE_ID}`, {
      headers: authHeader('Kari'),
    });

    expect((res.body as { message: string }).message).toBe('Fant ikke tjenesten');
    expect(res.status).toBe(404);
  });
});

describe('admin-mutasjoner', () => {
  const RULE_ID = '22222222-2222-2222-2222-222222222222';

  it('videresender regelopprettelse som query-parametre, ikke som body', async () => {
    const fetchMock = mockUpstream({ id: RULE_ID, name: 'Ordinær åpningstid' });

    const res = await call('/api/openinghours/rule', {
      method: 'PUT',
      headers: authHeader('Kari'),
      query: { name: 'Ordinær åpningstid', rule: '??.??.???? ? 1-5 08:00-15:30' },
    });

    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [URL, RequestInit];
    expect(url.pathname).toBe('/api/openinghours/rule');
    expect(url.searchParams.get('name')).toBe('Ordinær åpningstid');
    expect(url.searchParams.get('rule')).toBe('??.??.???? ? 1-5 08:00-15:30');
    // Backend leser query-parametre; en tom body ville bare vært støy.
    expect(init.body).toBeUndefined();
  });

  it('sender JSON-body videre for gruppeoppdatering', async () => {
    const fetchMock = mockUpstream({ id: 'g1', name: 'Selvbetjening' });

    await call('/api/openinghours/group/g1', {
      method: 'PUT',
      headers: authHeader('Kari'),
      payload: { name: 'Selvbetjening', ruleGroupIds: [RULE_ID] },
    });

    const [, init] = fetchMock.mock.calls[0] as unknown as [URL, RequestInit];
    expect(JSON.parse(init.body as string)).toEqual({
      name: 'Selvbetjening',
      ruleGroupIds: [RULE_ID],
    });
  });

  it('sender en array-body videre når en gruppe opprettes med medlemmer', async () => {
    const fetchMock = mockUpstream({ id: 'g1', name: 'Selvbetjening' });

    await call('/api/openinghours/group', {
      method: 'POST',
      headers: authHeader('Kari'),
      query: { name: 'Selvbetjening' },
      payload: [RULE_ID],
    });

    const [url, init] = fetchMock.mock.calls[0] as unknown as [URL, RequestInit];
    expect(url.searchParams.get('name')).toBe('Selvbetjening');
    // Arrayen må overleve som array — backend tar imot List<UUID>, ikke et objekt.
    expect(JSON.parse(init.body as string)).toEqual([RULE_ID]);
  });

  it('krever innlogging for å endre en regel', async () => {
    const fetchMock = mockUpstream({});

    const res = await call('/api/openinghours/rule', {
      method: 'PUT',
      query: { name: 'Ny', rule: '??.??.???? ? ? 08:00-15:30' },
    });

    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('slipper ikke gjennom confirm=true, som ville omgått 409 ved sletting', async () => {
    const fetchMock = mockUpstream(true);

    await call(`/api/openinghours/rule/${RULE_ID}`, {
      method: 'DELETE',
      headers: authHeader('Kari'),
      query: { confirm: 'true' },
    });

    const [url] = fetchMock.mock.calls[0] as unknown as [URL];
    expect(url.searchParams.has('confirm')).toBe(false);
  });

  it('sender 409-konflikten fra backend videre uendret', async () => {
    mockUpstream(
      { status: 409, message: 'Rule is used by 2 group(s): A, B. Pass ?confirm=true to delete anyway.' },
      409,
    );

    const res = await call(`/api/openinghours/rule/${RULE_ID}`, {
      method: 'DELETE',
      headers: authHeader('Kari'),
    });

    expect(res.status).toBe(409);
    expect((res.body as { message: string }).message).toContain('2 group(s)');
  });

  it('avviser mutasjoner mot ruter som ikke er admin', async () => {
    const fetchMock = mockUpstream({});

    const res = await call('/api/openinghours/daily', {
      method: 'POST',
      headers: authHeader('Kari'),
    });

    expect(res.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
