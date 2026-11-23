package com.uidai.securepayload.api;

import java.util.UUID;

/**
 * Acknowledgement returned after the verified payload has been published.
 *
 * @param transaction_id identifier assigned to the processed request
 * @param status publication status
 */
public record SecurePayloadResponse(UUID transaction_id, String status) {}
