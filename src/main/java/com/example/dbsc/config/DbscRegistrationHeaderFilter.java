package com.example.dbsc.config;

import com.example.dbsc.dbsc.store.RegistrationChallengeStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Adds {@code Secure-Session-Registration} and a long-lived cookie to the main document
 * response (GET / or /index.html) so Chrome can see a "post-login" style response and
 * optionally perform native DBSC registration (Chrome docs expect both header and cookie).
 */
public class DbscRegistrationHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DbscRegistrationHeaderFilter.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REGISTRATION_CHALLENGE_TTL_SECONDS = 300;
    private static final int CHALLENGE_BYTES = 32;
    /** Max-Age for the placeholder long-lived cookie (30 days). Chrome docs expect this on the response that has Secure-Session-Registration. */
    private static final int LONG_LIVED_COOKIE_MAX_AGE = 2592000;

    private final RegistrationChallengeStore registrationChallengeStore;
    private final String cookieName;
    private final String cookieAttributes;

    public DbscRegistrationHeaderFilter(RegistrationChallengeStore registrationChallengeStore,
                                         DbscProperties dbscProperties) {
        this.registrationChallengeStore = registrationChallengeStore;
        this.cookieName = dbscProperties.getCredentialCookieName();
        this.cookieAttributes = dbscProperties.getCredentialCookieAttributes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            String path = request.getRequestURI();
            if (path == null) path = "";
            if ("/".equals(path) || "/index.html".equals(path)) {
                String challenge = generateChallenge();
                Instant expiresAt = Instant.now().plusSeconds(REGISTRATION_CHALLENGE_TTL_SECONDS);
                registrationChallengeStore.put(challenge, expiresAt);
                String headerValue = "(ES256);path=\"/dbsc/register\";challenge=\"" + challenge + "\"";
                response.setHeader("Secure-Session-Registration", headerValue);
                String longLivedCookie = cookieName + "=pending; Max-Age=" + LONG_LIVED_COOKIE_MAX_AGE + "; " + cookieAttributes;
                response.addHeader("Set-Cookie", longLivedCookie);
                log.debug("Added Secure-Session-Registration and long-lived cookie to document response for path={}", path);
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String generateChallenge() {
        byte[] bytes = new byte[CHALLENGE_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
