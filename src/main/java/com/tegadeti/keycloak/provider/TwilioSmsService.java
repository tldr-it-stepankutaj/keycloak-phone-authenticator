package com.tegadeti.keycloak.provider;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;

/**
 * Service for sending SMS messages via Twilio API
 * Supports development mode and configurable templates
 */
public class TwilioSmsService {

    private static final Logger logger = Logger.getLogger(TwilioSmsService.class);

    /**
     * Sends SMS with verification code
     * In development mode only logs the code instead of sending
     *
     * @param context Authentication flow context for obtaining configuration
     * @param phoneNumber Recipient phone number
     * @param verificationCode Verification code
     * @throws TwilioSmsException If SMS sending fails
     */
    public void sendSms(AuthenticationFlowContext context, String phoneNumber, String verificationCode)
            throws TwilioSmsException {

        // Check development mode
        boolean developmentMode = getBooleanConfigValue(context, PhoneAuthenticatorFactory.DEVELOPMENT_MODE, false);

        if (developmentMode) {
            sendDevelopmentSms(phoneNumber, verificationCode);
            return;
        }

        // Production mode - send via Twilio
        sendTwilioSms(context, phoneNumber, verificationCode);
    }

    /**
     * Development mode - logs SMS code instead of sending
     */
    private void sendDevelopmentSms(String phoneNumber, String verificationCode) {
        logger.warnf("=== DEVELOPMENT MODE ===");
        logger.warnf("SMS would be sent to: %s", phoneNumber);
        logger.warnf("Verification code: %s", verificationCode);
        logger.warnf("========================");

        // Simulate successful sending
        logger.infof("Development SMS 'sent' successfully to: %s", maskPhoneNumber(phoneNumber));
    }

    /**
     * Sends SMS via Twilio API
     */
    private void sendTwilioSms(AuthenticationFlowContext context, String phoneNumber, String verificationCode)
            throws TwilioSmsException {

        // Get configuration from Keycloak
        String accountSid = getConfigValue(context, PhoneAuthenticatorFactory.TWILIO_ACCOUNT_SID);
        String authToken = getConfigValue(context, PhoneAuthenticatorFactory.TWILIO_AUTH_TOKEN);
        String fromNumber = getConfigValue(context, PhoneAuthenticatorFactory.TWILIO_FROM_NUMBER);

        // Validate configuration
        validateConfiguration(accountSid, authToken, fromNumber);

        try {
            // Initialize Twilio client
            Twilio.init(accountSid, authToken);

            // Prepare SMS message from template
            String messageBody = buildMessageBody(context, verificationCode);

            // Send SMS
            Message message = Message.creator(
                    new PhoneNumber(phoneNumber),
                    new PhoneNumber(fromNumber),
                    messageBody
            ).create();

            logger.infof("SMS sent successfully. SID: %s, Status: %s, To: %s",
                    message.getSid(),
                    message.getStatus(),
                    maskPhoneNumber(phoneNumber));

            // Check message status
            if (Message.Status.FAILED.equals(message.getStatus())) {
                throw new TwilioSmsException("SMS delivery failed with status: " + message.getStatus());
            }

        } catch (TwilioSmsException e) {
            throw e;
        } catch (Exception e) {
            logger.errorf("Failed to send SMS to %s: %s", maskPhoneNumber(phoneNumber), e.getMessage());
            throw new TwilioSmsException("Failed to send SMS: " + e.getMessage(), e);
        }
    }

    /**
     * Builds SMS message body from configurable template
     *
     * @param context Authentication flow context
     * @param verificationCode Verification code
     * @return Prepared SMS message
     */
    private String buildMessageBody(AuthenticationFlowContext context, String verificationCode) {
        // Get template from configuration or use default
        String template = getConfigValue(context, PhoneAuthenticatorFactory.SMS_MESSAGE_TEMPLATE);
        if (template == null || template.trim().isEmpty()) {
            template = PhoneAuthenticatorFactory.DEFAULT_SMS_TEMPLATE;
        }

        // Get TTL in minutes
        String ttlSeconds = getConfigValue(context, PhoneAuthenticatorFactory.SMS_CODE_TTL);
        int ttlMinutes = 5; // default value
        if (ttlSeconds != null && !ttlSeconds.isEmpty()) {
            try {
                ttlMinutes = Integer.parseInt(ttlSeconds) / 60;
                if (ttlMinutes < 1) ttlMinutes = 1;
            } catch (NumberFormatException e) {
                // use default value
            }
        }

        // Replace placeholders
        return template
                .replace("{code}", verificationCode)
                .replace("{ttl}", String.valueOf(ttlMinutes));
    }

    /**
     * Gets configuration value from Keycloak authenticator config
     */
    private String getConfigValue(AuthenticationFlowContext context, String key) {
        if (context.getAuthenticatorConfig() == null ||
                context.getAuthenticatorConfig().getConfig() == null) {
            return null;
        }

        return context.getAuthenticatorConfig().getConfig().get(key);
    }

    /**
     * Gets boolean configuration value
     */
    private boolean getBooleanConfigValue(AuthenticationFlowContext context, String key, boolean defaultValue) {
        String value = getConfigValue(context, key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Validates Twilio configuration
     */
    private void validateConfiguration(String accountSid, String authToken, String fromNumber)
            throws TwilioSmsException {

        if (accountSid == null || accountSid.trim().isEmpty()) {
            throw new TwilioSmsException("Twilio Account SID is not configured");
        }

        if (authToken == null || authToken.trim().isEmpty()) {
            throw new TwilioSmsException("Twilio Auth Token is not configured");
        }

        if (fromNumber == null || fromNumber.trim().isEmpty()) {
            throw new TwilioSmsException("Twilio From Number is not configured");
        }

        // Validate phone number format
        if (!fromNumber.matches("^\\+[1-9]\\d{1,14}$")) {
            throw new TwilioSmsException("Invalid Twilio From Number format: " + fromNumber);
        }
    }

    /**
     * Masks phone number for safe logging
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }

        return phoneNumber.substring(0, 4) + "*".repeat(phoneNumber.length() - 4);
    }

    /**
     * Tests Twilio configuration by sending a test SMS
     * Used for configuration validation in admin console
     */
    public boolean testConfiguration(String accountSid, String authToken, String fromNumber, String testPhoneNumber) {
        try {
            Twilio.init(accountSid, authToken);

            Message message = Message.creator(
                    new PhoneNumber(testPhoneNumber),
                    new PhoneNumber(fromNumber),
                    "Test message from Keycloak Phone Authenticator"
            ).create();

            logger.infof("Test SMS sent successfully. SID: %s", message.getSid());
            return true;

        } catch (Exception e) {
            logger.errorf("Test SMS failed: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Custom exception for Twilio SMS errors
     */
    public static class TwilioSmsException extends Exception {

        public TwilioSmsException(String message) {
            super(message);
        }

        public TwilioSmsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}