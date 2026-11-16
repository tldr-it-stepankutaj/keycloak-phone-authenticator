package com.tegadeti.keycloak.provider;

import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.SingleUseObjectProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Sliding-window rate limiting for SMS requests (per phone number and per IP address).
 * <p>
 * The counting logic is independent of where the timestamps live:
 * <ul>
 *   <li>{@link InMemoryStore} - node-local, used by the convenience methods (and unit tests)</li>
 *   <li>{@link SingleUseObjectStore} - backed by Keycloak's {@link SingleUseObjectProvider}, so the
 *       counters are shared across cluster nodes and survive a restart when Keycloak's cache is
 *       persistent. Read-modify-write is not atomic across nodes, so under heavy concurrency the limit is
 *       best-effort (it can be exceeded by a few requests), which is fine for abuse protection.</li>
 * </ul>
 * A limit {@code <= 0} disables the corresponding check.
 */
public class RateLimitService {

    private static final Logger logger = Logger.getLogger(RateLimitService.class);

    private static final RateLimitService INSTANCE = new RateLimitService();

    static final long WINDOW_SIZE_SECONDS = 3600; // 1 hour
    private static final long CLEANUP_INTERVAL_SECONDS = 60;

    private static final String PHONE_PREFIX = "phone:";
    private static final String IP_PREFIX = "ip:";

    private final InMemoryStore memoryStore = new InMemoryStore();
    private final Object lock = new Object();
    private volatile long lastCleanup = 0;

    private RateLimitService() {
    }

    public static RateLimitService getInstance() {
        return INSTANCE;
    }

    // === Storage abstraction ===

    /** Storage of request timestamps (epoch seconds) per key. */
    public interface RateLimitStore {
        /** Timestamps for the key that are not older than {@code windowStart}. */
        List<Long> get(String key, long windowStart);

        /** Appends a timestamp (and drops those older than {@code windowStart}). */
        void add(String key, long timestamp, long windowStart);

        void remove(String key);
    }

    // === Public API (node-local store) ===

    public boolean checkAndRecordPhoneRequest(String phoneNumber, int limit) {
        return checkAndRecordPhoneRequest(memoryStore, phoneNumber, limit);
    }

    public boolean checkAndRecordIpRequest(String ipAddress, int limit) {
        return checkAndRecordIpRequest(memoryStore, ipAddress, limit);
    }

    public RateLimitResult checkRateLimit(String phoneNumber, String ipAddress, int phoneLimit, int ipLimit) {
        return checkRateLimit(memoryStore, phoneNumber, ipAddress, phoneLimit, ipLimit);
    }

    public int getRemainingPhoneRequests(String phoneNumber, int limit) {
        return getRemaining(memoryStore, PHONE_PREFIX + phoneNumber, limit);
    }

    public int getRemainingIpRequests(String ipAddress, int limit) {
        return getRemaining(memoryStore, IP_PREFIX + ipAddress, limit);
    }

    public void resetPhoneLimit(String phoneNumber) {
        memoryStore.remove(PHONE_PREFIX + phoneNumber);
        logger.debugf("Rate limit reset for phone: %s", PhoneNumberUtils.mask(phoneNumber));
    }

    public void resetIpLimit(String ipAddress) {
        memoryStore.remove(IP_PREFIX + ipAddress);
        logger.debugf("Rate limit reset for IP: %s", maskIpAddress(ipAddress));
    }

    /** Removes expired entries from the node-local store. Also runs opportunistically on every check. */
    public void cleanupExpiredEntries() {
        memoryStore.removeOlderThan(now() - WINDOW_SIZE_SECONDS);
        lastCleanup = now();
        logger.debug("Rate limit cache cleanup completed");
    }

    // === Public API (explicit store, e.g. cluster-wide) ===

    public boolean checkAndRecordPhoneRequest(RateLimitStore store, String phoneNumber, int limit) {
        if (isBlank(phoneNumber) || limit <= 0) {
            return true;
        }
        synchronized (lock) {
            String key = PHONE_PREFIX + phoneNumber;
            if (!isUnderLimit(store, key, limit)) {
                return false;
            }
            record(store, key);
            return true;
        }
    }

    public boolean checkAndRecordIpRequest(RateLimitStore store, String ipAddress, int limit) {
        if (isBlank(ipAddress) || limit <= 0) {
            return true;
        }
        synchronized (lock) {
            String key = IP_PREFIX + ipAddress;
            if (!isUnderLimit(store, key, limit)) {
                return false;
            }
            record(store, key);
            return true;
        }
    }

    /**
     * Combined check for IP and phone. Requests are recorded only if both checks pass.
     */
    public RateLimitResult checkRateLimit(RateLimitStore store, String phoneNumber, String ipAddress,
                                          int phoneLimit, int ipLimit) {
        maybeCleanup();

        boolean checkIp = !isBlank(ipAddress) && ipLimit > 0;
        boolean checkPhone = !isBlank(phoneNumber) && phoneLimit > 0;
        String ipKey = IP_PREFIX + ipAddress;
        String phoneKey = PHONE_PREFIX + phoneNumber;

        synchronized (lock) {
            if (checkIp && !isUnderLimit(store, ipKey, ipLimit)) {
                logger.debugf("Rate limit exceeded for IP: %s", maskIpAddress(ipAddress));
                return new RateLimitResult(false, RateLimitType.IP, getRemainingTime(store, ipKey));
            }
            if (checkPhone && !isUnderLimit(store, phoneKey, phoneLimit)) {
                logger.debugf("Rate limit exceeded for phone: %s", PhoneNumberUtils.mask(phoneNumber));
                return new RateLimitResult(false, RateLimitType.PHONE, getRemainingTime(store, phoneKey));
            }

            if (checkPhone) {
                record(store, phoneKey);
            }
            if (checkIp) {
                record(store, ipKey);
            }
        }
        return new RateLimitResult(true, null, 0);
    }

    // === Private helpers ===

    private boolean isUnderLimit(RateLimitStore store, String key, int limit) {
        return store.get(key, now() - WINDOW_SIZE_SECONDS).size() < limit;
    }

    private void record(RateLimitStore store, String key) {
        long now = now();
        store.add(key, now, now - WINDOW_SIZE_SECONDS);
    }

    private int getRemaining(RateLimitStore store, String key, int limit) {
        if (limit <= 0) {
            return Integer.MAX_VALUE;
        }
        int used = store.get(key, now() - WINDOW_SIZE_SECONDS).size();
        return Math.max(0, limit - used);
    }

    private long getRemainingTime(RateLimitStore store, String key) {
        long now = now();
        return store.get(key, now - WINDOW_SIZE_SECONDS).stream()
                .min(Long::compareTo)
                .map(oldest -> Math.max(0, oldest + WINDOW_SIZE_SECONDS - now))
                .orElse(0L);
    }

    private void maybeCleanup() {
        if (now() - lastCleanup >= CLEANUP_INTERVAL_SECONDS) {
            cleanupExpiredEntries();
        }
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null) {
            return "****";
        }
        if (ipAddress.contains(".")) {
            int firstDot = ipAddress.indexOf('.');
            if (firstDot > 0) {
                return ipAddress.substring(0, firstDot) + ".***";
            }
        }
        if (ipAddress.contains(":")) {
            int firstColon = ipAddress.indexOf(':');
            if (firstColon > 0) {
                return ipAddress.substring(0, firstColon) + ":***";
            }
        }
        return "****";
    }

    // === Stores ===

    /** Node-local store. */
    static final class InMemoryStore implements RateLimitStore {
        private final Map<String, List<Long>> data = new HashMap<>();

        @Override
        public synchronized List<Long> get(String key, long windowStart) {
            List<Long> timestamps = data.get(key);
            if (timestamps == null) {
                return List.of();
            }
            timestamps.removeIf(ts -> ts < windowStart);
            if (timestamps.isEmpty()) {
                data.remove(key);
                return List.of();
            }
            return new ArrayList<>(timestamps);
        }

        @Override
        public synchronized void add(String key, long timestamp, long windowStart) {
            List<Long> timestamps = data.computeIfAbsent(key, k -> new ArrayList<>());
            timestamps.removeIf(ts -> ts < windowStart);
            timestamps.add(timestamp);
        }

        @Override
        public synchronized void remove(String key) {
            data.remove(key);
        }

        synchronized void removeOlderThan(long cutoff) {
            Iterator<Map.Entry<String, List<Long>>> it = data.entrySet().iterator();
            while (it.hasNext()) {
                List<Long> timestamps = it.next().getValue();
                timestamps.removeIf(ts -> ts < cutoff);
                if (timestamps.isEmpty()) {
                    it.remove();
                }
            }
        }

        synchronized int size() {
            return data.size();
        }
    }

    /**
     * Store backed by Keycloak's single-use object cache (shared across the cluster, entries expire on
     * their own). Keys are hashed so phone numbers / IPs do not end up in the cache in clear text.
     */
    public static final class SingleUseObjectStore implements RateLimitStore {
        private static final String KEY_PREFIX = "phone-authenticator-rl:";
        private static final String NOTE = "ts";

        private final SingleUseObjectProvider provider;

        public SingleUseObjectStore(KeycloakSession session) {
            this.provider = session.singleUseObjects();
        }

        @Override
        public List<Long> get(String key, long windowStart) {
            Map<String, String> notes = provider.get(cacheKey(key));
            List<Long> result = new ArrayList<>();
            if (notes == null || notes.get(NOTE) == null || notes.get(NOTE).isEmpty()) {
                return result;
            }
            for (String part : notes.get(NOTE).split(",")) {
                try {
                    long ts = Long.parseLong(part);
                    if (ts >= windowStart) {
                        result.add(ts);
                    }
                } catch (NumberFormatException ignored) {
                    // skip corrupted value
                }
            }
            return result;
        }

        @Override
        public void add(String key, long timestamp, long windowStart) {
            List<Long> timestamps = get(key, windowStart);
            timestamps.add(timestamp);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < timestamps.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(timestamps.get(i));
            }

            String cacheKey = cacheKey(key);
            provider.remove(cacheKey);
            provider.put(cacheKey, WINDOW_SIZE_SECONDS, Map.of(NOTE, sb.toString()));
        }

        @Override
        public void remove(String key) {
            provider.remove(cacheKey(key));
        }

        private static String cacheKey(String key) {
            try {
                byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
                return KEY_PREFIX + HexFormat.of().formatHex(hash);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 not available", e);
            }
        }
    }

    // === Result types ===

    public enum RateLimitType {
        PHONE,
        IP
    }

    public static class RateLimitResult {
        private final boolean allowed;
        private final RateLimitType exceededType;
        private final long retryAfterSeconds;

        public RateLimitResult(boolean allowed, RateLimitType exceededType, long retryAfterSeconds) {
            this.allowed = allowed;
            this.exceededType = exceededType;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public RateLimitType getExceededType() {
            return exceededType;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }

        /** Minutes to show to the user (rounded up, at least 1). */
        public long getRetryAfterMinutes() {
            return retryAfterSeconds / 60 + 1;
        }

        /** Theme message key for the exceeded limit; the single parameter is {@link #getRetryAfterMinutes()}. */
        public String getMessageKey() {
            if (allowed) {
                return null;
            }
            return exceededType == RateLimitType.PHONE ? "rateLimitPhone" : "rateLimitIp";
        }

        /** English fallback message (for logs / non-themed use). */
        public String getErrorMessage() {
            if (allowed) {
                return null;
            }
            if (exceededType == RateLimitType.PHONE) {
                return String.format("Too many requests for this phone number. Please try again in %d minutes.",
                        getRetryAfterMinutes());
            }
            return String.format("Too many requests from your IP address. Please try again in %d minutes.",
                    getRetryAfterMinutes());
        }
    }
}
