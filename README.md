# DBSC-Secured Spring Boot API

This project implements **Device Bound Session Credentials (DBSC)** as specified by the [W3C WebAppSec DBSC draft](https://w3c.github.io/webappsec-dbsc/). It provides a Spring Boot API secured by DBSC, with both **registration** and **refresh** endpoints configured.

## Overview

DBSC allows a server to verify that a session credential has not been exported from the device. The client holds a private key (e.g. TPM-protected) and proves possession by signing challenges; the server stores the public key and validates signatures on registration and refresh.

### Implemented components

| Component | Spec section | Purpose |
|-----------|--------------|---------|
| **Registration endpoint** | § 5, § 9.1 | Receives `Secure-Session-Response` (DBSC proof JWT), validates signature and challenge, creates session, returns JSON session instructions and sets bound cookie |
| **Refresh endpoint** | § 5 | Receives `Sec-Secure-Session-Id` + `Secure-Session-Response`, validates proof against stored key and recent challenge, issues new bound cookie and optional session config |
| **Session start** | § 9.1 | Returns `Secure-Session-Registration` with path and challenge so the client can POST to the registration endpoint |
| **Well-known** | § 11.6 | `/.well-known/device-bound-sessions` for `registering_origins`, `relying_origins`, or `provider_origin` |
| **Protected API** | — | `/api/**` requires a valid DBSC session (bound cookie present and session in store) |

### Who generates the key pair? Where is the private key stored?

- **Key pair:** The **client (browser)** generates it—never the server. The server only ever receives and stores the **public key** (inside the JWT header at registration) and uses it to verify signatures. The **private key never leaves the device**.
- **In this project’s test page:** The key pair is created in **JavaScript** on the test page when you click “Register”, using the [jose](https://github.com/panva/jose) library and the Web Crypto API (`generateKeyPair('ES256')`). So it’s “the browser” in the sense of client-side code, but it’s **page script**, not a built-in browser DBSC API.
- **Where the private key is stored (test page):** Only in **JavaScript memory**—in the page’s `state.keyPair.privateKey` (a `CryptoKey`). It is **not** written to disk, TPM, or any persistent store. If you refresh the page or close the tab, the key is gone and you must “Start session” and “Register” again. That’s why this is a **simulation**: a real DBSC implementation would store the key in secure, device-bound storage (e.g. TPM) managed by the browser.

### Spec adherence and browser behaviour (incl. Chrome 145)

The **server** follows the [W3C DBSC spec](https://w3c.github.io/webappsec-dbsc/) for registration, refresh, headers, and JSON session instructions.

**Chrome and DBSC:** Chrome is experimenting with DBSC via **origin trials** (e.g. from Chrome 135+, with a second trial in 2025–2026). In a build with DBSC enabled for your origin, Chrome would:

- Generate and store the key pair itself (e.g. TPM-backed on supported devices).
- Set `Sec-Secure-Session-Id` and related headers automatically.
- Refresh sessions automatically when the bound cookie is missing or expired.

**Testing native DBSC on Mac / Windows / Linux (Chrome team guidance):** To have the **browser** respond to `Secure-Session-Registration` (so you can test the native flow without our JS simulation), enable:

- **`chrome://flags#enable-bound-session-credentials-software-keys-for-manual-testing`**

With this flag, Chrome will perform registration and refresh using **software-backed keys** (no hardware protection). It is for manual testing only and does not provide the security benefit of TPM/Secure Enclave. **Origin trial enrollment is not required** for this testing (per Chrome team). For **production**: today only **Windows devices with a TPM** get hardware-protected keys; **Mac** support (Secure Enclave) is expected to follow within a few months.

**This project’s test page does not use Chrome’s native DBSC by default.** It uses a **JavaScript simulation** (Web Crypto + jose) so that:

- It works in **any** browser and version (no flag required).
- You can run and test the server and flows everywhere.

With the software-keys flag enabled, Chrome may use its **native** DBSC path (browser handles registration/refresh and sets `Sec-Secure-Session-Id`). If so, the browser will respond to `Secure-Session-Registration` automatically; you may not need to click “Register” on our test page.

Because the Fetch API **forbids** setting request headers whose names start with `Sec-` (see [MDN](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API/Basic_concepts)), the test page **cannot** send `Sec-Secure-Session-Id` from JavaScript. The refresh endpoint therefore also accepts **`X-Dbsc-Session-Id`** for the demo. Real DBSC user agents (e.g. Chrome with native DBSC) would send `Sec-Secure-Session-Id`.

**Why do I still see `X-Dbsc-Session-Id` after enabling the Chrome flag?** Chrome only sets `Sec-Secure-Session-Id` for sessions **it** registered. If you clicked “Register” on the test page, our JavaScript did the registration (key in page memory), so the session is “JS‑owned.” Refresh is then done by our script, which sends `X-Dbsc-Session-Id`. To try **native** behavior and see `Sec-Secure-Session-Id` on refresh:

1. **Do not click “Register”.** The server sends `Secure-Session-Registration` and a long-lived cookie on the **main document** (GET `/` or `/index.html`) so Chrome sees a “post-login” style response (as in Chrome’s integration guide).
2. Clear site data (cookies) for localhost, then reload **http://localhost:8080/** with the software-keys flag enabled.
3. In DevTools → Network, watch for an **automatic** `POST` to `/dbsc/register` from the browser. If Chrome sends it, the session is Chrome‑owned and refresh should show `Sec-Secure-Session-Id`.
4. **If you still don’t see the automatic POST:** Chrome’s automatic registration may not be triggered on all builds or platforms (e.g. Mac with software keys only). The server and JS flow are correct; you can keep using the “Register” button to test the API, or ask the Chrome team for the exact conditions under which the browser triggers registration. The JS simulation remains a reliable way to test the DBSC server.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/`, `/index.html` | Test page; response includes `Secure-Session-Registration` so Chrome can auto-register on load |
| GET | `/dbsc/session/start` | Start DBSC flow: response includes `Secure-Session-Registration` with challenge |
| POST | `/dbsc/register` | Register device-bound session (body not used; `Secure-Session-Response` and optional `Sec-Secure-Session-Id` in headers) |
| POST | `/dbsc/refresh` | Refresh session (headers: `Sec-Secure-Session-Id` or `X-Dbsc-Session-Id`, `Secure-Session-Response`) |
| GET | `/.well-known/device-bound-sessions` | Well-known JSON for DBSC (optional config) |
| GET | `/api/secure` | Protected resource; requires valid DBSC session cookie |

## Flow

1. **Start session**  
   Client: `GET /dbsc/session/start`  
   Server: responds with `Secure-Session-Registration: (ES256);path="/dbsc/register";challenge="<challenge>"` and stores the challenge.

2. **Register**  
   Client: generates key pair, builds DBSC proof JWT (header: `typ=dbsc+jwt`, `alg=ES256`, `jwk`; payload: `jti=<challenge>`), signs it.  
   Client: `POST /dbsc/register` with header `Secure-Session-Response: "<jwt>"`.  
   Server: validates JWT and that `jti` is the issued registration challenge, creates session, returns JSON session instructions and `Set-Cookie` for the bound cookie; optionally `Secure-Session-Challenge` for the next refresh.

3. **Refresh** (when bound cookie is missing or expired)  
   Client: `POST /dbsc/refresh` with `Sec-Secure-Session-Id: "<session-id>"` and `Secure-Session-Response: "<jwt>"` (JWT without `jwk`, signed with session key; `jti` = cached challenge).  
   Server: validates signature with stored public key and that `jti` is a recent challenge; returns new `Set-Cookie` and optional session config; may send `Secure-Session-Challenge` for the next refresh.

4. **Access protected API**  
   Client: `GET /api/secure` with cookie from step 2 or 3.  
   Server: allows access only if cookie matches a session in the store.

## Configuration

See `src/main/resources/application.yml`:

```yaml
dbsc:
  credential-cookie-name: dbsc_session
  credential-cookie-attributes: "Path=/; Secure; HttpOnly; SameSite=Lax"
  challenge-ttl-seconds: 300
  max-challenges-per-session: 5
  session-cookie-max-age: 86400
  # Optional: for well-known and session instructions
  registering-origins: []   # e.g. ["https://subdomain.example.com"]
  relying-origins: []       # e.g. ["https://example.co.uk"]
  provider-origin:          # when this origin is an RP
  allowed-refresh-initiators: []  # hosts allowed to trigger refresh
```

## Logging and access logs

All HTTP requests are logged so you can see every call to the server:

- **Console:** Each request is logged as `METHOD URI STATUS DURATIONms` by the `ACCESS` logger, e.g.  
  `GET /api/secure 200 12ms`. Application logs (registration, refresh, auth, etc.) use `com.example.dbsc` loggers.
- **Tomcat access log (file):** Optional file log is written under `logs/` (see `server.tomcat.accesslog` in `application.yml`). Enable or disable there; by default `enabled: true` with pattern including method, URI, status, duration, User-Agent.
- **Application log file:** Optional `logs/application.log` when `logging.file.name` is set in `application.yml`.

To see more detail (e.g. validation failures), set `logging.level.com.example.dbsc: DEBUG` in `application.yml`.

## Build and run

```bash
mvn spring-boot:run
```

- **Test page**: [http://localhost:8080/](http://localhost:8080/) or [http://localhost:8080/index.html](http://localhost:8080/index.html) — HTML UI to run Start session → Register → Call protected API → Refresh.
- Session start: `http://localhost:8080/dbsc/session/start`  
- Well-known: `http://localhost:8080/.well-known/device-bound-sessions`  
- Protected API: `http://localhost:8080/api/secure` (requires valid DBSC session cookie)

## Implementation notes

- **Session store**: In-memory (`DbscSessionStore`). For production, use a persistent store (e.g. Redis or DB) keyed by session id.
- **Challenges**: Registration challenges are one-time and TTL-limited; refresh challenges are kept per session with a short TTL and pruned on use.
- **Algorithms**: Registration and refresh support **ES256** and **RS256** (DBSC proof JWT). The registration response advertises ES256 in `Secure-Session-Registration`.
- **Headers**: `Secure-Session-Response` and `Sec-Secure-Session-Id` may be sent as structured header strings (e.g. quoted); the server strips surrounding quotes when parsing.
- **CORS / embedding**: Per the spec, the refresh endpoint should use a narrow CORS policy and avoid being embeddable (e.g. `X-Frame-Options`, `Cross-Origin-Resource-Policy`). Configure these in your deployment or security config as needed.

## Flow diagrams

See **[docs/FLOWS.md](docs/FLOWS.md)** for Mermaid flow diagrams of all flows: session start, registration, refresh, protected API auth, test page auto-refresh, well-known, filter chain, end-to-end happy path, and stolen-cookie scenario.

## References

- [W3C WebAppSec DBSC](https://w3c.github.io/webappsec-dbsc/)
- [RFC 7638 – JWK Thumbprint](https://www.rfc-editor.org/rfc/rfc7638)
- [RFC 7515 – JWS](https://www.rfc-editor.org/rfc/rfc7515), [RFC 7519 – JWT](https://www.rfc-editor.org/rfc/rfc7519)
