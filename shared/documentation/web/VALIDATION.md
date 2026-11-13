# Executed web validation

Executed locally on 2026-11-13 (Asia/Dubai), macOS / Apple Silicon, Node 26.4.0, pnpm 12.18.0. This records executed checks; implementation and release certification are separate.

| Check | Executed result |
|---|---|
| Registry metadata / framework references / plugin peer ranges | Stable versions checked before use. Next 16.4.6 / React 19.3.1 / Tailwind 4.3.5; TypeScript 6.0.3 and ESLint 9.39.4 chosen for compatible plugin peers |
| `pnpm install --frozen-lockfile` | Passed; resolution skipped, lockfile current |
| `pnpm peers check` | Passed; no peer dependency issues |
| `pnpm check:web` | Strict TypeScript passed; ESLint passed with zero warnings; 41 Vitest tests passed in 3 files |
| `pnpm --filter @rayan/web format:check` | Passed |
| `pnpm build:web` | Optimized production build passed, all 13 application/API route entries generated |
| Standalone production Node server | Actual traced server started with production runtime environment; login HTTP 200, CSP present, unauthenticated session API HTTP 401; process terminated after check |
| `pnpm test:web:e2e` | Final run: **12 passed, 0 failed, 0 skipped, 0 flaky**, 28.8 seconds; Chromium, Firefox, WebKit |
| Actual media decoding | H.264 MP4 and fMP4 HLS reached readyState ≥ 2 and MP4 reported nonzero decoded video dimensions in each engine; live preview stopped and released video on request |
| Responsive UI | 1440×1000, 820×1180, 390×844 checked in each engine, no document horizontal overflow; navigation visible |
| Accessibility | Axe WCAG 2 A/AA and 2.1 AA tagged rules: zero violations on the tested mobile Home screen in all three engines; this is not a whole-app accessibility certification |
| Large catalog | Real HTTP fixture supplied 105 titles: virtualized 48-item page rendered fewer than 48 cards, scrolled to final item, advanced and returned between 3 pages |
| Search and artwork | Debounced search reached the expected one-title result; real fixture JPEG was resized to WebP; internal upstream URL header absent from browser response |
| Account journeys | Provider login; encrypted HttpOnly/Lax session; movie details; favorites; watched toggle; series season selection; EPG; opt-in preview; theme persistence; Arabic RTL; logout cookie removal |
| API boundary | Missing session 401, cross-site mutation 403, unapproved metadata-service origin 403; hostname-based fixture requests exercise DNS/socket lookup |
| Pure domain / security tests | History limit/independent resume, completion, live no-resume, exact continuation, recommendations, normalization/escaping, authenticated encryption, purpose/account binding, private address rejection, HLS rewriting, paging/filtering, trailers, appearance contract agreement, artwork bounds/cache isolation |
| Shared contracts | `python3 scripts/check-contracts.py` passed |
| Android preservation | `scripts/build-android.sh assembleDebug testDebugUnitTest` passed; 39 JVM tests in current debug reports |
| iOS preservation | `scripts/build-ios.sh build` passed for generic iOS Simulator |
| Diff whitespace | `git diff --check` passed |

## Evidence

The sanitized [browser summary](validation/browser-summary.json), [mobile Home screenshot](validation/home-mobile.png) and [desktop search screenshot](validation/search-desktop.png) are retained. All imagery/catalog labels in these captures come from the local test provider and licensed sample assets; they are not production demo data. Full Playwright HTML reports and traces are ignored locally and uploaded by the Web CI workflow when it runs. No production account was supplied or used.

Earlier runs caught a compact search link missing an accessible name, selectors that asserted before route completion, a form-label selector mismatch, an IPv6/IPv4 fixture socket mismatch, and an owned preview server holding Next's development lock. These were corrected; the final complete run above passed. Firefox on macOS needed isolated test app-data directories; the personal Firefox profile is untouched. The in-app preview plugin could not connect because its installed runtime module was missing; screenshot review and the executed Playwright suites supplied visual and browser evidence instead.

## Limits and release follow-up

No commercial provider, branded Edge/Safari device, physical phone microphone/PiP, long-duration playback, multivariant/multitrack commercial stream, DRM license service, or cloud host was certified. WebKit/Chromium are engine coverage, not physical Safari/Edge certification. The test media is a short single-video-track licensed sample; track-selection source paths compile but are not certified against every provider manifest. EPG titles/plots/artwork remain provider supplied. Primary controls and navigation have English/Arabic/RTL support; complete linguistic review is outstanding. No measured Core Web Vitals score or load-test capacity is claimed.

Docker is not installed in this workspace, so the Docker recipe has not been built here. The equivalent standalone Node output was exercised as stated. Remote GitHub Actions and an actual staging/production deployment have not been run. HTTPS ingress, distributed rate/bandwidth limits, secret injection, approved provider/CDN origins and your provider's stream rights/configuration must be supplied for deployment; see DEPLOYMENT.md. No production secrets or fake production login were added. Work is local in the shared repository; this task has not created a remote deployment.

## Phase outcomes

| Phase | Delivered files / outcome |
|---|---|
| Analysis | FEATURE_PARITY.md, native model/provider/library review; browser boundaries documented |
| Monorepo | Root pnpm workspace/lockfile, apps/web, four shared packages; mobile build entrypoints retained |
| Foundation | Server API/session/security/provider/artwork modules, query/persistence services, scoped library/preferences stores |
| UI | App Router screens, responsive cinema shell, source-owned shadcn-style Button/Dialog, shared-token theme |
| Features | Real provider catalogs/details/EPG/playback, library/series continuation, filters/search/virtualization, appearance files and settings |
| QA | Vitest, component/security/domain tests, three-engine Playwright suite, screenshot review, native regression builds |
| Delivery | Web-specific CI, standalone Docker recipe, environment example, architecture/deployment/validation documents |
