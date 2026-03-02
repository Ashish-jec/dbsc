package com.example.dbsc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Logs every HTTP request and response (method, URI, status, duration) to the application log
 * so you can see all calls to the server in the console alongside other logs.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("ACCESS");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        if (request.getQueryString() != null) {
            uri = uri + "?" + request.getQueryString();
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            long duration = System.currentTimeMillis() - start;
            log.info("{} {} {} {}ms", method, uri, status, duration);
        }
    }
}
