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
public class ApiExceptionHandler {
    @ExceptionHandler({PayloadDecryptionException.class, MalformedPayloadException.class,
            MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> badRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_SECURE_PAYLOAD", messageFor(exception), request);
    }

    @ExceptionHandler(EventPublicationException.class)
    ResponseEntity<ApiError> kafkaUnavailable(EventPublicationException exception, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "EVENT_PUBLICATION_FAILED",
                "The secure payload could not be published", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The request could not be processed", request);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message,
                                              HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), UUID.randomUUID()));
    }

    private String messageFor(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validation) {
            return validation.getBindingResult().getFieldErrors().stream()
                    .findFirst().map(error -> error.getDefaultMessage()).orElse("Request is invalid");
        }
        return exception.getMessage();
    }
}
