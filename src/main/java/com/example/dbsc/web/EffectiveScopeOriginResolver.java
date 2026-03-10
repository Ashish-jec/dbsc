package com.example.dbsc.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * Resolves the origin to use for DBSC session scope and refresh URL base.
 * Ensures same-site and secure context: when the client is on HTTPS, we return
 * an HTTPS origin so scope and registration/refresh URLs match (required by Chrome DBSC).
 */
public final class EffectiveScopeOriginResolver {

    private static final String HEADER_ORIGIN = "Origin";
    private static final String HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto";
    private static final String HEADER_X_FORWARDED_HOST = "X-Forwarded-Host";
    private static final String HEADER_X_FORWARDED_PORT = "X-Forwarded-Port";

    private EffectiveScopeOriginResolver() {}

    /**
     * Resolves the effective origin for session scope and base URL (e.g. refresh_url).
     * Prefer: (1) configured scope origin, (2) Origin request header, (3) X-Forwarded-Proto https,
     * (4) request scheme/host/port. When (4) would be http, if corsOrigins contains any https
     * origin (e.g. when behind a proxy that strips headers), use the first https one so DBSC gets HTTPS.
     *
     * @param request the current request
     * @param configuredScopeOrigin optional config (dbsc.scope-origin); when set, used as-is
     * @param corsOrigins optional list from dbsc.cors-allowed-origins / cors-allow-origin; used as fallback when request is http
     * @return origin without trailing slash (e.g. https://app.example.com)
     */
    public static String resolve(HttpServletRequest request, String configuredScopeOrigin, List<String> corsOrigins) {
        if (configuredScopeOrigin != null && !configuredScopeOrigin.isBlank()) {
            return normalize(configuredScopeOrigin);
        }
        String origin = request.getHeader(HEADER_ORIGIN);
        if (origin != null && !origin.isBlank()) {
            return normalize(origin);
        }
        String forwardedProto = request.getHeader(HEADER_X_FORWARDED_PROTO);
        if ("https".equalsIgnoreCase(forwardedProto != null ? forwardedProto.trim() : null)) {
            String host = request.getHeader(HEADER_X_FORWARDED_HOST);
            if (host == null || host.isBlank()) {
                host = request.getServerName();
            } else {
                host = host.trim();
            }
            if (host != null && !host.isBlank()) {
                int port = 443;
                String portHeader = request.getHeader(HEADER_X_FORWARDED_PORT);
                if (portHeader != null && !portHeader.isBlank()) {
                    try {
                        port = Integer.parseInt(portHeader.trim());
                    } catch (NumberFormatException ignored) {}
                }
                return port == 443 ? "https://" + host : "https://" + host + ":" + port;
            }
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String requestOrigin;
        if (host == null || host.isBlank()) {
            requestOrigin = scheme + "://localhost";
        } else if ("https".equals(scheme) && port == 443 || "http".equals(scheme) && port == 80) {
            requestOrigin = scheme + "://" + host;
        } else {
            requestOrigin = scheme + "://" + host + ":" + port;
        }
        if (requestOrigin.startsWith("http://") && corsOrigins != null && !corsOrigins.isEmpty()) {
            for (String o : corsOrigins) {
                if (o != null && o.trim().toLowerCase().startsWith("https://")) {
                    return normalize(o);
                }
            }
        }
        return requestOrigin;
    }

    private static String normalize(String origin) {
        if (origin == null) return "https://localhost";
        return origin.trim().replaceAll("/$", "");
    }
}
