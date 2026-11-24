package com.uidai.securepayload.api;

import com.uidai.securepayload.service.SecurePayloadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/sandbox/secure-payload")
/**
 * REST controller for receiving encrypted sandbox identity payloads.
 */
public class SecurePayloadController {
    private final SecurePayloadService service;

    /**
     * Creates the controller with the application service dependency.
     *
     * @param service secure payload application service
     */
    public SecurePayloadController(SecurePayloadService service) {
        this.service = service;
    }

    /**
     * Creates and publishes a verified secure-payload event.
     *
     * @param request encrypted request received from the partner
     * @return HTTP 202 response containing the assigned transaction ID
     */
    @PostMapping
    public ResponseEntity<SecurePayloadResponse> receive(
            @Valid @RequestBody SecurePayloadRequest request,
            @org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key")
            @NotBlank(message = "Idempotency-Key is required")
            @Size(max = 128, message = "Idempotency-Key is too large") String idempotencyKey) {
        var transactionId = service.process(request, idempotencyKey);
        return ResponseEntity.accepted().body(new SecurePayloadResponse(transactionId, "PUBLISHED"));
    }
}
