package com.uidai.securepayload.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Stable error envelope returned by the REST API.
 *
 * @param timestamp time at which the error response was created
 * @param status HTTP status code
 * @param code application-specific error code
 * @param message safe, client-facing error message
 * @param path request URI that produced the error
 * @param request_id correlation identifier for support and server logs
 */
public record ApiError(Instant timestamp, int status, String code, String message, String path, UUID request_id) {}
