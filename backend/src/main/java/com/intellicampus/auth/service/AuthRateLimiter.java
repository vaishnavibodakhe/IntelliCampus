package com.intellicampus.auth.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class AuthRateLimiter {

    private final ConcurrentHashMap<String, List<Instant>> requestLog = new ConcurrentHashMap<>();

    /**
     * Checks if a request is permitted within the rate limit window.
     *
     * @param key Identifies the caller or action (e.g. IP or email)
     * @param maxRequests Maximum allowed requests in the time window
     * @param windowSeconds Window length in seconds
     * @return true if allowed, false if limit exceeded
     */
    public boolean isAllowed(String key, int maxRequests, int windowSeconds) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);

        List<Instant> timestamps = requestLog.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        timestamps.removeIf(t -> t.isBefore(windowStart));

        if (timestamps.size() >= maxRequests) {
            return false;
        }

        timestamps.add(now);
        return true;
    }

    /**
     * Clear rate limit history for a key (useful for tests or on verified success).
     */
    public void reset(String key) {
        requestLog.remove(key);
    }
}
