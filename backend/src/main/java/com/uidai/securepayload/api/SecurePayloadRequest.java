package com.uidai.securepayload.api;

import jakarta.validation.constraints.NotBlank;

public record SecurePayloadRequest(
        @NotBlank(message = "encrypted_data is required") String encrypted_data,
        @NotBlank(message = "partner_id is required") String partner_id
) {}
