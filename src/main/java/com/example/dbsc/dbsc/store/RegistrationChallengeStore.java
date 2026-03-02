package com.example.dbsc.dbsc.store;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores one-time registration challenges issued when sending Secure-Session-Registration.
 * Per spec, the client sends back the challenge as jti in the DBSC proof; we validate and consume it.
 */
@Component
public class RegistrationChallengeStore {

    private final Map<String, Instant> challenges = new ConcurrentHashMap<>();

    public void put(String challenge, Instant expiresAt) {
        challenges.put(challenge, expiresAt);
    }

    public boolean consume(String challenge) {
        Instant expiresAt = challenges.remove(challenge);
        return expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    public void removeExpired() {
        Instant now = Instant.now();
        challenges.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
