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
 * Factory for Phone Number Authenticator
 * Creates PhoneAuthenticator instances and defines configuration options
 */
public class PhoneAuthenticatorFactory implements AuthenticatorFactory, ConfigurableAuthenticatorFactory {

    public static final String PROVIDER_ID = "phone-authenticator";

    // Configuration properties
    public static final String TWILIO_ACCOUNT_SID = "twilio.account.sid";
    public static final String TWILIO_AUTH_TOKEN = "twilio.auth.token";
    public static final String TWILIO_FROM_NUMBER = "twilio.from.number";
    public static final String SMS_CODE_TTL = "sms.code.ttl";
    public static final String SMS_CODE_LENGTH = "sms.code.length";
    public static final String CREATE_USER_IF_NOT_EXISTS = "create.user.if.not.exists";
    public static final String DEFAULT_CALLING_CODE = "default.calling.code";
    public static final String STRICT_PHONE_VALIDATION = "strict.phone.validation";

    // Code verification
    public static final String MAX_CODE_ATTEMPTS = "max.code.attempts";
    public static final String RESEND_COOLDOWN = "resend.cooldown.seconds";
    public static final int DEFAULT_MAX_CODE_ATTEMPTS = 5;
    public static final long DEFAULT_RESEND_COOLDOWN_SECONDS = 60;

    // SMS provider
    public static final String SMS_PROVIDER = "sms.provider";
    public static final String HTTP_SMS_URL = "http.sms.url";
    public static final String HTTP_SMS_AUTH_TOKEN = "http.sms.auth.token";

    // SMS Template
    public static final String SMS_MESSAGE_TEMPLATE = "sms.message.template";
    public static final String DEFAULT_SMS_TEMPLATE = "Your verification code: {code}\n\nThe code is valid for {ttl} minutes.";

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
        return "Authenticates users via phone number and SMS verification code. Works as the first factor (user enters the number) or, placed after another step, as a second factor (code is sent to the number stored on the already identified user).";
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
        // Factory initialization
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Post-initialization
    }

    @Override
    public void close() {
        // Cleanup
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(SMS_PROVIDER)
                .label("SMS Provider")
                .helpText("How SMS messages are delivered: 'twilio' (Twilio Programmable Messaging) or 'http' (generic HTTP gateway, see HTTP SMS Gateway URL).")
                .type(ProviderConfigProperty.LIST_TYPE)
                .options("twilio", "http")
                .defaultValue("twilio")
                .secret(false)
                .add()
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
                .name(HTTP_SMS_URL)
                .label("HTTP SMS Gateway URL")
                .helpText("Used when SMS Provider is 'http'. Receives POST {\"to\": \"+420...\", \"message\": \"...\"}; any 2xx response means the SMS was accepted.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .secret(false)
                .add()
                .property()
                .name(HTTP_SMS_AUTH_TOKEN)
                .label("HTTP SMS Gateway Bearer Token")
                .helpText("Optional. Sent as 'Authorization: Bearer <token>' to the HTTP SMS gateway.")
                .type(ProviderConfigProperty.PASSWORD)
                .secret(true)
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
                .name(MAX_CODE_ATTEMPTS)
                .label("Max Code Attempts")
                .helpText("How many wrong codes are accepted before the code is invalidated.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(DEFAULT_MAX_CODE_ATTEMPTS))
                .secret(false)
                .add()
                .property()
                .name(RESEND_COOLDOWN)
                .label("Resend Cooldown (seconds)")
                .helpText("Minimum time between two SMS codes for the same login attempt.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(DEFAULT_RESEND_COOLDOWN_SECONDS))
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
                .name(DEFAULT_CALLING_CODE)
                .label("Default Calling Code")
                .helpText("International calling code (digits only, without +) added to numbers entered without a prefix, e.g. 420 for Czechia or 421 for Slovakia.")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(PhoneNumberUtils.DEFAULT_CALLING_CODE)
                .secret(false)
                .add()
                .property()
                .name(STRICT_PHONE_VALIDATION)
                .label("Strict Phone Validation")
                .helpText("Reject numbers that are not valid according to the libphonenumber database (country specific length and prefixes). Disable only for test numbers such as +420123456789.")
                .type(ProviderConfigProperty.BOOLEAN_TYPE)
                .defaultValue("false")
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