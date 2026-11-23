package com.uidai.securepayload.api;

import java.util.UUID;

public record SecurePayloadResponse(UUID transaction_id, String status) {}
