package com.uidai.securepayload.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request received by the secure payload endpoint.
 *
 * @param encrypted_data JSON-encoded hybrid encryption envelope
 * @param partner_id identifier of the submitting partner
 */
public record SecurePayloadRequest(
        @NotBlank(message = "encrypted_data is required") @Size(max = 4096, message = "encrypted_data is too large") String encrypted_data,
        @NotBlank(message = "partner_id is required") @Size(max = 128, message = "partner_id is too large") String partner_id
) {}
