/**
 * Tynn fetch-wrapper mot BFF-en.
 *
 * Klienten kaller alltid `/api/...` på sitt eget opphav — aldri backend direkte.
 * API-nøkkelen finnes kun i BFF-prosessen.
 */

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly technical?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** Backends `ResponseStatusException` → forståelig norsk melding. */
function userMessage(status: number, technical?: string): string {
  switch (status) {
    case 400:
      return 'Forespørselen var ugyldig. Kontroller datoene og prøv igjen.';
    case 401:
    case 403:
      return 'Du har ikke tilgang til dette. Logg inn som ansatt og prøv igjen.';
    case 404:
      return 'Fant ikke det du spurte etter. Det kan ha blitt slettet.';
    case 409:
      // 409 fra sletting bærer en meningsfull melding om hva som er i bruk.
      return technical ?? 'Endringen kolliderer med noe som allerede finnes.';
    case 503:
      return 'Tjenesten er utilgjengelig akkurat nå. Prøv igjen om litt.';
    default:
      return status >= 500
        ? 'Noe gikk galt hos oss. Prøv igjen om litt. Hvis det fortsetter, meld saken til teamet som eier tjenesten.'
        : 'Vi klarte ikke å hente dataene. Prøv igjen om litt.';
  }
}

async function readError(response: Response): Promise<string | undefined> {
  try {
    const body = await response.json();
    if (typeof body === 'string') return body;
    return body?.message ?? body?.error ?? undefined;
  } catch {
    return undefined;
  }
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { Accept: 'application/json', ...init?.headers },
  });

  if (!response.ok) {
    const technical = await readError(response);
    throw new ApiError(userMessage(response.status, technical), response.status, technical);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function query(params: Record<string, string | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) search.set(key, value);
  }
  const s = search.toString();
  return s ? `?${s}` : '';
}
