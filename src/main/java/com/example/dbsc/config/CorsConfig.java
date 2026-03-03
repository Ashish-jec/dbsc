package com.example.dbsc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * CORS config so same-origin fetch with custom headers (e.g. to /dbsc/refresh) succeeds.
 * POST with Secure-Session-Response triggers a preflight OPTIONS; the server must respond
 * with allowed origin, methods, and headers. In cloud, set dbsc.cors-allowed-origins or
 * DBSC_CORS_ALLOW_ORIGIN (env) to your app origin (the URL in the browser, e.g. tunnel URL).
 */
@Configuration
public class CorsConfig {

    private static final List<String> DEFAULT_ORIGINS = List.of("http://localhost:8080", "http://127.0.0.1:8080");

    @Bean
    public CorsConfigurationSource corsConfigurationSource(DbscProperties dbscProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        List<String> origins = effectiveCorsOrigins(dbscProperties);
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Secure-Session-Registration", "Secure-Session-Challenge", "Set-Cookie"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static List<String> effectiveCorsOrigins(DbscProperties p) {
        List<String> fromList = p.getCorsAllowedOrigins();
        String single = p.getCorsAllowOrigin();
        boolean hasList = fromList != null && !fromList.isEmpty();
        boolean hasSingle = single != null && !single.isBlank();
        if (!hasList && !hasSingle) return DEFAULT_ORIGINS;
        List<String> out = new ArrayList<>(hasList ? fromList : List.of());
        if (hasSingle && !out.contains(single)) out.add(single);
        return out.isEmpty() ? DEFAULT_ORIGINS : out;
    }
}
