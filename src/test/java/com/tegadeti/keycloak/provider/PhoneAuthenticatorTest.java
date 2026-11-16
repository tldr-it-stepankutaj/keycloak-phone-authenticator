package com.tegadeti.keycloak.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the trivial PhoneAuthenticator contract methods.
 * Phone number helpers are covered by {@link PhoneNumberUtilsTest}, the flow by {@link PhoneAuthenticatorFlowTest}.
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
}
