# Issue #7: Set up i18n framework (DE/EN/AR) with RTL groundwork

## What was done

Integrated i18next/react-i18next across all four screens (previously hardcoded English from issue #6), added a language switcher (DE/EN/AR), and wired genuine direction-aware RTL for Arabic — `document.documentElement.dir` flips to `rtl`, and the CSS was audited so the mirrored layout is real (sidebar moves sides, nav order reverses, badges push to the correct trailing edge), not a mirrored-English visual hack. Language preference is one shared `localStorage` key across all four screens, same model as the theme toggle from issue #6, confirmed with the user directly.

Also applied the PR #41 review feedback starting with this ticket, as instructed: every UI string touched here dropped its em dashes in favor of commas/plain phrasing while it was already being rewritten into translation files.

## Why each dependency

| Dependency | Purpose |
|---|---|
| `i18next` 26.4.2, `react-i18next` 17.0.14 | The i18n framework itself — versions checked against the npm registry directly (peer deps confirmed compatible with the installed React 19.2.8 / TypeScript 6.0.2). |
| `i18next-browser-languagedetector` | Installed per the original plan, then **removed** in a follow-up commit — never actually wired into `i18next.use()`, since each screen's `LanguageProvider` already sets an explicit default on mount (matching `Config`'s backend default of `de`). Left in would have been an unused dependency. |

## The other files

- **`src/i18n/i18n.ts`** — `i18next.init()` with all three languages' resources, `fallbackLng: 'en'`.
- **`src/i18n/locales/{en,de,ar}.json`** — 37 keys each, namespaced by screen; verified programmatically to match exactly across all three files (no accidental gaps).
- **`src/i18n/language-context.ts` + `LanguageProvider.tsx` + `LanguageSwitcher.tsx`** — structured as an exact parallel to `theme/theme-context.ts` + `ThemeProvider.tsx` + `ThemeToggle.tsx` (same shared-`localStorage`-key pattern, same per-screen mount, same hook/context file split for `react-refresh/only-export-components`). `LanguageProvider` is where the actual RTL switch happens — sets `document.documentElement.dir`/`lang`, the same place `ThemeProvider` already sets `data-theme`. `LanguageSwitcher` is three text pills (DE/EN/AR), matching `ThemeToggle`'s no-icons visual language rather than a native `<select>`.
- **`src/i18n/useT.ts`** — wraps `useTranslation()` with an explicit `i18n.exists(key, { lngs: [i18n.language] })` check; see the bug below for why this exists instead of just using `useTranslation()` directly. Every screen and `ThemeToggle`/`LanguageSwitcher` now import this instead.
- **`AdminScreen.css`** (`border-right` → `border-inline-end`) and **`KitchenScreen.css`** (`margin-left: auto` → `margin-inline-start: auto`) — the only two real RTL fixes needed. Grepping every CSS file for physical-direction properties first confirmed the rest of the layout (flexbox `justify-content`/`gap`/`align-items`) was already direction-agnostic by CSS spec.
- **`ComingSoonView.tsx`** — now takes `titleKey` instead of a literal `title` string; `App.tsx`'s admin route definitions pass translation keys (e.g. `admin.nav.orders`) instead of hardcoded English text.

## Verification performed

1. `npm run build` / `npm run lint` — clean after every commit.
2. Real headless-Chromium checks (Playwright, `chromium-cli` unavailable in this sandbox) confirmed, concretely, all three acceptance criteria:
   - Switching language via `LanguageSwitcher` changes visible text; confirmed the shared-preference behavior by switching to English on `/guest` then navigating to `/kitchen` and seeing it carry over.
   - Arabic *actually* mirrors the layout, not just sets an attribute: screenshotted `/admin` and `/kitchen` in Arabic and visually confirmed the sidebar moved to the physical right edge, nav/column order reversed to right-to-left reading order, and the kitchen column count badge correctly sits on the new trailing (left) edge — proving the `margin-inline-start`/`border-inline-end` fixes actually work, not just compile.
   - Missing-translation detection: temporarily removed `admin.nav.settings` from `ar.json`, confirmed in a live browser that the nav item rendered the English fallback text (not blank, not the raw key) *and* a console warning fired naming the exact key and language, then restored the key (verified all three locale files are back to 37/37 matching keys).
3. Root cause → fix, found while verifying step 2's missing-translation criterion for real rather than trusting the config:
   - i18next's own `missingKeyHandler`/`saveMissing` only fires when a key is missing from the *entire* fallback chain (confirmed by reading i18next's `translate()` source directly) — the realistic case this ticket needs to catch, a key present in English but missing from Arabic specifically, resolved silently via `fallbackLng` with zero warning under that config alone. Fixed by adding `useT.ts`, which explicitly checks `i18n.exists(key, { lngs: [i18n.language] })` — restricting the lookup to only the active language, bypassing i18next's automatic fallback expansion — and warns there. Re-verified against a live component afterward (not just re-reading the code) to confirm the fix actually works.
   - Also hit, and worked around: this sandbox's Windows-mounted filesystem (`/mnt/d`) doesn't reliably deliver file-change events to Vite's dev server — an edit to `i18n.ts` wasn't picked up until the dev server was killed and restarted, confirmed by `curl`-fetching the dev server's served module directly and seeing stale content. Any future verification step in this environment that edits a file needs a fresh dev server restart, not a reliance on HMR.
