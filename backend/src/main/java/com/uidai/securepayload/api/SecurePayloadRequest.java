package com.uidai.securepayload.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Request received by the secure payload endpoint.
 *
 * @param encrypted_data Base64-encoded RSA ciphertext
 * @param partner_id identifier of the submitting partner
 */
public record SecurePayloadRequest(
        @NotBlank(message = "encrypted_data is required") String encrypted_data,
        @NotBlank(message = "partner_id is required") String partner_id
) {}
