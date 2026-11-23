package com.uidai.securepayload.event;

/**
 * Signals that a verified event could not be acknowledged by Kafka.
 */
public class EventPublicationException extends RuntimeException {
    /**
     * Creates an event publication failure.
     *
     * @param message safe failure description
     * @param cause underlying producer or acknowledgement failure
     */
    public EventPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
