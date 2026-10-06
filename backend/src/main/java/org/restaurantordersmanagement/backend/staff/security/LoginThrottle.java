package org.restaurantordersmanagement.backend.staff.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Locks a staff name for 15 minutes after 5 failed sign-ins, so a 4 to 8
 * digit PIN cannot simply be tried until it works. While a name is locked even
 * the right PIN is refused, and refused attempts are not counted, so the lock
 * does not stretch.
 *
 * Keyed by the name as typed, existing or not: a name that does not exist locks
 * exactly like one that does, so the lock cannot be used to find out which
 * accounts exist. In memory, which is right for a single backend instance; a
 * restart clears it. The flip side is that anyone who knows a name can lock
 * that account for 15 minutes, which is the usual price of this kind of lock.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final int DEFAULT_MAX_TRACKED_NAMES = 10_000;

    private record Attempts(int failures, Instant lastFailure) {
    }

    private final Clock clock;
    private final int maxTrackedNames;
    private final Map<String, Attempts> attemptsByName = new HashMap<>();

    @Autowired
    public LoginThrottle() {
        this(Clock.systemUTC(), DEFAULT_MAX_TRACKED_NAMES);
    }

    LoginThrottle(Clock clock, int maxTrackedNames) {
        this.clock = clock;
        this.maxTrackedNames = maxTrackedNames;
    }

    /** How much longer this name stays locked, or zero when it may try. */
    public synchronized Duration lockedFor(@Nullable String name) {
        Attempts attempts = attemptsByName.get(key(name));
        if (attempts == null) {
            return Duration.ZERO;
        }
        Duration sinceLastFailure = Duration.between(attempts.lastFailure(), clock.instant());
        if (sinceLastFailure.compareTo(LOCK_DURATION) >= 0) {
            attemptsByName.remove(key(name));
            return Duration.ZERO;
        }
        return attempts.failures() >= MAX_FAILURES ? LOCK_DURATION.minus(sinceLastFailure) : Duration.ZERO;
    }

    /** Counts a failed sign-in; failures older than the lock duration no longer count. */
    public synchronized void recordFailure(@Nullable String name) {
        Instant now = clock.instant();
        Attempts previous = attemptsByName.get(key(name));
        boolean stale = previous == null || Duration.between(previous.lastFailure(), now).compareTo(LOCK_DURATION) >= 0;
        if (stale && attemptsByName.size() >= maxTrackedNames) {
            makeRoom(now);
        }
        attemptsByName.put(key(name), new Attempts(stale ? 1 : previous.failures() + 1, now));
    }

    public synchronized void recordSuccess(@Nullable String name) {
        attemptsByName.remove(key(name));
    }

    /** For tests: forget every name. */
    public synchronized void reset() {
        attemptsByName.clear();
    }

    /** Drops expired names first; if the table is still full, the name that failed longest ago. */
    private void makeRoom(Instant now) {
        attemptsByName.values().removeIf(a -> Duration.between(a.lastFailure(), now).compareTo(LOCK_DURATION) >= 0);
        if (attemptsByName.size() >= maxTrackedNames) {
            attemptsByName.entrySet().stream()
                    .min(Map.Entry.comparingByValue(java.util.Comparator.comparing(Attempts::lastFailure)))
                    .map(Map.Entry::getKey)
                    .ifPresent(attemptsByName::remove);
        }
    }

    /** A sign-in body with no "name" at all gives null here; it is counted under one shared, empty key. */
    private static String key(@Nullable String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }

}
