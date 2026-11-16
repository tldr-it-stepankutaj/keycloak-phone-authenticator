package com.tegadeti.keycloak.provider;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.vault.VaultStringSecret;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the verification SMS from the configured template and delivers it through the selected
 * {@link SmsProvider}. In development mode the code is only logged.
 */
public class SmsService {

    private static final Logger logger = Logger.getLogger(SmsService.class);

    /** Config values that may hold a vault expression such as {@code ${vault.twilio_token}}. */
    private static final Set<String> SECRET_KEYS = Set.of(
            PhoneAuthenticatorFactory.TWILIO_AUTH_TOKEN,
            PhoneAuthenticatorFactory.HTTP_SMS_AUTH_TOKEN);

    private final List<SmsProvider> providers;

    public SmsService() {
        this(List.of(new TwilioSmsProvider(), new HttpSmsProvider()));
    }

    SmsService(List<SmsProvider> providers) {
        this.providers = providers;
    }

    public void sendSms(AuthenticationFlowContext context, String phoneNumber, String verificationCode)
            throws SmsException {
        Map<String, String> config = context.getAuthenticatorConfig() != null
                && context.getAuthenticatorConfig().getConfig() != null
                ? context.getAuthenticatorConfig().getConfig() : Map.of();
        sendSms(resolveSecrets(context, config), phoneNumber, verificationCode);
    }

    /**
     * Returns a copy of the config where secret values are resolved through Keycloak's vault, so the token
     * can be configured as {@code ${vault.key}} instead of being stored in the database. Values that are not
     * vault expressions are returned unchanged.
     */
    private Map<String, String> resolveSecrets(AuthenticationFlowContext context, Map<String, String> config) {
        Map<String, String> resolved = new HashMap<>(config);
        for (String key : SECRET_KEYS) {
            String value = config.get(key);
            if (value == null || !value.contains("${vault.")) {
                continue;
            }
            try (VaultStringSecret secret = context.getSession().vault().getStringSecret(value)) {
                resolved.put(key, secret.get().orElse(null));
            } catch (Exception e) {
                logger.errorf("Could not resolve vault secret for %s: %s", key, e.getMessage());
                resolved.put(key, null);
            }
        }
        return resolved;
    }

    public void sendSms(Map<String, String> config, String phoneNumber, String verificationCode)
            throws SmsException {

        if (Boolean.parseBoolean(config.get(PhoneAuthenticatorFactory.DEVELOPMENT_MODE))) {
            sendDevelopmentSms(phoneNumber, verificationCode);
            return;
        }

        String providerId = config.get(PhoneAuthenticatorFactory.SMS_PROVIDER);
        if (providerId == null || providerId.trim().isEmpty()) {
            providerId = TwilioSmsProvider.ID; // default / backwards compatible
        }
        final String wanted = providerId.trim();

        SmsProvider provider = providers.stream()
                .filter(p -> p.getId().equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> new SmsException("Unknown SMS provider: " + wanted));

        provider.send(config, phoneNumber, buildMessageBody(config, verificationCode));
    }

    private void sendDevelopmentSms(String phoneNumber, String verificationCode) {
        logger.warnf("=== DEVELOPMENT MODE: SMS is NOT sent ===");
        logger.warnf("SMS would be sent to: %s", PhoneNumberUtils.mask(phoneNumber));
        logger.warnf("Verification code: %s", verificationCode);
        logger.warnf("=========================================");
    }

    /** Replaces {@code {code}} and {@code {ttl}} (minutes) in the configured template. */
    static String buildMessageBody(Map<String, String> config, String verificationCode) {
        String template = config.get(PhoneAuthenticatorFactory.SMS_MESSAGE_TEMPLATE);
        if (template == null || template.trim().isEmpty()) {
            template = PhoneAuthenticatorFactory.DEFAULT_SMS_TEMPLATE;
        }

        int ttlMinutes = 5;
        String ttlSeconds = config.get(PhoneAuthenticatorFactory.SMS_CODE_TTL);
        if (ttlSeconds != null && !ttlSeconds.trim().isEmpty()) {
            try {
                ttlMinutes = Math.max(1, Integer.parseInt(ttlSeconds.trim()) / 60);
            } catch (NumberFormatException e) {
                // keep default
            }
        }

        return template
                .replace("{code}", verificationCode)
                .replace("{ttl}", String.valueOf(ttlMinutes));
    }
}
