# Shared visual specification and native variations

The existing Android design uses artwork-led discovery with a purple/cyan cinematic palette. `shared/design-tokens/cinema.json` records the actual semantic colors and spacing taken from `DesignSystem.kt`. iOS applies those roles in `App/DesignSystem.swift`: a near-black background, dark blue cards, violet primary actions, cyan content labels, light violet action text, 12-point poster corners and 24-point page padding. System fonts use Dynamic Type rather than shipping duplicate font files. The existing Android TV/play vector is reproduced in the iOS app icon.

## Responsive layouts

Compact: native Home / Live / Movies / Series / More tabs → navigation stack → details → full screen player. More exposes favorites, search, history and settings. iPad regular width: native sidebar → detail navigation stack. Browser columns use available width; episode and action groups stack when horizontal space or large text requires it. Safe areas and system keyboard avoidance are native SwiftUI behavior. Arabic uses platform RTL layout and localized labels; provider titles retain their supplied writing direction.

Home: bounded artwork hero with title/metadata, previous/next controls and content rails. Posters use 2:3; channel artwork uses 16:9. Image loading/error fallbacks use meaningful film/TV symbols rather than fabricated artwork. High contrast makes secondary text primary. On dark cards, light violet action text has a measured **9.33:1** contrast ratio; saturated violet is reserved for primary fills with white text (**5.70:1**). Text size maps the user's 85–140% preference onto the system Dynamic Type ladder, retaining system accessibility sizes; explanatory body text also uses scaled metrics.

Artwork rotation is off by default. When enabled together with optional motion, it advances every 12 seconds; the system Reduce Motion preference disables it. It does not start video. Preview requires an explicit action and gives up ownership before fullscreen playback.

## Native differences and rationale

- Use native tab bars, sidebar, navigation/back, contextual menus, file import/export and permission prompts. HIG `tab-bars.md › Best practices`: “Include tab labels to help with navigation.” The four primary content destinations remain available while More groups secondary tools.
- HIG `split-views.md › Platform considerations`: “Account for narrow, compact, and intermediate window widths.” The shell switches based on horizontal size class; poster grids use adaptive columns.
- HIG `playing-video.md › Best practices`: “Use the system video player to give people a familiar and convenient experience.” AVPlayerViewController owns transport/audio/subtitle/PiP controls; app-specific stream/episode controls sit outside the video.
- iOS minimum action hit area is 44pt; Android retains its existing 48dp controls and TV focus rings. TV remote navigation is not simulated as an iPhone interface.
- iOS follows device language. English and Arabic labels reuse existing translations where the source English matches, with iOS-specific privacy/search strings. Provider-specific dynamic messages may retain English values; translation review is part of release QA.

References read from the installed Apple Design skill: accessibility, layout, typography, color, designing-for-ios, tab-bars, split-views and playing-video. Visual consistency is a design target; executed screenshot coverage and remaining device checks are stated in MIGRATION_RESULTS.md rather than inferred from code.
