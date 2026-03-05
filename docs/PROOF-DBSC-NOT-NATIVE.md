# Proof that native DBSC is not active (for Chrome team / origin trial)

**Purpose:** We have a DBSC-capable server and test page. On Chrome 145 with the DBSC flag enabled, we observe that **native browser DBSC is not used**; the page falls back to a JavaScript simulation. This document provides reproducible evidence so we can work with the Chrome team (e.g. to enroll in the origin trial or get a trial token for our deployment origin).

**Update (Chrome team):** On Mac, Windows, or Linux you can test **native** DBSC behavior (browser responding to `Secure-Session-Registration`) by enabling **`chrome://flags#enable-bound-session-credentials-software-keys-for-manual-testing`**. Keys are then software-backed (no security benefit). **Origin trial enrollment is not required** for this testing. For production: only **Windows + TPM** get hardware keys today; **Mac** (Secure Enclave) support is expected within a few months.

---

## 1. What we expect when native DBSC *is* working (per Chrome docs)

When Chrome implements DBSC natively for our origin:

- **Registration:** After the server returns `Secure-Session-Registration`, **Chrome** contacts the registration endpoint (e.g. `POST /dbsc/register`) **automatically**—no "Register" button or page script. Chrome generates the key pair and stores the private key in hardware (e.g. TPM) and sends the proof.
- **Headers:** Chrome sets **`Sec-Secure-Session-Id`** (and related `Sec-*` / `Secure-Session-*` headers) on requests. Per the platform security model, **JavaScript cannot set `Sec-*` headers**; only the browser can.
- **Refresh:** When the short-lived cookie is missing or expired, **Chrome** defers the request, calls the refresh endpoint with a signed proof, then resumes—all without page script.

References: [Chrome DBSC integration guide](https://developer.chrome.com/docs/web-platform/device-bound-session-credentials), [DBSC origin trial blog](https://developer.chrome.com/blog/dbsc-origin-trial).

---

## 2. What we actually see (evidence that native DBSC is not active)

### 2.1 Registration is driven by page JavaScript, not by the browser

- Our test page has an explicit **"Start session"** then **"Register"** flow.
- **"Register"** runs **JavaScript** that:
  - Calls `generateKeyPair('ES256')` (Web Crypto) and keeps the private key in **page memory** (`state.keyPair.privateKey`).
  - Builds a JWT and sends `POST /dbsc/register` with header `Secure-Session-Response`.
- If native DBSC were active, the **browser** would react to `Secure-Session-Registration` and perform registration **without** any "Register" button or key generation in our script. We would not need (or use) Web Crypto in the page for the key.

**Conclusion:** The browser is not handling session registration; our page script is. That is the JS simulation.

### 2.2 Refresh uses `X-Dbsc-Session-Id` because the browser does not set `Sec-Secure-Session-Id`

- Our server accepts **`X-Dbsc-Session-Id`** as an alternative to **`Sec-Secure-Session-Id`** solely because **`fetch()` cannot set `Sec-*` headers** (they are [forbidden for script](https://fetch.spec.whatwg.org/#forbidden-header-name)).
- In our code and UI we document: *"JS cannot set Sec-Secure-Session-Id; server accepts X-Dbsc-Session-Id for this demo."*

**Reproducible check:**

1. Open our app in **Chrome 145** with DBSC flag enabled: `chrome://flags#device-bound-session-credentials` → Enabled.
2. Open **DevTools → Network**.
3. Click **Start session**, then **Register**.
4. In Network, select the **`POST .../dbsc/register`** request and inspect **Request Headers**.

**What you will see:**

- A header like **`X-Dbsc-Session-Id`** (or similar) and **`Secure-Session-Response`** set by our page’s `fetch()`.
- **No** **`Sec-Secure-Session-Id`** set by the browser on that request (or on refresh). With native DBSC, Chrome would set `Sec-Secure-Session-Id` itself; we would not need a custom header.

**Conclusion:** The presence of `X-Dbsc-Session-Id` and the absence of browser-set `Sec-Secure-Session-Id` show that Chrome is not performing native DBSC for our origin; our JS is.

### 2.3 Private key is in script memory, not in browser/TPM

- Our test page stores the private key only in **JavaScript** (`state.keyPair.privateKey`). It is **not** in TPM or any browser-managed secure storage.
- Refreshing the page or closing the tab **loses** the key; the user must "Start session" and "Register" again. With native DBSC, the key would be managed by the browser (e.g. TPM-backed) and would survive reloads within the same origin/session.

**Conclusion:** Key storage behavior matches a JS simulation, not native DBSC.

---

## 3. Enabling native DBSC for testing (Chrome team guidance)

- **Origin trial:** The Chrome team has indicated that **origin trial enrollment is no longer required** for testing. You can test native DBSC on any origin (localhost or deployed) with the flag below.
- **Flag:** Enable **`chrome://flags#enable-bound-session-credentials-software-keys-for-manual-testing`**. Chrome will then respond to `Secure-Session-Registration` and perform registration/refresh with software-backed keys (no TPM/Secure Enclave; for manual testing only).


---

## 4. Summary for the Chrome team

| Check | Native DBSC (expected) | What we see |
|-------|------------------------|-------------|
| Registration | Browser reacts to `Secure-Session-Registration` and POSTs to register; no page JS key gen | Page JS generates key and POSTs; explicit "Register" button |
| Session ID header | Browser sets `Sec-Secure-Session-Id` | We use `X-Dbsc-Session-Id` because script cannot set `Sec-*` |
| Key storage | Browser/TPM (or secure storage) | Key only in page JS memory; lost on reload |
| Refresh | Browser defers request, calls refresh, then continues | Page JS calls refresh on 401 and retries |

We are running Chrome 145 with the software-keys flag enabled. Our server and test page are built to the W3C DBSC draft and Chrome’s integration guide. We can demonstrate when native DBSC is not active (JS simulation) vs when the browser takes over (native path); the proof steps above still apply to tell them apart.

---

## 5. How to run our app for reproduction

- **Repo / server:** Spring Boot app with `/dbsc/session/start`, `POST /dbsc/register`, `POST /dbsc/refresh`, and a test page at `/` (or the same app deployed via cloudflared).
- **Steps:** Open the app in Chrome with **`enable-bound-session-credentials-software-keys-for-manual-testing`** enabled (origin trial not required). Open DevTools → Network, run "Start session" then "Register", and inspect the `POST /dbsc/register` (and any `POST /dbsc/refresh`) request headers. If native DBSC is active, the browser will set `Sec-Secure-Session-Id`; if not, you’ll see our JS fallback (e.g. `X-Dbsc-Session-Id`).

Our README and code comments explicitly describe the current flow as a **simulation** and the use of `X-Dbsc-Session-Id` because the browser does not set `Sec-Secure-Session-Id` for our origin.
