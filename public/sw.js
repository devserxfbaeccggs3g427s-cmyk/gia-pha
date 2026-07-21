/* Kinship PWA service worker. Keep this file dependency-free: it is served
 * directly from /public and must remain usable before the Next.js bundle has
 * loaded. */
const VERSION = 'kinship-v3';
const SHELL_CACHE = `${VERSION}-shell`;
const RUNTIME_CACHE = `${VERSION}-runtime`;
const DATA_CACHE = `${VERSION}-data`;
const PRECACHE_URLS = [
  '/manifest.json',
  '/icons/icon-192.svg',
  '/icons/icon-512.svg',
  '/offline.html',
  '/vi/login',
  '/en/login'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE)
      .then((cache) => cache.addAll(PRECACHE_URLS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys
        .filter((key) => key.startsWith('kinship-') && ![SHELL_CACHE, RUNTIME_CACHE, DATA_CACHE].includes(key))
        .map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('message', (event) => {
  if (event.data?.type === 'SKIP_WAITING') self.skipWaiting();
  if (event.data?.type === 'CLEAR_PRIVATE_CACHES') {
    event.waitUntil?.(Promise.all([caches.delete(RUNTIME_CACHE), caches.delete(DATA_CACHE)]));
  }
  if (event.data?.type === 'PRECACHE_URLS' && Array.isArray(event.data.urls)) {
    event.waitUntil?.(caches.open(SHELL_CACHE).then((cache) => cache.addAll(event.data.urls)));
  }
});

// Background Sync cannot read the Zustand/localStorage queue directly. It
// wakes open clients instead; the React hook then replays the typed queue via
// the normal API mutation functions.
self.addEventListener('sync', (event) => {
  if (event.tag !== 'kinship-mutation-sync') return;
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true })
      .then((clients) => clients.forEach((client) => client.postMessage({ type: 'KINSHIP_SYNC_REQUEST' })))
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) return;

  const url = new URL(request.url);
  if (url.pathname.startsWith('/api/auth') || url.pathname.startsWith('/api/share')) return;

  if (request.mode === 'navigate') {
    event.respondWith(networkFirst(request, RUNTIME_CACHE, '/offline.html'));
    return;
  }

  if (url.pathname.startsWith('/api/')) {
    // API responses are private to this browser, but caching them locally is
    // what lets a previously viewed family tree open without connectivity.
    event.respondWith(networkFirst(request, DATA_CACHE));
    return;
  }

  if (url.pathname.startsWith('/_next/static/') || isStaticAsset(url.pathname)) {
    event.respondWith(cacheFirst(request, RUNTIME_CACHE));
    return;
  }

  // Next.js client navigation and prefetch requests carry the `_rsc` query
  // parameter (or the RSC header) and are not browser navigations. Caching
  // them keeps already visited routes usable when a user taps through the app
  // while offline.
  if (url.searchParams.has('_rsc') || request.headers.get('RSC') === '1') {
    event.respondWith(networkFirst(request, RUNTIME_CACHE));
  }
});

async function networkFirst(request, cacheName, fallbackUrl) {
  const cache = await caches.open(cacheName);
  try {
    const response = await fetchWithTimeout(request, 10000);
    if (response.ok && response.type !== 'opaque') await cache.put(request, response.clone());
    if (response.status >= 500) throw new Error(`Server responded with ${response.status}`);
    return response;
  } catch {
    const cached = await cache.match(request);
    if (cached) return cached;
    if (fallbackUrl) {
      const fallback = await caches.match(fallbackUrl);
      if (fallback) return fallback;
    }
    return new Response('Offline', { status: 503, statusText: 'Offline', headers: { 'Content-Type': 'text/plain' } });
  }
}

async function cacheFirst(request, cacheName) {
  const cache = await caches.open(cacheName);
  const cached = await cache.match(request);
  if (cached) return cached;
  const response = await fetch(request);
  if (response.ok && response.type !== 'opaque') {
    await cache.put(request, response.clone());
  }
  return response;
}

function fetchWithTimeout(request, timeoutMs) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('Network timeout')), timeoutMs);
    fetch(request).then((response) => {
      clearTimeout(timeout);
      resolve(response);
    }, (error) => {
      clearTimeout(timeout);
      reject(error);
    });
  });
}

function isStaticAsset(pathname) {
  return /\.(?:css|js|mjs|woff2?|png|jpe?g|webp|svg|ico)$/i.test(pathname);
}
