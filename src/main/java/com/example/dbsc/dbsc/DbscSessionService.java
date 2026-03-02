package com.example.dbsc.dbsc;

import com.example.dbsc.config.DbscProperties;
import com.example.dbsc.dbsc.model.DbscSession;
import com.example.dbsc.dbsc.store.DbscSessionStore;
import com.nimbusds.jose.jwk.JWK;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class DbscSessionService {

    private final DbscProperties properties;
    private final DbscSessionStore sessionStore;

    public DbscSessionService(DbscProperties properties, DbscSessionStore sessionStore) {
        this.properties = properties;
        this.sessionStore = sessionStore;
    }

    public DbscSession createSession(JWK publicKey, String algorithm, String origin) {
        String sessionId = "dbsc-" + UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(properties.getServerSessionTtlSeconds());
        DbscSession session = new DbscSession(sessionId, publicKey, algorithm, origin, expiresAt);
        sessionStore.save(session);
        return session;
    }

    public void terminateSession(String sessionId) {
        sessionStore.remove(sessionId);
    }

    public String getCredentialCookieName() {
        return properties.getCredentialCookieName();
    }

    public String getCredentialCookieAttributes() {
        return properties.getCredentialCookieAttributes();
    }

    public int getSessionCookieMaxAge() {
        return properties.getSessionCookieMaxAge();
    }
}
