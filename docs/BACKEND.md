# quizlet_fake — Backend Documentation

Comprehensive reference for the backend system: technologies, business rules, data model, security, API, learning algorithm, and operations.

Study feature deep-dive (business + workflow): [STUDY.md](STUDY.md) | [STUDY.vi.md](STUDY.vi.md) (Tiếng Việt)

---

## 1. Overview

**quizlet_fake** is a flashcard-based vocabulary learning platform (Quizlet-inspired).

**Core business capabilities:**

| Capability | Status | Description |
|---|---|---|
| User accounts | ✅ Done | Register, login (username **or** email), JWT auth |
| Flashcard decks | ✅ Done | Create/edit/delete personal study sets; public or private |
| Flashcards | ✅ Done | Term + definition + optional hint, nested in decks |
| Deck discovery | ✅ Done | Public deck search by free-text and category |
| Learning engine | ✅ Done | Per-user state machine (Not Learned → Still Learning → Mastered), persisted |
| Multiple-choice study | ✅ Done | Server-generated MCQ quiz (definition → pick term), grading, progress %, star flag |
| SRS review scheduling | ✅ Done | Modified SM-2: quality 1–4, ease factor, intervals, due queue (`/api/reviews/**`) |

**Product flow (end user perspective):**

```
Register/Login ──> Create Deck ──> Add Cards ("hello" → "xin chào")
                                       │
             ┌─────────────────────────┘
             ▼
      Study Session: card shown ──> user answers ──> engine updates state
             │
             ├─ NOT_LEARNED ──1st correct──> STILL_LEARNING ──2nd correct──> MASTERED
             └─ wrong answer ──> streak reset (MASTERED demotes to STILL_LEARNING)
```

---

## 2. Technology Stack

| Layer | Technology | Version | Why |
|---|---|---|---|
| Language | Java | 17 | LTS, records, switch expressions |
| Framework | Spring Boot | 3.3.4 | Opinionated, batteries-included REST stack |
| Build | Gradle | 8.14.3 (`gradlew`) | Primary build tool (legacy `pom.xml` exists but unused) |
| Web | Spring Web (MVC) | — | REST controllers, bean validation (`jakarta.validation`) |
| Security | Spring Security + JJWT (jjwt) | 0.12.6 | Stateless JWT, HS256 signed tokens |
| Password hashing | BCrypt (`BCryptPasswordEncoder`) | — | Industry standard, salted |
| ORM | Hibernate (Spring Data JPA) | — | Repository pattern, derived queries, JPQL |
| Database | PostgreSQL | 15 (Docker: `postgres:15-alpine`) | Relational, `TIMESTAMPTZ`, identity columns |
| Schema management | Flyway | `flyway-core` + `flyway-database-postgresql` | Versioned SQL migrations; Hibernate = `validate` only |
| Connection pool | HikariCP | default | Max 10 / min idle 5 |
| Boilerplate | Lombok | — | Getters, builders, constructors |
| JSON | Jackson | — | `non_null` inclusion, UTC timezone |
| Package root | `com.example.quizlet` | — | — |

**Run environment:**

| Item | Value | Note |
|---|---|---|
| App port | **8081** | 8080 occupied by local Jenkins |
| DB host:port | `localhost:5432` | Docker container `quizlet-fake-postgres` |
| DB name / user / pass | `quizlet_fake` / `postgres` / `postgres` | Dev only |
| JWT secret | `JWT_SECRET` env var | Fallback dev key in `application.properties`; must be Base64, ≥ 256 bits |
| Token TTL | 86,400,000 ms (24 h) | `app.jwt.expiration-ms` |

---

## 3. Architecture

Classic layered Spring architecture. Request flows: **Filter → Controller → Service → Repository → DB**, with DTOs at the API boundary.

```
HTTP Request
    │
    ▼
JwtAuthenticationFilter          (extracts Bearer token → SecurityContext)
    │
    ▼
Controller  (@RestController)    (HTTP mapping, validation @Valid, status codes)
    │  DTO in / DTO out
    ▼
Service     (@Service)           (business rules, ownership guards, @Transactional)
    │  Entities
    ▼
Repository  (Spring Data JPA)    (derived queries + JPQL)
    │
    ▼
PostgreSQL  (schema owned by Flyway)
```

Cross-cutting:
- `GlobalExceptionHandler` (`@RestControllerAdvice`) → uniform JSON errors
- `SecurityConfig` (`@Configuration`) → security filter chain, beans
- Anonymous requests: `@AuthenticationPrincipal User` resolves to **null** — services must handle it

### Package layout

```
src/main/java/com/example/quizlet/
├── QuizletFakeApplication.java
├── config/
│   └── SecurityConfig.java        # filter chain, beans, CORS
├── security/
│   ├── JwtService.java            # sign/parse HS256 tokens
│   └── JwtAuthenticationFilter.java
├── controller/
│   ├── AuthController.java        # /api/auth/**
│   ├── DeckController.java        # /api/decks**
│   └── CardController.java        # /api/decks/{deckId}/cards**
├── service/
│   ├── AuthService.java
│   ├── DeckService.java
│   └── CardService.java
├── repository/
│   ├── UserRepository.java
│   ├── DeckRepository.java
│   └── CardRepository.java
├── entity/
│   ├── User.java                  # implements UserDetails
│   ├── Role.java                  # enum
│   ├── Deck.java
│   └── Card.java
├── dto/
│   ├── auth/    (RegisterRequest, LoginRequest, AuthResponse)
│   ├── deck/    (DeckRequest, DeckResponse)
│   └── card/    (CardRequest, CardResponse)
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── ResourceNotFoundException.java
│   └── DuplicateResourceException.java
└── learning/                      # in-memory learning engine (see §9)
    ├── WordState.java
    ├── VocabularyCard.java
    ├── StudySet.java
    └── LearningDemo.java          # mock execution, has main()
```

---

## 4. Data Model

### ERD

```
users 1 ────────< decks 1 ────────< cards
(id, username,     (title, is_public,   (term, definition,
 email, password,   creator_id FK,       hint, deck_id FK)
 role, created_at)  category)
```

- `User` **1 → N** `Deck` (`@ManyToOne(fetch = LAZY)`, `creator_id`)
- `Deck` **1 → N** `Card` (`@OneToMany(cascade = ALL, orphanRemoval = true)`, `mappedBy = "deck"`)
- Deleting a deck cascades to its cards (both at JPA level and by owner-only delete flow)

### Tables

**`users`** (V1)

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT IDENTITY | PK |
| username | VARCHAR(50) | NOT NULL, UNIQUE (`uk_users_username`) |
| email | VARCHAR(100) | NOT NULL, UNIQUE (`uk_users_email`) |
| password | VARCHAR(255) | NOT NULL — BCrypt hash, never raw |
| role | VARCHAR(20) | NOT NULL — `ROLE_USER` / `ROLE_ADMIN` (stored as string) |
| created_at | TIMESTAMPTZ | NOT NULL |

**`decks`** (V2)

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT IDENTITY | PK |
| title | VARCHAR(150) | NOT NULL |
| description | VARCHAR(1000) | nullable |
| category | VARCHAR(100) | nullable, free-form label (e.g. "IELTS") |
| creator_id | BIGINT | NOT NULL, FK → `users(id)` (`fk_decks_creator`) |
| created_at | TIMESTAMPTZ | NOT NULL |
| is_public | BOOLEAN | NOT NULL, DEFAULT TRUE |

Indexes: `idx_decks_creator_id (creator_id)`, `idx_decks_is_public (is_public)`

**`cards`** (V3)

| Column | Type | Constraints |
|---|---|---|
| id | BIGINT IDENTITY | PK |
| term | VARCHAR(255) | NOT NULL |
| definition | VARCHAR(2000) | NOT NULL |
| hint | VARCHAR(1000) | nullable — usage example shown during study |
| deck_id | BIGINT | NOT NULL, FK → `decks(id)` (`fk_cards_deck`) |

Indexes: `idx_cards_deck_id (deck_id)`

### Entity ↔ table notes

- `User.createdAt` set via `@PrePersist`; `Deck.createdAt` likewise
- Lombok gotcha: field `isPublic` → generated setter is `setPublic()`, builder method `isPublic()`
- DTOs are Java records with static `from(entity)` factories
- `DeckResponse` intentionally excludes cards — fetch them via `/api/decks/{id}/cards`

---

## 5. Database Migrations (Flyway)

**Policy: Flyway owns the schema.** Hibernate runs with `ddl-auto=validate` — entity drift fails startup rather than silently altering tables.

- Location: `src/main/resources/db/migration`
- Naming: `V<version>__<description>.sql`
- Current migrations:
  1. `V1__create_users_table.sql`
  2. `V2__create_decks_table.sql`
  3. `V3__create_cards_table.sql`
- Never edit an applied migration. Always add a new `V<n>__...sql`.
- Boot 3.3.4 BOM does **not** manage `spring-boot-starter-flyway` → declare `org.flywaydb:flyway-core` + `flyway-database-postgresql` directly.
- `baseline-on-migrate=false` (clean dev DB; set true only when adopting Flyway on an existing DB).

---

## 6. Security

### Design

- **Stateless**: no HTTP sessions (`SessionCreationPolicy.STATELESS`), CSRF disabled (no cookies)
- **Token**: JWT, HS256, signed with Base64-decoded secret ≥ 256 bits, subject = username, 24 h expiry
- **Passwords**: BCrypt (encoded at registration, verified by `DaoAuthenticationProvider`)

### Request authentication flow

```
Client                         Server
  │  Authorization: Bearer <jwt>  │
  ├──────────────────────────────▶│
  │                    JwtAuthenticationFilter:
  │                      1. header present & starts "Bearer "?
  │                      2. JwtService.extractUsername(token)   (verify signature + expiry)
  │                      3. UserDetailsService.loadUserByUsername
  │                      4. isTokenValid(token, userDetails)
  │                      5. set SecurityContext authentication
  │                               │
  │                    Controller receives @AuthenticationPrincipal User
  ◀───────────────────────────────┤
```

- No/expired/tampered token → request proceeds **unauthenticated** → endpoint rules decide (401 or anonymous access for public GETs)
- Invalid token formats are swallowed by `JwtService.parse()` (returns null → rejected, no stack trace)

### Endpoint access rules (SecurityConfig)

| Rule | Endpoints |
|---|---|
| `permitAll` | `/api/auth/**` (register, login) |
| `permitAll` (GET only) | `/api/decks/**` — public deck reads, search, card lists |
| `authenticated` | everything else (deck/card writes, `/api/decks/me`) |

### Service-layer authorization (second gate)

Even authenticated users can only act within their rights — enforced in services, not just URL rules:

- `DeckService.getViewable(id, user)` — deck must be public **or** owned; private deck hidden from non-owner returns **404** (not 403 — hides existence)
- `DeckService.getOwned(id, user)` — owner or `AccessDeniedException` → 403
- `CardService` — every write calls `deckService.getOwned(...)` first; `findByIdAndDeckId` prevents cross-deck card access
- Anonymous user (`currentUser == null`) handled in `getViewable` — reads public decks only

### Auth business rules

- **Register**: username ≥ 3 chars, email valid, password ≥ 8 chars; duplicate username/email (case-insensitive) → `409 Conflict`; new users get `ROLE_USER`; JWT issued immediately
- **Login**: accepts `usernameOrEmail` — tries username first, then email; **uniform error message** `Invalid username or password` for both wrong password and unknown user (prevents account enumeration) → 401
- Bean-injection gotcha: `JwtAuthenticationFilter` is injected into `securityFilterChain(...)` as a **method parameter**, not constructor field — avoids circular dependency with the `userDetailsService` bean

### CORS

Dev-permissive: all origins (`allowedOriginPatterns("*")`), all methods, credentials allowed. **Restrict origins before production.**

---

## 7. API Reference

Base URL: `http://localhost:8081`. All bodies are JSON. Auth endpoints need no token; `Authorization: Bearer <accessToken>` required for writes and `/api/decks/me`.

### 7.1 Auth — `/api/auth`

**POST `/api/auth/register`** → `201 Created` (duplicate → 409; invalid → 400)

```json
// request
{ "username": "dung", "email": "dung@example.com", "password": "secret123" }

// response
{
  "accessToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "id": 1,
  "username": "dung",
  "email": "dung@example.com",
  "role": "ROLE_USER"
}
```

Validation: username 3–50, email valid ≤ 100, password 8–100.

**POST `/api/auth/login`** → `200 OK` (bad credentials → 401)

```json
// request  — username OR email
{ "usernameOrEmail": "dung", "password": "secret123" }
// response: same shape as register
```

### 7.2 Decks — `/api/decks`

| Method | Path | Auth | Description | Success | Errors |
|---|---|---|---|---|---|
| GET | `/api/decks/search?q=&category=&page=&size=` | none | Search **public** decks; `q` matches title/category (case-insensitive contains); `category` exact (case-insensitive); blank = no filter; paged (`sort=id,desc` default) | 200 | — |
| GET | `/api/decks/{id}` | none | Deck detail — public, or owned by caller | 200 | 404 (missing **or** private non-owned) |
| GET | `/api/decks/me` | Bearer | Caller's decks (public + private), paged | 200 | 401 |
| POST | `/api/decks` | Bearer | Create deck owned by caller | 201 + `Location` | 400, 401 |
| PUT | `/api/decks/{id}` | Bearer | Full update | 200 | 400, 401, 403 (not owner), 404 |
| DELETE | `/api/decks/{id}` | Bearer | Delete deck + cascade cards | 204 | 401, 403, 404 |

Deck request body:

```json
{ "title": "English Basics", "description": "Daily words", "category": "General", "isPublic": true }
```

Deck response: `{ id, title, description, category, isPublic, creatorId, creatorUsername, createdAt }`

### 7.3 Cards — `/api/decks/{deckId}/cards`

Nested under deck. Read requires deck visibility; writes require deck ownership.

| Method | Path | Auth | Description | Success | Errors |
|---|---|---|---|---|---|
| GET | `/api/decks/{deckId}/cards` | none | List deck's cards (public deck, or owner) | 200 | 404 |
| POST | `/api/decks/{deckId}/cards` | Bearer | Add card | 201 + `Location` | 400, 401, 403, 404 |
| PUT | `/api/decks/{deckId}/cards/{cardId}` | Bearer | Update card | 200 | 400, 401, 403, 404 |
| DELETE | `/api/decks/{deckId}/cards/{cardId}` | Bearer | Delete card | 204 | 401, 403, 404 |

Card request body:

```json
{ "term": "hello", "definition": "xin chào", "hint": "common greeting" }
```

Card response: `{ id, term, definition, hint, deckId }`

A card id under the wrong deck → 404 (`findByIdAndDeckId`), never leaks other decks' cards.

### 7.4 Study — `/api/decks/{deckId}/study` (all authenticated)

| Method | Path | Description | Success | Errors |
|---|---|---|---|---|
| GET | `/study/quiz?count=10&state=&starred=` | Generate MCQ quiz: prompt = definition, options = 4 terms (correct + 3 distractors from same deck, shuffled). Correct answer **never** sent to client. `state` filter = `NOT_LEARNED/STILL_LEARNING/MASTERED`, `starred` = true/false | 200 | 400, 401, 404 |
| POST | `/study/answer` | Grade answer server-side (`cardId` vs `selectedCardId`), apply state machine, persist progress + attempt | 200 | 400, 401, 404 |
| GET | `/study/progress` | Deck progress for caller | 200 | 401, 404 |
| POST | `/study/cards/{cardId}/star` | Toggle starred (works on public decks — personal note) | 200 | 401, 404 |

Quiz response:

```json
{
  "deckId": 2,
  "direction": "DEFINITION_TO_TERM",
  "questions": [
    { "cardId": 5,
      "prompt": "hoài bão, khát vọng",
      "options": [
        { "cardId": 5, "text": "ambition" },
        { "cardId": 6, "text": "ambitous" },
        { "cardId": 7, "text": "alleviate" },
        { "cardId": 8, "text": "permission" }
      ] }
  ]
}
```

Answer request/response:

```json
// request — selectedCardId null = skip
{ "cardId": 5, "selectedCardId": 5, "mode": "MULTIPLE_CHOICE" }

// response
{ "correct": true, "cardId": 5, "correctCardId": 5, "correctTerm": "ambition",
  "state": "STILL_LEARNING", "consecutiveCorrect": 1,
  "deckProgress": { "total": 4, "mastered": 0, "stillLearning": 1, "notLearned": 3, "percent": 0.0 } }
```

Note: `/api/decks/*/study/**` requires a JWT and is matched **before** the public `GET /api/decks/**` rule in SecurityConfig.

### 7.5 SRS Reviews — `/api/reviews` (all authenticated)

Spaced-repetition scheduling (modified SM-2), independent of the MCQ flow. State table: `user_card_progress` (V6).

| Method | Path | Description | Success | Errors |
|---|---|---|---|---|
| GET | `/api/reviews/due?limit=20` | Cards with `next_review_date <= now`, oldest due first (max 100) | 200 | 401 |
| POST | `/api/reviews/{cardId}` | Submit quality 1–4 → SM-2 recalculation, returns new schedule | 200 | 400 (quality invalid), 404 (card missing/invisible) |
| POST | `/api/reviews/{cardId}/save` | Add one card to review queue, due immediately. Idempotent (`saved: false` if already queued) | 200 | 401, 404 |
| POST | `/api/reviews/decks/{deckId}/enroll` | Add all deck cards to review queue. Idempotent per card; returns `{ totalCards, newlyEnrolled, alreadyEnrolled }` | 200 | 401, 404 |
| DELETE | `/api/reviews/{cardId}` | Remove card from review queue (SRS row only; MCQ progress untouched). No-op if absent | 204 | 401, 404 |

Enrollment is the bridge between the two learning modes: MCQ study needs no setup; SRS starts with `save`/`enroll` (both allow public decks — the queue is personal).

```json
// POST /api/reviews/8  body
{ "quality": 3 }

// response
{ "cardId": 8, "quality": 3, "status": "REVIEW", "intervalDays": 1,
  "easeFactor": 2.5, "repetition": 1, "nextReviewDate": "2026-10-10T10:37:55Z" }
```

Algorithm (see `UserCardProgress.applyReview`):

| Quality | Meaning | Effect |
|---|---|---|
| 1 AGAIN | forgot | repetition=0, relearn in 10 min, ease −0.20, LEARNING |
| 2 HARD | struggled | interval ×1.2 (or relearn if never recalled), ease −0.15 |
| 3 GOOD | correct | repetition+1; first success → 1 day, then interval × ease, REVIEW |
| 4 EASY | instant | like GOOD with interval × 1.3 bonus, ease +0.10 |

Ease clamped [1.3, 2.8]; interval capped at 365 days; interval ≥ 21 days → MASTERED.

### 7.6 Error format (all endpoints)

Uniform body from `GlobalExceptionHandler`:

```json
{
  "timestamp": "2026-10-09T08:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Deck not found with id: 42",
  "path": "/api/decks/42"
}
```

| Exception | HTTP | Message behavior |
|---|---|---|
| `ResourceNotFoundException` | 404 | specific ("Deck/Card not found…") |
| `DuplicateResourceException` | 409 | specific |
| `BadCredentialsException` | 401 | **fixed** "Invalid username or password" |
| `AccessDeniedException` | 403 | fixed permission message |
| `MethodArgumentNotValidException` | 400 | "Validation failed: {field: message, …}" |
| `IllegalArgumentException` | 400 | specific |
| any other `Exception` | 500 | **fixed** "An unexpected error occurred" (no stack trace leak) |

---

## 8. Key Business Flows

### 8.1 Learn one word end-to-end (today, CRUD only)

```
1. POST /api/auth/register            → account + token
2. POST /api/auth/login               → token (if returning user)
3. POST /api/decks                    → deck "English Basics" (id known)
4. POST /api/decks/{deckId}/cards     → card "hello" → "xin chào"
5. GET  /api/decks/{deckId}/cards     → self-review: read term, recall, check definition
```

Repeat step 5 daily. No scoring/persistence yet — see §9.

### 8.2 Sharing & discovery

- Deck owner sets `isPublic: true` → anyone can `GET /api/decks/{id}`, `/search`, card list — no token needed
- Private deck: invisible to others (404 on all GETs), visible in owner's `/api/decks/me`
- Others can **read** public decks but can never **modify** them (owner-only writes)

---

## 9. Learning Engine (Spaced-Repetition-Inspired)

Two parts: pure in-memory reference implementation (`learning/` package, `LearningDemo.main()`) and the persisted production flow (`CardProgress` entity + `StudyService`), both using the same state machine rules.

### 9.1 Word states

| State | Meaning |
|---|---|
| `NOT_LEARNED` | Initial. No correct answer yet. |
| `STILL_LEARNING` | Progressing. ≥ 1 correct answer, streak below mastery threshold. |
| `MASTERED` | Memorized. Streak reached the threshold (2 consecutive correct). |

### 9.2 State machine

```
             correct #1                 correct #2
NOT_LEARNED ─────────────▶ STILL_LEARNING ─────────────▶ MASTERED
     ▲                        ▲    ▲                        │
     │        wrong (stays) ──┘    └──────── wrong ─────────┘
     └─ (wrong here: stays, streak stays 0)     demote, streak = 0
```

Rules (see `VocabularyCard`):

| Event | Effect |
|---|---|
| start | `NOT_LEARNED`, `consecutiveCorrect = 0` |
| correct #1 | `STILL_LEARNING`, streak = 1 |
| correct #2 (from STILL_LEARNING) | `MASTERED`, streak = 2 |
| correct (already MASTERED) | stays MASTERED |
| wrong (any state) | streak → 0; MASTERED **demotes** to STILL_LEARNING; STILL_LEARNING stays; NOT_LEARNED stays |
| re-master after wrong | needs 2 fresh consecutive corrects |

Constant: `VocabularyCard.MASTERY_THRESHOLD = 2` (raise to 3+ without touching logic).

### 9.3 Set-level operations (`StudySet`)

- `progressPercent()` = `masteredCount / total * 100` (empty set = 0)
- `filterByState(WordState)` → words in one state
- `filterStarred()` → user-bookmarked (difficult) words; `card.toggleStar()`
- `shuffle()` → randomized card order (prevents position memorization)

### 9.4 Persisted production flow (implemented)

Schema (Flyway):

- **`card_progress`** (V4) — one row per `(user_id, card_id)`, unique; `state`, `consecutive_correct`, `starred`, `updated_at`. Missing row = implicitly `NOT_LEARNED` (lazy creation on first answer/star)
- **`study_attempts`** (V5) — answer history: `user_id`, `card_id`, `deck_id` (denormalized), `mode`, `is_correct`, `selected_card_id`, `answered_at`. Powers review lists ("my mistakes in deck X") and future SRS

Code:

- `entity/CardProgress` — same `recordCorrect()/recordIncorrect()` transitions as `learning.VocabularyCard`; dirty-checked updates via `save()`
- `entity/StudyAttempt` + `entity/StudyMode` (`MULTIPLE_CHOICE`, extend + widen V5 CHECK together)
- `service/StudyService` — quiz generation (shuffle mutable list, distractors from same deck), server-side grading, progress stats, star toggle
- `controller/StudyController` — see §7.4

MCQ design points:

- Prompt = definition, options = terms (direction `DEFINITION_TO_TERM`; reverse mode later)
- Correct answer never sent to client — grading compares `cardId` vs `selectedCardId` server-side
- Distractors come from the same deck; decks with < 4 cards get fewer options
- `selectedCardId` resolved within the deck; unknown → treated as incorrect with `selected = null`
- Progress rows: created lazily; **must call `save()`** on newly built entities (transient instances are not dirty-checked)

---

## 10. Configuration Reference

`src/main/resources/application.properties`:

```properties
spring.application.name=quizlet-fake
server.port=8081                        # 8080 taken by local Jenkins

spring.datasource.url=jdbc:postgresql://localhost:5432/quizlet_fake
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5

spring.jpa.hibernate.ddl-auto=validate  # Flyway owns schema
spring.jpa.open-in-view=false           # fetch collections inside @Transactional
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=false

app.jwt.secret=${JWT_SECRET:<dev-fallback>}   # Base64, >= 256 bits
app.jwt.expiration-ms=86400000                # 24h

spring.jackson.default-property-inclusion=non_null
spring.jackson.time-zone=UTC
```

---

## 11. Running Locally

```bash
# 1. Database (Docker)
cd docker
docker compose up -d                     # container quizlet-fake-postgres, volume postgres_data
# stop: docker compose down (data kept) | wipe: docker compose down -v (Flyway re-runs all)

# 2. App
./gradlew bootRun                        # starts on http://localhost:8081

# 3. Verify
curl http://localhost:8081/api/decks/search

# 4. Run the learning demo (in-memory state machine)
./gradlew compileJava
java -cp build/classes/java/main com.example.quizlet.learning.LearningDemo

# psql (if needed)
/opt/homebrew/opt/postgresql@15/bin/psql -h localhost -U postgres -d quizlet_fake
```

---

## 12. Architectural Decisions & Gotchas

| # | Decision / Gotcha | Detail |
|---|---|---|
| 1 | Flyway owns schema | `ddl-auto=validate`; never let Hibernate write DDL |
| 2 | Port 8081 | Local Jenkins occupies 8080 |
| 3 | Postgres in Docker | Homebrew `postgresql@15` service stopped; Docker is primary |
| 4 | Flyway starter not managed by Boot 3.3.4 BOM | Use `org.flywaydb:flyway-core` + `flyway-database-postgresql` |
| 5 | Filter injection via method param | Breaks bean cycle: `SecurityConfig` ↔ `userDetailsService` bean |
| 6 | Private deck = 404, not 403 | Hides existence from non-owners |
| 7 | Uniform login error | Prevents username/email enumeration |
| 8 | `open-in-view=false` | Deck cards must be fetched inside `@Transactional` (e.g. `CardService.listByDeck`) |
| 9 | Lombok `isPublic` | Setter is `setPublic()`, builder method `isPublic()` |
| 10 | `@AuthenticationPrincipal User` can be null | Anonymous calls to permitted GET endpoints — services must null-check |
| 11 | Anonymous JWT filter behavior | Missing/garbage token → request continues unauthenticated (public GETs still work) |
| 12 | Cross-deck protection | Cards fetched via `findByIdAndDeckId`; writes guarded by deck ownership |
| 13 | Legacy `pom.xml` | Present but unused; Gradle is the build system |

---

## 13. Roadmap

1. **Review lists**: "my mistakes in deck X" endpoint over `study_attempts` (schema ready)
2. **Reverse direction**: term → definition MCQ (`?direction=TERM_TO_DEFINITION`)
3. **SRS enhancements**: seed NEW cards into due queue, per-deck due filter, review on mobile-optimized batch sizes
4. **Tests**: service-layer unit tests (Mockito), `@SpringBootTest` + Testcontainers Postgres for repository/`MockMvc` coverage of security rules
5. **Production hardening**: real `JWT_SECRET` env, restrict CORS origins, HTTPS, rate limiting on `/api/auth/login`
6. **Optional later**: FLASHCARD/WRITING modes, deck import/export (CSV), admin role features
