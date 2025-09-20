package com.tegadeti.keycloak.provider;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.authentication.ConfigurableAuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

/**
 * Factory pro Phone Number Authenticator
 * Vytváří instance PhoneAuthenticator a definuje konfigurační možnosti
 */
public class PhoneAuthenticatorFactory implements AuthenticatorFactory, ConfigurableAuthenticatorFactory {

    public static final String PROVIDER_ID = "phone-authenticator";

    // Konfigurační properties
    public static final String TWILIO_ACCOUNT_SID = "twilio.account.sid";
    public static final String TWILIO_AUTH_TOKEN = "twilio.auth.token";
    public static final String TWILIO_FROM_NUMBER = "twilio.from.number";
    public static final String SMS_CODE_TTL = "sms.code.ttl";
    public static final String SMS_CODE_LENGTH = "sms.code.length";
    public static final String CREATE_USER_IF_NOT_EXISTS = "create.user.if.not.exists";

    private static final PhoneAuthenticator SINGLETON = new PhoneAuthenticator();

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Phone Number Authentication";
    }

    @Override
    public String getHelpText() {
        return "Authenticates users via phone number and SMS verification code";
    }

    @Override
    public String getReferenceCategory() {
        return "phone";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.REQUIRED,
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        // Inicializace factory
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Post-inicializace
    }

    @Override
    public void close() {
        // Cleanup
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(TWILIO_ACCOUNT_SID)
                .label("Twilio Account SID")
                .helpText("Your Twilio Account SID")
                .type(ProviderConfigProperty.STRING_TYPE)
                .secret(false)
                .add()
                .property()
                .name(TWILIO_AUTH_TOKEN)
                .label("Twilio Auth Token")
                .helpText("Your Twilio Auth Token")
                .type(ProviderConfigProperty.PASSWORD)
                .secret(true)
                .add()
                .property()
                .name(TWILIO_FROM_NUMBER)
                .label("Twilio From Number")
                .helpText("Twilio phone number to send SMS from (format: +1234567890)")
                .type(ProviderConfigProperty.STRING_TYPE)
                .secret(false)
                .add()
                .property()
                .name(SMS_CODE_TTL)
                .label("SMS Code TTL (seconds)")
                .helpText("Time to live for SMS verification codes in seconds")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("300")
                .secret(false)
                .add()
                .property()
                .name(SMS_CODE_LENGTH)
                .label("SMS Code Length")
                .helpText("Length of the SMS verification code")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("4", "5", "6", "8")
                .defaultValue("6")
                .secret(false)
                .add()
                .property()
                .name(CREATE_USER_IF_NOT_EXISTS)
                .label("Create User If Not Exists")
                .helpText("Create new user if phone number is not found")
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .defaultValue("true")
                .secret(false)
                .add()
                .build();
    }
}