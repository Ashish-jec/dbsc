package com.example.dbsc.web;

import com.example.dbsc.config.DbscProperties;
import com.example.dbsc.dbsc.DbscChallengeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.dbsc.dbsc.DbscProofValidator;
import com.example.dbsc.dbsc.DbscSessionService;
import com.example.dbsc.dbsc.dto.JsonSessionInstructions;
import com.example.dbsc.dbsc.model.DbscSession;
import com.example.dbsc.dbsc.store.RegistrationChallengeStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * DBSC registration endpoint per W3C spec § 5.
 * Receives POST with Secure-Session-Response (DBSC proof JWT), validates, creates session, returns JSON session instructions.
 */
@RestController
@RequestMapping("/dbsc")
public class DbscRegistrationController {

    private static final Logger log = LoggerFactory.getLogger(DbscRegistrationController.class);
    private static final String HEADER_SECURE_SESSION_RESPONSE = "Secure-Session-Response";

    private final DbscProofValidator proofValidator;
    private final DbscSessionService sessionService;
    private final DbscChallengeService challengeService;
    private final RegistrationChallengeStore registrationChallengeStore;
    private final DbscProperties properties;

    public DbscRegistrationController(DbscProofValidator proofValidator,
                                       DbscSessionService sessionService,
                                       DbscChallengeService challengeService,
                                       RegistrationChallengeStore registrationChallengeStore,
                                       DbscProperties properties) {
        this.proofValidator = proofValidator;
        this.sessionService = sessionService;
        this.challengeService = challengeService;
        this.registrationChallengeStore = registrationChallengeStore;
        this.properties = properties;
    }

    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonSessionInstructions> register(
            @RequestHeader(value = HEADER_SECURE_SESSION_RESPONSE, required = false) String secureSessionResponse,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (secureSessionResponse == null || secureSessionResponse.isBlank()) {
            log.warn("Registration: missing Secure-Session-Response header");
            return ResponseEntity.badRequest().build();
        }

        // Strip optional sf-string quotes per structured header
        String jwtString = stripStructuredHeaderString(secureSessionResponse);

        DbscProofValidator.RegistrationProofResult proof;
        try {
            proof = proofValidator.validateRegistrationProof(jwtString);
        } catch (DbscProofValidator.DbscValidationException e) {
            log.warn("Registration: invalid proof - {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        // Validate that jti was a challenge we issued for registration (one-time use)
        registrationChallengeStore.removeExpired();
        if (!registrationChallengeStore.consume(proof.jti())) {
            log.warn("Registration: invalid or reused challenge (jti={})", proof.jti());
            return ResponseEntity.badRequest().build();
        }

        String origin = EffectiveScopeOriginResolver.resolve(request, properties.getScopeOrigin());
        DbscSession session = sessionService.createSession(proof.publicKey(), proof.algorithm(), origin);
        log.info("Registration: created session sessionId={} origin={}", session.getSessionId(), origin);

        // Issue first challenge for future refresh; client will cache via Secure-Session-Challenge
        String nextChallenge = challengeService.issueChallenge(session.getSessionId());
        response.setHeader("Secure-Session-Challenge", "\"" + nextChallenge + "\";id=\"" + session.getSessionId() + "\"");

        String refreshUrl = origin + "/dbsc/refresh";
        List<String> allowedInitiators = properties.getAllowedRefreshInitiators() != null
                ? properties.getAllowedRefreshInitiators()
                : List.of();

        JsonSessionInstructions body = JsonSessionInstructions.of(
                session.getSessionId(),
                refreshUrl,
                origin,
                sessionService.getCredentialCookieName(),
                sessionService.getCredentialCookieAttributes(),
                allowedInitiators
        );

        // Set the bound cookie so the client has a session credential
        String cookieHeader = buildSessionCookie(request, session.getSessionId(), true);
        response.addHeader("Set-Cookie", cookieHeader);

        log.debug("Registration: returning session instructions for sessionId={}", session.getSessionId());
        return ResponseEntity.ok(body);
    }

    private String stripStructuredHeaderString(String value) {
        if (value == null) return null;
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String buildSessionCookie(HttpServletRequest request, String sessionId, boolean withMaxAge) {
        String name = sessionService.getCredentialCookieName();
        String attrs = sessionService.getCredentialCookieAttributes();
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(sessionId);
        if (withMaxAge) {
            sb.append("; Max-Age=").append(sessionService.getSessionCookieMaxAge());
        }
        if (attrs != null && !attrs.isBlank()) {
            sb.append("; ").append(attrs.trim());
        }
        return sb.toString();
    }
}

