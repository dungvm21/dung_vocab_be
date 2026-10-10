# Authentication & Authorization — Detailed Reference

Companion to [BACKEND.md](BACKEND.md). This document covers **everything** about how the API
proves *who you are* (authentication) and decides *what you may do* (authorization), including
the refresh-token mechanism: token anatomy, rotation, theft detection, logout, and error
semantics — with sequence diagrams, tables and rationale for every decision.

- Version: 1.0 (2026-10-10)
- Code map: `security/` (JwtService, JwtAuthenticationFilter, SecurityConfig),
  `service/AuthService(+Impl)`, `service/RefreshTokenService(+Impl)`,
  `entity/RefreshToken`, `repository/RefreshTokenRepository`, `controller/AuthController`,
  `db/migration/V7__create_refresh_tokens_table.sql`

---

## 1. Concepts: Authentication vs Authorization

| | **Authentication (AuthN)** | **Authorization (AuthZ)** |
|---|---|---|
| Question | *Who is making this request?* | *Is this identity allowed to do this?* |
| Failure code | `401 Unauthorized` | `403 Forbidden` (or `404` — see §4.3) |
| Mechanism here | JWT access token in `Authorization` header | URL rules + service-layer ownership guards |
| Where | `JwtAuthenticationFilter` → `SecurityContext` | `SecurityConfig` filter-chain rules + `DeckService`/`CardService` guards |

Order matters: AuthN always runs first. An unauthenticated request never reaches an
authorization decision that depends on identity.

---

## 2. Authentication

### 2.1 Credentials: password

- Stored as **BCrypt hash** (salt embedded, cost 10) — raw password never persisted or logged.
- Verified by Spring's `DaoAuthenticationProvider` at login (constant-time comparison).
- Registration duplicates are checked **case-insensitively** for username and email
  (`existsByUsernameIgnoreCase` / `existsByEmailIgnoreCase`) → `409 Conflict`.

### 2.2 Credential #1: access token (JWT)

| Property | Value | Why |
|---|---|---|
| Format | Signed JWT (JWS), **HS256** | Single-service symmetric signing; simplest safe option |
| Secret | `app.jwt.secret`, Base64, decodes to ≥ 256 bits | HMAC-SHA-384 requires ≥ 256-bit key; jjwt enforces this |
| Subject (`sub`) | username | Filter reloads user by username on every request |
| Expiry | **15 min** (`app.jwt.access-expiration-ms=900000`) | Short life limits blast radius of a leaked token |
| Storage | Stateless — **no server-side record** | Scales horizontally; per-request cost = 1 signature check |
| Transport | `Authorization: Bearer <jwt>` header | Standard, works cross-service |

**Verification performed on every request** (`JwtAuthenticationFilter`):

1. Header present and starts with `Bearer ` — else request continues unauthenticated.
2. `JwtService.extractUsername(token)` — parses and **verifies signature + expiry**.
   Malformed / tampered / expired → `null` → unauthenticated (no exception leaks).
3. `UserDetailsService.loadUserByUsername(sub)` — user must still exist.
4. `JwtService.isTokenValid(token, userDetails)` — subject matches and not expired.
5. `UsernamePasswordAuthenticationToken(user, null, authorities)` stored in
   `SecurityContext` → controllers read `@AuthenticationPrincipal User`.

> **Note**: a valid access token is *not* checked against the database — it is trusted until
> it expires (≤ 15 min). Immediate revocation of access is impossible by design; that is
> exactly what the refresh-token layer contains (§3).

### 2.3 Login

```
Client                                     Server
  │ POST /api/auth/login                      │
  │ { usernameOrEmail, password }             │
  ├──────────────────────────────────────────▶│
  │                        AuthService.login: │
  │                      1. findByUsername    │
  │                         .orElse(findByEmail)   ← username OR email
  │                         (unknown → BadCredentials)
  │                      2. AuthenticationManager.authenticate  (BCrypt check)
  │                      3. issueTokens(user): accessToken (JWT, 15 min)
  │                                          + refreshToken (opaque, 7 days, §3)
  ◀──────────────────────────────────────────┤ 200 { accessToken, refreshToken, user... }
```

- **Uniform error**: unknown user and wrong password both return
  `401 {"message": "Invalid username or password"}` — prevents account enumeration.
- Unknown username short-circuits *before* the encoder for that path, but the response is
  identical, so the timing/behavior signal is not usable to enumerate accounts reliably.

---

## 3. Refresh Tokens (implemented)

### 3.1 Why

A stateless JWT cannot be revoked. Two extreme designs both fail:

- **Long-lived JWT (24 h+)**: stolen token = day-long session hijack, no kill switch.
- **Short-lived JWT alone (15 min)**: user re-logs-in every 15 minutes — unusable.

**Solution — token pair**:

| Token | Nature | TTL | Server state |
|---|---|---|---|
| Access | JWT, self-contained | 15 min | none (stateless) |
| Refresh | **Opaque** random string | 7 days | DB row (hashed) — revocable, auditable, rotatable |

Client uses access token for API calls; when it expires (`401`), it presents the refresh
token once to `/api/auth/refresh` and receives a **fresh pair**.

### 3.2 Token anatomy & storage

```
raw token   = 32 bytes SecureRandom → Base64-URL, no padding (43 chars, ~256 bits entropy)
never stored; returned to client exactly once at issue time

storage     = SHA-256(raw) as 64-char hex  → refresh_tokens.token_hash (UNIQUE)
```

- A database leak exposes only hashes — infeasible to reconstruct raw tokens (SHA-256
  preimage resistance over 256-bit random input).
- Opaque (not a JWT) → cannot be read, minted, or algorithm-confused by an attacker;
  validity lives solely in the DB row.

`refresh_tokens` schema (V7):

| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT identity | PK |
| `user_id` | BIGINT NOT NULL | FK → users |
| `token_hash` | VARCHAR(64) UNIQUE NOT NULL | SHA-256 hex of raw token |
| `expires_at` | TIMESTAMPTZ NOT NULL | issued + 7 days |
| `revoked_at` | TIMESTAMPTZ nullable | **NULL = live**; set by rotation/logout/theft-response |
| `created_at` | TIMESTAMPTZ NOT NULL | `@PrePersist` |
| idx | `idx_refresh_user_id` | fast per-user revocation |

### 3.3 Rotation (burn-on-use)

Every successful refresh **burns** the presented token (`revoked_at = now`) and issues a
new one. A raw token is redeemable **exactly once**.

```
Client                          Server (RefreshTokenServiceImpl.validateAndRotate)
  │ POST /api/auth/refresh          │
  │ { refreshToken: R1 }            │
  ├────────────────────────────────▶│ 1. hash(R1) → lookup token_hash
  │                                 │ 2. revoked? → THEFT RESPONSE (§3.4)
  │                                 │    expired? → 401
  │                                 │ 3. revoke R1 (revoked_at = now)   ← rotation
  │                                 │ 4. mint JWT + raw refresh R2, store hash(R2)
  ◀─────────────────────────────────┤ 200 { accessToken, refreshToken: R2, ... }
```

Benefit: if a refresh token is stolen, the thief and the legitimate client race; the first
redeemer wins, and the loser's retry triggers theft detection (§3.4) — silent token theft
becomes loud, and damage is contained to minutes.

### 3.4 Reuse detection (theft response)

Presenting an **already-revoked** token means: someone is replaying a burnt token. The
server treats the whole token family as compromised:

```
Client(thief)                    Server
  │ POST /api/auth/refresh          │
  │ { refreshToken: R1 (burnt) }    │
  ├────────────────────────────────▶│ 1. hash(R1) found, revoked_at NOT NULL
  │                                 │ 2. revokeAllForUser(userId):
  │                                 │    every live token of this user → revoked
  │                                 │    **commits in its own REQUIRES_NEW tx**
  │                                 │ 3. throw BadCredentialsException → 401
  ◀─────────────────────────────────┤ (legitimate client's next refresh also 401
  │                                 │  → forced re-login; thief's copy worthless)
```

> **Critical transactional detail (real bug we hit):** the revocation and the rejection
> live in the same business transaction. If `revokeAllForUser` ran in that transaction,
> the subsequent `BadCredentialsException` would **roll the revocation back** — the family
> would stay alive. Fix: the repository method is annotated
> `@Transactional(propagation = Propagation.REQUIRES_NEW)` so it commits independently of
> the caller's rollback.

### 3.5 Logout

`POST /api/auth/logout { refreshToken }` → revokes that token → `204 No Content`.

- **Idempotent**: unknown / already-revoked token still returns 204 (no information leak).
- Access token is **not** immediately invalidated (stateless) — it simply expires within
  ≤ 15 min. With a 15-minute TTL this residual window is acceptable; a server-side
  denylist would reintroduce state into the fast path (see roadmap §7).
- After logout, the refresh token is dead → the session cannot be extended → effective
  logout time = now (+ up to 15 min of access-token residue).

### 3.6 Full lifecycle diagram

```
 register/login      refresh (15 min later)     refresh                 reuse of R1 (thief!)
      │  R1                    │  R1→R2               │  R2→R3                    │
      ▼                        ▼                      ▼                           ▼
 ┌─────────┐  revoked   ┌─────────┐  revoked   ┌─────────┐  revoked    ┌──────────────┐
 │ row: R1 │◀────────── │ row: R2 │◀────────── │ row: R3 │  (R3 stays) │ ALL of user  │
 └─────────┘  by use    └─────────┘  by use    └─────────┘             │ revoked ⟵ 401│
                                                                      └──────────────┘
```

---

## 4. Authorization

Three gates, applied in order. Each request must pass all of them.

### 4.1 Gate 1 — URL rules (`SecurityConfig`)

| # | Rule | Endpoints | Rationale |
|---|---|---|---|
| 1 | `permitAll` | `/api/auth/**` — register, login, **refresh, logout** | No token exists yet / refresh token *is* the credential |
| 2 | `authenticated` | `/api/decks/*/study/**` | Study actions are per-user; must outrank rule 3 (Spring Security = **first match wins**) |
| 3 | `permitAll` (GET only) | `GET /api/decks/**` | Public deck browsing (anonymous learners) |
| 4 | `anyRequest().authenticated()` | deck/card writes, `/api/reviews/**`, `/api/decks/me`, … | Default-deny |

Stateless sessions, CSRF disabled (no cookies → no CSRF surface).

### 4.2 Gate 2 — Service-layer ownership guards

URL rules only know *authentication*; ownership is business logic, enforced in services:

| Guard | Semantics | Used by |
|---|---|---|
| `DeckService.getViewable(id, user)` | deck must be public **or** owned; `user == null` (anonymous) sees public only | all deck/card/study reads |
| `DeckService.getOwned(id, user)` | owner or `AccessDeniedException` | all deck/card writes |

`CardService` routes every write through `getOwned`, and loads cards with
`findByIdAndDeckId` so a card can never be reached across decks.

### 4.3 Gate 3 — Error semantics (deliberate)

| Situation | Returned | Why not the "obvious" code |
|---|---|---|
| No / bad / expired access token | `401` | — |
| Authenticated, but not the owner | `403` | — |
| Private deck, non-owner asks for it | **`404`** | `403` would confirm the deck exists — existence itself leaks; `404` hides it |
| Unknown username at login | `401`, uniform message | distinguishing codes would enable account enumeration |
| Refresh: unknown / expired / revoked / reused token | `401`, uniform `"Invalid refresh token"` | never reveal *which* of the four it was |
| Refresh request without body token | `400` (validation) | client bug, safe to say so |

---

## 5. Endpoint reference — `/api/auth` (all public)

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| POST | `/api/auth/register` | `{username, email, password}` | `201` + token pair | 400 validation · 409 duplicate |
| POST | `/api/auth/login` | `{usernameOrEmail, password}` | `200` + token pair | 401 uniform |
| POST | `/api/auth/refresh` | `{refreshToken}` | `200` + **new** pair (old burnt) | 400 malformed · 401 uniform |
| POST | `/api/auth/logout` | `{refreshToken}` | `204` (idempotent) | — (always 204/400) |

Token-pair response shape (register / login / refresh):

```json
{
  "accessToken":  "eyJhbGciOiJIUzM4NCJ9…",   // JWT, 15 min
  "refreshToken": "VFY8xfmaggVWxw7O6sH66w47u2lLFV4wTj8mgcEwNKY",  // opaque, 7 days, single-use
  "tokenType": "Bearer",
  "id": 6, "username": "refresherX", "email": "refresherX@test.io", "role": "ROLE_USER"
}
```

### 5.1 Verified end-to-end behaviors (2026-10-10)

| Scenario | Result |
|---|---|
| register → response contains both tokens | ✓ |
| access token `exp − iat` = 900 000 ms | ✓ |
| refresh with live token → new pair, old row `revoked_at` set | ✓ |
| **reuse** of burnt token → `401` **and** every other row of that user revoked (DB-verified) | ✓ |
| token issued *before* the theft event → also `401` afterwards | ✓ |
| logout → `204`; same token again → `204` (idempotent) | ✓ |
| refresh after logout → `401` | ✓ |
| garbage / unknown refresh token → `401` (uniform) | ✓ |
| missing `refreshToken` field → `400` validation | ✓ |
| access token still usable after logout until natural 15-min expiry | ✓ (by design, §3.5) |

---

## 6. Client integration guidance

**Storing the tokens:**

| Storage | Verdict | Reason |
|---|---|---|
| In-memory (JS variable) | ✅ best for access token | gone on tab close → minimal theft window |
| httpOnly, Secure, SameSite cookie | ✅ best for refresh token | JS cannot read it → XSS cannot exfiltrate |
| localStorage | ❌ never | any XSS = both tokens stolen |
| Flutter / mobile `flutter_secure_storage`, Android Keystore, iOS Keychain | ✅ | OS-encrypted |

**Request loop (pseudo):**

```
call API with accessToken
  ├─ 200 → done
  └─ 401 → POST /api/auth/refresh {refreshToken}     (queue parallel calls while refreshing)
             ├─ 200 → store new pair, retry original call
             └─ 401 → clear tokens, route to login     (rotation burnt or theft detected)
```

- Persist **only the refresh token** across app restarts (if desired); fetch access tokens
  via refresh at startup. Never persist the access token long-term.
- On `401` from refresh: treat as "logged out everywhere" — that is the theft-response
  working (§3.4); send the user to login.

**Configuration keys** (`application.properties`):

| Key | Default | Meaning |
|---|---|---|
| `app.jwt.secret` | env `JWT_SECRET` | Base64 HS-key, ≥ 256 bits decoded |
| `app.jwt.access-expiration-ms` | `900000` (15 min) | access-token TTL |
| `app.jwt.refresh-expiration-days` | `7` | refresh-token TTL |

> **Production checklist**: set `JWT_SECRET` from a secret manager (never the committed
> default); restrict CORS origins; serve over HTTPS only.

---

## 7. Best practices & roadmap

Implemented today: BCrypt · uniform login errors · 15-min access JWT · opaque hashed
single-use refresh tokens · rotation · reuse detection with family revocation · idempotent
logout · 404-privately-hidden resources · REQUIRES_NEW theft-response commit.

Planned / recommended next:

1. **Rate-limit** `/api/auth/login` and `/api/auth/refresh` (bucket per IP + per account)
   against brute force and refresh-hammering.
2. **Cleanup job**: purge rows where `expires_at < now − grace` (currently tokens accumulate;
   unique index also blocks hash reuse forever, which is fine but grows the table).
3. **Server-side access-token denylist** (Redis) if instant logout of the access token
   becomes a requirement — costs one lookup per request.
4. **Device/session metadata** on refresh rows (IP, user-agent, last-used) + "log out all
   other sessions" endpoint.
5. **Refresh-token ring buffer per device** (store `device_id`) to let reuse detection
   revoke only the compromised device's family instead of all sessions.
6. Optional: migrate HS256 → RS256 (asymmetric) when other services need to verify tokens
   without holding the signing key.
