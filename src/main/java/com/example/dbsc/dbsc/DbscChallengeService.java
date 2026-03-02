package com.example.dbsc.dbsc;

import com.example.dbsc.config.DbscProperties;
import com.example.dbsc.dbsc.model.DbscSession;
import com.example.dbsc.dbsc.store.DbscSessionStore;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class DbscChallengeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CHALLENGE_BYTES = 32;

    private final DbscProperties properties;
    private final DbscSessionStore sessionStore;

    public DbscChallengeService(DbscProperties properties, DbscSessionStore sessionStore) {
        this.properties = properties;
        this.sessionStore = sessionStore;
    }

    /**
     * Generates a new challenge and associates it with the session for later validation.
     */
    public String issueChallenge(String sessionId) {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(CHALLENGE_BYTES));
        Instant expiresAt = Instant.now().plusSeconds(properties.getChallengeTtlSeconds());
        sessionStore.findBySessionId(sessionId).ifPresent(session -> {
            session.addChallenge(challenge, expiresAt);
            session.pruneExpiredChallenges(Instant.now());
        });
        return challenge;
    }

    /**
     * Checks that the given jti is a valid (recent, non-expired) challenge for the session.
     */
    public boolean isValidChallenge(String sessionId, String jti) {
        return sessionStore.findBySessionId(sessionId)
                .map(s -> s.isValidChallenge(jti))
                .orElse(false);
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
