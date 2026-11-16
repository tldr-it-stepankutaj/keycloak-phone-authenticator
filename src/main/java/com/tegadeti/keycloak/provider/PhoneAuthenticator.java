package com.tegadeti.keycloak.provider;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.events.EventType;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.managers.BruteForceProtector;
import org.keycloak.services.messages.Messages;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Phone number authenticator.
 * <p>
 * Flow:
 * <ol>
 *   <li><b>phone_input</b> - user enters the phone number, an SMS code is sent. Nothing about the
 *       existence of the account is revealed here.</li>
 *   <li><b>code_verification</b> - user enters the code (limited number of attempts). The user can request a
 *       new code (with a cooldown) or go back and change the number.</li>
 *   <li><b>name_input</b> - only for a number that has no account yet (and creation is enabled), after the
 *       number has been proven by the code. Asks for first/last name and creates the account.</li>
 * </ol>
 * <b>Second factor mode:</b> when the flow already identified the user (this authenticator placed after a
 * username/password step), no number is asked for. The code goes to the {@code phoneNumber} attribute of that
 * user and only steps 2 applies; accounts are never created.
 */
public class PhoneAuthenticator implements Authenticator {

    private static final Logger logger = Logger.getLogger(PhoneAuthenticator.class);

    // Auth session notes
    private static final String PHONE_NUMBER = "PHONE_NUMBER";
    private static final String SMS_CODE = "SMS_CODE";
    private static final String SMS_CODE_GENERATED_TIME = "SMS_CODE_GENERATED_TIME";
    private static final String CODE_ATTEMPTS = "SMS_CODE_ATTEMPTS";
    private static final String STEP = "STEP";

    // Steps
    private static final String STEP_CODE_VERIFICATION = "code_verification";
    private static final String STEP_NAME_INPUT = "name_input";

    // Form parameters
    private static final String FORM_PHONE_NUMBER = "phoneNumber";
    private static final String FORM_SMS_CODE = "smsCode";
    private static final String FORM_FIRST_NAME = "firstName";
    private static final String FORM_LAST_NAME = "lastName";
    private static final String FORM_RESEND = "resend";
    private static final String FORM_CHANGE_NUMBER = "changeNumber";

    // Templates
    private static final String TEMPLATE_PHONE_INPUT = "phone-input.ftl";
    private static final String TEMPLATE_CODE_VERIFICATION = "phone-code-verification.ftl";
    private static final String TEMPLATE_USER_DETAILS = "phone-user-details.ftl";

    // User attributes
    private static final String ATTR_PHONE_NUMBER = "phoneNumber";
    private static final String ATTR_PHONE_VERIFIED = "phoneNumberVerified";

    // Defaults of the configurable limits (see PhoneAuthenticatorFactory)
    static final int MAX_CODE_ATTEMPTS = PhoneAuthenticatorFactory.DEFAULT_MAX_CODE_ATTEMPTS;
    static final long RESEND_COOLDOWN_SECONDS = PhoneAuthenticatorFactory.DEFAULT_RESEND_COOLDOWN_SECONDS;
    private static final int MAX_NAME_LENGTH = 255;

    private final SmsService smsService = new SmsService();
    private final RateLimitService rateLimitService = RateLimitService.getInstance();
    private final SecureRandom random = new SecureRandom();

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        String currentStep = context.getAuthenticationSession().getAuthNote(STEP);

        if (STEP_CODE_VERIFICATION.equals(currentStep)) {
            showCodeVerificationForm(context, null);
        } else if (STEP_NAME_INPUT.equals(currentStep)) {
            showUserDetailsForm(context, null, null, null);
        } else if (isSecondFactor(context)) {
            startSecondFactor(context);
        } else {
            showPhoneInputForm(context, null, null);
        }
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String currentStep = context.getAuthenticationSession().getAuthNote(STEP);

        if (STEP_CODE_VERIFICATION.equals(currentStep)) {
            if (formData.containsKey(FORM_CHANGE_NUMBER)) {
                if (isSecondFactor(context)) {
                    // the number belongs to the already identified user and cannot be changed
                    showCodeVerificationForm(context, null);
                    return;
                }
                clearFlowNotes(context.getAuthenticationSession());
                showPhoneInputForm(context, null, null);
            } else if (formData.containsKey(FORM_RESEND)) {
                handleResend(context);
            } else {
                handleCodeVerification(context, formData);
            }
        } else if (STEP_NAME_INPUT.equals(currentStep)) {
            handleUserDetails(context, formData);
        } else if (isSecondFactor(context)) {
            startSecondFactor(context);
        } else {
            handlePhoneInput(context, formData);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Second factor mode
    // ------------------------------------------------------------------------------------------

    /** True when an earlier step of the flow already identified the user. */
    private boolean isSecondFactor(AuthenticationFlowContext context) {
        return context.getUser() != null;
    }

    private void startSecondFactor(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (rejectBlockedUser(context, user)) {
            return;
        }

        String phoneNumber = PhoneNumberUtils.normalize(user.getFirstAttribute(ATTR_PHONE_NUMBER), getDefaultCallingCode(context));
        if (!PhoneNumberUtils.isValid(phoneNumber, isStrictValidation(context))) {
            logger.warnf("User %s has no usable phoneNumber attribute, cannot send second factor code", user.getUsername());
            failFlow(context, AuthenticationFlowError.CREDENTIAL_SETUP_REQUIRED, new FormMessage(null, "secondFactorNoPhone"));
            return;
        }

        FormMessage error = sendCode(context, phoneNumber);
        if (error != null) {
            failFlow(context, AuthenticationFlowError.INTERNAL_ERROR, error);
            return;
        }
        showCodeVerificationForm(context, null);
    }

    private void failFlow(AuthenticationFlowContext context, AuthenticationFlowError flowError, FormMessage message) {
        var form = context.form();
        form.setError(message.getMessage(), message.getParameters());
        context.failureChallenge(flowError, form.createErrorPage(Response.Status.BAD_REQUEST));
    }

    // ------------------------------------------------------------------------------------------
    // Step 1: phone number
    // ------------------------------------------------------------------------------------------

    private void handlePhoneInput(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        String rawInput = formData.getFirst(FORM_PHONE_NUMBER);

        if (rawInput == null || rawInput.trim().isEmpty()) {
            showPhoneInputForm(context, rawInput, new FormMessage(FORM_PHONE_NUMBER, "phoneNumberRequired"));
            return;
        }

        String phoneNumber = PhoneNumberUtils.normalize(rawInput, getDefaultCallingCode(context));

        if (!PhoneNumberUtils.isValid(phoneNumber, isStrictValidation(context))) {
            showPhoneInputForm(context, rawInput, new FormMessage(FORM_PHONE_NUMBER, "invalidPhoneNumber"));
            return;
        }

        FormMessage error = sendCode(context, phoneNumber);
        if (error != null) {
            showPhoneInputForm(context, rawInput, withField(error, FORM_PHONE_NUMBER));
            return;
        }

        showCodeVerificationForm(context, null);
    }

    // ------------------------------------------------------------------------------------------
    // Step 2: code verification
    // ------------------------------------------------------------------------------------------

    private void handleResend(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String phoneNumber = authSession.getAuthNote(PHONE_NUMBER);
        if (phoneNumber == null) {
            clearFlowNotes(authSession);
            showPhoneInputForm(context, null, null);
            return;
        }

        long waitSeconds = getResendCooldown(context) - secondsSinceCodeGenerated(authSession);
        if (waitSeconds > 0) {
            showCodeVerificationForm(context, new FormMessage(FORM_SMS_CODE, "resendTooSoon", waitSeconds));
            return;
        }

        FormMessage error = sendCode(context, phoneNumber);
        if (error != null) {
            showCodeVerificationForm(context, withField(error, FORM_SMS_CODE));
            return;
        }
        showCodeVerificationForm(context, null);
    }

    private void handleCodeVerification(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String enteredCode = formData.getFirst(FORM_SMS_CODE);
        String storedCode = authSession.getAuthNote(SMS_CODE);
        String phoneNumber = authSession.getAuthNote(PHONE_NUMBER);

        if (storedCode == null || phoneNumber == null) {
            clearFlowNotes(authSession);
            showPhoneInputForm(context, null, null);
            return;
        }

        if (enteredCode == null || enteredCode.trim().isEmpty()) {
            showCodeVerificationForm(context, new FormMessage(FORM_SMS_CODE, "smsCodeRequired"));
            return;
        }
        enteredCode = enteredCode.trim();

        // Existing account: refuse disabled / brute-force locked users before even looking at the code
        boolean secondFactor = isSecondFactor(context);
        UserLookup lookup = secondFactor ? new UserLookup(context.getUser(), false) : findUser(context, phoneNumber);
        if (lookup.conflict) {
            clearFlowNotes(authSession);
            showPhoneInputForm(context, null, new FormMessage(FORM_PHONE_NUMBER, "phoneNumberInUse"));
            return;
        }
        if (lookup.user != null && rejectBlockedUser(context, lookup.user)) {
            return;
        }

        // Expiration (a missing timestamp is treated as expired - never fail open)
        long ttl = getLongConfig(context, PhoneAuthenticatorFactory.SMS_CODE_TTL, 300L);
        if (ttl <= 0) {
            ttl = 300L;
        }
        if (secondsSinceCodeGenerated(authSession) > ttl) {
            showCodeVerificationForm(context, new FormMessage(FORM_SMS_CODE, "smsCodeExpired"));
            return;
        }

        // Verification (constant time)
        if (!codeMatches(enteredCode, storedCode)) {
            int attempts = parseIntOrZero(authSession.getAuthNote(CODE_ATTEMPTS)) + 1;
            int maxAttempts = getMaxCodeAttempts(context);
            logger.warnf("Invalid SMS code entered for phone: %s (attempt %d/%d)",
                    PhoneNumberUtils.mask(phoneNumber), attempts, maxAttempts);

            if (lookup.user != null) {
                // Feeds Keycloak's brute force detection
                context.getEvent().user(lookup.user).error(Errors.INVALID_USER_CREDENTIALS);
            }

            if (attempts >= maxAttempts) {
                audit(context, EventType.LOGIN, "too_many_code_attempts", lookup.user, phoneNumber);
                clearFlowNotes(authSession);
                if (secondFactor) {
                    failFlow(context, AuthenticationFlowError.INVALID_CREDENTIALS, new FormMessage(null, "tooManyCodeAttempts"));
                } else {
                    showPhoneInputForm(context, null, new FormMessage(FORM_PHONE_NUMBER, "tooManyCodeAttempts"));
                }
                return;
            }
            authSession.setAuthNote(CODE_ATTEMPTS, String.valueOf(attempts));
            showCodeVerificationForm(context, new FormMessage(FORM_SMS_CODE, "invalidSmsCode"));
            return;
        }

        // Code is correct - it is single use
        authSession.removeAuthNote(SMS_CODE);
        authSession.removeAuthNote(SMS_CODE_GENERATED_TIME);
        authSession.removeAuthNote(CODE_ATTEMPTS);

        if (lookup.user != null) {
            completeLogin(context, lookup.user);
            return;
        }

        boolean createUser = getBooleanConfig(context, PhoneAuthenticatorFactory.CREATE_USER_IF_NOT_EXISTS, true);
        if (!createUser) {
            clearFlowNotes(authSession);
            showPhoneInputForm(context, null, new FormMessage(FORM_PHONE_NUMBER, "userNotFound"));
            return;
        }

        // Number is proven, now collect the name for the new account
        authSession.setAuthNote(STEP, STEP_NAME_INPUT);
        showUserDetailsForm(context, null, null, null);
    }

    // ------------------------------------------------------------------------------------------
    // Step 3: details of a new user
    // ------------------------------------------------------------------------------------------

    private void handleUserDetails(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String phoneNumber = authSession.getAuthNote(PHONE_NUMBER);
        if (phoneNumber == null) {
            clearFlowNotes(authSession);
            showPhoneInputForm(context, null, null);
            return;
        }

        String firstName = trimToNull(formData.getFirst(FORM_FIRST_NAME));
        String lastName = trimToNull(formData.getFirst(FORM_LAST_NAME));

        if (firstName == null || firstName.length() > MAX_NAME_LENGTH) {
            showUserDetailsForm(context, firstName, lastName, new FormMessage(FORM_FIRST_NAME, "firstNameRequired"));
            return;
        }
        if (lastName == null || lastName.length() > MAX_NAME_LENGTH) {
            showUserDetailsForm(context, firstName, lastName, new FormMessage(FORM_LAST_NAME, "lastNameRequired"));
            return;
        }

        UserModel user = createUser(context, phoneNumber, firstName, lastName);
        if (user == null) {
            showUserDetailsForm(context, firstName, lastName, new FormMessage(null, "failedToCreateUser"));
            return;
        }
        if (rejectBlockedUser(context, user)) {
            return;
        }
        completeLogin(context, user);
    }

    // ------------------------------------------------------------------------------------------
    // Shared logic
    // ------------------------------------------------------------------------------------------

    /**
     * Rate-limits, generates and sends a code and stores it in the auth session.
     *
     * @return {@code null} on success, otherwise the error to show
     */
    private FormMessage sendCode(AuthenticationFlowContext context, String phoneNumber) {
        String clientIp = getClientIpAddress(context);
        int phoneLimit = getIntConfig(context, PhoneAuthenticatorFactory.RATE_LIMIT_PER_PHONE, 5);
        int ipLimit = getIntConfig(context, PhoneAuthenticatorFactory.RATE_LIMIT_PER_IP, 20);

        RateLimitService.RateLimitResult rateLimitResult = rateLimitService.checkRateLimit(
                new RateLimitService.SingleUseObjectStore(context.getSession()),
                phoneNumber, clientIp, phoneLimit, ipLimit);

        if (!rateLimitResult.isAllowed()) {
            logger.warnf("Rate limit exceeded for phone: %s, IP: %s, type: %s",
                    PhoneNumberUtils.mask(phoneNumber), clientIp, rateLimitResult.getExceededType());
            audit(context, EventType.LOGIN, "sms_rate_limited", context.getUser(), phoneNumber);
            return new FormMessage(null, rateLimitResult.getMessageKey(), rateLimitResult.getRetryAfterMinutes());
        }

        String smsCode = generateSmsCode(context);

        try {
            smsService.sendSms(context, phoneNumber, smsCode);
            logger.infof("SMS code sent to phone number: %s", PhoneNumberUtils.mask(phoneNumber));
        } catch (Exception e) {
            logger.error("Failed to send SMS", e);
            audit(context, EventType.LOGIN, "sms_send_failed", context.getUser(), phoneNumber);
            return new FormMessage(null, "failedToSendSms");
        }

        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        authSession.setAuthNote(PHONE_NUMBER, phoneNumber);
        authSession.setAuthNote(SMS_CODE, protectCode(smsCode));
        authSession.setAuthNote(SMS_CODE_GENERATED_TIME, String.valueOf(Instant.now().getEpochSecond()));
        authSession.setAuthNote(CODE_ATTEMPTS, "0");
        authSession.setAuthNote(STEP, STEP_CODE_VERIFICATION);
        return null;
    }

    /**
     * Fails the flow if the account is disabled or temporarily/permanently locked by brute force protection.
     *
     * @return true if the flow was terminated
     */
    private boolean rejectBlockedUser(AuthenticationFlowContext context, UserModel user) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        if (!user.isEnabled()) {
            context.getEvent().user(user).error(Errors.USER_DISABLED);
            context.failure(AuthenticationFlowError.USER_DISABLED,
                    context.form().setError(Messages.ACCOUNT_DISABLED).createErrorPage(Response.Status.BAD_REQUEST));
            return true;
        }

        BruteForceProtector protector = context.getProtector();
        if (protector != null) {
            if (protector.isPermanentlyLockedOut(session, realm, user)) {
                context.getEvent().user(user).error(Errors.USER_DISABLED);
                context.failure(AuthenticationFlowError.USER_DISABLED,
                        context.form().setError(Messages.ACCOUNT_DISABLED).createErrorPage(Response.Status.BAD_REQUEST));
                return true;
            }
            if (protector.isTemporarilyDisabled(session, realm, user)) {
                context.getEvent().user(user).error(Errors.USER_TEMPORARILY_DISABLED);
                context.failure(AuthenticationFlowError.USER_TEMPORARILY_DISABLED,
                        context.form().setError(Messages.ACCOUNT_TEMPORARILY_DISABLED)
                                .createErrorPage(Response.Status.BAD_REQUEST));
                return true;
            }
        }
        return false;
    }

    private void completeLogin(AuthenticationFlowContext context, UserModel user) {
        clearFlowNotes(context.getAuthenticationSession());
        context.setUser(user);
        context.success();
        logger.infof("User %s successfully authenticated via phone number", user.getUsername());
    }

    private void clearFlowNotes(AuthenticationSessionModel authSession) {
        authSession.removeAuthNote(PHONE_NUMBER);
        authSession.removeAuthNote(SMS_CODE);
        authSession.removeAuthNote(SMS_CODE_GENERATED_TIME);
        authSession.removeAuthNote(CODE_ATTEMPTS);
        authSession.removeAuthNote(STEP);
    }

    private String generateSmsCode(AuthenticationFlowContext context) {
        int codeLength = getIntConfig(context, PhoneAuthenticatorFactory.SMS_CODE_LENGTH, 6);
        if (codeLength < 4 || codeLength > 10) {
            codeLength = 6;
        }
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < codeLength; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /** Result of a user lookup by phone number. */
    private static final class UserLookup {
        final UserModel user;
        final boolean conflict;

        UserLookup(UserModel user, boolean conflict) {
            this.user = user;
            this.conflict = conflict;
        }
    }

    /**
     * Finds the user owning the phone number. More than one match is a conflict (never pick one at random).
     * Falls back to the username, because accounts created by this authenticator use the number as username.
     */
    private UserLookup findUser(AuthenticationFlowContext context, String phoneNumber) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();

        List<UserModel> matches = session.users()
                .searchForUserByUserAttributeStream(realm, ATTR_PHONE_NUMBER, phoneNumber)
                .limit(2)
                .toList();

        if (matches.size() > 1) {
            logger.errorf("Multiple users share phone number %s - refusing to authenticate",
                    PhoneNumberUtils.mask(phoneNumber));
            return new UserLookup(null, true);
        }
        if (matches.size() == 1) {
            return new UserLookup(matches.get(0), false);
        }

        UserModel byUsername = session.users().getUserByUsername(realm, phoneNumber);
        if (byUsername != null) {
            String attr = byUsername.getFirstAttribute(ATTR_PHONE_NUMBER);
            if (attr == null || attr.equals(phoneNumber)) {
                return new UserLookup(byUsername, false);
            }
            // username is this number but the account belongs to a different number
            return new UserLookup(null, true);
        }
        return new UserLookup(null, false);
    }

    /**
     * Creates a user. Two concurrent registrations of the same number end up with the same account instead
     * of a duplicate (the username is unique, the loser re-reads the winner).
     */
    private UserModel createUser(AuthenticationFlowContext context, String phoneNumber,
                                 String firstName, String lastName) {
        UserLookup existing = findUser(context, phoneNumber);
        if (existing.conflict) {
            return null;
        }
        if (existing.user != null) {
            return existing.user;
        }

        try {
            UserModel user = context.getSession().users().addUser(context.getRealm(), phoneNumber);
            user.setEnabled(true);
            user.setEmailVerified(false);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setSingleAttribute(ATTR_PHONE_NUMBER, phoneNumber);
            user.setSingleAttribute(ATTR_PHONE_VERIFIED, "true");

            logger.infof("Created new user with phone number: %s", PhoneNumberUtils.mask(phoneNumber));
            audit(context, EventType.REGISTER, null, user, phoneNumber);
            return user;
        } catch (Exception e) {
            logger.warnf("Failed to create user (%s), checking whether it was created concurrently", e.getMessage());
            logger.debug("Create user failure details", e);
            UserLookup retry = findUser(context, phoneNumber);
            return retry.conflict ? null : retry.user;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Forms
    // ------------------------------------------------------------------------------------------

    private void showPhoneInputForm(AuthenticationFlowContext context, String enteredPhone, FormMessage error) {
        var form = context.form();
        if (enteredPhone != null) {
            form.setAttribute(FORM_PHONE_NUMBER, enteredPhone);
        }
        applyError(form, error);
        context.challenge(form.createForm(TEMPLATE_PHONE_INPUT));
    }

    private void showCodeVerificationForm(AuthenticationFlowContext context, FormMessage error) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String phoneNumber = authSession.getAuthNote(PHONE_NUMBER);

        long ttl = getLongConfig(context, PhoneAuthenticatorFactory.SMS_CODE_TTL, 300L);
        if (ttl <= 0) {
            ttl = 300L;
        }
        long secondsLeft = Math.max(0, ttl - secondsSinceCodeGenerated(authSession));
        long resendIn = Math.max(0, getResendCooldown(context) - secondsSinceCodeGenerated(authSession));

        var form = context.form()
                .setAttribute(FORM_PHONE_NUMBER, PhoneNumberUtils.mask(phoneNumber))
                .setAttribute("codeSecondsLeft", secondsLeft)
                .setAttribute("resendCooldownSeconds", resendIn)
                .setAttribute("secondFactor", isSecondFactor(context));
        applyError(form, error);
        context.challenge(form.createForm(TEMPLATE_CODE_VERIFICATION));
    }

    private void showUserDetailsForm(AuthenticationFlowContext context, String firstName, String lastName,
                                     FormMessage error) {
        var form = context.form();
        if (firstName != null) {
            form.setAttribute(FORM_FIRST_NAME, firstName);
        }
        if (lastName != null) {
            form.setAttribute(FORM_LAST_NAME, lastName);
        }
        applyError(form, error);
        context.challenge(form.createForm(TEMPLATE_USER_DETAILS));
    }

    /** Field errors are rendered next to the input, global ones in the page alert. */
    private void applyError(org.keycloak.forms.login.LoginFormsProvider form, FormMessage error) {
        if (error == null) {
            return;
        }
        if (error.getField() == null) {
            form.setError(error.getMessage(), error.getParameters());
        } else {
            form.addError(error);
        }
    }

    private static FormMessage withField(FormMessage error, String field) {
        if (error.getField() != null) {
            return error;
        }
        return new FormMessage(field, error.getMessage(), error.getParameters());
    }

    // ------------------------------------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------------------------------------

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * What is stored in the auth session instead of the plain code: {@code salt:SHA-256(salt + code)}.
     * The auth session is server side, this only keeps live codes out of cache dumps and logs.
     */
    static String protectCode(String code) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String saltHex = HexFormat.of().formatHex(salt);
        return saltHex + ":" + sha256Hex(saltHex + code);
    }

    /** Constant-time check of an entered code against a value produced by {@link #protectCode}. */
    static boolean codeMatches(String enteredCode, String stored) {
        int separator = stored.indexOf(':');
        if (separator < 0) {
            return false;
        }
        String saltHex = stored.substring(0, separator);
        return constantTimeEquals(sha256Hex(saltHex + enteredCode), stored.substring(separator + 1));
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Records an audit event (visible in the admin console when realm events are enabled). Never lets an
     * auditing problem break the login.
     *
     * @param error {@code null} for a success event, otherwise the error code
     */
    private void audit(AuthenticationFlowContext context, EventType type, String error, UserModel user,
                       String phoneNumber) {
        try {
            EventBuilder event = context.newEvent().event(type)
                    .detail("auth_method", "phone")
                    .detail("phone_number", PhoneNumberUtils.mask(phoneNumber))
                    .detail("mode", isSecondFactor(context) ? "second_factor" : "first_factor");
            if (user != null) {
                event.user(user);
            }
            if (error == null) {
                event.success();
            } else {
                event.error(error);
            }
        } catch (Exception e) {
            logger.debug("Could not record audit event", e);
        }
    }

    /** Seconds since the current code was generated; a huge value if there is none. */
    private static long secondsSinceCodeGenerated(AuthenticationSessionModel authSession) {
        String generated = authSession.getAuthNote(SMS_CODE_GENERATED_TIME);
        if (generated == null) {
            return Long.MAX_VALUE / 2;
        }
        try {
            return Instant.now().getEpochSecond() - Long.parseLong(generated);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE / 2;
        }
    }

    private static int parseIntOrZero(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

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
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long getLongConfig(AuthenticationFlowContext context, String key, long defaultValue) {
        String value = getStringConfig(context, key, String.valueOf(defaultValue));
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String getDefaultCallingCode(AuthenticationFlowContext context) {
        return getStringConfig(context, PhoneAuthenticatorFactory.DEFAULT_CALLING_CODE, PhoneNumberUtils.DEFAULT_CALLING_CODE);
    }

    private boolean isStrictValidation(AuthenticationFlowContext context) {
        return getBooleanConfig(context, PhoneAuthenticatorFactory.STRICT_PHONE_VALIDATION, false);
    }

    private int getMaxCodeAttempts(AuthenticationFlowContext context) {
        int value = getIntConfig(context, PhoneAuthenticatorFactory.MAX_CODE_ATTEMPTS, MAX_CODE_ATTEMPTS);
        return value > 0 ? value : MAX_CODE_ATTEMPTS;
    }

    private long getResendCooldown(AuthenticationFlowContext context) {
        long value = getLongConfig(context, PhoneAuthenticatorFactory.RESEND_COOLDOWN, RESEND_COOLDOWN_SECONDS);
        return value >= 0 ? value : RESEND_COOLDOWN_SECONDS;
    }

    private boolean getBooleanConfig(AuthenticationFlowContext context, String key, boolean defaultValue) {
        return Boolean.parseBoolean(getStringConfig(context, key, String.valueOf(defaultValue)));
    }

    /**
     * Client IP as seen by Keycloak. Forwarding headers (X-Forwarded-For, X-Real-IP) are intentionally NOT
     * read here: any client can set them, which would make the per-IP limit trivial to bypass. Behind a
     * reverse proxy configure Keycloak's {@code --proxy-headers} so that
     * {@link org.keycloak.common.ClientConnection#getRemoteAddr()} already returns the real client address.
     */
    private String getClientIpAddress(AuthenticationFlowContext context) {
        try {
            String addr = context.getConnection().getRemoteAddr();
            return addr == null || addr.isEmpty() ? null : addr;
        } catch (Exception e) {
            logger.warn("Could not determine client IP address", e);
            return null;
        }
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        // First factor (no user yet): always available. Second factor: the user needs a phone number.
        if (user == null) {
            return true;
        }
        return PhoneNumberUtils.isValid(
                PhoneNumberUtils.normalize(user.getFirstAttribute(ATTR_PHONE_NUMBER), PhoneNumberUtils.DEFAULT_CALLING_CODE));
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
