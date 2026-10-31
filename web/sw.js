/* Poch! · funciona sin Internet: guarda la app en el móvil la primera vez que se abre. */
const CACHE = 'poch-1.1.0';
const FILES = [
  './', 'index.html', 'manifest.webmanifest', 'css/app.css',
  'js/engine.js', 'js/ui.js', 'js/game.js', 'js/screens.js', 'js/main.js',
  'fonts/Inter-Regular.woff', 'fonts/Inter-Medium.woff', 'fonts/Inter-SemiBold.woff', 'fonts/Inter-Bold.woff', 'fonts/Inter-ExtraBold.woff',
  'icons/poch-256.png', 'icons/icon-180.png', 'icons/icon-192.png', 'icons/icon-512.png', 'icons/maskable-512.png',
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

// Primero la red (para recibir las novedades) y, sin conexión, lo guardado.
self.addEventListener('fetch', (e) => {
  if (e.request.method !== 'GET') return;
  e.respondWith(
    fetch(e.request).then((r) => {
      if (r.ok && new URL(e.request.url).origin === location.origin) {
        const copy = r.clone();
        caches.open(CACHE).then((c) => c.put(e.request, copy));
      }
      return r;
    }).catch(() => caches.match(e.request, { ignoreSearch: true }).then((r) => r || caches.match('index.html')))
  );
});
