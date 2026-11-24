package com.uidai.securepayload.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a non-sensitive correlation identifier to every HTTP request.
 * The identifier is available to logs and is returned to clients for support correlation.
 */
@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "request_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        UUID requestId = UUID.randomUUID();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId.toString());
        MDC.put(MDC_KEY, requestId.toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
