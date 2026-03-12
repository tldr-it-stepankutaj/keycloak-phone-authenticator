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
 * Main authenticator for phone number authentication
 * Implements a two-step process: phone number input and SMS code verification
 */
public class PhoneAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(PhoneAuthenticator.class);

    // Session notes for storing data between steps
    private static final String PHONE_NUMBER = "PHONE_NUMBER";
    private static final String SMS_CODE = "SMS_CODE";
    private static final String SMS_CODE_GENERATED_TIME = "SMS_CODE_GENERATED_TIME";
    private static final String STEP = "STEP";

    // Authentication steps
    private static final String STEP_PHONE_INPUT = "phone_input";
    private static final String STEP_CODE_VERIFICATION = "code_verification";

    // Form parameters
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
            // First step - show phone number input form
            showPhoneInputForm(context);
        } else if (STEP_CODE_VERIFICATION.equals(currentStep)) {
            // Second step - show SMS code verification form
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
     * Displays the phone number input form
     */
    private void showPhoneInputForm(AuthenticationFlowContext context) {
        LoginFormsProvider form = context.form();

        Response response = form.createForm("phone-input.ftl");
        context.challenge(response);
    }

    /**
     * Processes phone number input and sends SMS code
     */
    private void handlePhoneInput(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        String phoneNumber = formData.getFirst(FORM_PHONE_NUMBER);
        String firstName = formData.getFirst(FORM_FIRST_NAME);
        String lastName = formData.getFirst(FORM_LAST_NAME);

        // Phone number validation
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            showPhoneInputFormWithError(context, "Phone number is required");
            return;
        }

        // Phone number normalization
        phoneNumber = normalizePhoneNumber(phoneNumber);

        if (!isValidPhoneNumber(phoneNumber)) {
            showPhoneInputFormWithError(context, "Invalid phone number");
            return;
        }

        // Name validation (only for new users)
        boolean createUserIfNotExists = getBooleanConfig(context, PhoneAuthenticatorFactory.CREATE_USER_IF_NOT_EXISTS, true);
        UserModel existingUser = findUserByPhoneNumber(context, phoneNumber);

        if (existingUser == null && createUserIfNotExists) {
            if (firstName == null || firstName.trim().isEmpty()) {
                showPhoneInputFormWithError(context, "First name is required for new users");
                return;
            }
            if (lastName == null || lastName.trim().isEmpty()) {
                showPhoneInputFormWithError(context, "Last name is required for new users");
                return;
            }
        }

        // Rate limiting check
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

        // SMS code generation
        String smsCode = generateSmsCode(context);

        // Send SMS
        try {
            smsService.sendSms(context, phoneNumber, smsCode);
            logger.infof("SMS code sent to phone number: %s", maskPhoneNumber(phoneNumber));
        } catch (Exception e) {
            logger.error("Failed to send SMS", e);
            showPhoneInputFormWithError(context, "Failed to send SMS. Please try again.");
            return;
        }

        // Store data in session
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

        // Proceed to second step
        showCodeVerificationForm(context);
    }

    /**
     * Displays the SMS code verification form
     */
    private void showCodeVerificationForm(AuthenticationFlowContext context) {
        String phoneNumber = context.getAuthenticationSession().getAuthNote(PHONE_NUMBER);

        LoginFormsProvider form = context.form()
                .setAttribute("phoneNumber", maskPhoneNumber(phoneNumber));

        Response response = form.createForm("phone-code-verification.ftl");
        context.challenge(response);
    }

    /**
     * Processes SMS code verification
     */
    private void handleCodeVerification(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        String enteredCode = formData.getFirst(FORM_SMS_CODE);
        String storedCode = context.getAuthenticationSession().getAuthNote(SMS_CODE);
        String phoneNumber = context.getAuthenticationSession().getAuthNote(PHONE_NUMBER);
        String generatedTimeStr = context.getAuthenticationSession().getAuthNote(SMS_CODE_GENERATED_TIME);

        // Input validation
        if (enteredCode == null || enteredCode.trim().isEmpty()) {
            showCodeVerificationFormWithError(context, "SMS code is required");
            return;
        }

        // Code expiration check
        if (generatedTimeStr != null) {
            long generatedTime = Long.parseLong(generatedTimeStr);
            long ttl = getLongConfig(context, PhoneAuthenticatorFactory.SMS_CODE_TTL, 300L);
            if (Instant.now().getEpochSecond() - generatedTime > ttl) {
                showCodeVerificationFormWithError(context, "SMS code has expired. Please request a new one.");
                return;
            }
        }

        // Code verification
        if (!enteredCode.equals(storedCode)) {
            logger.warnf("Invalid SMS code entered for phone: %s", maskPhoneNumber(phoneNumber));
            showCodeVerificationFormWithError(context, "Invalid SMS code");
            return;
        }

        // Find or create user
        UserModel user = findUserByPhoneNumber(context, phoneNumber);

        if (user == null) {
            boolean createUser = getBooleanConfig(context, PhoneAuthenticatorFactory.CREATE_USER_IF_NOT_EXISTS, true);
            if (createUser) {
                user = createUser(context, phoneNumber);
                if (user == null) {
                    showCodeVerificationFormWithError(context, "Failed to create user account");
                    return;
                }
            } else {
                showCodeVerificationFormWithError(context, "User with this phone number does not exist");
                return;
            }
        }

        // Successful authentication
        context.setUser(user);
        context.success();

        logger.infof("User %s successfully authenticated via phone number", user.getUsername());
    }

    /**
     * Generates SMS code
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
     * Finds a user by phone number
     */
    private UserModel findUserByPhoneNumber(AuthenticationFlowContext context, String phoneNumber) {
        return context.getSession().users()
                .searchForUserByUserAttributeStream(context.getRealm(), "phoneNumber", phoneNumber)
                .findFirst()
                .orElse(null);
    }

    /**
     * Creates a new user
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
     * Normalizes phone number
     */
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;

        // Remove all non-numeric characters except +
        String normalized = phoneNumber.replaceAll("[^+\\d]", "");

        // If it doesn't start with +, add +420 for Czech numbers
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
     * Validates phone number
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return false;

        // Basic validation - starts with + and has correct length
        return phoneNumber.matches("^\\+[1-9]\\d{1,14}$");
    }

    /**
     * Masks phone number for logging
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }

        return phoneNumber.substring(0, 4) + "*".repeat(phoneNumber.length() - 4);
    }

    // Utility methods for configuration
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
     * Gets client IP address from HTTP request
     * Considers X-Forwarded-For header for reverse proxy
     */
    private String getClientIpAddress(AuthenticationFlowContext context) {
        // Try X-Forwarded-For header (for reverse proxy)
        String xForwardedFor = context.getHttpRequest().getHttpHeaders()
                .getHeaderString("X-Forwarded-For");

        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For may contain multiple IP addresses separated by comma
            // The first one is the original client
            String[] ips = xForwardedFor.split(",");
            return ips[0].trim();
        }

        // Try X-Real-IP header
        String xRealIp = context.getHttpRequest().getHttpHeaders()
                .getHeaderString("X-Real-IP");

        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }

        // Fallback to remote address from connection
        try {
            return context.getConnection().getRemoteAddr();
        } catch (Exception e) {
            logger.warn("Could not determine client IP address", e);
            return "unknown";
        }
    }

    // Error handling methods
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
        // No required actions
    }

    @Override
    public void close() {
        // Cleanup
    }
}