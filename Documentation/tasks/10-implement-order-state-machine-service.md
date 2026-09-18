# Issue #10: Implement the server-side order state machine service

## What was done

Added the single service that validates and executes every order status change, so nothing else can set `order.status` directly and every change is audited — the service layer issue #9's entities deliberately deferred, matching the #4→#5 (entities-then-service) pattern already used once. Authorization (which role may trigger which transition, e.g. "cancel is admin-only") is deliberately out of scope — that's issue #29's job.

## The other files

- **`order/model/Order.java`** — `status` loses its public setter (`@Setter(AccessLevel.NONE)`); the only way to change it now is `recordTransition(newStatus, changedAt, changedBy)`, which changes `status` *and* appends the matching `OrderStatusHistory` row in the same call — structurally impossible to change status without an audit entry, even bypassing the service.
- **`order/service/OrderTransitions.java`** — the legal-transition graph as a pure static function, zero Spring/DB dependency on purpose, independently testable from the service.
- **`order/service/IllegalOrderTransitionException.java`** — unchecked, names the order id and both statuses.
- **`order/service/OrderStateMachineService.java`** — validates via `OrderTransitions`, sets `placedAt` specifically when transitioning to `SUBMITTED` (issue #9 explicitly left this as "a later ticket's job" — this is that ticket), then delegates to `Order.recordTransition` and saves. `actor` is a nullable `StaffAccount` — `null` means guest-triggered, matching `OrderStatusHistory.changedBy`'s existing nullability from issue #9 rather than inventing a separate "guest" sentinel.
- **`order/repository/OrderRepositoryTest.java`** (issue #9) — fixed: it called the now-removed `setStatus` directly; the test never asserted on status, so the line just drops out.

## Verification performed

1. `OrderTransitionsTest` — plain JUnit, **no Spring context, no Testcontainers, no Docker dependency at all**. Ran for real in under a second: every legal edge accepted, both illegal examples the issue itself names (`READY→DRAFT`, cancelling a `SERVED` order) rejected, both terminal statuses confirmed to have zero legal outbound transitions, skipped-stage transitions rejected, self-transitions rejected. 11/11 pass.
2. `OrderStateMachineServiceTest` — Testcontainers-backed, ran for real against a live Postgres (Docker was reachable this session): the full `DRAFT→SUBMITTED→PREPARING→READY→SERVED` lifecycle (confirms `placedAt` gets set on `SUBMITTED`, each history entry records the right actor including `null` for the guest-triggered submission); an illegal transition is rejected and leaves the order completely unchanged; cancelling a served order is rejected. 3/3 pass.
3. Full backend suite: **37 tests across 16 classes, 0 failures** — includes the fixed `OrderRepositoryTest` from issue #9.
   - Root cause → fix: `illegalTransitionIsRejectedAndNothingChanges` re-fetches the order via `orderRepository.findById(...)` and then reads its lazy `history` collection — `LazyInitializationException`, because plain `@SpringBootTest` (unlike `@DataJpaTest`) doesn't wrap test methods in a transaction automatically, so the fetch's session had already closed by the time the collection was read. Fixed with `@Transactional` on the test class (the standard Spring idiom for exactly this), not by changing `Order.history`'s fetch strategy to `EAGER` — that would've fixed the symptom at the cost of a real N+1 risk in production code that every other entity in this codebase deliberately avoids.
