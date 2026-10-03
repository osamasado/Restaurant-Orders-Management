# Issue #26: Hall status board (numbers-only, two columns)

## What was done

The hall screen was an empty shell: two panels, a `--:--` clock placeholder and no data. It is now a live board for the dining area. Order numbers sit in two columns, In preparation (amber) and Ready (green), stacked vertically. A number moves across as the kitchen presses Start and Ready, and disappears when the order is served or cancelled. The board refreshes every 5 s with no login, and the header clock ticks with seconds.

Decisions worth knowing:
- **Which orders show:** only `PREPARING` and `READY`. A submitted order the kitchen has not started yet is not on the board, so a guest's number appears once the kitchen starts it. Their own device already shows "submitted".
- **Table number added (changes the ticket):** the ticket and the design say "no table numbers". After reviewing the first version, the table number was added beside each order number, in amber. The footer now reads "Order numbers and table numbers only, no names, no prices on this screen." The ticket's acceptance criteria and `Documentation/Design/README.md` were updated to match (`shots/hall.png` is the old prototype capture and still shows numbers only).
- **Three-digit order numbers:** every screen (guest confirmation, kitchen card, cancelled banner, hall) now shows at least three digits: 001, 002, and so on. This is display padding only, and the stored number stays an integer. Past 999 it grows to four digits, because wrapping would break the unique-number rule.
- **Same colours on the kitchen board:** amber for In preparation and green for Ready. New is neutral, because green now means Ready.
- **Public endpoint:** the board is a wall screen, so `GET /api/hall/orders` needs no login. That is only acceptable because it returns `{orderNumber, tableNumber}` per entry and nothing else.

Visual polish (forest Ready panel, blinking dot, rise-in animation, Arabic RTL polish, overflow with many orders) was deliberately left to #62.

## The other files

Backend:
- **`order/repository/OrderRepository.java`**: `findHallBoardRows(status)`, an explicit query returning the `HallBoardRow` projection (`orderNumber`, `tableNumber`). The SQL is `select o.order_number, t.table_number … where status = ? order by order_number`, so items, prices and the table's pairing code are never loaded. A first attempt with a nested derived projection loaded the whole table row, including `paired_device_id`.
- **`hall/service/HallBoardService.java`, `hall/web/HallBoardController.java`, `HallBoardResponse.java`**: the public endpoint. Two lists of `Entry(orderNumber, tableNumber)`, each in ascending order number so entries don't jump between polls.

Frontend:
- **`api/hallApi.ts`, `api/types.ts`**: `getHallBoard()` and the `HallBoardResponse` / `HallBoardEntry` types.
- **`screens/hall/useHallBoard.ts`**: polls every 5 s with chained timeouts, so requests never overlap. It keeps the last good data while polls fail and has no 401 handling, since there is no login.
- **`screens/hall/HallScreen.tsx` + `.css`**: the stacked lists (`<ul>`, the order number as the React key, so a number moving columns unmounts and mounts), the live clock, and a notice while polls fail. Sizes are 74 px in preparation and 86 px ready, as in the design doc.
- **`lib/useNow.ts`** (moved from `screens/kitchen`): shared by the kitchen and hall clocks.
- **`lib/formatOrderNumber.ts`**: the three-digit padding, used on all four screens.
- **`screens/kitchen/*`**: column header, dot and card order number coloured by column.
- **`i18n/locales/{en,de,ar}.json`**: hall panel labels ("Ready, please collect"), footer, `hall.table` and `hall.connectionLost`. Commas only, no em dashes.

## Verification performed

1. `./mvnw test -Dtest='HallBoardControllerTest,OrderRepositoryTest'` (Testcontainers Postgres): 8 tests, 0 failures. They cover:
   - 200 without login and exactly two lists;
   - a number moving from preparing to ready and then off the board when served;
   - submitted and cancelled orders never appearing;
   - the table number beside the order number;
   - entries having exactly two fields, with no items, totals or prices anywhere in the JSON;
   - the repository query returning only the requested status, ascending, with the table number.
2. Ran the repository test with SQL logging to confirm the hall query selects only `order_number` and `table_number`.
3. `npx tsc -b`, `npm run build`, and eslint on `src/screens/hall src/screens/kitchen src/screens/guest src/api src/lib src/i18n`: clean.
4. The first version was reviewed in the browser against a running backend. That review led to the stacked layout, the column colours, the three-digit numbers and the table number.
5. A read-only `curl` against the already running dev backend showed it was still the older build (plain numbers, not entries), so the final layout has not yet been checked against the rebuilt backend.

Issues found along the way:
- **Over-wide projection:** when the table number was added, a nested derived projection looked right and its javadoc said only two columns were selected, but the SQL log showed the full `restaurant_table` row being loaded, including the pairing code. It was never in the response, but it should not be loaded for a public endpoint. Replaced with the explicit two-column query, and the log re-checked.

Not yet verified:
- Visual check of the stacked layout, colours and table number on the rebuilt backend, including Arabic. Restart the backend first, because the running one predates the table-number change.
