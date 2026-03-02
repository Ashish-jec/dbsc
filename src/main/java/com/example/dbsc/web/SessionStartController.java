package com.example.dbsc.web;

import com.example.dbsc.dbsc.store.RegistrationChallengeStore;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Initiates DBSC session registration. Returns Secure-Session-Registration header
 * with path to registration endpoint and a one-time challenge. The client will POST
 * to the registration endpoint with a signed DBSC proof (jti = challenge).
 */
@RestController
@RequestMapping("/dbsc")
public class SessionStartController {

    private static final Logger log = LoggerFactory.getLogger(SessionStartController.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REGISTRATION_CHALLENGE_TTL_SECONDS = 300;
    private static final int CHALLENGE_BYTES = 32;

    private final RegistrationChallengeStore registrationChallengeStore;

    public SessionStartController(RegistrationChallengeStore registrationChallengeStore) {
        this.registrationChallengeStore = registrationChallengeStore;
    }

    @GetMapping("/session/start")
    public ResponseEntity<Void> startSession(HttpServletResponse response) {
        String challenge = generateRegistrationChallenge();
        Instant expiresAt = Instant.now().plusSeconds(REGISTRATION_CHALLENGE_TTL_SECONDS);
        registrationChallengeStore.put(challenge, expiresAt);

        // Per spec: path may be relative to current URL. (ES256);path="...";challenge="..."
        String headerValue = "(ES256);path=\"/dbsc/register\";challenge=\"" + challenge + "\"";
        response.setHeader("Secure-Session-Registration", headerValue);

        log.info("Session start: issued registration challenge (expires in {}s)", REGISTRATION_CHALLENGE_TTL_SECONDS);
        return ResponseEntity.ok().build();
    }

    private String generateRegistrationChallenge() {
        byte[] bytes = new byte[CHALLENGE_BYTES];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
