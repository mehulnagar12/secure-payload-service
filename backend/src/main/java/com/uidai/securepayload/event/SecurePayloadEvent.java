package com.uidai.securepayload.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record SecurePayloadEvent(
        String event_type,
        String schema_version,
        UUID transaction_id,
        String partner_id,
        Instant received_at,
        JsonNode identity
) {}
