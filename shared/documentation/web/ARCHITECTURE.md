# Web architecture

`apps/web` is an independent Next.js App Router Node application. Server Components own route composition and validate route parameters. Interactive screens use React, Zustand, TanStack Query and React Hook Form/Zod. The player is split into an on-demand module; hls.js loads only when native HLS is unavailable. Tailwind v4 and source-owned shadcn-style Button/Dialog components use the existing cinema tokens. Radix supplies dialog focus trapping, Escape and focus restoration. No third-party media images or fake production catalogs are bundled.

## Request boundary

Browser → same-origin `/api` → approved Xtream provider. Only the server constructs credential-bearing upstream URLs. An AES-256-GCM authenticated HttpOnly SameSite=Lax cookie stores the account; production requires an operator-supplied secret and Secure cookies. Cookies last at most seven days. The browser receives an HMAC account scope, safe catalog fields, and opaque encrypted session-bound media URLs. Passwords never enter localStorage, IndexedDB, client bundles, analytics or application logs. The BFF never returns raw exceptions/upstream URLs. Reverse proxies must also redact API media paths and never log Cookie headers.

Login/logout/refresh validate Origin. Login bodies and field lengths are bounded. A conservative in-process login limiter protects the endpoint; production ingress must enforce distributed IP limits and request body limits. TRUST_PROXY remains off unless ingress overwrites forwarded headers. An exact-origin operator allowlist prevents an open proxy. Upstream sockets resolve DNS and validate all addresses before connecting to the selected address, including redirects. Production rejects private/link-local/loopback and IPv4-mapped IPv6 destinations. Trusted origins do not grant private-network access. No external authentication, DRM circumvention, provider licensing or arbitrary-origin proxy is included.

Provider requests have timeouts, JSON responses are capped at 25 MB, and playlists at 2 MB. Process-local catalog caching is bounded to 80 MB / 24 entries with a two-minute TTL. Account data is isolated by HMAC scope. Large catalog display is filtered/paged server-side (48 entries), with TanStack row virtualization and lazy artwork; catalogs must fit the documented JSON budget. If a provider exceeds it, add a persistent catalog ingestion service rather than raising every limit without measurements. Artwork is decoded with Sharp under a 20-megapixel / 10 MB input bound and resized to bounded WebP widths. Its separate 16 MB / 64-entry two-minute cache is account-scoped and cleared at logout/refresh. Each node has its own transient cache; sticky sessions are unnecessary with a common SESSION_SECRET. Operators should cap concurrency/bandwidth at ingress before public release.

## Media

Video ranges and selected safe response headers are forwarded without buffering full videos. HLS variant/segment/map/key URI references are rewritten to opaque same-origin URLs. Every child/redirect origin is checked. Provider/CDN CORS is handled at this authorized server boundary; browser media never talks to an arbitrary third-party origin. No transcoding takes place. HLS DRM still needs a supported license integration; no commercial DRM configuration is claimed. DASH is not emitted by the existing native Xtream contract; an unsupported VOD extension gets an actionable error. External subtitles need provider manifest/native track support; the app does not scrape subtitle sites.

Network recovery has two automatic retries, plus explicit manual retry. Video/hls instances and timers are destroyed on route/channel changes; preview never starts until requested. HTML5 fullscreen/PiP are capability gated. Browser controls, user gestures, codecs and OS policies remain real constraints. Native Safari HLS manages adaptive quality; hls.js exposes actual manifest levels. Track choices appear only when available. Keyboard: Space/K play, arrows seek, M mute, F fullscreen, N/P queue. Lock hides controls except unlock. Background playback is browser controlled.

## Persistence and privacy

IndexedDB stores account-scoped favorites, history (100), independent resume positions, watched keys, selected seasons and recent searches (10). Writes are serialized; logout waits for pending writes and deletes that account. Appearance is non-sensitive localStorage, strictly validated on import against the shared schema (32 KB limit). Local data remains on the current browser/device; this is not cloud synchronization. Storage failures surface a visible notice. Browser caches receive `no-store` API responses. CSP restricts scripts, resources and connections to self, with media blob workers for hls.js. Next hydration currently needs inline scripts; a nonce-based CSP is a future hardening option, not claimed implemented. React escapes provider strings and no raw HTML renderer is used.

## Shared packages

- `@rayan/shared-types`: browser-safe models and pure library rules.
- `@rayan/api-contracts`: imports the existing canonical shared JSON contracts.
- `@rayan/design-tokens`: imports the existing canonical cinema JSON.
- `@rayan/eslint-config`: Next/TypeScript conventions. React Compiler is not enabled; TanStack Virtual compatibility is explicitly documented in local lint overrides.

Mobile remains native; no JS dependencies or workspace tools are required to build Android/iOS. Browser-local favorites and native app libraries do not synchronize across devices.
