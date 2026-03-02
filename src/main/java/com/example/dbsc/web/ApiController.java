package com.example.dbsc.web;

import com.example.dbsc.dbsc.store.DbscSessionStore;
import com.example.dbsc.security.DbscSessionAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Protected API secured by DBSC. Only requests with a valid bound session cookie (and thus
 * a session that was created via DBSC registration and refreshed via DBSC refresh) are allowed.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);
    private final DbscSessionStore sessionStore;

    public ApiController(DbscSessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    @GetMapping("/secure")
    public ResponseEntity<Map<String, Object>> secure(Authentication authentication) {
        if (!(authentication instanceof DbscSessionAuthentication auth)) {
            log.debug("Protected API: unauthenticated (no valid DBSC session)");
            return ResponseEntity.status(401).build();
        }
        String sessionId = auth.getSessionId();
        log.info("Protected API: request for sessionId={}", sessionId);
        return sessionStore.findBySessionId(sessionId)
                .map(session -> {
                    Map<String, Object> body = Map.of(
                            "message", "DBSC-protected resource",
                            "session_id", sessionId,
                            "authenticated", true
                    );
                    log.debug("Protected API: returning 200 for sessionId={}", sessionId);
                    return ResponseEntity.<Map<String, Object>>ok(body);
                })
                .orElseGet(() -> {
                    log.warn("Protected API: session not in store sessionId={}", sessionId);
                    return ResponseEntity.status(401).build();
                });
    }
}
