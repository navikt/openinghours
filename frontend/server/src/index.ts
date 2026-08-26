import { fileURLToPath } from 'node:url';
import path from 'node:path';
import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import { loadConfig, type Config } from './config.ts';
import { getUser } from './auth.ts';
import { createProxy } from './proxy.ts';

export function createApp(config: Config) {
  const app = express();

  app.set('trust proxy', 1);
  app.disable('x-powered-by');
  app.use(
    helmet({
      contentSecurityPolicy: {
        directives: {
          defaultSrc: ["'self'"],
          // Aksel injiserer stiler ved kjøretid.
          styleSrc: ["'self'", "'unsafe-inline'"],
          imgSrc: ["'self'", 'data:'],
          connectSrc: ["'self'"],
          fontSrc: ["'self'", 'data:'],
          objectSrc: ["'none'"],
          frameAncestors: ["'none'"],
        },
      },
    }),
  );
  app.use(express.json({ limit: '100kb' }));

  // Uten health-endepunktet får NAIS aldri startet poden.
  app.get('/internal/health', (_req, res) => {
    res.json({ status: 'UP' });
  });

  app.get('/me', (req, res) => {
    const user = getUser(req);
    res.json(user ? { loggedIn: true, name: user.name } : { loggedIn: false });
  });

  app.use(
    '/api',
    rateLimit({ windowMs: 60_000, limit: 300, standardHeaders: true, legacyHeaders: false }),
    createProxy(config),
  );

  if (config.serveStatic) {
    const here = path.dirname(fileURLToPath(import.meta.url));
    const dir = path.resolve(here, config.staticDir);
    app.use(express.static(dir, { index: false, maxAge: '1h' }));
    // SPA-fallback: all ruting skjer i klienten.
    app.get('*', (_req, res) => {
      res.sendFile(path.join(dir, 'index.html'));
    });
  }

  return app;
}

// Startes kun når filen kjøres direkte, slik at testene kan importere createApp.
if (process.argv[1] && import.meta.url.endsWith(path.basename(process.argv[1]))) {
  const config = loadConfig();
  createApp(config).listen(config.port, () => {
    console.log(`BFF lytter på http://localhost:${config.port} → ${config.backendUrl}`);
  });
}
