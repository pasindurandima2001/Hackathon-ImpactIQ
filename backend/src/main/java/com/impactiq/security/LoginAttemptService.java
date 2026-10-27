package com.impactiq.security;

import com.impactiq.config.AuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory brute-force guard: locks a username after repeated failures. Resets on restart (single-node demo). */
@Service
public class LoginAttemptService {

    private static final int MAX_TRACKED_USERNAMES = 10_000;

    private final AuthProperties properties;
    private final Clock clock;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(AuthProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginAttemptService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean isLocked(String username) {
        Attempts current = attempts.get(key(username));
        return current != null && current.lockedUntil != null && clock.instant().isBefore(current.lockedUntil);
    }

    public void recordFailure(String username) {
        if (attempts.size() > MAX_TRACKED_USERNAMES) {
            attempts.clear();
        }
        attempts.compute(key(username), (ignored, current) -> {
            Attempts next = current == null ? new Attempts() : current;
            if (next.lockedUntil != null && !clock.instant().isBefore(next.lockedUntil)) {
                next.failures = 0;
                next.lockedUntil = null;
            }
            next.failures++;
            if (next.failures >= properties.getMaxFailedLogins()) {
                next.lockedUntil = clock.instant().plusSeconds(properties.getLockoutSeconds());
            }
            return next;
        });
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Attempts {
        private int failures;
        private Instant lockedUntil;
    }
}
