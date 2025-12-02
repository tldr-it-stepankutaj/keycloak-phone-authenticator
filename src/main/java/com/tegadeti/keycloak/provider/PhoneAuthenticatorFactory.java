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

    // SMS Template
    public static final String SMS_MESSAGE_TEMPLATE = "sms.message.template";
    public static final String DEFAULT_SMS_TEMPLATE = "Váš ověřovací kód: {code}\n\nKód je platný {ttl} minut.";

    // Rate Limiting
    public static final String RATE_LIMIT_PER_PHONE = "rate.limit.per.phone";
    public static final String RATE_LIMIT_PER_IP = "rate.limit.per.ip";

    // Development Mode
    public static final String DEVELOPMENT_MODE = "development.mode";

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
                .property()
                .name(SMS_MESSAGE_TEMPLATE)
                .label("SMS Message Template")
                .helpText("Template for SMS message. Use {code} for verification code and {ttl} for TTL in minutes.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(DEFAULT_SMS_TEMPLATE)
                .secret(false)
                .add()
                .property()
                .name(RATE_LIMIT_PER_PHONE)
                .label("Rate Limit Per Phone (per hour)")
                .helpText("Maximum SMS messages per phone number per hour. Set to 0 to disable.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("5")
                .secret(false)
                .add()
                .property()
                .name(RATE_LIMIT_PER_IP)
                .label("Rate Limit Per IP (per hour)")
                .helpText("Maximum SMS messages per IP address per hour. Set to 0 to disable.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue("20")
                .secret(false)
                .add()
                .property()
                .name(DEVELOPMENT_MODE)
                .label("Development Mode")
                .helpText("When enabled, SMS codes are logged instead of being sent via Twilio. Use only for development!")
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .defaultValue("false")
                .secret(false)
                .add()
                .build();
    }
}