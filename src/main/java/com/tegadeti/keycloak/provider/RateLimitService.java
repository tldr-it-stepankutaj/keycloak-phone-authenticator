package com.tegadeti.keycloak.provider;

import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service pro rate limiting SMS požadavků
 * Implementuje limity per telefon a per IP adresa
 */
public class RateLimitService {

    private static final Logger logger = Logger.getLogger(RateLimitService.class);

    // Singleton instance
    private static final RateLimitService INSTANCE = new RateLimitService();

    // Výchozí hodnoty
    private static final int DEFAULT_RATE_LIMIT_PER_PHONE = 5;
    private static final int DEFAULT_RATE_LIMIT_PER_IP = 20;
    private static final long WINDOW_SIZE_SECONDS = 3600; // 1 hodina

    // Cache pro rate limiting - klíč -> seznam časových značek požadavků
    private final Map<String, CopyOnWriteArrayList<Long>> phoneRequests = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<Long>> ipRequests = new ConcurrentHashMap<>();

    // Prefixy pro klíče
    private static final String PHONE_PREFIX = "phone:";
    private static final String IP_PREFIX = "ip:";

    private RateLimitService() {
        // Private constructor pro singleton
    }

    public static RateLimitService getInstance() {
        return INSTANCE;
    }

    /**
     * Zkontroluje a zaznamená požadavek pro telefonní číslo
     *
     * @param phoneNumber Telefonní číslo
     * @param limit       Maximální počet požadavků za hodinu
     * @return true pokud je požadavek povolen, false pokud je limit překročen
     */
    public boolean checkAndRecordPhoneRequest(String phoneNumber, int limit) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return true;
        }

        int effectiveLimit = limit > 0 ? limit : DEFAULT_RATE_LIMIT_PER_PHONE;
        return checkAndRecord(phoneRequests, PHONE_PREFIX + phoneNumber, effectiveLimit);
    }

    /**
     * Zkontroluje a zaznamená požadavek pro IP adresu
     *
     * @param ipAddress IP adresa
     * @param limit     Maximální počet požadavků za hodinu
     * @return true pokud je požadavek povolen, false pokud je limit překročen
     */
    public boolean checkAndRecordIpRequest(String ipAddress, int limit) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return true;
        }

        int effectiveLimit = limit > 0 ? limit : DEFAULT_RATE_LIMIT_PER_IP;
        return checkAndRecord(ipRequests, IP_PREFIX + ipAddress, effectiveLimit);
    }

    /**
     * Kombinovaná kontrola pro telefon i IP
     *
     * @param phoneNumber   Telefonní číslo
     * @param ipAddress     IP adresa
     * @param phoneLimit    Limit pro telefon
     * @param ipLimit       Limit pro IP
     * @return RateLimitResult s informací o výsledku
     */
    public RateLimitResult checkRateLimit(String phoneNumber, String ipAddress, int phoneLimit, int ipLimit) {
        // Nejdříve kontrola IP (méně specifická)
        if (!checkIpWithoutRecord(ipAddress, ipLimit)) {
            logger.warnf("Rate limit exceeded for IP: %s", maskIpAddress(ipAddress));
            return new RateLimitResult(false, RateLimitType.IP, getRemainingTime(ipRequests, IP_PREFIX + ipAddress));
        }

        // Pak kontrola telefonu (specifičtější)
        if (!checkPhoneWithoutRecord(phoneNumber, phoneLimit)) {
            logger.warnf("Rate limit exceeded for phone: %s", maskPhoneNumber(phoneNumber));
            return new RateLimitResult(false, RateLimitType.PHONE, getRemainingTime(phoneRequests, PHONE_PREFIX + phoneNumber));
        }

        // Obě kontroly prošly - zaznamenáme požadavky
        recordRequest(phoneRequests, PHONE_PREFIX + phoneNumber);
        recordRequest(ipRequests, IP_PREFIX + ipAddress);

        return new RateLimitResult(true, null, 0);
    }

    /**
     * Získá počet zbývajících požadavků pro telefon
     */
    public int getRemainingPhoneRequests(String phoneNumber, int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_RATE_LIMIT_PER_PHONE;
        return getRemainingRequests(phoneRequests, PHONE_PREFIX + phoneNumber, effectiveLimit);
    }

    /**
     * Získá počet zbývajících požadavků pro IP
     */
    public int getRemainingIpRequests(String ipAddress, int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_RATE_LIMIT_PER_IP;
        return getRemainingRequests(ipRequests, IP_PREFIX + ipAddress, effectiveLimit);
    }

    /**
     * Vyčistí staré záznamy z cache (pro údržbu)
     */
    public void cleanupExpiredEntries() {
        long cutoff = Instant.now().getEpochSecond() - WINDOW_SIZE_SECONDS;

        cleanupMap(phoneRequests, cutoff);
        cleanupMap(ipRequests, cutoff);

        logger.debug("Rate limit cache cleanup completed");
    }

    /**
     * Resetuje rate limit pro konkrétní telefon (pro admin účely)
     */
    public void resetPhoneLimit(String phoneNumber) {
        phoneRequests.remove(PHONE_PREFIX + phoneNumber);
        logger.infof("Rate limit reset for phone: %s", maskPhoneNumber(phoneNumber));
    }

    /**
     * Resetuje rate limit pro konkrétní IP (pro admin účely)
     */
    public void resetIpLimit(String ipAddress) {
        ipRequests.remove(IP_PREFIX + ipAddress);
        logger.infof("Rate limit reset for IP: %s", maskIpAddress(ipAddress));
    }

    // === Private metody ===

    private boolean checkAndRecord(Map<String, CopyOnWriteArrayList<Long>> cache, String key, int limit) {
        long now = Instant.now().getEpochSecond();
        long windowStart = now - WINDOW_SIZE_SECONDS;

        CopyOnWriteArrayList<Long> timestamps = cache.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());

        // Odstraníme staré záznamy
        timestamps.removeIf(ts -> ts < windowStart);

        // Kontrola limitu
        if (timestamps.size() >= limit) {
            return false;
        }

        // Záznam nového požadavku
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

        // Počítáme pouze platné záznamy
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

        // Najdeme nejstarší platný záznam
        long oldestValid = timestamps.stream()
                .filter(ts -> ts >= windowStart)
                .min(Long::compareTo)
                .orElse(Instant.now().getEpochSecond());

        // Čas do vypršení nejstaršího záznamu
        return Math.max(0, (oldestValid + WINDOW_SIZE_SECONDS) - Instant.now().getEpochSecond());
    }

    private void cleanupMap(Map<String, CopyOnWriteArrayList<Long>> cache, long cutoff) {
        Iterator<Map.Entry<String, CopyOnWriteArrayList<Long>>> iterator = cache.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, CopyOnWriteArrayList<Long>> entry = iterator.next();
            CopyOnWriteArrayList<Long> timestamps = entry.getValue();

            // Odstraníme staré záznamy
            timestamps.removeIf(ts -> ts < cutoff);

            // Pokud je seznam prázdný, odstraníme celý záznam
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
        // Pro IPv4 zobrazíme první oktet
        if (ipAddress.contains(".")) {
            int firstDot = ipAddress.indexOf('.');
            if (firstDot > 0) {
                return ipAddress.substring(0, firstDot) + ".***";
            }
        }
        // Pro IPv6 zobrazíme první segment
        if (ipAddress.contains(":")) {
            int firstColon = ipAddress.indexOf(':');
            if (firstColon > 0) {
                return ipAddress.substring(0, firstColon) + ":***";
            }
        }
        return "****";
    }

    // === Vnitřní třídy ===

    /**
     * Typ rate limitu, který byl překročen
     */
    public enum RateLimitType {
        PHONE,
        IP
    }

    /**
     * Výsledek rate limit kontroly
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
                return String.format("Příliš mnoho požadavků pro toto telefonní číslo. Zkuste to znovu za %d minut.", minutes + 1);
            } else {
                return String.format("Příliš mnoho požadavků z vaší IP adresy. Zkuste to znovu za %d minut.", minutes + 1);
            }
        }
    }
}
