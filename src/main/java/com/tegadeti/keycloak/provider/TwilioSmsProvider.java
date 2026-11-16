package com.tegadeti.keycloak.provider;

import com.twilio.http.NetworkHttpClient;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.util.Timeout;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends SMS through the Twilio Programmable Messaging API.
 */
public class TwilioSmsProvider implements SmsProvider {

    public static final String ID = "twilio";

    private static final Logger logger = Logger.getLogger(TwilioSmsProvider.class);

    private static final int CONNECT_TIMEOUT_SECONDS = 5;
    private static final int RESPONSE_TIMEOUT_SECONDS = 10;
    private static final int MAX_CACHED_CLIENTS = 16;

    /**
     * Twilio clients per credentials. The static {@code Twilio.init()} is deliberately NOT used: it is
     * global, so realms with different Twilio accounts would overwrite each other's credentials.
     */
    private static final Map<String, TwilioRestClient> CLIENTS = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void send(Map<String, String> config, String phoneNumber, String message) throws SmsException {
        String accountSid = config.get(PhoneAuthenticatorFactory.TWILIO_ACCOUNT_SID);
        String authToken = config.get(PhoneAuthenticatorFactory.TWILIO_AUTH_TOKEN);
        String fromNumber = config.get(PhoneAuthenticatorFactory.TWILIO_FROM_NUMBER);

        validateConfiguration(accountSid, authToken, fromNumber);

        try {
            Message sent = Message.creator(
                    new PhoneNumber(phoneNumber),
                    new PhoneNumber(fromNumber),
                    message
            ).create(getClient(accountSid, authToken));

            logger.infof("SMS sent successfully. SID: %s, Status: %s, To: %s",
                    sent.getSid(), sent.getStatus(), PhoneNumberUtils.mask(phoneNumber));

            if (Message.Status.FAILED.equals(sent.getStatus())) {
                throw new SmsException("SMS delivery failed with status: " + sent.getStatus());
            }
        } catch (SmsException e) {
            throw e;
        } catch (Exception e) {
            logger.errorf("Failed to send SMS to %s: %s", PhoneNumberUtils.mask(phoneNumber), e.getMessage());
            throw new SmsException("Failed to send SMS: " + e.getMessage(), e);
        }
    }

    private static TwilioRestClient getClient(String accountSid, String authToken) {
        if (CLIENTS.size() >= MAX_CACHED_CLIENTS) {
            CLIENTS.clear(); // credentials were rotated / many realms - just rebuild lazily
        }
        return CLIENTS.computeIfAbsent(accountSid + ":" + authToken, k -> {
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                    .setResponseTimeout(Timeout.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
                    .build();
            return new TwilioRestClient.Builder(accountSid, authToken)
                    .httpClient(new NetworkHttpClient(requestConfig))
                    .build();
        });
    }

    private static void validateConfiguration(String accountSid, String authToken, String fromNumber)
            throws SmsException {
        if (accountSid == null || accountSid.trim().isEmpty()) {
            throw new SmsException("Twilio Account SID is not configured");
        }
        if (authToken == null || authToken.trim().isEmpty()) {
            throw new SmsException("Twilio Auth Token is not configured");
        }
        if (fromNumber == null || fromNumber.trim().isEmpty()) {
            throw new SmsException("Twilio From Number is not configured");
        }
        if (!PhoneNumberUtils.isValid(fromNumber)) {
            throw new SmsException("Invalid Twilio From Number format: " + fromNumber);
        }
    }

    /** Sends a test SMS; used to validate credentials. */
    public boolean testConfiguration(String accountSid, String authToken, String fromNumber, String testPhoneNumber) {
        try {
            Message message = Message.creator(
                    new PhoneNumber(testPhoneNumber),
                    new PhoneNumber(fromNumber),
                    "Test message from Keycloak Phone Authenticator"
            ).create(getClient(accountSid, authToken));
            logger.infof("Test SMS sent successfully. SID: %s", message.getSid());
            return true;
        } catch (Exception e) {
            logger.errorf("Test SMS failed: %s", e.getMessage());
            return false;
        }
    }
}
