package com.tegadeti.keycloak.provider;

import java.util.Map;

/**
 * A way of delivering an SMS (Twilio, a generic HTTP gateway, ...).
 * <p>
 * Implementations receive the authenticator configuration of the current flow as a plain map so they can be
 * tested without Keycloak, and must be thread-safe (one instance serves all requests).
 */
public interface SmsProvider {

    /** Value of the {@code sms.provider} config option that selects this provider. */
    String getId();

    /**
     * @param config      authenticator configuration (never null)
     * @param phoneNumber recipient in E.164 format
     * @param message     final message text
     * @throws SmsException if the message was not accepted by the gateway
     */
    void send(Map<String, String> config, String phoneNumber, String message) throws SmsException;
}
