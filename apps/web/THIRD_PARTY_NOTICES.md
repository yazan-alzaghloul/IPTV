# Third-party components and test media

Button composition (Slot + class-variance-authority) and Dialog composition follow the source-owned shadcn/ui conventions (MIT): https://github.com/shadcn-ui/ui. Radix, Lucide, React, Next.js, TanStack, Zustand, Motion, hls.js, Sharp and the remaining dependencies retain their package licenses in the installed distribution. No commercial artwork is bundled.

Browser tests use the repository's existing licensed sample imagery/video and its H.264 HLS derivative. See `shared/test-fixtures/hls/NOTICE.md` and the existing Android fixture attribution. Screenshots under `shared/documentation/web/validation` contain that fixture artwork and synthetic test catalog labels; they are never injected into production services.
