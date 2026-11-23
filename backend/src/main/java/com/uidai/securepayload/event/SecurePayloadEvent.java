package com.uidai.securepayload.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Event emitted after an encrypted payload has been decrypted and validated.
 *
 * @param event_type event name
 * @param schema_version event schema version
 * @param transaction_id request correlation identifier
 * @param partner_id submitting partner identifier
 * @param received_at event creation time
 * @param identity verified decrypted identity JSON
 */
public record SecurePayloadEvent(
        String event_type,
        String schema_version,
        UUID transaction_id,
        String partner_id,
        Instant received_at,
        JsonNode identity
) {}
