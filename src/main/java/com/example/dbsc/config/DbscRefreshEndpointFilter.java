package com.example.dbsc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds security headers recommended by W3C DBSC § 5 for the refresh endpoint:
 * X-Frame-Options and Cross-Origin-Resource-Policy to prevent embedding.
 */
public class DbscRefreshEndpointFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI() != null && request.getRequestURI().endsWith("/dbsc/refresh")) {
            response.setHeader("X-Frame-Options", "DENY");
            // Omit Cross-Origin-Resource-Policy so same-origin fetch() is not blocked
        }
        filterChain.doFilter(request, response);
    }
}
