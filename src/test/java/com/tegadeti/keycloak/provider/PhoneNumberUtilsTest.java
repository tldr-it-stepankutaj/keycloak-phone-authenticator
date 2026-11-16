package com.tegadeti.keycloak.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumberUtilsTest {

    private static String cz(String input) {
        return PhoneNumberUtils.normalize(input, "420");
    }

    @Test
    @DisplayName("Valid E.164 numbers")
    void validNumbers() {
        for (String n : new String[]{"+420123456789", "+421987654321", "+1234567890", "+442071234567", "+86123456789012"}) {
            assertTrue(PhoneNumberUtils.isValid(n), n);
        }
    }

    @Test
    @DisplayName("Invalid numbers")
    void invalidNumbers() {
        for (String n : new String[]{"123456789", "+0123456789", "++420123456789", "+4", "", "abcdefghij", "+", "+abc123"}) {
            assertFalse(PhoneNumberUtils.isValid(n), n);
        }
        assertFalse(PhoneNumberUtils.isValid(null));
    }

    @Test
    @DisplayName("Masking")
    void masking() {
        assertEquals("+420*********", PhoneNumberUtils.mask("+420123456789"));
        assertEquals("+420", PhoneNumberUtils.mask("+420"));
        assertEquals("****", PhoneNumberUtils.mask("+42"));
        assertEquals("****", PhoneNumberUtils.mask(""));
        assertEquals("****", PhoneNumberUtils.mask(null));
    }

    @Test
    @DisplayName("Normalization of national and formatted numbers")
    void normalizeNational() {
        assertEquals("+420123456789", cz("123456789"));
        assertEquals("+420123456789", cz("0123456789"));
        assertEquals("+420123456789", cz("+420 123 456 789"));
        assertEquals("+420123456789", cz("+420-123-456-789"));
        assertEquals("+420123456789", cz("+420(123)456789"));
        assertEquals("+442071234567", cz("+44 207 123 4567"));
    }

    @Test
    @DisplayName("00 prefix is an international prefix")
    void normalizeDoubleZero() {
        assertEquals("+420123456789", cz("00420123456789"));
        assertEquals("+442071234567", cz("0044 207 123 4567"));
    }

    @Test
    @DisplayName("Default calling code is configurable (Slovak numbers are no longer prefixed with +420)")
    void normalizeWithSlovakCode() {
        assertEquals("+421912345678", PhoneNumberUtils.normalize("912 345 678", "421"));
        assertEquals("+421912345678", PhoneNumberUtils.normalize("0912345678", "+421"));
        // an explicit prefix always wins
        assertEquals("+420123456789", PhoneNumberUtils.normalize("+420123456789", "421"));
    }

    @Test
    @DisplayName("Malformed calling code falls back to the default")
    void malformedCallingCode() {
        assertEquals("420", PhoneNumberUtils.sanitizeCallingCode(null));
        assertEquals("420", PhoneNumberUtils.sanitizeCallingCode("abc"));
        assertEquals("420", PhoneNumberUtils.sanitizeCallingCode("0420"));
        assertEquals("421", PhoneNumberUtils.sanitizeCallingCode(" +421 "));
        assertEquals("+420123456789", PhoneNumberUtils.normalize("123456789", "garbage"));
    }

    @Test
    @DisplayName("Null and empty input")
    void nullAndEmpty() {
        assertNull(cz(null));
        assertEquals("", cz(""));
    }

    @Test
    @DisplayName("libphonenumber handles trunk prefixes per country")
    void libraryNormalization() {
        // UK: the national 0 is a trunk prefix and must be dropped
        assertEquals("+447911123456", PhoneNumberUtils.normalize("07911 123456", "44"));
        // Italy: the leading 0 of fixed lines is part of the number and must be kept
        assertEquals("+390612345678", PhoneNumberUtils.normalize("06 1234 5678", "39"));
        // Valid Czech mobile without prefix
        assertEquals("+420601123456", PhoneNumberUtils.normalize("601 123 456", "420"));
    }

    @Test
    @DisplayName("Strict validation uses libphonenumber, basic validation only checks the E.164 shape")
    void strictValidation() {
        assertTrue(PhoneNumberUtils.isValid("+420601123456", true));
        assertTrue(PhoneNumberUtils.isValid("+447911123456", true));
        // shaped like E.164 but not a real Czech number
        assertTrue(PhoneNumberUtils.isValid("+420123456789", false));
        assertFalse(PhoneNumberUtils.isValid("+420123456789", true));
        assertFalse(PhoneNumberUtils.isValid("+4201", true));
        assertFalse(PhoneNumberUtils.isValid(null, true));
        assertFalse(PhoneNumberUtils.isValid("601123456", true));
    }
}
