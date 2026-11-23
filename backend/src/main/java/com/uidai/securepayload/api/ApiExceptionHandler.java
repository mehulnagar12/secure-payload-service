package com.uidai.securepayload.api;

import com.uidai.securepayload.crypto.PayloadDecryptionException;
import com.uidai.securepayload.event.EventPublicationException;
import com.uidai.securepayload.service.MalformedPayloadException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
/**
 * Translates domain and infrastructure exceptions into safe HTTP responses.
 */
public class ApiExceptionHandler {
    @ExceptionHandler({PayloadDecryptionException.class, MalformedPayloadException.class,
            MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class})
    /**
     * Handles invalid request data, decryption failures, and malformed plaintext.
     *
     * @param exception exception raised while processing the request
     * @param request current servlet request
     * @return HTTP 400 response with a stable application error code
     */
    ResponseEntity<ApiError> badRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_SECURE_PAYLOAD", messageFor(exception), request);
    }

    @ExceptionHandler(EventPublicationException.class)
    /**
     * Handles failures while waiting for Kafka publication acknowledgement.
     *
     * @param exception publication failure
     * @param request current servlet request
     * @return HTTP 503 response without exposing Kafka internals
     */
    ResponseEntity<ApiError> kafkaUnavailable(EventPublicationException exception, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "EVENT_PUBLICATION_FAILED",
                "The secure payload could not be published", request);
    }

    @ExceptionHandler(Exception.class)
    /**
     * Handles unexpected failures using a generic client-safe message.
     *
     * @param exception unexpected exception
     * @param request current servlet request
     * @return HTTP 500 response
     */
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The request could not be processed", request);
    }

    /**
     * Builds the common API error response envelope.
     *
     * @param status HTTP status to return
     * @param code application error code
     * @param message client-safe message
     * @param request current servlet request
     * @return populated error response
     */
    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
                                              HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID()));
    }

    /**
     * Extracts a useful validation message while preserving safe error handling.
     *
     * @param exception handled exception
     * @return client-safe message
     */
    private String messageFor(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validation) {
            return validation.getBindingResult().getFieldErrors().stream()
                    .findFirst().map(error -> error.getDefaultMessage()).orElse("Request is invalid");
        }
        return exception.getMessage();
    }
}
