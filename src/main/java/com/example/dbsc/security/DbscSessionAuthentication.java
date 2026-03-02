package com.example.dbsc.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class DbscSessionAuthentication extends AbstractAuthenticationToken {

    private final String sessionId;

    public DbscSessionAuthentication(String sessionId, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.sessionId = sessionId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return sessionId;
    }

    public String getSessionId() {
        return sessionId;
    }
}
