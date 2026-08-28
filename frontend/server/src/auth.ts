import type { Request } from 'express';

/**
 * Innlogging håndteres av Wonderwall-sidecaren, som terminerer Azure AD-flyten
 * og setter `Authorization: Bearer <token>` på requester som slipper gjennom.
 *
 * BFF-en tar ikke imot tokens fra nettleseren og validerer ingen signatur selv —
 * sidecaren står mellom, og alt som når oss har allerede passert den.
 */

export interface User {
  name: string;
}

function decodeName(token: string): string {
  try {
    const payload = token.split('.')[1];
    if (!payload) return 'Innlogget';
    const json = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
    return json.name ?? json.preferred_username ?? 'Innlogget';
  } catch {
    return 'Innlogget';
  }
}

export function getUser(req: Request): User | null {
  const header = req.header('authorization');
  if (!header?.toLowerCase().startsWith('bearer ')) return null;
  const token = header.slice(7).trim();
  if (!token) return null;
  return { name: decodeName(token) };
}

export function isLoggedIn(req: Request): boolean {
  return getUser(req) !== null;
}
