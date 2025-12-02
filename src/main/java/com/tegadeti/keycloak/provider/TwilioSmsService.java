package com.tegadeti.keycloak.provider;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;

/**
 * Service pro odesílání SMS zpráv přes Twilio API
 * Podporuje development mode a konfigurovatelné šablony
 */
public class TwilioSmsService {

    private static final Logger logger = Logger.getLogger(TwilioSmsService.class);

    /**
     * Odešle SMS s ověřovacím kódem
     * V development mode pouze loguje kód místo odesílání
     *
     * @param context Authentication flow context pro získání konfigurace
     * @param phoneNumber Telefonní číslo příjemce
     * @param verificationCode Ověřovací kód
     * @throws TwilioSmsException Pokud se nepodaří SMS odeslat
     */
    public void sendSms(AuthenticationFlowContext context, String phoneNumber, String verificationCode)
            throws TwilioSmsException {

        // Kontrola development mode
        boolean developmentMode = getBooleanConfigValue(context, PhoneAuthenticatorFactory.DEVELOPMENT_MODE, false);

        if (developmentMode) {
            sendDevelopmentSms(phoneNumber, verificationCode);
            return;
        }

        // Produkční režim - odesílání přes Twilio
        sendTwilioSms(context, phoneNumber, verificationCode);
    }

    /**
     * Development mode - loguje SMS kód místo odesílání
     */
    private void sendDevelopmentSms(String phoneNumber, String verificationCode) {
        logger.warnf("=== DEVELOPMENT MODE ===");
        logger.warnf("SMS would be sent to: %s", phoneNumber);
        logger.warnf("Verification code: %s", verificationCode);
        logger.warnf("========================");

        // Simulace úspěšného odeslání
        logger.infof("Development SMS 'sent' successfully to: %s", maskPhoneNumber(phoneNumber));
    }

    /**
     * Odesílá SMS přes Twilio API
     */
    private void sendTwilioSms(AuthenticationFlowContext context, String phoneNumber, String verificationCode)
            throws TwilioSmsException {

        // Získání konfigurace z Keycloak
        String accountSid = getConfigValue(context, PhoneAuthenticatorFactory.TWILIO_ACCOUNT_SID);
        String authToken = getConfigValue(context, PhoneAuthenticatorFactory.TWILIO_AUTH_TOKEN);
        String fromNumber = getConfigValue(context, PhoneAuthenticatorFactory.TWILIO_FROM_NUMBER);

        // Validace konfigurace
        validateConfiguration(accountSid, authToken, fromNumber);

        try {
            // Inicializace Twilio klienta
            Twilio.init(accountSid, authToken);

            // Příprava SMS zprávy z šablony
            String messageBody = buildMessageBody(context, verificationCode);

            // Odeslání SMS
            Message message = Message.creator(
                    new PhoneNumber(phoneNumber),
                    new PhoneNumber(fromNumber),
                    messageBody
            ).create();

            logger.infof("SMS sent successfully. SID: %s, Status: %s, To: %s",
                    message.getSid(),
                    message.getStatus(),
                    maskPhoneNumber(phoneNumber));

            // Kontrola statusu zprávy
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
     * Sestaví tělo SMS zprávy z konfigurovatelné šablony
     *
     * @param context Authentication flow context
     * @param verificationCode Ověřovací kód
     * @return Připravená SMS zpráva
     */
    private String buildMessageBody(AuthenticationFlowContext context, String verificationCode) {
        // Získání šablony z konfigurace nebo použití výchozí
        String template = getConfigValue(context, PhoneAuthenticatorFactory.SMS_MESSAGE_TEMPLATE);
        if (template == null || template.trim().isEmpty()) {
            template = PhoneAuthenticatorFactory.DEFAULT_SMS_TEMPLATE;
        }

        // Získání TTL v minutách
        String ttlSeconds = getConfigValue(context, PhoneAuthenticatorFactory.SMS_CODE_TTL);
        int ttlMinutes = 5; // výchozí hodnota
        if (ttlSeconds != null && !ttlSeconds.isEmpty()) {
            try {
                ttlMinutes = Integer.parseInt(ttlSeconds) / 60;
                if (ttlMinutes < 1) ttlMinutes = 1;
            } catch (NumberFormatException e) {
                // použijeme výchozí hodnotu
            }
        }

        // Nahrazení placeholderů
        return template
                .replace("{code}", verificationCode)
                .replace("{ttl}", String.valueOf(ttlMinutes));
    }

    /**
     * Získá konfigurační hodnotu z Keycloak authenticator config
     */
    private String getConfigValue(AuthenticationFlowContext context, String key) {
        if (context.getAuthenticatorConfig() == null ||
                context.getAuthenticatorConfig().getConfig() == null) {
            return null;
        }

        return context.getAuthenticatorConfig().getConfig().get(key);
    }

    /**
     * Získá boolean konfigurační hodnotu
     */
    private boolean getBooleanConfigValue(AuthenticationFlowContext context, String key, boolean defaultValue) {
        String value = getConfigValue(context, key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Validuje Twilio konfiguraci
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

        // Validace formátu telefonního čísla
        if (!fromNumber.matches("^\\+[1-9]\\d{1,14}$")) {
            throw new TwilioSmsException("Invalid Twilio From Number format: " + fromNumber);
        }
    }

    /**
     * Maskuje telefonní číslo pro bezpečné logování
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }

        return phoneNumber.substring(0, 4) + "*".repeat(phoneNumber.length() - 4);
    }

    /**
     * Testuje Twilio konfiguraci odesláním testovací SMS
     * Používá se pro validaci konfigurace v admin konzoli
     */
    public boolean testConfiguration(String accountSid, String authToken, String fromNumber, String testPhoneNumber) {
        try {
            Twilio.init(accountSid, authToken);

            Message message = Message.creator(
                    new PhoneNumber(testPhoneNumber),
                    new PhoneNumber(fromNumber),
                    "Test zpráva z Keycloak Phone Authenticator"
            ).create();

            logger.infof("Test SMS sent successfully. SID: %s", message.getSid());
            return true;

        } catch (Exception e) {
            logger.errorf("Test SMS failed: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Custom exception pro Twilio SMS chyby
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