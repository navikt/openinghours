// Local proxy that fixes GitHub Packages' broken classic tarball URL redirect
// for scoped packages. pnpm requests tarballs at the conventional npm path
// `/@scope/name/-/name-version.tgz`, but GitHub Packages redirects that path
// to an unscoped registry.npmjs.com URL (a bug), causing 404s. This proxy:
//  - Forwards metadata (packument) requests unchanged.
//  - For tarball requests, looks up the real `dist.tarball` URL from the
//    package metadata and streams that instead.
const http = require('http');
const https = require('https');

const TOKEN = process.env.NPM_AUTH_TOKEN;
const UPSTREAM = 'https://npm.pkg.github.com';
const PORT = 4873;
const agent = new https.Agent({ keepAlive: true, timeout: 30000 });

function safeEnd(res, status, message) {
  if (!res.headersSent) {
    res.writeHead(status);
  }
  res.end(message);
}

function upstreamGet(path, callback, onError) {
  const options = {
    agent,
    timeout: 30000,
    headers: {
      Authorization: `Bearer ${TOKEN}`,
      'User-Agent': 'gh-proxy',
      Accept: 'application/vnd.npm.install-v1+json',
    },
  };
  const req = https.get(UPSTREAM + path, options, callback);
  req.on('timeout', () => req.destroy(new Error('upstream timeout')));
  req.on('error', onError);
}

function fetchUrl(url, callback, onError, redirects = 0) {
  if (redirects > 5) {
    onError(new Error('too many redirects'));
    return;
  }
  const options = {
    agent,
    timeout: 30000,
    headers: {
      Authorization: `Bearer ${TOKEN}`,
      'User-Agent': 'gh-proxy',
    },
  };
  const req = https.get(url, options, (res) => {
    if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
      res.resume();
      fetchUrl(res.headers.location, callback, onError, redirects + 1);
      return;
    }
    callback(res);
  });
  req.on('timeout', () => req.destroy(new Error('upstream timeout')));
  req.on('error', onError);
}

const server = http.createServer((req, res) => {
  res.on('error', () => {});
  let url;
  try {
    url = decodeURIComponent(req.url);
  } catch (e) {
    safeEnd(res, 400, 'bad url');
    return;
  }
  const tarballMatch = url.match(/^\/(@[^/]+)\/([^/]+)\/-\/[^/]+-([0-9][^/]*)\.tgz$/);

  if (tarballMatch) {
    const [, scope, name, version] = tarballMatch;
    const metaPath = `/${scope}/${name}`;
    upstreamGet(
      metaPath,
      (metaRes) => {
        let body = '';
        metaRes.on('data', (chunk) => (body += chunk));
        metaRes.on('end', () => {
          try {
            const json = JSON.parse(body);
            const versionInfo = json.versions && json.versions[version];
            if (!versionInfo || !versionInfo.dist || !versionInfo.dist.tarball) {
              safeEnd(res, 404, 'version not found');
              return;
            }
            fetchUrl(
              versionInfo.dist.tarball,
              (tarRes) => {
                res.writeHead(tarRes.statusCode, tarRes.headers);
                tarRes.pipe(res);
                tarRes.on('error', () => res.destroy());
              },
              (err) => safeEnd(res, 502, `tarball fetch error: ${err.message}`)
            );
          } catch (e) {
            safeEnd(res, 500, String(e));
          }
        });
        metaRes.on('error', () => safeEnd(res, 502, 'metadata stream error'));
      },
      (err) => safeEnd(res, 502, `metadata fetch error: ${err.message}`)
    );
    return;
  }

  // Pass through everything else (metadata requests) unchanged.
  upstreamGet(
    url,
    (upRes) => {
      res.writeHead(upRes.statusCode, upRes.headers);
      upRes.pipe(res);
      upRes.on('error', () => res.destroy());
    },
    (err) => safeEnd(res, 502, `upstream error: ${err.message}`)
  );
});

server.on('clientError', (err, socket) => {
  if (socket.writable) socket.end('HTTP/1.1 400 Bad Request\r\n\r\n');
});

process.on('uncaughtException', (err) => {
  console.error('uncaughtException (ignored):', err.message);
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`gh-proxy listening on http://127.0.0.1:${PORT}`);
});
