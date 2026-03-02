package com.example.dbsc.web;

import com.example.dbsc.config.DbscProperties;
import com.example.dbsc.dbsc.store.DbscSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Debug endpoint to see what cookie the server receives and whether the session exists.
 * GET /dbsc/debug — call from the same browser tab after registering to verify cookie is sent.
 */
@RestController
@RequestMapping("/dbsc")
public class DbscDebugController {

    private static final Logger log = LoggerFactory.getLogger(DbscDebugController.class);
    private final DbscSessionStore sessionStore;
    private final String cookieName;

    public DbscDebugController(DbscSessionStore sessionStore, DbscProperties properties) {
        this.sessionStore = sessionStore;
        this.cookieName = properties.getCredentialCookieName();
    }

    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debug(HttpServletRequest request) {
        Map<String, Object> out = new HashMap<>();
        String sessionId = null;
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (cookieName.equals(c.getName())) {
                    sessionId = c.getValue();
                    break;
                }
            }
        }
        out.put("cookieName", cookieName);
        out.put("dbscCookieSent", sessionId != null);
        out.put("sessionId", sessionId != null ? sessionId : "(none)");
        boolean sessionFound = sessionId != null && sessionStore.findBySessionId(sessionId).isPresent();
        out.put("sessionFound", sessionFound);
        out.put("storeSize", sessionStore.size());
        log.debug("Debug: dbscCookieSent={} sessionFound={} storeSize={}", sessionId != null, sessionFound, sessionStore.size());
        return ResponseEntity.ok(out);
    }
}
