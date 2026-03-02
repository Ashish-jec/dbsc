package com.example.dbsc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Handles CORS and OPTIONS preflight so fetch() with custom headers (e.g. to /dbsc/refresh) never fails.
 * Runs early; for OPTIONS requests responds immediately with 200 and CORS headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SimpleCorsFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWED_ORIGINS = List.of(
            "http://localhost:8080",
            "http://127.0.0.1:8080",
            "http://localhost:8080/",
            "http://127.0.0.1:8080/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null) origin = request.getHeader("Referer");
        if (origin == null) {
            String scheme = request.getScheme();
            String host = request.getServerName();
            int port = request.getServerPort();
            if ("https".equals(scheme) && port == 443 || "http".equals(scheme) && port == 80)
                origin = scheme + "://" + host;
            else
                origin = scheme + "://" + host + ":" + port;
        }
        if (origin.endsWith("/")) origin = origin.substring(0, origin.length() - 1);
        boolean allowOrigin = ALLOWED_ORIGINS.contains(origin)
                || origin.startsWith("http://localhost:") || origin.startsWith("http://127.0.0.1:");

        if (allowOrigin) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type, Sec-Secure-Session-Id, X-Dbsc-Session-Id, Secure-Session-Response");
            response.setHeader("Access-Control-Expose-Headers", "Secure-Session-Challenge, Set-Cookie");
            response.setHeader("Access-Control-Max-Age", "86400");
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
