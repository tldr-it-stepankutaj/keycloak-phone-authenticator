package com.tegadeti.keycloak.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SmsServiceTest {

    private static class RecordingProvider implements SmsProvider {
        private final String id;
        final List<String> sent = new ArrayList<>();

        RecordingProvider(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public void send(Map<String, String> config, String phoneNumber, String message) {
            sent.add(phoneNumber + "|" + message);
        }
    }

    private final RecordingProvider twilio = new RecordingProvider("twilio");
    private final RecordingProvider http = new RecordingProvider("http");
    private final SmsService service = new SmsService(List.of(twilio, http));

    @Test
    @DisplayName("Default provider is twilio (backwards compatible)")
    void defaultProvider() throws Exception {
        service.sendSms(new HashMap<>(), "+420601123456", "123456");
        assertEquals(1, twilio.sent.size());
        assertTrue(http.sent.isEmpty());
    }

    @Test
    @DisplayName("Provider is selected by config, case-insensitively")
    void selectedProvider() throws Exception {
        Map<String, String> config = new HashMap<>();
        config.put(PhoneAuthenticatorFactory.SMS_PROVIDER, "HTTP");
        service.sendSms(config, "+420601123456", "123456");
        assertEquals(1, http.sent.size());
        assertTrue(twilio.sent.isEmpty());
    }

    @Test
    @DisplayName("Unknown provider fails instead of silently falling back")
    void unknownProvider() {
        Map<String, String> config = new HashMap<>();
        config.put(PhoneAuthenticatorFactory.SMS_PROVIDER, "carrier-pigeon");
        assertThrows(SmsException.class, () -> service.sendSms(config, "+420601123456", "123456"));
    }

    @Test
    @DisplayName("Development mode never calls a provider")
    void developmentMode() throws Exception {
        Map<String, String> config = new HashMap<>();
        config.put(PhoneAuthenticatorFactory.DEVELOPMENT_MODE, "true");
        service.sendSms(config, "+420601123456", "123456");
        assertTrue(twilio.sent.isEmpty());
        assertTrue(http.sent.isEmpty());
    }

    @Test
    @DisplayName("Template placeholders are replaced, TTL is shown in minutes")
    void template() throws Exception {
        Map<String, String> config = new HashMap<>();
        config.put(PhoneAuthenticatorFactory.SMS_MESSAGE_TEMPLATE, "Code {code}, valid {ttl} min");
        config.put(PhoneAuthenticatorFactory.SMS_CODE_TTL, "600");
        service.sendSms(config, "+420601123456", "987654");
        assertEquals("+420601123456|Code 987654, valid 10 min", twilio.sent.get(0));
    }

    @Test
    @DisplayName("Default template and TTL fall back to defaults")
    void templateDefaults() {
        String body = SmsService.buildMessageBody(new HashMap<>(), "111222");
        assertTrue(body.contains("111222"));
        assertTrue(body.contains("5"));
        Map<String, String> bad = new HashMap<>();
        bad.put(PhoneAuthenticatorFactory.SMS_CODE_TTL, "abc");
        assertTrue(SmsService.buildMessageBody(bad, "1").contains("5"));
        Map<String, String> tiny = new HashMap<>();
        tiny.put(PhoneAuthenticatorFactory.SMS_CODE_TTL, "30");
        assertTrue(SmsService.buildMessageBody(tiny, "1").contains("1"));
    }
}
