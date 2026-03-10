# Design Document: DBSC-Secured Spring Boot API

---

## 1. Design

### 1.1 Status

| Item | Value |
|------|--------|
| Document title | DBSC-Secured Spring Boot API – Design |
| Status | Approved / In use |
| Owner | [Project owner] |

#### 1.1.1 Document Revision History

| Version | Date | Author | Summary of changes |
|---------|------|--------|--------------------|
| 0.1 | [Date] | [Author] | Initial design document |

---

### 1.2 Purpose

This document describes the design of a **Device Bound Session Credentials (DBSC)**–secured API implemented in Spring Boot. The purpose is to:

- **Mitigate session hijacking** by binding session credentials to the device that registered them. A stolen cookie alone cannot be used from another device because the server requires proof of possession of a client-held private key (signature over a challenge) for registration and refresh.
- **Align with the W3C DBSC draft** ([webappsec-dbsc](https://w3c.github.io/webappsec-dbsc/)) and Chrome’s integration guidance: registration endpoint, refresh endpoint, short-lived bound cookie, and JSON session instructions.
- **Support both** a browser-native DBSC path (when the UA sends `Sec-Secure-Session-Id` and performs registration/refresh) and a **JavaScript simulation** for demos and testing (using `X-Dbsc-Session-Id` because script cannot set `Sec-*` headers).

---

### 1.3 Functional Requirements

#### Core Requirements

- **Registration:** Server issues a one-time registration challenge (e.g. via `Secure-Session-Registration`). Client proves possession of a private key by signing a JWT whose `jti` is the challenge; server validates the proof, stores the public key, creates a session, and sets a short-lived bound cookie.
- **Refresh:** When the bound cookie is missing or expired, the client can call a refresh endpoint with the session id and a new proof (JWT signed with the same key over a recent challenge). Server validates and issues a new short-lived cookie; server-side session TTL is extended only on successful refresh.
- **Protected API:** Access to `/api/**` is allowed only when the request carries a valid DBSC session cookie that matches a non-expired session in the server store.
- **Well-known:** Optional `/.well-known/device-bound-sessions` returning `registering_origins`, `relying_origins`, or `provider_origin` per spec § 11.6.
- **Session start:** At least one way to obtain a registration challenge and path (e.g. `GET /dbsc/session/start` and/or `Secure-Session-Registration` on the main document) so clients (or the browser) can initiate registration.

#### Technical Requirements

- **Algorithms:** Support ES256 and RS256 for DBSC proof JWT (registration and refresh).
- **Challenge handling:** Registration challenges are one-time use and TTL-limited; refresh challenges are per-session, TTL-limited, and support a small number of recent challenges per session for race conditions.
- **Cookie:** Short-lived bound cookie (configurable `Max-Age`), HttpOnly, SameSite, and configurable attributes (e.g. Secure in production).
- **CORS:** Configurable allowed origins for DBSC endpoints (registration/refresh) so browser preflight and credentialed requests succeed from allowed front-end origins.
- **Security:** CSRF disabled for API; stateless session model; refresh endpoint protected with `X-Frame-Options` (and optionally narrow CORS / no embedding) per spec recommendations.

---

### 1.4 Design Proposal(s) Overview

#### 1.4.1 Proposal 1: Per-Request Signature Validation (POC-Based)

- **Idea:** Every protected request carries a signed proof (e.g. JWT) that the server validates using a stored public key. No session cookie; authentication is purely “signature valid for this key.”
- **Pros:** Simple mental model; no cookie lifecycle.
- **Cons:** Does not match the DBSC spec’s cookie-based model; no short-lived cookie or refresh flow; harder to align with browser-native DBSC (which uses cookies and refresh). Not chosen.

#### 1.4.2 Proposal 2: Session-Based DBSC with Short-Lived Cookie and Refresh (Spec-Aligned)

- **Idea:** Server maintains a session (session id, public key, algorithm, origin, server-side expiry, recent refresh challenges). Registration creates the session and sets a short-lived bound cookie. Refresh validates proof against the stored key and a recent challenge, extends server session expiry, and sets a new short-lived cookie. Protected API requires the bound cookie and a valid, non-expired session in the store.
- **Pros:** Aligns with W3C DBSC and Chrome integration guide; supports stolen-cookie mitigation (cookie alone is useless after TTL or without refresh proof); supports both native UA and JS simulation.
- **Cons:** Requires session store (in-memory for demo; Redis/DB for production) and challenge lifecycle.

---

### 1.5 Chosen Proposal

**Proposal 2: Session-Based DBSC with Short-Lived Cookie and Refresh.**

- Registration and refresh endpoints as in the spec; JSON session instructions and `Secure-Session-Challenge` for the next refresh.
- Short-lived bound cookie; server-side session TTL extended only on successful refresh.
- Optional `Secure-Session-Registration` (and long-lived placeholder cookie) on the main document to encourage browser-native registration where supported.

---

### 1.6 Design Deep Dive

#### 1.6.1 Service Level Contracts

| Component | Contract |
|-----------|----------|
| **Session start** | `GET /dbsc/session/start` → 200, `Secure-Session-Registration: (ES256);path="/dbsc/register";challenge="<base64url>"`. Challenge stored server-side with TTL (e.g. 300s). |
| **Registration** | `POST /dbsc/register` with header `Secure-Session-Response: <JWT>`. JWT: `typ=dbsc+jwt`, `alg=ES256|RS256`, `jwk` in header (registration only), `jti` = registration challenge. Server: validate JWT, consume challenge once, create session, return 200 + `Set-Cookie` (bound cookie) + `Secure-Session-Challenge` + JSON session instructions; or 400 if invalid/reused challenge. |
| **Refresh** | `POST /dbsc/refresh` with `Sec-Secure-Session-Id` or `X-Dbsc-Session-Id` (session id) and `Secure-Session-Response: <JWT>`. JWT signed with session’s public key, `jti` = recent refresh challenge (no `jwk` in header). Server: validate signature and challenge, extend session expiry, return 200 + `Set-Cookie` + `Secure-Session-Challenge` + JSON; or 403 + optional new challenge on failure. |
| **Protected API** | `GET /api/**`: requires cookie matching a valid, non-expired session in store; otherwise 401. |
| **Well-known** | `GET /.well-known/device-bound-sessions` → 200, JSON with optional `registering_origins`, `relying_origins`, `provider_origin` from config. |

#### 1.6.2 Data Model

- **DbscSession:** sessionId (String), publicKey (JWK), algorithm (String), origin (String), createdAt (Instant), expiresAt (Instant), recentChallenges (list of ChallengeEntry with challenge + expiresAt). Methods: isExpired(), extendExpiry(), addChallenge(), isValidChallenge(), pruneExpiredChallenges().
- **ChallengeEntry:** challenge (String), expiresAt (Instant).
- **Registration challenge store:** Map of one-time registration challenges (challenge → expiresAt); consume-on-use and TTL cleanup.
- **Session store:** Keyed by sessionId; in-memory `ConcurrentHashMap` for the current implementation. Production: replace with persistent store (e.g. Redis or DB) keyed by session id.
- **JSON session instructions (outbound):** session_identifier, refresh_url, scope (origin, include_site, scope_specification), credentials (type, name, attributes), allowed_refresh_initiators.

#### 1.6.3 Data Migration

- **Current:** In-memory stores only; no persistent data. No migration required for the initial implementation.
- **Future:** If the session store is moved to Redis or a DB, a one-time migration or dual-write strategy would be needed only if existing production sessions must be preserved; otherwise, new deployments can start with an empty store.

#### 1.6.4 Caching

- **Sessions:** Lookup by session id is the hot path; in-memory store is effectively the cache. For a Redis/DB-backed store, application-level or Redis caching of the session object can be used to reduce latency.
- **Registration challenges:** Short-lived, one-time use; no long-term caching beyond the in-memory map with TTL cleanup.
- **Refresh challenges:** Held per session with a small cap (e.g. 10) and TTL; no separate cache layer in the current design.

#### 1.6.5 Messaging

- Not applicable. The system is synchronous request/response (HTTP); no message queue or async messaging.

#### 1.6.6 Metrics and Alerts

- **Application:** Registration success/failure counts, refresh success/failure counts, protected-API 401 rate. Optional: latency percentiles for registration, refresh, and protected API.
- **Infrastructure:** CPU, memory, request rate, error rate (e.g. 4xx/5xx) for the service.
- **Alerts:** Optional: spike in 401s on `/api/**`, high refresh failure rate, or session store errors (when persistent store is used).

#### 1.6.7 Backward Compatibility

- Greenfield API; no prior production API contract. Backward compatibility considerations apply when changing JSON session instructions or adding/removing headers; the W3C spec and Chrome integration guide should be followed for any changes.

#### 1.6.8 Security Checks

- **Proof validation:** Registration and refresh JWTs are cryptographically verified (signature and algorithm). Registration requires `jwk` in header and `jti` equal to a one-time, non-expired registration challenge. Refresh requires signature verification with the stored public key and `jti` equal to a recent, non-expired refresh challenge for that session.
- **Cookie:** Bound cookie is HttpOnly, SameSite=Lax, and configurable Secure in production; short Max-Age to limit usefulness of a stolen cookie.
- **Session expiry:** Server-side session has a TTL; it is not extended unless the client successfully refreshes (proves key possession). Expired sessions are removed from the store and no longer accepted.
- **CORS:** Allowed origins for DBSC endpoints are configurable (and default to localhost for dev); production should restrict to known front-end origins.
- **Refresh endpoint:** `X-Frame-Options: DENY` (and optionally strict CORS / no embedding) to reduce abuse and timing side channels per spec.
- **No CSRF** on API (stateless, cookie used for session binding only; CSRF disabled in Spring Security for this design).

---

### 1.7 Milestones

| Milestone | Description |
|-----------|-------------|
| M1 | Session start + registration endpoint + in-memory session and challenge stores |
| M2 | Refresh endpoint + challenge issuance and validation |
| M3 | Protected API + auth filter (cookie → session lookup, expiry check) |
| M4 | JSON session instructions + well-known + CORS and config (cookie name, TTLs, origins) |
| M5 | Test page (Start session, Register, Call API, Refresh) with JS simulation and optional auto-refresh on 401 |
| M6 | Document response sends `Secure-Session-Registration` (+ long-lived cookie) for browser-native testing |
| M7 | Logging and access logs; production-ready config (e.g. persistent session store, metrics) as needed |

---

### 1.8 Test Scenarios

| Scenario | Steps | Expected |
|----------|--------|----------|
| Happy path: register → API → refresh → API | Start session → Register → GET /api/secure → wait for cookie expiry or trigger refresh → GET /api/secure | 200 on API before and after refresh; new cookie after refresh |
| Invalid registration proof | POST /dbsc/register with bad or reused challenge / wrong signature | 400 |
| Refresh with wrong key | POST /dbsc/refresh with valid session id but JWT signed by different key | 403; optional new challenge in response |
| Stolen cookie | Use cookie in another browser; after server TTL or cookie expiry, call API or refresh without the original device key | 401 on API; refresh fails (403) from the other browser; original device can still refresh with correct key |
| No cookie / expired session | GET /api/secure without cookie or with cookie for expired/removed session | 401 |
| CORS preflight | OPTIONS to /dbsc/register from allowed origin | 200 with CORS headers; subsequent POST succeeds |
| Well-known | GET /.well-known/device-bound-sessions | 200; JSON with configured origins if set |

---

### 1.9 Production Readiness

#### 1.9.1 DevOps

- **Build:** Maven; `mvn spring-boot:run` or packaged JAR for deployment.
- **Config:** Externalized via `application.yml` and `dbsc.*` properties (and env such as `DBSC_CORS_ALLOW_ORIGIN`). Production: override cookie attributes (e.g. Secure), CORS origins, TTLs, and session store (e.g. Redis).
- **Logs:** Console and optional file (e.g. `logs/application.log`); Tomcat access log optional under `logs/` with rotation.
- **Deployment:** Stateless app; horizontal scaling supported if session store is shared (e.g. Redis). No sticky session required when store is external.

#### 1.9.2 Testing

- **Manual:** Test page at `/` for full flow (start, register, API, refresh). DevTools to verify headers (`Secure-Session-Registration`, `Secure-Session-Response`, `X-Dbsc-Session-Id` or `Sec-Secure-Session-Id`, `Set-Cookie`).
- **Automated:** Unit tests for proof validation, challenge consume/expiry, session expiry; integration tests for registration and refresh endpoints and protected API (with/without valid cookie) as needed.

#### 1.9.3 Metrics and Monitors

- **Metrics:** Registration/refresh success and failure counts; protected API 401 rate; optional latency histograms for registration, refresh, and `/api/**`.
- **Monitors:** Health endpoint (e.g. Spring Boot actuator); optional alerts on error rate or session store failures when using a persistent store.

#### 1.9.4 Deployment Criteria

- **Functional:** Registration, refresh, and protected API behave per spec and test scenarios; CORS and cookie attributes correct for the deployment origin (e.g. HTTPS, Secure cookie).
- **Operational:** Logging and access logs enabled; config (CORS, TTLs, cookie) reviewed for production; session store persistence and backup/restore considered if using Redis/DB.
- **Security:** Proof validation and session expiry verified; CORS restricted to intended origins; refresh endpoint not embeddable and narrow CORS where applicable.

---

### 2.0 Reference Documents (Optional)

| Document | Link / location |
|----------|------------------|
| W3C DBSC (draft) | https://w3c.github.io/webappsec-dbsc/ |
| Chrome DBSC integration guide | https://developer.chrome.com/docs/web-platform/device-bound-session-credentials |
| Chrome DBSC origin trial | https://developer.chrome.com/blog/dbsc-origin-trial |
| RFC 7515 (JWS) | https://www.rfc-editor.org/rfc/rfc7515 |
| RFC 7519 (JWT) | https://www.rfc-editor.org/rfc/rfc7519 |
| RFC 7638 (JWK thumbprint) | https://www.rfc-editor.org/rfc/rfc7638 |
| Project README | [README.md](../README.md) |
| Flow diagrams | [docs/FLOWS.md](FLOWS.md) |
| Proof (native vs JS) | [docs/PROOF-DBSC-NOT-NATIVE.md](PROOF-DBSC-NOT-NATIVE.md) |

---

### 1.10 Design Review Meeting Minutes

*To be captured when a formal design review is held.*
