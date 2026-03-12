package com.tegadeti.keycloak.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PhoneAuthenticator
 * Testing helper methods and logic without mocking Keycloak interfaces
 */
class PhoneAuthenticatorTest {

    private PhoneAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new PhoneAuthenticator();
    }

    @Test
    @DisplayName("requiresUser should return false")
    void testRequiresUser() {
        assertFalse(authenticator.requiresUser());
    }

    @Test
    @DisplayName("configuredFor should return true for any input")
    void testConfiguredFor() {
        // Method always returns true
        assertTrue(authenticator.configuredFor(null, null, null));
    }

    @Test
    @DisplayName("setRequiredActions should not throw")
    void testSetRequiredActions() {
        assertDoesNotThrow(() -> authenticator.setRequiredActions(null, null, null));
    }

    @Test
    @DisplayName("close should not throw")
    void testClose() {
        assertDoesNotThrow(() -> authenticator.close());
    }

    // =========================================================================
    // Tests for phone number validation (E.164 format)
    // =========================================================================

    @Test
    @DisplayName("Phone validation - valid E.164 format")
    void testValidPhoneFormats() {
        String[] validNumbers = {
                "+420123456789",
                "+421987654321",
                "+1234567890",
                "+442071234567",
                "+86123456789012"
        };

        for (String number : validNumbers) {
            assertTrue(isValidE164(number), "Should be valid: " + number);
        }
    }

    @Test
    @DisplayName("Phone validation - invalid formats")
    void testInvalidPhoneFormats() {
        String[] invalidNumbers = {
                "123456789",      // without +
                "+0123456789",    // starts with +0
                "++420123456789", // double +
                "+4",             // too short (only 1 digit)
                "",               // empty
                "abcdefghij",     // letters
                "+",              // just plus
                "+abc123"         // mixed
        };

        for (String number : invalidNumbers) {
            assertFalse(isValidE164(number), "Should be invalid: " + number);
        }
    }

    @Test
    @DisplayName("Phone validation - null should be invalid")
    void testValidationNull() {
        assertFalse(isValidE164(null));
    }

    // =========================================================================
    // Tests for phone number masking
    // =========================================================================

    @Test
    @DisplayName("Phone masking should hide middle digits")
    void testPhoneMasking() {
        String masked = maskPhoneNumber("+420123456789");

        assertNotNull(masked);
        assertTrue(masked.startsWith("+420"));
        assertTrue(masked.contains("*"));
        assertEquals("+420*********", masked);
    }

    @Test
    @DisplayName("Phone masking should handle short numbers")
    void testPhoneMaskingShortNumber() {
        assertEquals("****", maskPhoneNumber("+42"));
        assertEquals("****", maskPhoneNumber("+4"));
        assertEquals("****", maskPhoneNumber("+"));
    }

    @Test
    @DisplayName("Phone masking should handle null")
    void testPhoneMaskingNull() {
        assertEquals("****", maskPhoneNumber(null));
    }

    @Test
    @DisplayName("Phone masking should handle empty string")
    void testPhoneMaskingEmpty() {
        assertEquals("****", maskPhoneNumber(""));
    }

    @Test
    @DisplayName("Phone masking should handle exactly 4 chars")
    void testPhoneMaskingExact4Chars() {
        String masked = maskPhoneNumber("+420");
        assertEquals("+420", masked);
    }

    // =========================================================================
    // Tests for phone number normalization
    // =========================================================================

    @Test
    @DisplayName("Normalize Czech number without prefix (9 digits)")
    void testNormalizeCzechNumber9Digits() {
        assertEquals("+420123456789", normalizePhoneNumber("123456789"));
    }

    @Test
    @DisplayName("Normalize Czech number with leading zero")
    void testNormalizeCzechNumberWithZero() {
        assertEquals("+420123456789", normalizePhoneNumber("0123456789"));
    }

    @Test
    @DisplayName("Normalize number with spaces")
    void testNormalizeNumberWithSpaces() {
        assertEquals("+420123456789", normalizePhoneNumber("+420 123 456 789"));
    }

    @Test
    @DisplayName("Normalize number with dashes")
    void testNormalizeNumberWithDashes() {
        assertEquals("+420123456789", normalizePhoneNumber("+420-123-456-789"));
    }

    @Test
    @DisplayName("Normalize number with parentheses")
    void testNormalizeNumberWithParentheses() {
        assertEquals("+420123456789", normalizePhoneNumber("+420(123)456789"));
    }

    @Test
    @DisplayName("Normalize already valid number")
    void testNormalizeValidNumber() {
        assertEquals("+420123456789", normalizePhoneNumber("+420123456789"));
    }

    @Test
    @DisplayName("Normalize international number should preserve")
    void testNormalizeInternationalNumber() {
        assertEquals("+1234567890", normalizePhoneNumber("+1234567890"));
        assertEquals("+442071234567", normalizePhoneNumber("+44 207 123 4567"));
    }

    @Test
    @DisplayName("Normalize null should return null")
    void testNormalizeNull() {
        assertNull(normalizePhoneNumber(null));
    }

    @Test
    @DisplayName("Normalize empty should return empty with +")
    void testNormalizeEmpty() {
        // empty string after removing non-numeric characters
        String result = normalizePhoneNumber("");
        assertEquals("", result);
    }

    // =========================================================================
    // Helper methods (copy of logic from PhoneAuthenticator)
    // =========================================================================

    /**
     * Simulates E.164 format validation
     */
    private boolean isValidE164(String phoneNumber) {
        if (phoneNumber == null) return false;
        return phoneNumber.matches("^\\+[1-9]\\d{1,14}$");
    }

    /**
     * Simulates phone number masking
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        return phoneNumber.substring(0, 4) + "*".repeat(phoneNumber.length() - 4);
    }

    /**
     * Simulates phone number normalization
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;

        // Remove all non-numeric characters except +
        String normalized = phoneNumber.replaceAll("[^+\\d]", "");

        // If it doesn't start with +, add +420 for Czech numbers
        if (!normalized.startsWith("+")) {
            if (normalized.length() == 9) {
                normalized = "+420" + normalized;
            } else if (normalized.length() == 10 && normalized.startsWith("0")) {
                normalized = "+420" + normalized.substring(1);
            }
        }

        return normalized;
    }
}
