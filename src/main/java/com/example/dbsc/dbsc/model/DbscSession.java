package com.example.dbsc.dbsc.model;

import com.nimbusds.jose.jwk.JWK;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-side representation of a device-bound session per W3C DBSC spec.
 * Stores the public key, session id, recent challenges, and server-side expiry.
 * Session is invalid after expiresAt unless the client refreshes (proves key possession).
 */
public class DbscSession {

    private final String sessionId;
    private final JWK publicKey;
    private final String algorithm;
    private final Instant createdAt;
    private final String origin;
    private final List<ChallengeEntry> recentChallenges = new CopyOnWriteArrayList<>();
    private volatile Instant expiresAt;

    public DbscSession(String sessionId, JWK publicKey, String algorithm, String origin, Instant expiresAt) {
        this.sessionId = sessionId;
        this.publicKey = publicKey;
        this.algorithm = algorithm;
        this.origin = origin;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    /** Returns true if this session is still valid (not past server-side expiry). */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /** Extend server-side validity (call after successful refresh). */
    public void extendExpiry(Instant newExpiresAt) {
        this.expiresAt = newExpiresAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public JWK getPublicKey() {
        return publicKey;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getOrigin() {
        return origin;
    }

    public List<ChallengeEntry> getRecentChallenges() {
        return new ArrayList<>(recentChallenges);
    }

    public void addChallenge(String challenge, Instant expiresAt) {
        recentChallenges.add(new ChallengeEntry(challenge, expiresAt));
        while (recentChallenges.size() > 10) {
            recentChallenges.remove(0);
        }
    }

    public boolean isValidChallenge(String jti) {
        Instant now = Instant.now();
        return recentChallenges.stream()
                .anyMatch(e -> e.challenge.equals(jti) && e.expiresAt.isAfter(now));
    }

    public void pruneExpiredChallenges(Instant before) {
        recentChallenges.removeIf(e -> e.expiresAt.isBefore(before));
    }

    public record ChallengeEntry(String challenge, Instant expiresAt) {}
}
