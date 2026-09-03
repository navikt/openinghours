/** All konfigurasjon leses én gang ved oppstart, og feiler høyt hvis noe mangler. */

function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Mangler påkrevd miljøvariabel: ${name}`);
  }
  return value;
}

export interface Config {
  port: number;
  backendUrl: string;
  apiKey: string;
  staticDir: string;
  /** I dev kjøres klienten av Vite, og BFF-en skal ikke servere filer. */
  serveStatic: boolean;
  /**
   * Er kalenderen åpen for uinnloggede?
   *
   * I dag er den ikke det: hele appen ligger bak `ansatt.nav.no` med Wonderwall
   * og `autoLogin`, så alt som når oss er autentisert. Når kalenderen skal ut til
   * publikum, settes denne til `true` samtidig som ingressen flyttes til
   * `nav.no` og `autoLogin` skrus av — da åpnes de offentlige leserutene, og
   * interne åpningstider maskeres for uinnloggede.
   */
  publicAccess: boolean;
  /**
   * Entra ID-gruppen som gir tilgang til administrasjon.
   *
   * Tom streng betyr «alle innloggede er admin». Det er oppførselen appen hadde
   * før gruppestyringen, og den holder lokal utvikling kjørbar uten en ekte
   * gruppe. I prod settes den i nais.yaml, og gruppen må samtidig ligge i
   * `azure.application.claims.groups` — ellers kommer den aldri i tokenet.
   */
  adminGroupId: string;
}

export function loadConfig(): Config {
  const isDev = process.env.NODE_ENV !== 'production';
  return {
    port: Number(process.env.PORT ?? 3000),
    backendUrl: process.env.BACKEND_URL ?? 'http://localhost:8081',
    // I dev er nøkkelen den samme som i backendens env.local.
    apiKey: isDev ? (process.env.API_KEY ?? 'key') : required('API_KEY'),
    staticDir: process.env.STATIC_DIR ?? '../client/dist',
    serveStatic: !isDev,
    publicAccess: process.env.PUBLIC_ACCESS === 'true',
    adminGroupId: process.env.ADMIN_GROUP_ID ?? '',
  };
}
