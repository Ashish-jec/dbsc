package com.example.dbsc.config;

import com.example.dbsc.dbsc.store.DbscSessionStore;
import com.example.dbsc.security.DbscSessionAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   DbscSessionStore sessionStore,
                                                   DbscProperties dbscProperties,
                                                   CorsConfigurationSource corsConfigurationSource) throws Exception {
        DbscSessionAuthenticationFilter dbscFilter = new DbscSessionAuthenticationFilter(sessionStore, dbscProperties);
        DbscRefreshEndpointFilter refreshEndpointFilter = new DbscRefreshEndpointFilter();

        http
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/dbsc/**", "/.well-known/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, authException) -> send401(response))
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            var auth = SecurityContextHolder.getContext().getAuthentication();
                            if (auth instanceof AnonymousAuthenticationToken) {
                                send401(response);
                            } else {
                                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                            }
                        })
                )
                .addFilterBefore(refreshEndpointFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(dbscFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void send401(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Valid DBSC session required\"}");
    }
}
