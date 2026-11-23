package com.uidai.securepayload.api;

import com.uidai.securepayload.service.SecurePayloadService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/api/v1/sandbox/secure-payload")
@CrossOrigin(origins = "http://localhost:5173")
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
    public ResponseEntity<SecurePayloadResponse> receive(@Valid @RequestBody SecurePayloadRequest request) {
        var transactionId = service.process(request);
        return ResponseEntity.accepted().body(new SecurePayloadResponse(transactionId, "PUBLISHED"));
    }
}
