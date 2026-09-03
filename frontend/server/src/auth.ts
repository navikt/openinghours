import type { Request } from 'express';

/**
 * Innlogging håndteres av Wonderwall-sidecaren, som terminerer Azure AD-flyten
 * og setter `Authorization: Bearer <token>` på requester som slipper gjennom.
 *
 * BFF-en tar ikke imot tokens fra nettleseren og validerer ingen signatur selv —
 * sidecaren står mellom, og alt som når oss har allerede passert den. Derfor er
 * det trygt å lese claims ut av tokenet uten å verifisere signaturen på nytt.
 *
 * Å *se* kalenderen krever bare innlogging. Å *endre* noe krever i tillegg
 * medlemskap i admingruppen — se `isAdmin`.
 */

export interface User {
  name: string;
  /** Gruppe-ID-ene fra `groups`-claimet. Tom liste når claimet mangler. */
  groups: string[];
}

function decodePayload(token: string): Record<string, unknown> | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
  } catch {
    return null;
  }
}

function decodeName(claims: Record<string, unknown> | null): string {
  const name = claims?.name ?? claims?.preferred_username;
  return typeof name === 'string' && name ? name : 'Innlogget';
}

/**
 * Entra ID utelater `groups` helt når brukeren ikke er medlem av noen av
 * gruppene appen har bedt om, så et manglende claim betyr «ingen grupper» —
 * ikke at noe er galt.
 */
function decodeGroups(claims: Record<string, unknown> | null): string[] {
  const groups = claims?.groups;
  if (!Array.isArray(groups)) return [];
  return groups.filter((g): g is string => typeof g === 'string');
}

export function getUser(req: Request): User | null {
  const header = req.header('authorization');
  if (!header?.toLowerCase().startsWith('bearer ')) return null;
  const token = header.slice(7).trim();
  if (!token) return null;
  const claims = decodePayload(token);
  return { name: decodeName(claims), groups: decodeGroups(claims) };
}

export function isLoggedIn(req: Request): boolean {
  return getUser(req) !== null;
}

/**
 * Administrasjon krever medlemskap i en bestemt Entra ID-gruppe, ikke bare
 * innlogging. Gruppen listes i `azure.application.claims.groups` i nais.yaml —
 * uten den kommer `groups`-claimet aldri i tokenet, og ingen blir admin.
 *
 * Er `adminGroupId` tom, faller vi tilbake til «alle innloggede er admin».
 * Det er oppførselen appen hadde før gruppestyringen, og den holder lokal
 * utvikling kjørbar uten en ekte Entra ID-gruppe.
 */
export function isAdmin(req: Request, adminGroupId: string): boolean {
  const user = getUser(req);
  if (!user) return false;
  if (!adminGroupId) return true;
  return user.groups.includes(adminGroupId);
}
