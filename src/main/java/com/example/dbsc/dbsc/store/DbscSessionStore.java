package com.example.dbsc.dbsc.store;

import com.example.dbsc.dbsc.model.DbscSession;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for DBSC sessions. In production, replace with a persistent store (e.g. Redis/DB).
 */
@Component
public class DbscSessionStore {

    private final Map<String, DbscSession> sessionsById = new ConcurrentHashMap<>();

    public void save(DbscSession session) {
        sessionsById.put(session.getSessionId(), session);
    }

    public Optional<DbscSession> findBySessionId(String sessionId) {
        return Optional.ofNullable(sessionsById.get(sessionId));
    }

    public void remove(String sessionId) {
        sessionsById.remove(sessionId);
    }

    public boolean exists(String sessionId) {
        return sessionsById.containsKey(sessionId);
    }

    public int size() {
        return sessionsById.size();
    }
}
