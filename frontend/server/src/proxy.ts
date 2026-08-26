import type { Request, Response } from 'express';
import type { Config } from './config.ts';
import { isLoggedIn } from './auth.ts';
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

const SAFE_METHODS = new Set(['GET', 'HEAD']);

/**
 * Sletting av noe som er i bruk skal alltid stoppes av backendens 409.
 * `?confirm=true` overstyrer den sperren, og designet sier eksplisitt at
 * en «slett likevel»-knapp ikke skal finnes — så parameteren slippes aldri gjennom.
 */
const FORBIDDEN_PARAMS = new Set(['confirm']);

/**
 * @param publicAccess Er kalenderen åpen for uinnloggede? Er den ikke det —
 *   som i dag, bak `ansatt.nav.no` — krever alt innlogging. Sjekken er da
 *   forsvar i dybden mot kall som omgår Wonderwall-sidecaren.
 */
export function isAllowed(
  path: string,
  method: string,
  loggedIn: boolean,
  publicAccess = false,
): boolean {
  // Alt som endrer data krever innlogging, uten unntak.
  if (!SAFE_METHODS.has(method)) {
    return loggedIn && MUTABLE_ROUTES.some((r) => r.test(path));
  }
  if (CALENDAR_ROUTES.some((r) => r.test(path))) return loggedIn || publicAccess;
  if (ADMIN_ROUTES.some((r) => r.test(path))) return loggedIn;
  return false;
}

export function createProxy(config: Config) {
  return async function proxy(req: Request, res: Response): Promise<void> {
    const loggedIn = isLoggedIn(req);
    const path = req.path;

    if (!isAllowed(path, req.method, loggedIn, config.publicAccess)) {
      res.status(loggedIn ? 404 : 401).json({
        message: loggedIn
          ? 'Fant ikke ressursen.'
          : 'Du må logge inn som ansatt for å gjøre dette.',
      });
      return;
    }

    const target = new URL(`/api${path}`, config.backendUrl);
    for (const [key, value] of Object.entries(req.query)) {
      if (typeof value === 'string' && !FORBIDDEN_PARAMS.has(key)) {
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

      const text = await upstream.text();
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
      // Den tekniske årsaken logges, men sendes aldri til nettleseren.
      console.error('Proxy-kall feilet', { path, error });
      res.status(502).json({ message: 'Vi fikk ikke kontakt med åpningstidstjenesten.' });
    }
  };
}
