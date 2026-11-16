package com.tegadeti.keycloak.provider;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpSmsProviderTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final HttpSmsProvider provider = new HttpSmsProvider();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sms", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private Map<String, String> config(String token) {
        Map<String, String> config = new HashMap<>();
        config.put(PhoneAuthenticatorFactory.HTTP_SMS_URL, "http://127.0.0.1:" + server.getAddress().getPort() + "/sms");
        if (token != null) {
            config.put(PhoneAuthenticatorFactory.HTTP_SMS_AUTH_TOKEN, token);
        }
        return config;
    }

    @Test
    @DisplayName("Posts JSON with to/message and bearer token")
    void sendsJson() throws Exception {
        provider.send(config("secret"), "+420601123456", "Code: 123456 \"quoted\"");

        assertEquals("application/json", contentType.get());
        assertEquals("Bearer secret", auth.get());
        assertTrue(body.get().contains("\"to\":\"+420601123456\""));
        assertTrue(body.get().contains("\"message\":\"Code: 123456 \\\"quoted\\\"\""), body.get());
    }

    @Test
    @DisplayName("No Authorization header without a token")
    void noToken() throws Exception {
        provider.send(config(null), "+420601123456", "x");
        assertNull(auth.get());
    }

    @Test
    @DisplayName("Non-2xx response is an error")
    void errorStatus() {
        status.set(500);
        SmsException e = assertThrows(SmsException.class, () -> provider.send(config(null), "+420601123456", "x"));
        assertTrue(e.getMessage().contains("500"));
    }

    @Test
    @DisplayName("Missing or non-http URL is rejected")
    void invalidUrl() {
        assertThrows(SmsException.class, () -> provider.send(new HashMap<>(), "+420601123456", "x"));
        Map<String, String> ftp = new HashMap<>();
        ftp.put(PhoneAuthenticatorFactory.HTTP_SMS_URL, "ftp://example.com/sms");
        assertThrows(SmsException.class, () -> provider.send(ftp, "+420601123456", "x"));
        Map<String, String> garbage = new HashMap<>();
        garbage.put(PhoneAuthenticatorFactory.HTTP_SMS_URL, "not a url");
        assertThrows(SmsException.class, () -> provider.send(garbage, "+420601123456", "x"));
    }

    @Test
    @DisplayName("Unreachable gateway is an SmsException, not a raw IOException")
    void unreachable() {
        server.stop(0);
        assertThrows(SmsException.class, () -> provider.send(config(null), "+420601123456", "x"));
    }
}
