package com.example.dbsc.web;

import com.example.dbsc.config.DbscProperties;
import com.example.dbsc.dbsc.DbscChallengeService;
import com.example.dbsc.dbsc.DbscProofValidator;
import com.example.dbsc.dbsc.DbscSessionService;
import com.example.dbsc.dbsc.dto.JsonSessionInstructions;
import com.example.dbsc.dbsc.model.DbscSession;
import com.example.dbsc.dbsc.store.DbscSessionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * DBSC refresh endpoint per W3C spec § 5.
 * Called when a request has an expired/missing bound cookie. Validates DBSC proof and issues new bound cookies.
 */
@RestController
@RequestMapping("/dbsc")
public class DbscRefreshController {

    private static final Logger log = LoggerFactory.getLogger(DbscRefreshController.class);
    private static final String HEADER_SECURE_SESSION_RESPONSE = "Secure-Session-Response";
    private static final String HEADER_SEC_SECURE_SESSION_ID = "Sec-Secure-Session-Id";
    /** Alternative header for JS test page: fetch() cannot set Sec-* headers (forbidden by browser). Real DBSC UAs use Sec-Secure-Session-Id. */
    private static final String HEADER_X_DBSC_SESSION_ID = "X-Dbsc-Session-Id";

    private final DbscProofValidator proofValidator;
    private final DbscSessionService sessionService;
    private final DbscChallengeService challengeService;
    private final DbscSessionStore sessionStore;
    private final DbscProperties properties;

    public DbscRefreshController(DbscProofValidator proofValidator,
                                 DbscSessionService sessionService,
                                 DbscChallengeService challengeService,
                                 DbscSessionStore sessionStore,
                                 DbscProperties properties) {
        this.proofValidator = proofValidator;
        this.sessionService = sessionService;
        this.challengeService = challengeService;
        this.sessionStore = sessionStore;
        this.properties = properties;
    }

    @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonSessionInstructions> refresh(
            @RequestHeader(value = HEADER_SEC_SECURE_SESSION_ID, required = false) String sessionIdHeader,
            @RequestHeader(value = HEADER_X_DBSC_SESSION_ID, required = false) String xSessionIdHeader,
            @RequestHeader(value = HEADER_SECURE_SESSION_RESPONSE, required = false) String secureSessionResponse,
            HttpServletRequest request,
            HttpServletResponse response) {

        String sessionIdRaw = sessionIdHeader != null && !sessionIdHeader.isBlank() ? sessionIdHeader : xSessionIdHeader;
        if (sessionIdRaw == null || sessionIdRaw.isBlank() || secureSessionResponse == null || secureSessionResponse.isBlank()) {
            log.warn("Refresh: missing session id or Secure-Session-Response header");
            return ResponseEntity.status(407).build(); // 407 Proxy Auth Required or 400; spec says 407/429 for network/backoff
        }

        String sessionId = stripStructuredHeaderString(sessionIdRaw);
        String jwtString = stripStructuredHeaderString(secureSessionResponse);

        var sessionOpt = sessionStore.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("Refresh: session not found sessionId={}", sessionId);
            return ResponseEntity.status(403).build();
        }
        DbscSession session = sessionOpt.get();

        DbscProofValidator.RefreshProofResult proof;
        try {
            proof = proofValidator.validateRefreshProof(jwtString, session.getPublicKey());
        } catch (DbscProofValidator.DbscValidationException e) {
            log.warn("Refresh: invalid proof sessionId={} - {}", sessionId, e.getMessage());
            String newChallenge = challengeService.issueChallenge(session.getSessionId());
            response.setHeader("Secure-Session-Challenge", "\"" + newChallenge + "\";id=\"" + session.getSessionId() + "\"");
            return ResponseEntity.status(403).build();
        }

        if (!challengeService.isValidChallenge(sessionId, proof.jti())) {
            log.warn("Refresh: invalid or stale challenge sessionId={}", sessionId);
            String newChallenge = challengeService.issueChallenge(session.getSessionId());
            response.setHeader("Secure-Session-Challenge", "\"" + newChallenge + "\";id=\"" + session.getSessionId() + "\"");
            return ResponseEntity.status(403).build();
        }

        // Issue next challenge for subsequent refresh
        String nextChallenge = challengeService.issueChallenge(session.getSessionId());
        response.setHeader("Secure-Session-Challenge", "\"" + nextChallenge + "\";id=\"" + session.getSessionId() + "\"");

        // Extend server-side session validity (only real device can refresh; stolen cookie cannot)
        session.extendExpiry(Instant.now().plusSeconds(properties.getServerSessionTtlSeconds()));

        String cookieHeader = buildSessionCookie(request, session.getSessionId(), true);
        response.addHeader("Set-Cookie", cookieHeader);

        List<String> corsOrigins = buildCorsOriginsList();
        String origin = EffectiveScopeOriginResolver.resolve(request, properties.getScopeOrigin(), corsOrigins);
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

        log.info("Refresh: success sessionId={} expiry extended", session.getSessionId());
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

    private List<String> buildCorsOriginsList() {
        List<String> out = new ArrayList<>();
        if (properties.getCorsAllowedOrigins() != null) out.addAll(properties.getCorsAllowedOrigins());
        String single = properties.getCorsAllowOrigin();
        if (single != null && !single.isBlank() && !out.contains(single)) out.add(single);
        return out;
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
