package com.example.dbsc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "dbsc")
public class DbscProperties {

    private String credentialCookieName = "dbsc_session";
    private String credentialCookieAttributes = "Path=/; HttpOnly; SameSite=Lax";
    private int challengeTtlSeconds = 300;
    private int maxChallengesPerSession = 5;
    private int sessionCookieMaxAge = 30;
    /** Server-side session TTL in seconds. Session is invalid after this unless client refreshes (proves key). Stolen cookies become useless after this. */
    private int serverSessionTtlSeconds = 120;
    private List<String> registeringOrigins = new ArrayList<>();
    private List<String> relyingOrigins = new ArrayList<>();
    private String providerOrigin;
    private List<String> allowedRefreshInitiators = new ArrayList<>();

    public String getCredentialCookieName() {
        return credentialCookieName;
    }

    public void setCredentialCookieName(String credentialCookieName) {
        this.credentialCookieName = credentialCookieName;
    }

    public String getCredentialCookieAttributes() {
        return credentialCookieAttributes;
    }

    public void setCredentialCookieAttributes(String credentialCookieAttributes) {
        this.credentialCookieAttributes = credentialCookieAttributes;
    }

    public int getChallengeTtlSeconds() {
        return challengeTtlSeconds;
    }

    public void setChallengeTtlSeconds(int challengeTtlSeconds) {
        this.challengeTtlSeconds = challengeTtlSeconds;
    }

    public int getMaxChallengesPerSession() {
        return maxChallengesPerSession;
    }

    public void setMaxChallengesPerSession(int maxChallengesPerSession) {
        this.maxChallengesPerSession = maxChallengesPerSession;
    }

    public int getSessionCookieMaxAge() {
        return sessionCookieMaxAge;
    }

    public void setSessionCookieMaxAge(int sessionCookieMaxAge) {
        this.sessionCookieMaxAge = sessionCookieMaxAge;
    }

    public int getServerSessionTtlSeconds() {
        return serverSessionTtlSeconds;
    }

    public void setServerSessionTtlSeconds(int serverSessionTtlSeconds) {
        this.serverSessionTtlSeconds = serverSessionTtlSeconds;
    }

    public List<String> getRegisteringOrigins() {
        return registeringOrigins;
    }

    public void setRegisteringOrigins(List<String> registeringOrigins) {
        this.registeringOrigins = registeringOrigins;
    }

    public List<String> getRelyingOrigins() {
        return relyingOrigins;
    }

    public void setRelyingOrigins(List<String> relyingOrigins) {
        this.relyingOrigins = relyingOrigins;
    }

    public String getProviderOrigin() {
        return providerOrigin;
    }

    public void setProviderOrigin(String providerOrigin) {
        this.providerOrigin = providerOrigin;
    }

    public List<String> getAllowedRefreshInitiators() {
        return allowedRefreshInitiators;
    }

    public void setAllowedRefreshInitiators(List<String> allowedRefreshInitiators) {
        this.allowedRefreshInitiators = allowedRefreshInitiators;
    }
}
