package com.example.dbsc.security;

import com.example.dbsc.config.DbscProperties;
import com.example.dbsc.dbsc.model.DbscSession;
import com.example.dbsc.dbsc.store.DbscSessionStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests by validating the DBSC bound session cookie against the session store.
 */
public class DbscSessionAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DbscSessionAuthenticationFilter.class);

    private final DbscSessionStore sessionStore;
    private final String cookieName;

    public DbscSessionAuthenticationFilter(DbscSessionStore sessionStore, DbscProperties properties) {
        this.sessionStore = sessionStore;
        this.cookieName = properties.getCredentialCookieName();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String sessionId = getSessionIdFromCookie(request);
        var sessionOpt = sessionId != null ? sessionStore.findBySessionId(sessionId) : java.util.Optional.<DbscSession>empty();
        if (sessionOpt.isPresent() && sessionOpt.get().isExpired()) {
            sessionStore.remove(sessionId);
            sessionOpt = java.util.Optional.empty();
        }
        boolean sessionFound = sessionOpt.isPresent();
        if (request.getRequestURI() != null && request.getRequestURI().startsWith("/api/")) {
            log.info("DBSC filter /api request: cookiePresent={}, sessionFound={}, storeSize={}",
                    sessionId != null, sessionFound, sessionStore.size());
        }
        if (sessionFound) {
            var auth = new DbscSessionAuthentication(sessionId, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } else {
            SecurityContextHolder.getContext().setAuthentication(
                    new AnonymousAuthenticationToken("key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        }
        filterChain.doFilter(request, response);
    }

    private String getSessionIdFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (cookieName.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }
}
