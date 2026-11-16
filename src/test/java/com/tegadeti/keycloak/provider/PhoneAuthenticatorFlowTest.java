package com.tegadeti.keycloak.provider;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.common.ClientConnection;
import org.keycloak.events.EventBuilder;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.services.managers.BruteForceProtector;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Flow tests of {@link PhoneAuthenticator} against mocked Keycloak SPI (no Keycloak runtime needed).
 * SMS sending runs in development mode, so nothing leaves the JVM.
 */
class PhoneAuthenticatorFlowTest {

    private static final String PHONE = "+420123456789";

    private PhoneAuthenticator authenticator;

    private AuthenticationFlowContext context;
    private AuthenticationSessionModel authSession;
    private LoginFormsProvider form;
    private UserProvider users;
    private BruteForceProtector protector;
    private EventBuilder event;
    private final Map<String, String> notes = new HashMap<>();
    private final Map<String, Map<String, String>> singleUse = new HashMap<>();
    private final MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
    private final Map<String, String> config = new HashMap<>();
    private final Response response = mock(Response.class);

    @BeforeEach
    void setUp() {
        authenticator = new PhoneAuthenticator();
        notes.clear();
        singleUse.clear();
        formData.clear();
        config.clear();
        config.put(PhoneAuthenticatorFactory.DEVELOPMENT_MODE, "true");
        config.put(PhoneAuthenticatorFactory.RATE_LIMIT_PER_PHONE, "0");
        config.put(PhoneAuthenticatorFactory.RATE_LIMIT_PER_IP, "0");

        context = mock(AuthenticationFlowContext.class);
        authSession = mock(AuthenticationSessionModel.class);
        form = mock(LoginFormsProvider.class, invocation ->
                LoginFormsProvider.class.isAssignableFrom(invocation.getMethod().getReturnType())
                        ? invocation.getMock() : (invocation.getMethod().getReturnType() == Response.class ? response : null));
        users = mock(UserProvider.class);
        protector = mock(BruteForceProtector.class);
        event = mock(EventBuilder.class, RETURNS_SELF);

        when(authSession.getAuthNote(anyString())).thenAnswer(i -> notes.get(i.<String>getArgument(0)));
        doAnswer(i -> notes.put(i.getArgument(0), i.getArgument(1))).when(authSession).setAuthNote(anyString(), anyString());
        doAnswer(i -> notes.remove(i.<String>getArgument(0))).when(authSession).removeAuthNote(anyString());

        KeycloakSession session = mock(KeycloakSession.class);
        when(session.users()).thenReturn(users);
        SingleUseObjectProvider singleUseProvider = mock(SingleUseObjectProvider.class);
        when(singleUseProvider.get(anyString())).thenAnswer(i -> singleUse.get(i.<String>getArgument(0)));
        doAnswer(i -> singleUse.put(i.getArgument(0), i.getArgument(2)))
                .when(singleUseProvider).put(anyString(), anyLong(), any());
        when(singleUseProvider.remove(anyString())).thenAnswer(i -> singleUse.remove(i.<String>getArgument(0)));
        when(session.singleUseObjects()).thenReturn(singleUseProvider);

        AuthenticatorConfigModel configModel = new AuthenticatorConfigModel();
        configModel.setConfig(config);

        HttpRequest request = mock(HttpRequest.class);
        when(request.getDecodedFormParameters()).thenReturn(formData);
        ClientConnection connection = mock(ClientConnection.class);
        when(connection.getRemoteAddr()).thenReturn("10.0.0.1");

        when(context.getAuthenticationSession()).thenReturn(authSession);
        when(context.getSession()).thenReturn(session);
        when(context.getRealm()).thenReturn(mock(RealmModel.class));
        when(context.getAuthenticatorConfig()).thenReturn(configModel);
        when(context.getHttpRequest()).thenReturn(request);
        when(context.getConnection()).thenReturn(connection);
        when(context.form()).thenReturn(form);
        when(context.getProtector()).thenReturn(protector);
        when(context.getEvent()).thenReturn(event);
        when(context.newEvent()).thenReturn(event);

        noUsers();
    }

    // --- helpers ---

    private void noUsers() {
        when(users.searchForUserByUserAttributeStream(any(), anyString(), anyString())).thenAnswer(i -> Stream.empty());
        when(users.getUserByUsername(any(), anyString())).thenReturn(null);
    }

    private UserModel existingUser(boolean enabled) {
        UserModel user = mock(UserModel.class);
        when(user.isEnabled()).thenReturn(enabled);
        when(user.getUsername()).thenReturn(PHONE);
        when(users.searchForUserByUserAttributeStream(any(), eq("phoneNumber"), eq(PHONE)))
                .thenAnswer(i -> Stream.of(user));
        return user;
    }

    private void inCodeStep(String code, long ageSeconds) {
        notes.put("STEP", "code_verification");
        notes.put("PHONE_NUMBER", PHONE);
        notes.put("SMS_CODE", PhoneAuthenticator.protectCode(code));
        notes.put("SMS_CODE_GENERATED_TIME", String.valueOf(Instant.now().getEpochSecond() - ageSeconds));
        notes.put("SMS_CODE_ATTEMPTS", "0");
    }

    private void submit(String key, String value) {
        formData.putSingle(key, value);
        authenticator.action(context);
    }

    private FormMessage lastError() {
        ArgumentCaptor<FormMessage> fieldError = ArgumentCaptor.forClass(FormMessage.class);
        try {
            verify(form, atLeastOnce()).addError(fieldError.capture());
            return fieldError.getValue();
        } catch (AssertionError e) {
            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            verify(form, atLeastOnce()).setError(key.capture(), any());
            return new FormMessage(null, key.getValue());
        }
    }

    // --- tests ---

    @Test
    @DisplayName("Phone input: code is sent, stored and the code form is shown - without touching the user store")
    void phoneInputSendsCode() {
        submit("phoneNumber", "123 456 789");

        assertEquals("code_verification", notes.get("STEP"));
        assertEquals(PHONE, notes.get("PHONE_NUMBER"));
        // only a salted hash of the code is kept in the auth session
        assertNotNull(notes.get("SMS_CODE"));
        assertTrue(notes.get("SMS_CODE").matches("[0-9a-f]{32}:[0-9a-f]{64}"), notes.get("SMS_CODE"));
        verify(form).createForm("phone-code-verification.ftl");
        verifyNoInteractions(users); // no user enumeration at step 1
    }

    @Test
    @DisplayName("Phone input: invalid number is rejected with a message key")
    void phoneInputInvalid() {
        submit("phoneNumber", "abc");

        assertEquals("invalidPhoneNumber", lastError().getMessage());
        assertNull(notes.get("SMS_CODE"));
        verify(form).createForm("phone-input.ftl");
    }

    @Test
    @DisplayName("Phone input: rate limit is applied and reported with a message key")
    void phoneInputRateLimited() {
        config.put(PhoneAuthenticatorFactory.RATE_LIMIT_PER_PHONE, "1");
        String phone = "+420" + (100000000 + (int) (Math.random() * 899999999));

        submit("phoneNumber", phone);
        assertEquals("code_verification", notes.get("STEP"));

        notes.clear();
        submit("phoneNumber", phone);
        assertNull(notes.get("STEP"));
        assertEquals("rateLimitPhone", lastError().getMessage());
    }

    @Test
    @DisplayName("X-Forwarded-For is ignored: the connection address is used")
    void forwardedHeaderIgnored() {
        // The authenticator never reads HttpHeaders; if it did, this mock would be hit.
        submit("phoneNumber", PHONE);
        verify(context.getHttpRequest(), never()).getHttpHeaders();
        verify(context.getConnection(), atLeastOnce()).getRemoteAddr();
    }

    @Test
    @DisplayName("Correct code for an existing user: login succeeds and the code is single use")
    void correctCodeExistingUser() {
        UserModel user = existingUser(true);
        inCodeStep("123456", 5);

        submit("smsCode", "123456");

        verify(context).setUser(user);
        verify(context).success();
        assertNull(notes.get("SMS_CODE"));
        assertNull(notes.get("STEP"));
    }

    @Test
    @DisplayName("Wrong code: attempts are counted and brute force detection is notified")
    void wrongCodeCountsAttempts() {
        UserModel user = existingUser(true);
        inCodeStep("123456", 5);

        submit("smsCode", "000000");

        assertEquals("1", notes.get("SMS_CODE_ATTEMPTS"));
        assertEquals("invalidSmsCode", lastError().getMessage());
        verify(event).user(user);
        verify(context, never()).success();
    }

    @Test
    @DisplayName("Too many wrong codes invalidate the code and send the user back to the phone step")
    void tooManyAttempts() {
        inCodeStep("123456", 5);

        for (int i = 0; i < PhoneAuthenticator.MAX_CODE_ATTEMPTS; i++) {
            submit("smsCode", "000000");
        }

        assertNull(notes.get("SMS_CODE"));
        assertNull(notes.get("STEP"));
        assertEquals("tooManyCodeAttempts", lastError().getMessage());

        // even the right code does not work any more
        formData.putSingle("smsCode", "123456");
        authenticator.action(context);
        verify(context, never()).success();
    }

    @Test
    @DisplayName("Expired code is rejected")
    void expiredCode() {
        existingUser(true);
        inCodeStep("123456", 301);

        submit("smsCode", "123456");

        assertEquals("smsCodeExpired", lastError().getMessage());
        verify(context, never()).success();
    }

    @Test
    @DisplayName("Missing generation timestamp counts as expired (no fail-open)")
    void missingTimestampIsExpired() {
        existingUser(true);
        inCodeStep("123456", 5);
        notes.remove("SMS_CODE_GENERATED_TIME");

        submit("smsCode", "123456");

        verify(context, never()).success();
    }

    @Test
    @DisplayName("Disabled user cannot log in")
    void disabledUser() {
        existingUser(false);
        inCodeStep("123456", 5);

        submit("smsCode", "123456");

        verify(context).failure(eq(AuthenticationFlowError.USER_DISABLED), any(Response.class));
        verify(context, never()).success();
    }

    @Test
    @DisplayName("Temporarily locked user (brute force) cannot log in")
    void temporarilyLockedUser() {
        UserModel user = existingUser(true);
        when(protector.isTemporarilyDisabled(any(), any(), eq(user))).thenReturn(true);
        inCodeStep("123456", 5);

        submit("smsCode", "123456");

        verify(context).failure(eq(AuthenticationFlowError.USER_TEMPORARILY_DISABLED), any(Response.class));
        verify(context, never()).success();
    }

    @Test
    @DisplayName("Two users with the same phone number: refuse instead of picking one")
    void duplicatePhoneNumber() {
        UserModel a = mock(UserModel.class);
        UserModel b = mock(UserModel.class);
        when(users.searchForUserByUserAttributeStream(any(), eq("phoneNumber"), eq(PHONE)))
                .thenAnswer(i -> Stream.of(a, b));
        inCodeStep("123456", 5);

        submit("smsCode", "123456");

        assertEquals("phoneNumberInUse", lastError().getMessage());
        verify(context, never()).success();
        verify(context, never()).setUser(any());
    }

    @Test
    @DisplayName("Unknown number with creation enabled: names are asked only after the code was verified")
    void newUserAsksForNameAfterVerification() {
        inCodeStep("123456", 5);

        submit("smsCode", "123456");

        assertEquals("name_input", notes.get("STEP"));
        verify(form).createForm("phone-user-details.ftl");
        verify(users, never()).addUser(any(), anyString());
        verify(context, never()).success();
    }

    @Test
    @DisplayName("Name step creates the user and logs in")
    void nameStepCreatesUser() {
        notes.put("STEP", "name_input");
        notes.put("PHONE_NUMBER", PHONE);
        UserModel created = mock(UserModel.class);
        when(created.getUsername()).thenReturn(PHONE);
        when(created.isEnabled()).thenReturn(true);
        when(users.addUser(any(), eq(PHONE))).thenReturn(created);
        formData.putSingle("firstName", "Jan");
        formData.putSingle("lastName", "Novak");

        authenticator.action(context);

        verify(created).setFirstName("Jan");
        verify(created).setLastName("Novak");
        verify(created).setSingleAttribute("phoneNumber", PHONE);
        verify(context).setUser(created);
        verify(context).success();
    }

    @Test
    @DisplayName("Name step without names re-renders the form")
    void nameStepRequiresNames() {
        notes.put("STEP", "name_input");
        notes.put("PHONE_NUMBER", PHONE);
        formData.putSingle("firstName", " ");
        formData.putSingle("lastName", "Novak");

        authenticator.action(context);

        assertEquals("firstNameRequired", lastError().getMessage());
        verify(users, never()).addUser(any(), anyString());
        verify(context, never()).success();
    }

    @Test
    @DisplayName("Concurrent registration: the loser of the race reuses the winner's account")
    void concurrentRegistration() {
        notes.put("STEP", "name_input");
        notes.put("PHONE_NUMBER", PHONE);
        UserModel winner = mock(UserModel.class);
        when(winner.isEnabled()).thenReturn(true);
        when(winner.getUsername()).thenReturn(PHONE);
        // first lookup: nobody; addUser blows up on the unique username; second lookup: the winner
        when(users.searchForUserByUserAttributeStream(any(), eq("phoneNumber"), eq(PHONE)))
                .thenAnswer(i -> Stream.empty())
                .thenAnswer(i -> Stream.of(winner));
        when(users.addUser(any(), eq(PHONE))).thenThrow(new RuntimeException("duplicate"));
        formData.putSingle("firstName", "Jan");
        formData.putSingle("lastName", "Novak");

        authenticator.action(context);

        verify(context).setUser(winner);
        verify(context).success();
    }

    @Test
    @DisplayName("Unknown number with creation disabled: back to phone step with an error")
    void creationDisabled() {
        config.put(PhoneAuthenticatorFactory.CREATE_USER_IF_NOT_EXISTS, "false");
        inCodeStep("123456", 5);

        submit("smsCode", "123456");

        assertEquals("userNotFound", lastError().getMessage());
        assertNull(notes.get("STEP"));
        verify(context, never()).success();
    }

    @Test
    @DisplayName("Resend is blocked during the cooldown")
    void resendCooldown() {
        inCodeStep("123456", 10);

        submit("resend", "true");

        assertEquals("resendTooSoon", lastError().getMessage());
        assertTrue(PhoneAuthenticator.codeMatches("123456", notes.get("SMS_CODE")));
    }

    @Test
    @DisplayName("Resend after the cooldown issues a new code and resets attempts")
    void resendAfterCooldown() {
        inCodeStep("123456", PhoneAuthenticator.RESEND_COOLDOWN_SECONDS + 5);
        notes.put("SMS_CODE_ATTEMPTS", "3");

        submit("resend", "true");

        assertEquals("0", notes.get("SMS_CODE_ATTEMPTS"));
        assertEquals("code_verification", notes.get("STEP"));
        assertEquals(PHONE, notes.get("PHONE_NUMBER"));
    }

    @Test
    @DisplayName("Change number returns to the phone step and drops the code")
    void changeNumber() {
        inCodeStep("123456", 5);

        submit("changeNumber", "true");

        assertNull(notes.get("STEP"));
        assertNull(notes.get("SMS_CODE"));
        verify(form).createForm("phone-input.ftl");
    }

    @Test
    @DisplayName("Name step cannot be reached without a verified code (STEP is server side only)")
    void nameStepNotReachableFromPhoneStep() {
        formData.putSingle("firstName", "Jan");
        formData.putSingle("lastName", "Novak");
        formData.putSingle("phoneNumber", "");

        authenticator.action(context);

        verify(users, never()).addUser(any(), anyString());
        verify(context, never()).success();
    }

    // --- second factor mode ---

    private UserModel identifiedUser(String phoneAttr) {
        UserModel user = mock(UserModel.class);
        when(user.isEnabled()).thenReturn(true);
        when(user.getUsername()).thenReturn("jan");
        when(user.getFirstAttribute("phoneNumber")).thenReturn(phoneAttr);
        when(context.getUser()).thenReturn(user);
        return user;
    }

    @Test
    @DisplayName("2FA: no phone form, the code goes to the user's stored number")
    void secondFactorSendsToStoredNumber() {
        identifiedUser("+420601123456");

        authenticator.authenticate(context);

        assertEquals("code_verification", notes.get("STEP"));
        assertEquals("+420601123456", notes.get("PHONE_NUMBER"));
        verify(form).createForm("phone-code-verification.ftl");
        verify(form, never()).createForm("phone-input.ftl");
        verify(form).setAttribute("secondFactor", true);
    }

    @Test
    @DisplayName("2FA: correct code completes the login of the identified user, no lookup / creation")
    void secondFactorSuccess() {
        UserModel user = identifiedUser("+420601123456");
        notes.put("STEP", "code_verification");
        notes.put("PHONE_NUMBER", "+420601123456");
        notes.put("SMS_CODE", PhoneAuthenticator.protectCode("123456"));
        notes.put("SMS_CODE_GENERATED_TIME", String.valueOf(Instant.now().getEpochSecond()));
        notes.put("SMS_CODE_ATTEMPTS", "0");

        submit("smsCode", "123456");

        verify(context).setUser(user);
        verify(context).success();
        verifyNoInteractions(users);
    }

    @Test
    @DisplayName("2FA: user without a phone number fails the flow with a setup-required error")
    void secondFactorWithoutPhone() {
        identifiedUser(null);

        authenticator.authenticate(context);

        verify(context).failureChallenge(eq(AuthenticationFlowError.CREDENTIAL_SETUP_REQUIRED), any(Response.class));
        assertNull(notes.get("SMS_CODE"));
    }

    @Test
    @DisplayName("2FA: the number cannot be changed")
    void secondFactorCannotChangeNumber() {
        identifiedUser("+420601123456");
        notes.put("STEP", "code_verification");
        notes.put("PHONE_NUMBER", "+420601123456");
        notes.put("SMS_CODE", PhoneAuthenticator.protectCode("123456"));

        submit("changeNumber", "true");

        assertEquals("code_verification", notes.get("STEP"));
        assertTrue(PhoneAuthenticator.codeMatches("123456", notes.get("SMS_CODE")));
    }

    @Test
    @DisplayName("2FA: exhausted attempts fail the flow")
    void secondFactorTooManyAttempts() {
        identifiedUser("+420601123456");
        config.put(PhoneAuthenticatorFactory.MAX_CODE_ATTEMPTS, "2");
        inCodeStep("123456", 5);

        submit("smsCode", "000000");
        submit("smsCode", "000000");

        verify(context).failureChallenge(eq(AuthenticationFlowError.INVALID_CREDENTIALS), any(Response.class));
        verify(context, never()).success();
    }

    @Test
    @DisplayName("configuredFor: first factor always, second factor only with a phone number")
    void configuredForSecondFactor() {
        UserModel with = mock(UserModel.class);
        when(with.getFirstAttribute("phoneNumber")).thenReturn("+420601123456");
        UserModel without = mock(UserModel.class);

        assertTrue(authenticator.configuredFor(null, null, null));
        assertTrue(authenticator.configuredFor(null, null, with));
        assertFalse(authenticator.configuredFor(null, null, without));
    }

    // --- configurable limits / validation ---

    @Test
    @DisplayName("Max code attempts is configurable")
    void configurableMaxAttempts() {
        config.put(PhoneAuthenticatorFactory.MAX_CODE_ATTEMPTS, "2");
        inCodeStep("123456", 5);

        submit("smsCode", "000000");
        assertEquals("code_verification", notes.get("STEP"));
        submit("smsCode", "000000");

        assertNull(notes.get("STEP"));
        assertEquals("tooManyCodeAttempts", lastError().getMessage());
    }

    @Test
    @DisplayName("Resend cooldown is configurable (0 = resend immediately)")
    void configurableCooldown() {
        config.put(PhoneAuthenticatorFactory.RESEND_COOLDOWN, "0");
        inCodeStep("123456", 1);
        long originalTime = Long.parseLong(notes.get("SMS_CODE_GENERATED_TIME"));

        submit("resend", "true");

        // a new code was issued: the timestamp moved forward
        assertTrue(Long.parseLong(notes.get("SMS_CODE_GENERATED_TIME")) > originalTime);
        assertEquals("0", notes.get("SMS_CODE_ATTEMPTS"));
    }

    @Test
    @DisplayName("Strict validation rejects numbers libphonenumber does not know")
    void strictValidationRejectsFakeNumber() {
        config.put(PhoneAuthenticatorFactory.STRICT_PHONE_VALIDATION, "true");

        submit("phoneNumber", "+420123456789");

        assertEquals("invalidPhoneNumber", lastError().getMessage());
        assertNull(notes.get("SMS_CODE"));
    }

    @Test
    @DisplayName("Strict validation accepts a real number")
    void strictValidationAcceptsRealNumber() {
        config.put(PhoneAuthenticatorFactory.STRICT_PHONE_VALIDATION, "true");

        submit("phoneNumber", "601 123 456");

        assertEquals("+420601123456", notes.get("PHONE_NUMBER"));
    }

    // --- code protection / audit ---

    @Test
    @DisplayName("Protected codes are salted (same code, different value) and verified in constant time")
    void codeProtection() {
        String a = PhoneAuthenticator.protectCode("123456");
        String b = PhoneAuthenticator.protectCode("123456");

        assertNotEquals(a, b);
        assertFalse(a.contains("123456"));
        assertTrue(PhoneAuthenticator.codeMatches("123456", a));
        assertFalse(PhoneAuthenticator.codeMatches("123457", a));
        assertFalse(PhoneAuthenticator.codeMatches("123456", "no-separator"));
    }

    @Test
    @DisplayName("Audit: exhausting the attempts records an error event with the masked number")
    void auditTooManyAttempts() {
        config.put(PhoneAuthenticatorFactory.MAX_CODE_ATTEMPTS, "1");
        inCodeStep("123456", 5);

        submit("smsCode", "000000");

        verify(event).detail("phone_number", "+420*********");
        verify(event).error("too_many_code_attempts");
    }

    @Test
    @DisplayName("Audit: a new account records a REGISTER event")
    void auditRegistration() {
        notes.put("STEP", "name_input");
        notes.put("PHONE_NUMBER", PHONE);
        UserModel created = mock(UserModel.class);
        when(created.getUsername()).thenReturn(PHONE);
        when(created.isEnabled()).thenReturn(true);
        when(users.addUser(any(), eq(PHONE))).thenReturn(created);
        formData.putSingle("firstName", "Jan");
        formData.putSingle("lastName", "Novak");

        authenticator.action(context);

        verify(event).event(org.keycloak.events.EventType.REGISTER);
        verify(event).success();
    }

    @Test
    @DisplayName("Audit problems never break the login")
    void auditFailureIsHarmless() {
        when(context.newEvent()).thenThrow(new RuntimeException("event store down"));
        UserModel user = existingUser(true);
        inCodeStep("123456", 5);

        submit("smsCode", "123456");

        verify(context).setUser(user);
        verify(context).success();
    }
}
