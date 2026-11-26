import { randomUUID } from 'node:crypto';
import type { Request, Response } from 'express';
import type { Config } from './config.ts';
import { isAdmin, isLoggedIn } from './auth.ts';
import { maskResponse } from './masking.ts';

/**
 * Whitelist av hva klienten får lov til å be om.
 *
 * BFF-en videresender ikke blindt: den kjenner API-nøkkelen, så en åpen proxy
 * ville gjort hele backend-API-et tilgjengelig for alle som når podden.
 */

/** Kalenderoppslag. Disse er de eneste som kan bli offentlige. */
const CALENDAR_ROUTES: RegExp[] = [
  /^\/openinghours\/service$/,
  /^\/openinghours\/service\/[0-9a-f-]{36}$/i,
  /^\/openinghours\/query\/service\/[0-9a-f-]{36}$/i,
  /^\/openinghours\/query\/service\/[0-9a-f-]{36}\/range$/i,
  /^\/openinghours\/query\/group\/[0-9a-f-]{36}$/i,
  /^\/openinghours\/daily$/,
  /^\/openinghours\/daily\/[0-9a-f-]{36}$/i,
];

/** Admin. Krever innlogging uansett hvordan appen er eksponert. */
const ADMIN_ROUTES: RegExp[] = [
  /^\/openinghours\/rule(\/.*)?$/,
  /^\/openinghours\/group(\/.*)?$/,
  /^\/openinghours\/service\/[0-9a-f-]{36}\/oh-groups?(\/[0-9a-f-]{36})?$/i,
];

/**
 * Ruter som faktisk har skrive-endepunkter i backend.
 *
 * Uten dette skillet ville proxyen videresendt f.eks. `POST /daily`, som ikke
 * finnes. Å slippe gjennom bare det som kan brukes holder angrepsflaten lik
 * backendens faktiske API.
 */
const MUTABLE_ROUTES: RegExp[] = [
  /^\/openinghours\/service$/,
  /^\/openinghours\/service\/[0-9a-f-]{36}$/i,
  /^\/openinghours\/service\/[0-9a-f-]{36}\/oh-group(\/[0-9a-f-]{36})?$/i,
  /^\/openinghours\/rule(\/.*)?$/,
  /^\/openinghours\/group(\/.*)?$/,
];

const ACTIVITY_ID = /^\/openinghours\/activity\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ACTIVITY_RANGE = '/openinghours/activity/range';
const ACTIVITY_SERVICE_RANGE =
  /^\/openinghours\/activity\/service\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\/range$/i;

function isActivityRange(path: string): boolean {
  return path === ACTIVITY_RANGE || ACTIVITY_SERVICE_RANGE.test(path);
}

function isActivityMutation(path: string, method: string): boolean {
  return (
    (method === 'POST' && path === '/openinghours/activity') ||
    ((method === 'PUT' || method === 'DELETE') && ACTIVITY_ID.test(path))
  );
}

const SAFE_METHODS = new Set(['GET', 'HEAD']);

/**
 * Sletting av noe som er i bruk skal alltid stoppes av backendens 409.
 * `?confirm=true` overstyrer den sperren, og designet sier eksplisitt at
 * en «slett likevel»-knapp ikke skal finnes — så parameteren slippes aldri gjennom.
 */
const FORBIDDEN_PARAMS = new Set(['confirm']);

const LOG_ROUTE_SEGMENTS = new Set([
  'openinghours', 'query', 'service', 'group', 'rule', 'activity', 'daily',
  'range', 'associations', 'oh-group', 'oh-groups',
]);

const NETWORK_ERROR_CODES = new Set([
  'ECONNREFUSED', 'ECONNRESET', 'ETIMEDOUT', 'ENOTFOUND', 'EAI_AGAIN',
  'ENETUNREACH', 'EHOSTUNREACH', 'UND_ERR_CONNECT_TIMEOUT',
  'UND_ERR_HEADERS_TIMEOUT', 'UND_ERR_BODY_TIMEOUT', 'UND_ERR_SOCKET',
  'UND_ERR_ABORTED', 'CERT_HAS_EXPIRED', 'UNABLE_TO_VERIFY_LEAF_SIGNATURE',
  'DEPTH_ZERO_SELF_SIGNED_CERT', 'ERR_TLS_CERT_ALTNAME_INVALID',
]);

function proxyErrorCode(error: unknown): string {
  let current = error;
  for (let depth = 0; depth < 5; depth++) {
    if (typeof current !== 'object' || current === null) break;
    if ('code' in current && typeof current.code === 'string' && NETWORK_ERROR_CODES.has(current.code)) {
      return current.code;
    }
    if ('name' in current && current.name === 'TimeoutError') return 'REQUEST_TIMEOUT';
    if ('name' in current && current.name === 'AbortError') return 'REQUEST_ABORTED';
    current = 'cause' in current ? current.cause : undefined;
  }
  return 'UNKNOWN';
}

/**
 * @param publicAccess Er kalenderen åpen for uinnloggede? Er den ikke det —
 *   som i dag, bak `ansatt.nav.no` — krever alt innlogging. Sjekken er da
 *   forsvar i dybden mot kall som omgår Wonderwall-sidecaren.
 */
/** Ba forespørselen om noe som krever admintilgang? Styrer 403 mot 404. */
function isAdminRequest(path: string, method: string): boolean {
  if (isActivityMutation(path, method)) return true;
  if (!SAFE_METHODS.has(method)) return MUTABLE_ROUTES.some((r) => r.test(path));
  return ADMIN_ROUTES.some((r) => r.test(path));
}

export function isAllowed(
  path: string,
  method: string,
  loggedIn: boolean,
  publicAccess = false,
  admin = false,
): boolean {
  // Aktiviteter er interne også dersom åpningstidskalenderen blir offentlig.
  if (isActivityRange(path) || ACTIVITY_ID.test(path)) {
    if (method === 'GET') return loggedIn;
    return loggedIn && admin && isActivityMutation(path, method);
  }
  if (path === '/openinghours/activity') {
    return loggedIn && admin && isActivityMutation(path, method);
  }
  // Alt som endrer data krever medlemskap i admingruppen, uten unntak.
  if (!SAFE_METHODS.has(method)) {
    return admin && MUTABLE_ROUTES.some((r) => r.test(path));
  }
  if (CALENDAR_ROUTES.some((r) => r.test(path))) return loggedIn || publicAccess;
  /*
   * Adminrutene er lesing av regler og grupper. Vi krever admin også her: uten
   * skrivetilgang er sidene likevel uten nytte, og oppsettet skal ikke kunne
   * kartlegges av alle ansatte.
   */
  if (ADMIN_ROUTES.some((r) => r.test(path))) return admin;
  return false;
}

export function createProxy(config: Config) {
  let activeRequests = 0;
  return async function proxy(req: Request, res: Response): Promise<void> {
    const loggedIn = isLoggedIn(req);
    const admin = isAdmin(req, config.adminGroupId);
    const path = req.path;

    if (!isAllowed(path, req.method, loggedIn, config.publicAccess, admin)) {
      /*
       * Innlogget uten admintilgang gir 403, ikke 404: brukeren finnes og ba om
       * noe som finnes, og en «fant ikke»-melding ville sendt hen på leting
       * etter en feil som ikke er der.
       */
      const forbidden = loggedIn && !admin && isAdminRequest(path, req.method);
      if (forbidden) {
        res.status(403).json({
          message: 'Du har ikke tilgang til å administrere åpningstider.',
        });
        return;
      }
      res.status(loggedIn ? 404 : 401).json({
        message: loggedIn
          ? 'Fant ikke ressursen.'
          : 'Du må logge inn som ansatt for å gjøre dette.',
      });
      return;
    }

    const target = new URL(`/api${path}`, config.backendUrl);
    const activityRequest = path === '/openinghours/activity' || path.startsWith('/openinghours/activity/');
    for (const [key, value] of Object.entries(req.query)) {
      const activityParam = isActivityRange(path) && (key === 'from' || key === 'to');
      if (
        typeof value === 'string' &&
        !FORBIDDEN_PARAMS.has(key) &&
        (!activityRequest || activityParam)
      ) {
        target.searchParams.set(key, value);
      }
    }

    // Regel-endepunktene tar query-parametre framfor JSON-body, så en tom body er
    // normalt. express.json() setter `req.body` til `{}` når det ikke kom noen
    // JSON — det må skilles fra en reell, tom array (`POST /group` med ingen
    // medlemmer), som er en gyldig body.
    const body: unknown = req.body;
    const hasBody =
      !SAFE_METHODS.has(req.method) &&
      (Array.isArray(body) ||
        (typeof body === 'object' && body !== null && Object.keys(body).length > 0));

    const requestId = randomUUID();
    res.setHeader('X-Request-ID', requestId);
    const started = performance.now();
    const concurrentRequests = ++activeRequests;
    let phase = 'response_headers';
    let upstreamStatus: number | undefined;
    let errorCode: string | undefined;

    try {
      const upstream = await fetch(target, {
        method: req.method,
        headers: {
          'X-API-Key': config.apiKey,
          Accept: 'application/json',
          ...(hasBody ? { 'Content-Type': 'application/json' } : {}),
        },
        body: hasBody ? JSON.stringify(body) : undefined,
        signal: AbortSignal.timeout(15_000),
      });

      upstreamStatus = upstream.status;
      phase = 'response_body';
      const text = await upstream.text();
      phase = 'response_processing';
      if (!text) {
        res.status(upstream.status).end();
        return;
      }

      let parsed: unknown;
      try {
        parsed = JSON.parse(text);
      } catch {
        res.status(upstream.status).type('text/plain').send(text);
        return;
      }

      // Maskeringen er en no-op så lenge alle er innlogget, men den er det
      // eneste som skiller interne tider fra offentlige når kalenderen åpnes.
      res.status(upstream.status).json(upstream.ok ? maskResponse(parsed, loggedIn) : parsed);
    } catch (error) {
      errorCode = proxyErrorCode(error);
      res.status(502).json({ message: 'Vi fikk ikke kontakt med åpningstidstjenesten.' });
    } finally {
      activeRequests--;
      const level = errorCode ? 'error' : res.statusCode >= 400 ? 'warn' : 'info';
      // Stier og feil kan inneholde brukerdata. Logg bare rutemønster og kjente feilkoder.
      console[level](JSON.stringify({
        timestamp: new Date().toISOString(),
        level,
        event: 'proxy_completed',
        requestId,
        pod: process.env.HOSTNAME,
        method: req.method,
        route: path.split('/').map((segment) =>
          !segment || LOG_ROUTE_SEGMENTS.has(segment) ? segment : ':id',
        ).join('/'),
        upstreamOrigin: target.origin,
        status: res.statusCode,
        upstreamStatus,
        durationMs: Math.round(performance.now() - started),
        concurrentRequests,
        ...(errorCode ? { errorCode, phase } : {}),
      }));
    }
  };
}
