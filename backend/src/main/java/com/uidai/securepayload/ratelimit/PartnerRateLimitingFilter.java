package com.uidai.securepayload.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uidai.securepayload.api.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Rejects partner requests over the configured fixed-window limit before the
 * controller and cryptographic service are invoked.
 */
@Component
public class PartnerRateLimitingFilter extends OncePerRequestFilter {
    private static final String SECURE_PAYLOAD_PATH = "/api/v1/sandbox/secure-payload";
    private static final int TOO_MANY_REQUESTS_STATUS = 429;
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    private final ObjectMapper objectMapper;
    private final PartnerRateLimiter rateLimiter;

    /**
     * Creates the request filter.
     *
     * @param objectMapper JSON parser used to read only the partner identifier
     * @param rateLimiter distributed partner rate limiter
     */
    public PartnerRateLimitingFilter(ObjectMapper objectMapper, PartnerRateLimiter rateLimiter) {
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Applies rate limiting only to the secure-payload POST endpoint.
     *
     * @param request incoming HTTP request
     * @param response outgoing HTTP response
     * @param filterChain remaining servlet filters and controller chain
     * @throws ServletException when downstream processing fails
     * @throws IOException when request-body or response I/O fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !SECURE_PAYLOAD_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
        String partnerId = extractPartnerId(cachedRequest.getCachedBody());
        if (partnerId != null) {
            PartnerRateLimiter.RateLimitDecision decision = rateLimiter.tryAcquire(partnerId);
            if (!decision.allowed()) {
                reject(response, decision);
                return;
            }
            response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        }
        filterChain.doFilter(cachedRequest, response);
    }

    private String extractPartnerId(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode partnerId = root == null ? null : root.get("partner_id");
            return partnerId != null && partnerId.isTextual() && !partnerId.asText().isBlank()
                    ? partnerId.asText() : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private void reject(HttpServletResponse response, PartnerRateLimiter.RateLimitDecision decision)
            throws IOException {
        // The filter can terminate the request before Spring MVC applies @CrossOrigin.
        response.setHeader("Access-Control-Allow-Origin", FRONTEND_ORIGIN);
        response.setHeader("Access-Control-Expose-Headers", "Retry-After");
        response.setHeader("Vary", "Origin");
        response.setStatus(TOO_MANY_REQUESTS_STATUS);
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(Instant.now(), TOO_MANY_REQUESTS_STATUS,
                "RATE_LIMIT_EXCEEDED", "Too many requests for this partner", SECURE_PAYLOAD_PATH, UUID.randomUUID());
        objectMapper.writeValue(response.getWriter(), error);
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            body = request.getInputStream().readAllBytes();
        }

        private byte[] getCachedBody() {
            return body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(jakarta.servlet.ReadListener listener) { }
            };
        }
    }
}
