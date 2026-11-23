package com.uidai.securepayload.event;

/**
 * Abstraction for publishing verified secure-payload events.
 */
public interface SecurePayloadEventPublisher {
    /**
     * Publishes an event and returns only after the implementation's delivery contract completes.
     *
     * @param event verified event to publish
     * @throws EventPublicationException if publication cannot be confirmed
     */
    void publish(SecurePayloadEvent event);
}
