# Restaurant Orders Management — Frontend

React 19 + TypeScript + Vite. Four route groups, one per screen:

| Route | Screen | Default theme |
|---|---|---|
| `/guest` | Guest ordering (table-side device) | light |
| `/kitchen` | Kitchen display (wall monitor) | dark |
| `/hall` | Hall status board | dark |
| `/admin/*` | Management backend (7 nested sections) | light |

`/` is a dev-only landing page linking to the four screens — not a production route.

## Running locally

```bash
npm install
npm run dev
```

Dev server runs on port 5174 and proxies `/api` to the backend at `http://localhost:8080` (see `vite.config.ts`) — run the backend separately (`backend/`) for any screen that calls the API.

## Design system

Colors, fonts, radii, spacing, and shadows live in `src/styles/tokens.css` as CSS custom properties, ported from `Documentation/Design/README.md`. Each screen picks a default light/dark theme on first load; `src/theme/` (`ThemeProvider`/`useTheme`/`ThemeToggle`) implements a manual override, stored under one shared `localStorage` key so a choice made on one screen carries over to the others.

## Scripts

- `npm run dev` — start the dev server
- `npm run build` — type-check (`tsc -b`) and build for production
- `npm run lint` — ESLint
- `npm run preview` — serve the production build locally
