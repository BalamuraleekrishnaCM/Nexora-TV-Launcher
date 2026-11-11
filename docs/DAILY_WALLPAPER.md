# Daily wallpaper specification

**Status:** Planned; not yet implemented.

## User experience
- Automatically rotate the home-screen background once per local calendar day (or after 24 hours, configurable).
- Offer Nature/Mountains, Space, Minimal/Abstract and Mixed categories.
- Provide manual Next, Previous, Favorite and Local/USB wallpaper selection.
- Offer Auto/1080p/4K quality, Wi-Fi-only downloads and adjustable dark overlay.
- Preserve the existing Nexora TV glass layout and readable text.

## Sources and licensing
- Prefer a bundled set of properly licensed images for offline first-run.
- Candidate sources: Wikimedia Commons with per-image license and attribution; NASA images after reviewing applicable media-use guidance; Unsplash/Pexels only under their current API terms and attribution requirements.
- Do not scrape image websites, embed API keys in a public APK or assume all photos are freely redistributable.
- Store attribution, source URL, author and license metadata where required.

## Lightweight architecture
- Schedule periodic work with WorkManager, respecting Android background scheduling limits; do not promise an exact midnight switch.
- On launcher resume, check local date and apply an already cached wallpaper if rotation is due.
- Keep one current and one fallback image, plus optional bounded favorites cache; clean up old downloads.
- Fetch once per day at most by default; use ETag/conditional requests where available.
- Decode to the actual screen dimensions and avoid retaining full-resolution bitmaps in memory.
- Use static backgrounds only, no video/live wallpapers or continuous animation.
- Perform network/decode operations off the main thread and avoid wake locks.
- Never blank the wallpaper on network failures.

## Settings
- Daily rotation enabled by default.
- Category: Nature/Mountains by default.
- Resolution: Auto by default.
- Wi-Fi-only: enabled by default.
- Dark overlay: 35% by default.
- Local wallpaper: opt-in via Android Storage Access Framework.

## Acceptance tests
- Offline boot displays last cached or bundled background.
- Wallpaper changes at most once per configured period, even across app restarts.
- No uncontrolled disk growth or repeated background network requests.
- Text contrast and focus highlights remain readable across varied photos.
- Works with remote-only navigation.
