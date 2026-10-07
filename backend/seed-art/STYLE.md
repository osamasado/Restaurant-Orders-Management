# Seed art: style guide

Illustrations for the demo menu and raw materials (issue #32). Drawn as SVG, rendered to PNG by `render.mjs`, and attached by `DemoDataSeeder`. One consistent flat style so the menu looks like one set.

## Files

- `meals/<slug>.svg`: **viewBox `0 0 800 600`** (4:3), one per meal.
- `raw-materials/<slug>.svg`: **viewBox `0 0 400 400`** (square), one per raw material.
- The slug list is in `../src/main/resources/seed-images/catalog.json` (the same file the seeder reads). Use exactly those slugs.

## Rules

- Flat vector illustration: solid fills, optional 2 to 3 tone shading with extra shapes (a darker shade on one side, a light highlight), soft shadow as a low-opacity ellipse. No outlines heavier than 4px; most shapes have no stroke.
- Pure SVG only: no text, no letters or digits, no `<image>`, no external fonts or links, no scripts, no filters. Gradients are allowed but keep them subtle. Keep each file under 20 KB.
- Always start with a full-bleed background `<rect width=... height=... fill=...>` (the picture is cropped by the app, so the background must cover the whole canvas).
- **Meals:** top-down view of the dish on a plate, board or bowl (a drink may be shown from the side). The photo slot is cropped by the app (78px square on the menu, a wide 2:1 band in the detail view, 46px in admin), so keep every important part of the dish inside the circle of radius 190 around the centre (400, 300). The background (a table surface: wood, linen, slate) may be plain or have a few simple shapes, but must not compete with the dish.
- **Raw materials:** one clear object or a small group (for example a few apples), centred, inside the central 280 x 280 square, on a flat soft tint. Easy to recognise at 46px: a strong silhouette and one or two recognisable details.
- Recognisable first, pretty second: a viewer must know what it is at a glance (a pumpkin has ribs and a stem; a schnitzel has a golden crumb with bubbles and a lemon wedge).

## Palette (use these as the base, shade freely around them)

- Paper / linen: `#F5F0E8`, `#EFE9DF`, `#E4DCCD`
- Ink: `#1B1A17`, `#3A3732`
- Forest green: `#1F4D3A`, `#2F6B50`, `#7FB899` (herbs, leaves, cucumber)
- Amber / gold: `#D98F00`, `#F0B93A`, `#F7D98B` (crumb, butter, pastry)
- Clay / red: `#B5432C`, `#D8664A`, `#7A2A1B` (meat, berries, wine, bacon)
- Warm browns: `#8A5A2B`, `#B98350`, `#5A3A1E` (wood, crust, sauce)
- Cream / white: `#FFF8EC`, `#FFFFFF` (plates, milk, cream)
- Slate: `#4A4F55`, `#6B7178` (a slate or stone surface)

Meal backgrounds alternate between wood, linen and slate so neighbours differ. Raw-material backgrounds are a soft tint of the item's own colour family (`#F5F0E8`-light, never saturated).

## Checking your work

```
PLAYWRIGHT=/home/sado/.npm/_npx/6bcb61ec6d5aea22/node_modules/playwright \
BROWSER=/home/sado/.cache/ms-playwright/chromium_headless_shell-1234/chrome-headless-shell-linux64/chrome-headless-shell \
node render.mjs --out <preview-dir> meals/pumpkin-soup.svg raw-materials/onion.svg
```

It writes `<preview-dir>/<folder>/<slug>.png`. Look at the PNG, and also at a 46px version of it (`--small` adds `<slug>.46.png`). Revise until it is recognisable, at most two rounds.
