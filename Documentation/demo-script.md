# Demo script

The demonstration goal from the proposal: *an order placed on a phone appears on the kitchen screen within seconds, and its number moves across the hall board as the kitchen advances it, all three screens visible side by side.* About ten minutes.

## Before the demo

### 1. Rehearse on a throwaway stack (day before)

`frontend/scripts/demo-rehearsal.mjs` runs the whole flow below in a real browser, with three screens side by side, and times every hand-off. It places orders, so run it against a **throwaway** stack (its own database), never the one you will present from. Its header explains the settings. It fails if any hand-off takes longer than 10 s (the screens refresh every 5 s), and saves a side-by-side screenshot after each step.

The last run: **22 of 22 checks passed**, hand-offs between 3.6 and 6.8 s (the slowest is the cancelled banner, which has to wait for the kitchen's next refresh):

| Hand-off | Time |
|---|---|
| Order confirmed on the phone, card appears on the kitchen screen | 3.6 s |
| Start pressed, number appears on the hall board under "In preparation" | 4.9 s |
| Same moment, the guest's timeline shows "In preparation" | 4.9 s |
| Ready pressed, number moves to "Ready" on the hall board | 4.4 s |
| Same moment, the guest's timeline shows "Ready" | 4.4 s |
| Picked up pressed, number leaves the hall board | 4.9 s |
| Same moment, the guest's timeline shows "Served" | 4.9 s |
| Admin cancels, the kitchen shows the red banner and the hall board drops the number | 6.8 s |
| Kitchen marks a meal sold out, it vanishes from a menu already open on a table device | 5.4 s |

### 2. Start from clean data (ten minutes before)

The boards must start empty, or the demo order sits under yesterday's rehearsal orders.

```
cd backend
docker compose down -v          # drops the database volume
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The dev profile seeds, on an empty database: 4 categories and 8 meals (DE/EN/AR, sizes, prices, **illustrations**), 31 raw materials (**with pictures**, unit, stock, supplier), a recipe for every meal size, 6 tables, and 4 staff accounts, all with PIN `1234`:

| Account | Role |
|---|---|
| O. Sado | Admin |
| M. Behr | Kitchen |
| L. Adler | Waiter (no screen yet) |
| T. Nowak | Cashier (no screen yet) |

Then the frontend. Use the **production build**: the installable app and its service worker only exist there.

```
cd frontend
npm run build
npm run preview                 # http://localhost:4173
```

(`npm run dev` on port 5174 works for everything except installing.)

### 3. Set up the desk

- **Phone** (or a 390 px wide browser window): `/guest`. Enter the pairing code **GULF77** (table 7, Garden room). For a real phone on the same network use `npm run preview -- --host` and open the address it prints.
- **Kitchen** monitor: `/kitchen`, sign in as M. Behr / 1234.
- **Hall board**: `/hall`, no sign-in.
- **Admin** (a fourth window, off to the side): `/admin`, sign in as O. Sado / 1234.
- Kitchen and hall board next to each other, the phone beside them. Leave the guest on German or English; Arabic comes at the end.

Two other codes are good for a second device: ALPHA2 (table 1), BRAVE3 (table 2), NAVY99 (table 9).

## The demo

1. **The idea (30 s).** Show the three empty screens. "Today an order passes through several hands. Here the guest orders from the table, the kitchen sees it at once, the hall board shows the progress."
2. **Guest orders (2 min).**
   - Welcome, choose English. Menu: categories, photos, prices.
   - Open **Wiener Schnitzel**: description, preparation, ingredients, two sizes with prices. Pick **300 g**, quantity **2**, note "no lingonberries, please". Add to order.
   - Cart: subtotal, VAT, total (calculated and verified on the server). Choose payment, pick a method, **Confirm**.
   - The phone shows the order number, large (`001`), and the status timeline.
3. **Kitchen (1 min).** Within about 5 s the card appears in **New**: number, table, 2x Wiener Schnitzel 300 g, the note, and a timer (amber after 6 min, red after 12). Press **Start**.
4. **Hall board and phone.** The number appears under **In preparation** with its table; the phone's timeline moves too. Press **Ready**: the number moves to **Ready, please collect** (green). Press **Picked up by waiter**: it leaves the board.
5. **Cancel (1 min).** Place a second order from the phone, **Start** it, then in the admin window open **Orders** and press **Cancel**, then **Yes, cancel it**. The kitchen shows the red banner until acknowledged, the hall board drops the number, the phone says cancelled. Acknowledge on the kitchen screen.
6. **A meal runs out (30 s).** Keep the phone on the menu. In the kitchen's "Ran out?" row press **Radler**; within about 5 s it vanishes from the phone's menu. Press it again to bring it back.
7. **Admin tour (2 min).**
   - **Orders**: every order, the filter by status and day.
   - **Audit history**: the order from step 2 with each stage, its time and who made it (Guest, M. Behr).
   - **Meals**: the three languages per meal, sizes, prices, the recipe linking each size to raw materials, the picture. **Raw materials**: pictures, stock, supplier. **Tables & devices**: pairing codes. **Staff accounts** and their roles. **Settings**: currency, tax, payment methods, default language.
8. **Arabic (1 min).** Switch the phone to Arabic: right-to-left layout (not mirrored English), Arabic menu content, Latin digits for numbers and prices. Then the hall board.
9. **Installable (30 s).** On the phone or tablet, with the production build: browser menu, **Add to Home Screen** (or Install). It opens full screen, starting at the guest screen. Turn on airplane mode and reopen it: the app shell still loads.

## What to say about the build (proposal section 6)

| Point | Where to show or point |
|---|---|
| A state machine, not a status field | The order moved Submitted, In preparation, Ready, Served only through one service; the audit history shows who and when. `OrderStateMachineService`, `NonNegotiableRulesGuardTest` (no other code may set a status) |
| Order numbers unique under load | `GuestSubmissionConcurrencyTest`: 30 simultaneous submissions over real HTTP, all distinct and consecutive |
| Prices calculated on the server | The cart's "calculated and verified on the server" line; `OrderLifecycleEndToEndTest` sends a wrong total and it is ignored |
| Orders snapshot what was sold | The same test changes the menu price and name afterwards; the order does not change |
| German, English, Arabic, right-to-left from the start | Step 8; `npm run check:i18n` (325 keys, three languages) |
| Role-based access | Sign in as Kitchen at `/admin`: access denied. `AccessControlMatrixTest` checks every endpoint against the table in `Documentation/access-control.md` |

## If something goes wrong

- **A card or number has not appeared:** the screens refresh every 5 s. Wait one cycle before assuming a fault.
- **"Connection lost" on a screen:** the backend is not answering; the screen keeps what it has and recovers by itself when it is back.
- **The guest phone asks for a code:** enter GULF77. An unknown code is refused with a message.
- **Kitchen sign-in says locked:** five wrong PINs lock a name for 15 minutes. Restart the backend (the lock is in memory) or use another account.
- **Boards already full of old orders:** do step 2 of "Before the demo" again.
- **No "Install" offer:** it needs the production build (`npm run build`, `npm run preview`) on `localhost` or HTTPS, and not an already installed copy.
- **Fonts look different offline:** the three fonts come from Google Fonts; without a connection the browser falls back to its own.

## Rehearsal record

- [x] Automated rehearsal of steps 2 to 6 (and the audit check) on a throwaway stack: 22 of 22, results above.
- [ ] **Human rehearsal, once end to end on the real devices** (phone, kitchen monitor, hall screen, with the talking points): to be ticked by the presenter.
- [ ] Install on a real phone or tablet from the production build, and reopen offline.
