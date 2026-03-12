package com.tegadeti.keycloak.provider;

import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for rate limiting SMS requests
 * Implements limits per phone number and per IP address
 */
public class RateLimitService {

    private static final Logger logger = Logger.getLogger(RateLimitService.class);

    // Singleton instance
    private static final RateLimitService INSTANCE = new RateLimitService();

    // Default values
    private static final int DEFAULT_RATE_LIMIT_PER_PHONE = 5;
    private static final int DEFAULT_RATE_LIMIT_PER_IP = 20;
    private static final long WINDOW_SIZE_SECONDS = 3600; // 1 hour

    // Cache for rate limiting - key -> list of request timestamps
    private final Map<String, CopyOnWriteArrayList<Long>> phoneRequests = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Long>> ipRequests = new ConcurrentHashMap<>();

    // Key prefixes
    private static final String PHONE_PREFIX = "phone:";
    private static final String IP_PREFIX = "ip:";

    private RateLimitService() {
        // Private constructor for singleton
    }

    public static RateLimitService getInstance() {
        return INSTANCE;
    }

    /**
     * Checks and records a request for a phone number
     *
     * @param phoneNumber Phone number
     * @param limit       Maximum number of requests per hour
     * @return true if the request is allowed, false if the limit is exceeded
     */
    public boolean checkAndRecordPhoneRequest(String phoneNumber, int limit) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return true;
        }

        int effectiveLimit = limit > 0 ? limit : DEFAULT_RATE_LIMIT_PER_PHONE;
        return checkAndRecord(phoneRequests, PHONE_PREFIX + phoneNumber, effectiveLimit);
    }

    /**
     * Checks and records a request for an IP address
     *
     * @param ipAddress IP address
     * @param limit     Maximum number of requests per hour
     * @return true if the request is allowed, false if the limit is exceeded
     */
    public boolean checkAndRecordIpRequest(String ipAddress, int limit) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return true;
        }

        int effectiveLimit = limit > 0 ? limit : DEFAULT_RATE_LIMIT_PER_IP;
        return checkAndRecord(ipRequests, IP_PREFIX + ipAddress, effectiveLimit);
    }

    /**
     * Combined check for both phone and IP
     *
     * @param phoneNumber   Phone number
     * @param ipAddress     IP address
     * @param phoneLimit    Limit for phone
     * @param ipLimit       Limit for IP
     * @return RateLimitResult with result information
     */
    public RateLimitResult checkRateLimit(String phoneNumber, String ipAddress, int phoneLimit, int ipLimit) {
        // First check IP (less specific)
        if (!checkIpWithoutRecord(ipAddress, ipLimit)) {
            logger.warnf("Rate limit exceeded for IP: %s", maskIpAddress(ipAddress));
            return new RateLimitResult(false, RateLimitType.IP, getRemainingTime(ipRequests, IP_PREFIX + ipAddress));
        }

        // Then check phone (more specific)
        if (!checkPhoneWithoutRecord(phoneNumber, phoneLimit)) {
            logger.warnf("Rate limit exceeded for phone: %s", maskPhoneNumber(phoneNumber));
            return new RateLimitResult(false, RateLimitType.PHONE, getRemainingTime(phoneRequests, PHONE_PREFIX + phoneNumber));
        }

        // Both checks passed - record the requests
        recordRequest(phoneRequests, PHONE_PREFIX + phoneNumber);
        recordRequest(ipRequests, IP_PREFIX + ipAddress);

        return new RateLimitResult(true, null, 0);
    }

    /**
     * Gets the number of remaining requests for a phone number
     */
    public int getRemainingPhoneRequests(String phoneNumber, int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_RATE_LIMIT_PER_PHONE;
        return getRemainingRequests(phoneRequests, PHONE_PREFIX + phoneNumber, effectiveLimit);
    }

    /**
     * Gets the number of remaining requests for an IP address
     */
    public int getRemainingIpRequests(String ipAddress, int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_RATE_LIMIT_PER_IP;
        return getRemainingRequests(ipRequests, IP_PREFIX + ipAddress, effectiveLimit);
    }

    /**
     * Cleans up old entries from cache (for maintenance)
     */
    public void cleanupExpiredEntries() {
        long cutoff = Instant.now().getEpochSecond() - WINDOW_SIZE_SECONDS;

        cleanupMap(phoneRequests, cutoff);
        cleanupMap(ipRequests, cutoff);

        logger.debug("Rate limit cache cleanup completed");
    }

    /**
     * Resets rate limit for a specific phone number (for admin purposes)
     */
    public void resetPhoneLimit(String phoneNumber) {
        phoneRequests.remove(PHONE_PREFIX + phoneNumber);
        logger.infof("Rate limit reset for phone: %s", maskPhoneNumber(phoneNumber));
    }

    /**
     * Resets rate limit for a specific IP address (for admin purposes)
     */
    public void resetIpLimit(String ipAddress) {
        ipRequests.remove(IP_PREFIX + ipAddress);
        logger.infof("Rate limit reset for IP: %s", maskIpAddress(ipAddress));
    }

    // === Private methods ===

    private boolean checkAndRecord(Map<String, CopyOnWriteArrayList<Long>> cache, String key, int limit) {
        long now = Instant.now().getEpochSecond();
        long windowStart = now - WINDOW_SIZE_SECONDS;

        CopyOnWriteArrayList<Long> timestamps = cache.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());

        // Remove old entries
        timestamps.removeIf(ts -> ts < windowStart);

        // Check limit
        if (timestamps.size() >= limit) {
            return false;
        }

        // Record new request
        timestamps.add(now);
        return true;
    }

    private boolean checkPhoneWithoutRecord(String phoneNumber, int limit) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return true;
        }
        int effectiveLimit = limit > 0 ? limit : DEFAULT_RATE_LIMIT_PER_PHONE;
        return checkWithoutRecord(phoneRequests, PHONE_PREFIX + phoneNumber, effectiveLimit);
    }

    private boolean checkIpWithoutRecord(String ipAddress, int limit) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return true;
        }
        int effectiveLimit = limit > 0 ? limit : DEFAULT_RATE_LIMIT_PER_IP;
        return checkWithoutRecord(ipRequests, IP_PREFIX + ipAddress, effectiveLimit);
    }

    private boolean checkWithoutRecord(Map<String, CopyOnWriteArrayList<Long>> cache, String key, int limit) {
        long windowStart = Instant.now().getEpochSecond() - WINDOW_SIZE_SECONDS;

        CopyOnWriteArrayList<Long> timestamps = cache.get(key);
        if (timestamps == null) {
            return true;
        }

        // Count only valid entries
        long validCount = timestamps.stream()
                .filter(ts -> ts >= windowStart)
                .count();

        return validCount < limit;
    }

    private void recordRequest(Map<String, CopyOnWriteArrayList<Long>> cache, String key) {
        long now = Instant.now().getEpochSecond();
        CopyOnWriteArrayList<Long> timestamps = cache.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        timestamps.add(now);
    }

    private int getRemainingRequests(Map<String, CopyOnWriteArrayList<Long>> cache, String key, int limit) {
        long windowStart = Instant.now().getEpochSecond() - WINDOW_SIZE_SECONDS;

        CopyOnWriteArrayList<Long> timestamps = cache.get(key);
        if (timestamps == null) {
            return limit;
        }

        long validCount = timestamps.stream()
                .filter(ts -> ts >= windowStart)
                .count();

        return Math.max(0, limit - (int) validCount);
    }

    private long getRemainingTime(Map<String, CopyOnWriteArrayList<Long>> cache, String key) {
        CopyOnWriteArrayList<Long> timestamps = cache.get(key);
        if (timestamps == null || timestamps.isEmpty()) {
            return 0;
        }

        long windowStart = Instant.now().getEpochSecond() - WINDOW_SIZE_SECONDS;

        // Find the oldest valid entry
        long oldestValid = timestamps.stream()
                .filter(ts -> ts >= windowStart)
                .min(Long::compareTo)
                .orElse(Instant.now().getEpochSecond());

        // Time until the oldest entry expires
        return Math.max(0, (oldestValid + WINDOW_SIZE_SECONDS) - Instant.now().getEpochSecond());
    }

    private void cleanupMap(Map<String, CopyOnWriteArrayList<Long>> cache, long cutoff) {
        Iterator<Map.Entry<String, CopyOnWriteArrayList<Long>>> iterator = cache.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, CopyOnWriteArrayList<Long>> entry = iterator.next();
            CopyOnWriteArrayList<Long> timestamps = entry.getValue();

            // Remove old entries
            timestamps.removeIf(ts -> ts < cutoff);

            // If the list is empty, remove the entire entry
            if (timestamps.isEmpty()) {
                iterator.remove();
            }
        }
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return phoneNumber.substring(0, 4) + "*".repeat(phoneNumber.length() - 4);
    }

    private String maskIpAddress(String ipAddress) {
        if (ipAddress == null) {
            return "****";
        }
        // For IPv4 show first octet
        if (ipAddress.contains(".")) {
            int firstDot = ipAddress.indexOf('.');
            if (firstDot > 0) {
                return ipAddress.substring(0, firstDot) + ".***";
            }
        }
        // For IPv6 show first segment
        if (ipAddress.contains(":")) {
            int firstColon = ipAddress.indexOf(':');
            if (firstColon > 0) {
                return ipAddress.substring(0, firstColon) + ":***";
            }
        }
        return "****";
    }

    // === Inner classes ===

    /**
     * Type of rate limit that was exceeded
     */
    public enum RateLimitType {
        PHONE,
        IP
    }

    /**
     * Rate limit check result
     */
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

        public String getErrorMessage() {
            if (allowed) {
                return null;
            }

            long minutes = retryAfterSeconds / 60;
            if (exceededType == RateLimitType.PHONE) {
                return String.format("Too many requests for this phone number. Please try again in %d minutes.", minutes + 1);
            } else {
                return String.format("Too many requests from your IP address. Please try again in %d minutes.", minutes + 1);
            }
        }
    }
}
