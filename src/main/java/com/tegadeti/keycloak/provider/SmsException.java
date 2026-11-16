package com.tegadeti.keycloak.provider;

/** Thrown when an SMS could not be sent. */
public class SmsException extends Exception {

    public SmsException(String message) {
        super(message);
    }

    public SmsException(String message, Throwable cause) {
        super(message, cause);
    }
}
