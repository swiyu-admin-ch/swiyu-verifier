/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.keyValue;
import static net.logstash.logback.argument.StructuredArguments.value;

/**
 * Logs requests and responses.
 * <p>
 * It is similar to {@link org.springframework.web.filter.CommonsRequestLoggingFilter} but also
 * logs responses.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Component
@Slf4j
class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String UNKNOWN_METHOD = "UNKNOWN";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain) throws ServletException, IOException {
        boolean shouldSkipTracing = isAsyncDispatch(request);
        if (shouldSkipTracing) {
            filterChain.doFilter(request, response);
            return;
        }
        ZonedDateTime incomingTime = ZonedDateTime.now();
        logRequest(request);
        try {
            filterChain.doFilter(request, response);
        } finally {
            logResponse(request, response, incomingTime);
        }
    }

    private void logRequest(HttpServletRequest request) {
        var servletRequest = request instanceof ServletServerHttpRequest ?
                (ServletServerHttpRequest) request : new ServletServerHttpRequest(request);
        var method = method(servletRequest);
        log.debug("Incoming {} Request to {}",
                value("method", method),
                value("uri", servletRequest.getURI().toASCIIString()));

    }

    private void logResponse(HttpServletRequest request, HttpServletResponse response, ZonedDateTime incomingTime) {
        var responseHeaders = response.getHeaderNames().stream().distinct()
                .collect(Collectors.toMap(Function.identity(), name -> new ArrayList<>(response.getHeaders(name))));
        var servletServerHttpRequest = request instanceof ServletServerHttpRequest ?
                (ServletServerHttpRequest) request : new ServletServerHttpRequest(request);
        var method = method(servletServerHttpRequest);
        var durationTime = ChronoUnit.MILLIS.between(incomingTime, ZonedDateTime.now());
        var remoteAddress = servletServerHttpRequest.getRemoteAddress();
        String format = "Response: {} {} {} {} {} {} {}";
        log.debug(format,
                value("method", method),
                value("uri", servletServerHttpRequest.getURI().toASCIIString()),
                keyValue("result", response.getStatus()),
                keyValue("dt", durationTime),
                keyValue("remoteAddr", remoteAddress.toString()),
                keyValue("requestHeaders", servletServerHttpRequest.getHeaders()),
                keyValue("responseHeaders", responseHeaders));
    }

    private static String method(ServletServerHttpRequest request) {
        return request.getMethod().toString();
    }
}
