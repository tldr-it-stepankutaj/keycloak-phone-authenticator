package com.tegadeti.keycloak.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic HTTP SMS gateway: {@code POST <url>} with a JSON body {@code {"to": "+420...", "message": "..."}}
 * and an optional {@code Authorization: Bearer <token>} header. Any 2xx response means "accepted".
 * <p>
 * This lets you plug in a local SMS gateway, an internal notification service or a small adapter in front
 * of any other provider without changing this extension.
 */
public class HttpSmsProvider implements SmsProvider {

    public static final String ID = "http";

    private static final Logger logger = Logger.getLogger(HttpSmsProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER) // never forward the bearer token to another host
            .build();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void send(Map<String, String> config, String phoneNumber, String message) throws SmsException {
        String url = config.get(PhoneAuthenticatorFactory.HTTP_SMS_URL);
        String token = config.get(PhoneAuthenticatorFactory.HTTP_SMS_AUTH_TOKEN);

        URI uri = parseUri(url);

        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("to", phoneNumber);
            payload.put("message", message);

            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)));
            if (token != null && !token.trim().isEmpty()) {
                request.header("Authorization", "Bearer " + token.trim());
            }

            HttpResponse<Void> response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                throw new SmsException("SMS gateway responded with HTTP " + response.statusCode());
            }
            logger.infof("SMS handed over to HTTP gateway (HTTP %d), to: %s",
                    response.statusCode(), PhoneNumberUtils.mask(phoneNumber));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SmsException("Interrupted while calling SMS gateway", e);
        } catch (IOException e) {
            throw new SmsException("Failed to call SMS gateway: " + e.getMessage(), e);
        }
    }

    private static URI parseUri(String url) throws SmsException {
        if (url == null || url.trim().isEmpty()) {
            throw new SmsException("HTTP SMS gateway URL is not configured");
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new SmsException("HTTP SMS gateway URL must be an http(s) URL");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new SmsException("HTTP SMS gateway URL is invalid: " + e.getMessage(), e);
        }
    }
}
