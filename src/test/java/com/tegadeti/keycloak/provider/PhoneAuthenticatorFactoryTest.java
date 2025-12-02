package com.tegadeti.keycloak.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit testy pro PhoneAuthenticatorFactory
 */
class PhoneAuthenticatorFactoryTest {

    private PhoneAuthenticatorFactory factory;

    @BeforeEach
    void setUp() {
        factory = new PhoneAuthenticatorFactory();
    }

    @Test
    @DisplayName("Provider ID should be 'phone-authenticator'")
    void testGetId() {
        assertEquals("phone-authenticator", factory.getId());
    }

    @Test
    @DisplayName("Display type should be descriptive")
    void testGetDisplayType() {
        String displayType = factory.getDisplayType();
        assertNotNull(displayType);
        assertTrue(displayType.toLowerCase().contains("phone"));
    }

    @Test
    @DisplayName("Help text should be provided")
    void testGetHelpText() {
        String helpText = factory.getHelpText();
        assertNotNull(helpText);
        assertFalse(helpText.isEmpty());
    }

    @Test
    @DisplayName("Reference category should be 'phone'")
    void testGetReferenceCategory() {
        assertEquals("phone", factory.getReferenceCategory());
    }

    @Test
    @DisplayName("Should be configurable")
    void testIsConfigurable() {
        assertTrue(factory.isConfigurable());
    }

    @Test
    @DisplayName("User setup should not be allowed")
    void testIsUserSetupAllowed() {
        assertFalse(factory.isUserSetupAllowed());
    }

    @Test
    @DisplayName("Should support REQUIRED, ALTERNATIVE, DISABLED requirements")
    void testGetRequirementChoices() {
        AuthenticationExecutionModel.Requirement[] choices = factory.getRequirementChoices();

        assertNotNull(choices);
        assertEquals(3, choices.length);

        List<AuthenticationExecutionModel.Requirement> choiceList = List.of(choices);
        assertTrue(choiceList.contains(AuthenticationExecutionModel.Requirement.REQUIRED));
        assertTrue(choiceList.contains(AuthenticationExecutionModel.Requirement.ALTERNATIVE));
        assertTrue(choiceList.contains(AuthenticationExecutionModel.Requirement.DISABLED));
    }

    @Test
    @DisplayName("Should create PhoneAuthenticator instance")
    void testCreate() {
        Authenticator authenticator = factory.create(null);

        assertNotNull(authenticator);
        assertInstanceOf(PhoneAuthenticator.class, authenticator);
    }

    @Test
    @DisplayName("Should return singleton Authenticator instance")
    void testCreateReturnsSingleton() {
        Authenticator auth1 = factory.create(null);
        Authenticator auth2 = factory.create(null);

        assertSame(auth1, auth2, "Factory should return singleton instance");
    }

    @Test
    @DisplayName("Config properties should include Twilio settings")
    void testGetConfigPropertiesTwilio() {
        List<ProviderConfigProperty> props = factory.getConfigProperties();

        assertNotNull(props);
        assertFalse(props.isEmpty());

        // Kontrola Twilio properties
        assertTrue(hasProperty(props, PhoneAuthenticatorFactory.TWILIO_ACCOUNT_SID));
        assertTrue(hasProperty(props, PhoneAuthenticatorFactory.TWILIO_AUTH_TOKEN));
        assertTrue(hasProperty(props, PhoneAuthenticatorFactory.TWILIO_FROM_NUMBER));
    }

    @Test
    @DisplayName("Config properties should include SMS settings")
    void testGetConfigPropertiesSms() {
        List<ProviderConfigProperty> props = factory.getConfigProperties();

        assertTrue(hasProperty(props, PhoneAuthenticatorFactory.SMS_CODE_TTL));
        assertTrue(hasProperty(props, PhoneAuthenticatorFactory.SMS_CODE_LENGTH));
        assertTrue(hasProperty(props, PhoneAuthenticatorFactory.SMS_MESSAGE_TEMPLATE));
    }

    @Test
    @DisplayName("Config properties should include rate limiting settings")
    void testGetConfigPropertiesRateLimit() {
        List<ProviderConfigProperty> props = factory.getConfigProperties();

        assertTrue(hasProperty(props, PhoneAuthenticatorFactory.RATE_LIMIT_PER_PHONE));
        assertTrue(hasProperty(props, PhoneAuthenticatorFactory.RATE_LIMIT_PER_IP));
    }

    @Test
    @DisplayName("Config properties should include development mode")
    void testGetConfigPropertiesDevelopmentMode() {
        List<ProviderConfigProperty> props = factory.getConfigProperties();

        assertTrue(hasProperty(props, PhoneAuthenticatorFactory.DEVELOPMENT_MODE));
    }

    @Test
    @DisplayName("Config properties should include user creation setting")
    void testGetConfigPropertiesCreateUser() {
        List<ProviderConfigProperty> props = factory.getConfigProperties();

        assertTrue(hasProperty(props, PhoneAuthenticatorFactory.CREATE_USER_IF_NOT_EXISTS));
    }

    @Test
    @DisplayName("Auth token should be marked as secret")
    void testAuthTokenIsSecret() {
        List<ProviderConfigProperty> props = factory.getConfigProperties();

        ProviderConfigProperty authToken = findProperty(props, PhoneAuthenticatorFactory.TWILIO_AUTH_TOKEN);
        assertNotNull(authToken);
        assertTrue(authToken.isSecret(), "Auth token should be secret");
    }

    @Test
    @DisplayName("SMS code length should have predefined options")
    void testSmsCodeLengthOptions() {
        List<ProviderConfigProperty> props = factory.getConfigProperties();

        ProviderConfigProperty codeLength = findProperty(props, PhoneAuthenticatorFactory.SMS_CODE_LENGTH);
        assertNotNull(codeLength);
        assertNotNull(codeLength.getOptions());
        assertTrue(codeLength.getOptions().contains("6"), "Should include 6-digit option");
    }

    @Test
    @DisplayName("Default SMS template should use placeholders")
    void testDefaultSmsTemplate() {
        String template = PhoneAuthenticatorFactory.DEFAULT_SMS_TEMPLATE;

        assertNotNull(template);
        assertTrue(template.contains("{code}"), "Template should have {code} placeholder");
        assertTrue(template.contains("{ttl}"), "Template should have {ttl} placeholder");
    }

    @Test
    @DisplayName("Init should not throw exception")
    void testInit() {
        assertDoesNotThrow(() -> factory.init(null));
    }

    @Test
    @DisplayName("PostInit should not throw exception")
    void testPostInit() {
        assertDoesNotThrow(() -> factory.postInit(null));
    }

    @Test
    @DisplayName("Close should not throw exception")
    void testClose() {
        assertDoesNotThrow(() -> factory.close());
    }

    // Helper methods

    private boolean hasProperty(List<ProviderConfigProperty> props, String name) {
        return props.stream().anyMatch(p -> name.equals(p.getName()));
    }

    private ProviderConfigProperty findProperty(List<ProviderConfigProperty> props, String name) {
        return props.stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }
}
