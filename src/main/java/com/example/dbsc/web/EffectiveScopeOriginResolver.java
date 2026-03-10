package com.example.dbsc.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the origin to use for DBSC session scope and refresh URL base.
 * Ensures same-site and secure context: when the client is on HTTPS (e.g. via
 * Origin header or X-Forwarded-Proto), we return an HTTPS origin so scope and
 * registration/refresh URLs match the client's origin (required by Chrome DBSC).
 */
public final class EffectiveScopeOriginResolver {

    private static final String HEADER_ORIGIN = "Origin";
    private static final String HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto";
    private static final String HEADER_X_FORWARDED_HOST = "X-Forwarded-Host";
    private static final String HEADER_X_FORWARDED_PORT = "X-Forwarded-Port";

    private EffectiveScopeOriginResolver() {}

    /**
     * Resolves the effective origin for session scope and base URL (e.g. refresh_url).
     * Prefer: (1) configured scope origin, (2) Origin request header (browser's real origin),
     * (3) X-Forwarded-Proto when https, (4) request scheme/host/port.
     *
     * @param request the current request
     * @param configuredScopeOrigin optional config (dbsc.scope-origin); when set, used as-is
     * @return origin without trailing slash (e.g. https://app.example.com)
     */
    public static String resolve(HttpServletRequest request, String configuredScopeOrigin) {
        if (configuredScopeOrigin != null && !configuredScopeOrigin.isBlank()) {
            return configuredScopeOrigin.trim().replaceAll("/$", "");
        }
        String origin = request.getHeader(HEADER_ORIGIN);
        if (origin != null && !origin.isBlank()) {
            return origin.trim().replaceAll("/$", "");
        }
        String forwardedProto = request.getHeader(HEADER_X_FORWARDED_PROTO);
        if ("https".equalsIgnoreCase(forwardedProto != null ? forwardedProto.trim() : null)) {
            String host = request.getHeader(HEADER_X_FORWARDED_HOST);
            if (host == null || host.isBlank()) {
                host = request.getServerName();
            } else {
                host = host.trim();
            }
            int port = 443;
            String portHeader = request.getHeader(HEADER_X_FORWARDED_PORT);
            if (portHeader != null && !portHeader.isBlank()) {
                try {
                    port = Integer.parseInt(portHeader.trim());
                } catch (NumberFormatException ignored) {}
            }
            return port == 443 ? "https://" + host : "https://" + host + ":" + port;
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        if ("https".equals(scheme) && port == 443 || "http".equals(scheme) && port == 80) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }
}
