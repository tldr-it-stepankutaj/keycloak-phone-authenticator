package com.tegadeti.keycloak.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit testy pro RateLimitService
 */
class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = RateLimitService.getInstance();
        // Reset rate limits pro čistý test
        rateLimitService.resetPhoneLimit("+420123456789");
        rateLimitService.resetPhoneLimit("+420987654321");
        rateLimitService.resetIpLimit("192.168.1.1");
        rateLimitService.resetIpLimit("10.0.0.1");
    }

    @Test
    @DisplayName("Singleton instance should be the same")
    void testSingletonInstance() {
        RateLimitService instance1 = RateLimitService.getInstance();
        RateLimitService instance2 = RateLimitService.getInstance();
        assertSame(instance1, instance2);
    }

    @Test
    @DisplayName("Should allow requests within phone limit")
    void testPhoneRequestWithinLimit() {
        String phone = "+420123456789";
        int limit = 5;

        for (int i = 0; i < limit; i++) {
            assertTrue(rateLimitService.checkAndRecordPhoneRequest(phone, limit),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("Should block requests exceeding phone limit")
    void testPhoneRequestExceedsLimit() {
        String phone = "+420987654321";
        int limit = 3;

        // První 3 požadavky by měly projít
        for (int i = 0; i < limit; i++) {
            assertTrue(rateLimitService.checkAndRecordPhoneRequest(phone, limit));
        }

        // 4. požadavek by měl být zablokován
        assertFalse(rateLimitService.checkAndRecordPhoneRequest(phone, limit));
    }

    @Test
    @DisplayName("Should allow requests within IP limit")
    void testIpRequestWithinLimit() {
        String ip = "192.168.1.1";
        int limit = 10;

        for (int i = 0; i < limit; i++) {
            assertTrue(rateLimitService.checkAndRecordIpRequest(ip, limit),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("Should block requests exceeding IP limit")
    void testIpRequestExceedsLimit() {
        String ip = "10.0.0.1";
        int limit = 2;

        assertTrue(rateLimitService.checkAndRecordIpRequest(ip, limit));
        assertTrue(rateLimitService.checkAndRecordIpRequest(ip, limit));
        assertFalse(rateLimitService.checkAndRecordIpRequest(ip, limit));
    }

    @Test
    @DisplayName("Combined rate limit check should work")
    void testCombinedRateLimitCheck() {
        String phone = "+420111222333";
        String ip = "172.16.0.1";

        rateLimitService.resetPhoneLimit(phone);
        rateLimitService.resetIpLimit(ip);

        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(phone, ip, 5, 20);

        assertTrue(result.isAllowed());
        assertNull(result.getExceededType());
    }

    @Test
    @DisplayName("Should return correct remaining phone requests")
    void testRemainingPhoneRequests() {
        String phone = "+420555666777";
        int limit = 5;

        rateLimitService.resetPhoneLimit(phone);

        assertEquals(limit, rateLimitService.getRemainingPhoneRequests(phone, limit));

        rateLimitService.checkAndRecordPhoneRequest(phone, limit);
        assertEquals(limit - 1, rateLimitService.getRemainingPhoneRequests(phone, limit));

        rateLimitService.checkAndRecordPhoneRequest(phone, limit);
        assertEquals(limit - 2, rateLimitService.getRemainingPhoneRequests(phone, limit));
    }

    @Test
    @DisplayName("Should return correct remaining IP requests")
    void testRemainingIpRequests() {
        String ip = "8.8.8.8";
        int limit = 10;

        rateLimitService.resetIpLimit(ip);

        assertEquals(limit, rateLimitService.getRemainingIpRequests(ip, limit));

        rateLimitService.checkAndRecordIpRequest(ip, limit);
        assertEquals(limit - 1, rateLimitService.getRemainingIpRequests(ip, limit));
    }

    @Test
    @DisplayName("Reset should clear phone limit")
    void testResetPhoneLimit() {
        String phone = "+420999888777";
        int limit = 2;

        rateLimitService.resetPhoneLimit(phone);

        // Vyčerpáme limit
        rateLimitService.checkAndRecordPhoneRequest(phone, limit);
        rateLimitService.checkAndRecordPhoneRequest(phone, limit);
        assertFalse(rateLimitService.checkAndRecordPhoneRequest(phone, limit));

        // Reset
        rateLimitService.resetPhoneLimit(phone);

        // Nyní by měl být opět povolen
        assertTrue(rateLimitService.checkAndRecordPhoneRequest(phone, limit));
    }

    @Test
    @DisplayName("Reset should clear IP limit")
    void testResetIpLimit() {
        String ip = "1.2.3.4";
        int limit = 1;

        rateLimitService.resetIpLimit(ip);

        rateLimitService.checkAndRecordIpRequest(ip, limit);
        assertFalse(rateLimitService.checkAndRecordIpRequest(ip, limit));

        rateLimitService.resetIpLimit(ip);
        assertTrue(rateLimitService.checkAndRecordIpRequest(ip, limit));
    }

    @Test
    @DisplayName("Should handle null phone number gracefully")
    void testNullPhoneNumber() {
        assertTrue(rateLimitService.checkAndRecordPhoneRequest(null, 5));
    }

    @Test
    @DisplayName("Should handle empty phone number gracefully")
    void testEmptyPhoneNumber() {
        assertTrue(rateLimitService.checkAndRecordPhoneRequest("", 5));
    }

    @Test
    @DisplayName("Should handle null IP address gracefully")
    void testNullIpAddress() {
        assertTrue(rateLimitService.checkAndRecordIpRequest(null, 20));
    }

    @Test
    @DisplayName("Should handle empty IP address gracefully")
    void testEmptyIpAddress() {
        assertTrue(rateLimitService.checkAndRecordIpRequest("", 20));
    }

    @Test
    @DisplayName("RateLimitResult should provide correct error message for phone")
    void testRateLimitResultErrorMessagePhone() {
        RateLimitService.RateLimitResult result = new RateLimitService.RateLimitResult(
                false,
                RateLimitService.RateLimitType.PHONE,
                120 // 2 minuty
        );

        assertFalse(result.isAllowed());
        assertEquals(RateLimitService.RateLimitType.PHONE, result.getExceededType());
        assertTrue(result.getErrorMessage().contains("telefonn"));
    }

    @Test
    @DisplayName("RateLimitResult should provide correct error message for IP")
    void testRateLimitResultErrorMessageIp() {
        RateLimitService.RateLimitResult result = new RateLimitService.RateLimitResult(
                false,
                RateLimitService.RateLimitType.IP,
                300 // 5 minut
        );

        assertFalse(result.isAllowed());
        assertEquals(RateLimitService.RateLimitType.IP, result.getExceededType());
        assertTrue(result.getErrorMessage().contains("IP"));
    }

    @Test
    @DisplayName("Allowed result should have no error message")
    void testRateLimitResultAllowed() {
        RateLimitService.RateLimitResult result = new RateLimitService.RateLimitResult(
                true, null, 0
        );

        assertTrue(result.isAllowed());
        assertNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("Different phones should have independent limits")
    void testIndependentPhoneLimits() {
        String phone1 = "+420111111111";
        String phone2 = "+420222222222";
        int limit = 2;

        rateLimitService.resetPhoneLimit(phone1);
        rateLimitService.resetPhoneLimit(phone2);

        // Vyčerpáme limit pro phone1
        rateLimitService.checkAndRecordPhoneRequest(phone1, limit);
        rateLimitService.checkAndRecordPhoneRequest(phone1, limit);
        assertFalse(rateLimitService.checkAndRecordPhoneRequest(phone1, limit));

        // phone2 by měl být stále OK
        assertTrue(rateLimitService.checkAndRecordPhoneRequest(phone2, limit));
    }

    @Test
    @DisplayName("Different IPs should have independent limits")
    void testIndependentIpLimits() {
        String ip1 = "192.168.1.100";
        String ip2 = "192.168.1.200";
        int limit = 1;

        rateLimitService.resetIpLimit(ip1);
        rateLimitService.resetIpLimit(ip2);

        rateLimitService.checkAndRecordIpRequest(ip1, limit);
        assertFalse(rateLimitService.checkAndRecordIpRequest(ip1, limit));

        // ip2 by měl být stále OK
        assertTrue(rateLimitService.checkAndRecordIpRequest(ip2, limit));
    }
}
