package com.uidai.securepayload.api;

import java.time.Instant;
import java.util.UUID;

public record ApiError(Instant timestamp, int status, String code, String message, String path, UUID request_id) {}
