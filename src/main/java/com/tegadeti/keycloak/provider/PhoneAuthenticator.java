package com.tegadeti.keycloak.provider;

import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Hlavní authenticator pro phone number authentication
 * Implementuje dvojstupňový proces: zadání telefonu a ověření SMS kódu
 */
public class PhoneAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(PhoneAuthenticator.class);

    // Session notes pro ukládání dat mezi kroky
    private static final String PHONE_NUMBER = "PHONE_NUMBER";
    private static final String SMS_CODE = "SMS_CODE";
    private static final String SMS_CODE_GENERATED_TIME = "SMS_CODE_GENERATED_TIME";
    private static final String STEP = "STEP";

    // Kroky autentifikace
    private static final String STEP_PHONE_INPUT = "phone_input";
    private static final String STEP_CODE_VERIFICATION = "code_verification";

    // Form parametry
    private static final String FORM_PHONE_NUMBER = "phoneNumber";
    private static final String FORM_SMS_CODE = "smsCode";
    private static final String FORM_FIRST_NAME = "firstName";
    private static final String FORM_LAST_NAME = "lastName";

    private final TwilioSmsService smsService = new TwilioSmsService();
    private final RateLimitService rateLimitService = RateLimitService.getInstance();
    private final SecureRandom random = new SecureRandom();

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        String currentStep = context.getAuthenticationSession().getAuthNote(STEP);

        if (currentStep == null) {
            // První krok - zobrazit formulář pro zadání telefonu
            showPhoneInputForm(context);
        } else if (STEP_CODE_VERIFICATION.equals(currentStep)) {
            // Druhý krok - zobrazit formulář pro ověření SMS kódu
            showCodeVerificationForm(context);
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String currentStep = context.getAuthenticationSession().getAuthNote(STEP);

        if (currentStep == null || STEP_PHONE_INPUT.equals(currentStep)) {
            handlePhoneInput(context, formData);
        } else if (STEP_CODE_VERIFICATION.equals(currentStep)) {
            handleCodeVerification(context, formData);
        }
    }

    /**
     * Zobrazí formulář pro zadání telefonního čísla
     */
    private void showPhoneInputForm(AuthenticationFlowContext context) {
        LoginFormsProvider form = context.form();

        Response response = form.createForm("phone-input.ftl");
        context.challenge(response);
    }

    /**
     * Zpracuje zadání telefonního čísla a odešle SMS kód
     */
    private void handlePhoneInput(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        String phoneNumber = formData.getFirst(FORM_PHONE_NUMBER);
        String firstName = formData.getFirst(FORM_FIRST_NAME);
        String lastName = formData.getFirst(FORM_LAST_NAME);

        // Validace telefonního čísla
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            showPhoneInputFormWithError(context, "Telefonní číslo je povinné");
            return;
        }

        // Normalizace telefonního čísla
        phoneNumber = normalizePhoneNumber(phoneNumber);

        if (!isValidPhoneNumber(phoneNumber)) {
            showPhoneInputFormWithError(context, "Neplatné telefonní číslo");
            return;
        }

        // Kontrola jména (pouze pro nové uživatele)
        boolean createUserIfNotExists = getBooleanConfig(context, PhoneAuthenticatorFactory.CREATE_USER_IF_NOT_EXISTS, true);
        UserModel existingUser = findUserByPhoneNumber(context, phoneNumber);

        if (existingUser == null && createUserIfNotExists) {
            if (firstName == null || firstName.trim().isEmpty()) {
                showPhoneInputFormWithError(context, "Jméno je povinné pro nové uživatele");
                return;
            }
            if (lastName == null || lastName.trim().isEmpty()) {
                showPhoneInputFormWithError(context, "Příjmení je povinné pro nové uživatele");
                return;
            }
        }

        // Rate limiting kontrola
        String clientIp = getClientIpAddress(context);
        int phoneLimit = getIntConfig(context, PhoneAuthenticatorFactory.RATE_LIMIT_PER_PHONE, 5);
        int ipLimit = getIntConfig(context, PhoneAuthenticatorFactory.RATE_LIMIT_PER_IP, 20);

        RateLimitService.RateLimitResult rateLimitResult = rateLimitService.checkRateLimit(
                phoneNumber, clientIp, phoneLimit, ipLimit);

        if (!rateLimitResult.isAllowed()) {
            logger.warnf("Rate limit exceeded for phone: %s, IP: %s, type: %s",
                    maskPhoneNumber(phoneNumber), clientIp, rateLimitResult.getExceededType());
            showPhoneInputFormWithError(context, rateLimitResult.getErrorMessage());
            return;
        }

        // Generování SMS kódu
        String smsCode = generateSmsCode(context);

        // Odeslání SMS
        try {
            smsService.sendSms(context, phoneNumber, smsCode);
            logger.infof("SMS code sent to phone number: %s", maskPhoneNumber(phoneNumber));
        } catch (Exception e) {
            logger.error("Failed to send SMS", e);
            showPhoneInputFormWithError(context, "Nepodařilo se odeslat SMS. Zkuste to znovu.");
            return;
        }

        // Uložení dat do session
        context.getAuthenticationSession().setAuthNote(PHONE_NUMBER, phoneNumber);
        context.getAuthenticationSession().setAuthNote(SMS_CODE, smsCode);
        context.getAuthenticationSession().setAuthNote(SMS_CODE_GENERATED_TIME, String.valueOf(Instant.now().getEpochSecond()));
        context.getAuthenticationSession().setAuthNote(STEP, STEP_CODE_VERIFICATION);

        if (firstName != null) {
            context.getAuthenticationSession().setAuthNote(FORM_FIRST_NAME, firstName);
        }
        if (lastName != null) {
            context.getAuthenticationSession().setAuthNote(FORM_LAST_NAME, lastName);
        }

        // Přechod na druhý krok
        showCodeVerificationForm(context);
    }

    /**
     * Zobrazí formulář pro ověření SMS kódu
     */
    private void showCodeVerificationForm(AuthenticationFlowContext context) {
        String phoneNumber = context.getAuthenticationSession().getAuthNote(PHONE_NUMBER);

        LoginFormsProvider form = context.form()
                .setAttribute("phoneNumber", maskPhoneNumber(phoneNumber));

        Response response = form.createForm("phone-code-verification.ftl");
        context.challenge(response);
    }

    /**
     * Zpracuje ověření SMS kódu
     */
    private void handleCodeVerification(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        String enteredCode = formData.getFirst(FORM_SMS_CODE);
        String storedCode = context.getAuthenticationSession().getAuthNote(SMS_CODE);
        String phoneNumber = context.getAuthenticationSession().getAuthNote(PHONE_NUMBER);
        String generatedTimeStr = context.getAuthenticationSession().getAuthNote(SMS_CODE_GENERATED_TIME);

        // Validace vstupu
        if (enteredCode == null || enteredCode.trim().isEmpty()) {
            showCodeVerificationFormWithError(context, "SMS kód je povinný");
            return;
        }

        // Kontrola vypršení kódu
        if (generatedTimeStr != null) {
            long generatedTime = Long.parseLong(generatedTimeStr);
            long ttl = getLongConfig(context, PhoneAuthenticatorFactory.SMS_CODE_TTL, 300L);
            if (Instant.now().getEpochSecond() - generatedTime > ttl) {
                showCodeVerificationFormWithError(context, "SMS kód vypršel. Požádejte o nový.");
                return;
            }
        }

        // Ověření kódu
        if (!enteredCode.equals(storedCode)) {
            logger.warnf("Invalid SMS code entered for phone: %s", maskPhoneNumber(phoneNumber));
            showCodeVerificationFormWithError(context, "Neplatný SMS kód");
            return;
        }

        // Najít nebo vytvořit uživatele
        UserModel user = findUserByPhoneNumber(context, phoneNumber);

        if (user == null) {
            boolean createUser = getBooleanConfig(context, PhoneAuthenticatorFactory.CREATE_USER_IF_NOT_EXISTS, true);
            if (createUser) {
                user = createUser(context, phoneNumber);
                if (user == null) {
                    showCodeVerificationFormWithError(context, "Nepodařilo se vytvořit uživatelský účet");
                    return;
                }
            } else {
                showCodeVerificationFormWithError(context, "Uživatel s tímto telefonním číslem neexistuje");
                return;
            }
        }

        // Úspěšná autentifikace
        context.setUser(user);
        context.success();

        logger.infof("User %s successfully authenticated via phone number", user.getUsername());
    }

    /**
     * Vygeneruje SMS kód
     */
    private String generateSmsCode(AuthenticationFlowContext context) {
        int codeLength = getIntConfig(context, PhoneAuthenticatorFactory.SMS_CODE_LENGTH, 6);
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < codeLength; i++) {
            code.append(random.nextInt(10));
        }

        return code.toString();
    }

    /**
     * Najde uživatele podle telefonního čísla
     */
    private UserModel findUserByPhoneNumber(AuthenticationFlowContext context, String phoneNumber) {
        return context.getSession().users()
                .searchForUserByUserAttributeStream(context.getRealm(), "phoneNumber", phoneNumber)
                .findFirst()
                .orElse(null);
    }

    /**
     * Vytvoří nového uživatele
     */
    private UserModel createUser(AuthenticationFlowContext context, String phoneNumber) {
        String firstName = context.getAuthenticationSession().getAuthNote(FORM_FIRST_NAME);
        String lastName = context.getAuthenticationSession().getAuthNote(FORM_LAST_NAME);

        try {
            UserModel user = context.getSession().users().addUser(context.getRealm(), phoneNumber);
            user.setEnabled(true);
            user.setEmailVerified(false);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setSingleAttribute("phoneNumber", phoneNumber);
            user.setSingleAttribute("phoneNumberVerified", "true");

            logger.infof("Created new user with phone number: %s", maskPhoneNumber(phoneNumber));
            return user;
        } catch (Exception e) {
            logger.error("Failed to create user", e);
            return null;
        }
    }

    /**
     * Normalizuje telefonní číslo
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;

        // Odstranění všech ne-číselných znaků kromě +
        String normalized = phoneNumber.replaceAll("[^+\\d]", "");

        // Pokud nezačína +, přidáme +420 pro české číslo
        if (!normalized.startsWith("+")) {
            if (normalized.length() == 9) {
                normalized = "+420" + normalized;
            } else if (normalized.length() == 10 && normalized.startsWith("0")) {
                normalized = "+420" + normalized.substring(1);
            }
        }

        return normalized;
    }

    /**
     * Validuje telefonní číslo
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return false;

        // Základní validace - začíná + a má správnou délku
        return phoneNumber.matches("^\\+[1-9]\\d{1,14}$");
    }

    /**
     * Maskuje telefonní číslo pro logování
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }

        return phoneNumber.substring(0, 4) + "*".repeat(phoneNumber.length() - 4);
    }

    // Utility metody pro konfiguraci
    private String getStringConfig(AuthenticationFlowContext context, String key, String defaultValue) {
        if (context.getAuthenticatorConfig() == null ||
                context.getAuthenticatorConfig().getConfig() == null) {
            return defaultValue;
        }
        String value = context.getAuthenticatorConfig().getConfig().get(key);
        return value != null && !value.trim().isEmpty() ? value : defaultValue;
    }

    private int getIntConfig(AuthenticationFlowContext context, String key, int defaultValue) {
        String value = getStringConfig(context, key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long getLongConfig(AuthenticationFlowContext context, String key, long defaultValue) {
        String value = getStringConfig(context, key, String.valueOf(defaultValue));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBooleanConfig(AuthenticationFlowContext context, String key, boolean defaultValue) {
        String value = getStringConfig(context, key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    /**
     * Získá IP adresu klienta z HTTP požadavku
     * Zohledňuje X-Forwarded-For header pro reverse proxy
     */
    private String getClientIpAddress(AuthenticationFlowContext context) {
        // Zkusíme X-Forwarded-For header (pro reverse proxy)
        String xForwardedFor = context.getHttpRequest().getHttpHeaders()
                .getHeaderString("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For může obsahovat více IP adres oddělených čárkou
            // První je originální klient
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim();
        }

        // Zkusíme X-Real-IP header
        String xRealIp = context.getHttpRequest().getHttpHeaders()
                .getHeaderString("X-Real-IP");

        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }

        // Fallback na remote address z connection
        try {
            return context.getConnection().getRemoteAddr();
        } catch (Exception e) {
            logger.warn("Could not determine client IP address", e);
            return "unknown";
        }
    }

    // Error handling metody
    private void showPhoneInputFormWithError(AuthenticationFlowContext context, String errorMessage) {
        LoginFormsProvider form = context.form()
                .setError(errorMessage);

        Response response = form.createForm("phone-input.ftl");
        context.challenge(response);
    }

    private void showCodeVerificationFormWithError(AuthenticationFlowContext context, String errorMessage) {
        String phoneNumber = context.getAuthenticationSession().getAuthNote(PHONE_NUMBER);

        LoginFormsProvider form = context.form()
                .setError(errorMessage)
                .setAttribute("phoneNumber", maskPhoneNumber(phoneNumber));

        Response response = form.createForm("phone-code-verification.ftl");
        context.challenge(response);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // Žádné required actions
    }

    @Override
    public void close() {
        // Cleanup
    }
}