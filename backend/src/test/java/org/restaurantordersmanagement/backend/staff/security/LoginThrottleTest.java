package org.restaurantordersmanagement.backend.staff.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The lock rules, with a clock the test moves by hand instead of waiting 15 minutes. */
class LoginThrottleTest {

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-10-04T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MovableClock clock;
    private LoginThrottle throttle;

    @BeforeEach
    void setUp() {
        clock = new MovableClock();
        throttle = new LoginThrottle(clock, 1000);
    }

    private void fail(String name, int times) {
        for (int i = 0; i < times; i++) {
            throttle.recordFailure(name);
        }
    }

    @Test
    void fourFailuresDoNotLock() {
        fail("O. Sado", 4);

        assertTrue(throttle.lockedFor("O. Sado").isZero());
    }

    @Test
    void theFifthFailureLocksForFifteenMinutes() {
        fail("O. Sado", 5);

        assertEquals(Duration.ofMinutes(15), throttle.lockedFor("O. Sado"));

        clock.advance(Duration.ofMinutes(10));
        assertEquals(Duration.ofMinutes(5), throttle.lockedFor("O. Sado"));
    }

    @Test
    void theLockEndsAfterFifteenMinutesAndCountingStartsOver() {
        fail("O. Sado", 5);

        clock.advance(Duration.ofMinutes(15));
        assertTrue(throttle.lockedFor("O. Sado").isZero());

        fail("O. Sado", 4);
        assertTrue(throttle.lockedFor("O. Sado").isZero(), "the old five failures must not count again");
    }

    @Test
    void failuresSpreadOverMoreThanFifteenMinutesDoNotAddUp() {
        fail("O. Sado", 4);
        clock.advance(Duration.ofMinutes(16));
        fail("O. Sado", 1);

        assertTrue(throttle.lockedFor("O. Sado").isZero());
    }

    @Test
    void aSuccessfulSignInClearsTheCount() {
        fail("O. Sado", 4);
        throttle.recordSuccess("O. Sado");
        fail("O. Sado", 4);

        assertTrue(throttle.lockedFor("O. Sado").isZero());
    }

    @Test
    void theNameIsMatchedIgnoringCaseAndSurroundingSpaces() {
        fail("O. Sado", 2);
        fail("  o. sado ", 2);
        fail("O. SADO", 1);

        assertFalse(throttle.lockedFor("o. sado").isZero());
    }

    @Test
    void eachNameIsCountedOnItsOwn() {
        fail("O. Sado", 5);

        assertFalse(throttle.lockedFor("O. Sado").isZero());
        assertTrue(throttle.lockedFor("M. Behr").isZero());
    }

    @Test
    void aNameThatDoesNotExistLocksLikeAnyOther() {
        fail("nobody-by-this-name", 5);

        assertFalse(throttle.lockedFor("nobody-by-this-name").isZero());
    }

    @Test
    void aMissingNameIsHandled() {
        fail(null, 5);

        assertFalse(throttle.lockedFor(null).isZero());
    }

    @Test
    void theTableOfNamesIsCappedAndDropsTheOldestFirst() {
        LoginThrottle small = new LoginThrottle(clock, 3);
        for (String name : new String[] {"a", "b", "c"}) {
            for (int i = 0; i < 5; i++) {
                small.recordFailure(name);
            }
            clock.advance(Duration.ofSeconds(1));
        }

        for (int i = 0; i < 5; i++) {
            small.recordFailure("d");
        }

        assertTrue(small.lockedFor("a").isZero(), "the oldest name made room for the new one");
        assertFalse(small.lockedFor("b").isZero());
        assertFalse(small.lockedFor("c").isZero());
        assertFalse(small.lockedFor("d").isZero());
    }

}
