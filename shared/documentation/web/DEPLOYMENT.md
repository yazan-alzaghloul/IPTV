# Run and deploy Rayan TV Web

Use Node 26 and the root-pinned pnpm 12.18.0. Install pnpm using your normal package-manager tooling (`npm install --global pnpm@12.18.0` if needed). From the repository root:

```sh
pnpm install --frozen-lockfile
cp apps/web/.env.example apps/web/.env.local
# Set a random SESSION_SECRET and your exact PROVIDER_ORIGINS / MEDIA_ORIGINS.
pnpm dev:web
```

Open http://localhost:3000/login. Supply your own licensed Xtream provider credentials. There is no default production account and no catalog until login succeeds. The operator must approve the provider and its real CDN/artwork origins; missing approval produces a visible error. Never put secrets in NEXT_PUBLIC variables, source control, screenshots or issue reports. A temporary in-memory random key is available only during development; server restarts invalidate those development sessions.

```sh
pnpm check:web
pnpm --filter @rayan/web format:check
pnpm test:web:e2e
pnpm build:web
pnpm --filter @rayan/web start
```

Playwright starts its own Python fixture provider on 18765 and dev app on 3100. Close processes on those ports first. Install browser engines using `pnpm --filter @rayan/web exec playwright install chromium firefox webkit` (Linux CI also needs `--with-deps`). Fixtures never replace application services; tests exercise the same BFF and real media pipeline. Fixture private networking is explicitly enabled only in the test development process.

## Production / staging

Build from the repository root:

```sh
docker build -f apps/web/Dockerfile -t rayan-tv-web .
docker run --rm -p 3000:3000 --env-file /secure/path/rayan-production.env rayan-tv-web
```

Docker is a Node deployment; static-only hosts cannot run the credential/media relay. The standalone server is also suitable for a VM/container service behind an HTTPS ingress. Set APP_ORIGIN to the exact HTTPS site origin, SESSION_SECRET to at least 32 random characters shared across replicas, PROVIDER_ORIGINS and MEDIA_ORIGINS to the minimum approved exact origins. Use separate secrets and origins for staging and production. Keep secrets in the host's secret manager and inject them at runtime; no provider accounts are required at build time. Rotate the secret to invalidate all sessions. ALLOW_PRIVATE_PROVIDERS is ignored in production. Account data is browser-local; server caches are disposable.

TLS is required because production cookies are Secure. Configure ingress request-size limits, authenticated bandwidth budgets, connection/concurrency limits and distributed login throttling; process-local throttling is only a backstop. Do not expose internal Node debug ports. Avoid recording Cookie headers or upstream request URLs. Review your provider's relay rights and available CDN origins before configuring them. Test your own authorized provider in staging, including redirects, audio/subtitle tracks, long playback, expiry, signed segment tokens and concurrent session limits.

The Web GitHub workflow is path-scoped and independent of mobile pipelines. It checks lockfile/peers, TypeScript, lint, tests, formatting, build and three Playwright engines, then retains reports. No deployment is triggered until the owner configures a hosting target and runtime secrets. The Docker recipe and remote CI configuration require execution in their actual target environments; a local Next build alone does not certify those systems.

## Compatibility boundaries

Chromium approximates Chrome/Edge engines, WebKit approximates Safari engine and Firefox exercises Gecko. These are not branded-device certification. iPhone Safari autoplay/PiP/volume, external DRM, physical microphone permissions and provider codecs require real-device/provider testing. Native MP4/HLS playback and hls.js paths are recorded separately in VALIDATION.md. No fabricated trending/popularity is shown when provider data is absent. UI labels are bilingual in primary journeys; provider names/plots are not translated and a full Arabic linguistic review remains a release task.

Dependency references: [Next installation](https://nextjs.org/docs/app/getting-started/installation), [Next Node/Docker deployment](https://nextjs.org/docs/app/getting-started/deploying), [shadcn Tailwind v4](https://ui.shadcn.com/docs/tailwind-v4), [hls.js](https://github.com/video-dev/hls.js). Versions were checked against registry stable metadata; ESLint 9.39.4 and TypeScript 6.0.3 satisfy plugin peer ranges even though newer majors exist. pnpm lockfile pins all transitive resolutions.

Firefox on macOS 27 uses isolated test-only MOZ_APP_DATA / MOZ_LOCAL_APP_DATA directories to avoid reading the user's protected personal browser profile. This follows the [upstream Playwright issue](https://github.com/microsoft/playwright/issues/42768); the directories are removed at test process exit.
