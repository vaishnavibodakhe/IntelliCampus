package com.intellicampus.user.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IpRateLimiterService {

    private static final int MAX_FAILED_LOGINS_PER_IP = 10;
    private static final long LOGIN_WINDOW_SECONDS = 600; // 10 minutes

    private static final int MAX_ENROLL_ATTEMPTS = 5;
    private static final long ENROLL_WINDOW_SECONDS = 900; // 15 minutes

    private static class AttemptBucket {
        int count;
        long windowStartEpochSec;

        AttemptBucket(long nowSec) {
            this.count = 1;
            this.windowStartEpochSec = nowSec;
        }
    }

    private final Map<String, AttemptBucket> loginIpBuckets = new ConcurrentHashMap<>();
    private final Map<String, AttemptBucket> enrollBuckets = new ConcurrentHashMap<>();

    public boolean isLoginIpAllowed(String ip) {
        if (ip == null || ip.isBlank()) return true;
        long now = Instant.now().getEpochSecond();
        AttemptBucket bucket = loginIpBuckets.get(ip);
        if (bucket == null) return true;
        if (now - bucket.windowStartEpochSec > LOGIN_WINDOW_SECONDS) {
            loginIpBuckets.remove(ip);
            return true;
        }
        return bucket.count < MAX_FAILED_LOGINS_PER_IP;
    }

    public void recordFailedLogin(String ip) {
        if (ip == null || ip.isBlank()) return;
        long now = Instant.now().getEpochSecond();
        loginIpBuckets.compute(ip, (k, bucket) -> {
            if (bucket == null || now - bucket.windowStartEpochSec > LOGIN_WINDOW_SECONDS) {
                return new AttemptBucket(now);
            }
            bucket.count++;
            return bucket;
        });
    }

    public void resetLoginIp(String ip) {
        if (ip != null) {
            loginIpBuckets.remove(ip);
        }
    }

    public boolean isEnrollAllowed(String key) {
        if (key == null || key.isBlank()) return true;
        long now = Instant.now().getEpochSecond();
        AttemptBucket bucket = enrollBuckets.get(key);
        if (bucket == null) return true;
        if (now - bucket.windowStartEpochSec > ENROLL_WINDOW_SECONDS) {
            enrollBuckets.remove(key);
            return true;
        }
        return bucket.count < MAX_ENROLL_ATTEMPTS;
    }

    public void recordEnrollAttempt(String key) {
        if (key == null || key.isBlank()) return;
        long now = Instant.now().getEpochSecond();
        enrollBuckets.compute(key, (k, bucket) -> {
            if (bucket == null || now - bucket.windowStartEpochSec > ENROLL_WINDOW_SECONDS) {
                return new AttemptBucket(now);
            }
            bucket.count++;
            return bucket;
        });
    }
}
