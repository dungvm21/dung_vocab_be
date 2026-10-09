# Study Feature — Business & Workflow Guide

Language: English | Tiếng Việt: [STUDY.vi.md](STUDY.vi.md)

Complete guideline for the vocabulary learning (Study) feature: business rules, learning states, quiz generation, grading, progress tracking, and API workflow.

Related docs: [BACKEND.md](BACKEND.md) (system overview), §9 learning engine, §7.4 API.

---

## 1. Purpose & Scope

The platform offers **two independent learning modes** for every deck — the user picks one or both:

1. **Study (MCQ)** — quiz-style learning. Zero setup: works on any viewable deck immediately. Tracks `NOT_LEARNED → STILL_LEARNING → MASTERED` per user/card (`card_progress` table).
2. **SRS (Spaced Repetition)** — daily review queue with SM-2 scheduling. Opt-in: user adds single words (`save`) or enrolls a whole deck (`enroll`), then reviews due cards with quality scores 1–4 (`user_card_progress` table).

The modes share decks and cards but keep **independent progress** — mastering a word in MCQ never changes its SRS schedule, and vice versa. A user may quiz with MCQ in the morning and clear their SRS queue at night, on the same deck.

The Study feature turns a flashcard deck into an interactive learning session:

- User is shown a **question** (a card's definition) and picks the correct **term** from 4 options
- The system grades the answer **server-side**, updates the word's **learning state**, records the attempt, and reports deck progress
- Learning state is tracked **per user, per card** — studying someone else's public deck never affects its owner or other learners

Out of scope (future): reverse direction (term → definition), writing mode, cross-mode auto-sync.

---

## 2. Core Concepts

| Concept | Meaning |
|---|---|
| **Deck** | A study set of cards. May be public (anyone can study) or private (owner only) |
| **Card** | `term` + `definition` (+ optional `hint`). One deck owns many cards |
| **CardProgress** | Per-user learning state of one card: state + streak + starred. Missing row = implicitly `NOT_LEARNED` |
| **StudyAttempt** | One recorded answer (history log). Powers future review lists and analytics |
| **Question** | Definition shown as prompt + 4 term options (1 correct + 3 distractors) |
| **Quiz** | A shuffled batch of questions (default 10, max 50) |

**Key rule: the correct answer is NEVER sent to the client.** The client sends back which option the user picked; the server compares and grades.

---

## 3. Learning States

Each word (card) has exactly one state per user:

| State | Meaning | How reached |
|---|---|---|
| `NOT_LEARNED` | Never answered correctly | Initial state |
| `STILL_LEARNING` | In progress: answered correctly at least once, but not mastered (or demoted after a mistake) | First correct answer |
| `MASTERED` | Fully memorized | 2 consecutive correct answers |

Mastery threshold = **2 consecutive correct answers** (`MASTERY_THRESHOLD = 2`, same constant in reference impl and production entity). Raise it in one place to make mastery harder.

### State machine

```
             correct #1                  correct #2
NOT_LEARNED ──────────────▶ STILL_LEARNING ──────────────▶ MASTERED
     ▲                         ▲    ▲                         │
     │        wrong (stays) ───┘    └───────── wrong ─────────┘
     │  (streak already 0)                  demote, streak = 0
```

### Transition table (the exact business rules)

| Current state | Event | New state | Streak |
|---|---|---|---|
| NOT_LEARNED (streak 0) | correct #1 | STILL_LEARNING | 1 |
| NOT_LEARNED | wrong / skip | NOT_LEARNED (stays) | 0 |
| STILL_LEARNING (streak 1) | correct #2 | **MASTERED** | 2 |
| STILL_LEARNING | wrong / skip | STILL_LEARNING (stays) | 0 (reset) |
| MASTERED | correct | MASTERED (stays) | 2 |
| MASTERED | wrong / skip | **STILL_LEARNING** (demoted) | 0 (reset) |

Consequences worth knowing:

- **One mistake never fully wipes progress** — a mastered word drops to STILL_LEARNING, so it needs 2 fresh consecutive corrects to re-master
- **Skip counts as incorrect** (no `selectedCardId` = wrong answer)
- A mastered word answered correctly again stays mastered (no streak growth)

---

## 4. Data Model

```
users 1──< card_progress >──1 cards     state, consecutive_correct, starred
  │ 1
  └──< study_attempts >──1 cards        mode, is_correct, selected_card_id
              └── decks                  (denormalized deck_id for fast history queries)
```

**`card_progress`** (migration V4)

| Column | Type | Notes |
|---|---|---|
| user_id, card_id | BIGINT | UNIQUE together (`uq_progress_user_card`) — one row per user per card |
| state | VARCHAR(20) | `NOT_LEARNED` / `STILL_LEARNING` / `MASTERED` (CHECK constraint) |
| consecutive_correct | INT | Current streak of correct answers |
| starred | BOOLEAN | User bookmarked this word as difficult |
| updated_at | TIMESTAMPTZ | Set on insert/update |

**`study_attempts`** (migration V5)

| Column | Notes |
|---|---|
| user_id, card_id, deck_id | Who answered what, in which deck (deck denormalized) |
| mode | `MULTIPLE_CHOICE` (CHECK constraint; extend when new modes ship) |
| is_correct | Grading result |
| selected_card_id | Option the user picked; NULL = skipped |
| answered_at | Timestamp |

Lazy creation: rows in `card_progress` are only written on the **first answer or star toggle**. Not-yet-studied cards cost zero storage.

---

## 5. API Workflow

All endpoints under `/api/decks/{deckId}/study` — **authentication (JWT) required**, even for GET. Base URL: `http://localhost:8081`.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/study/quiz?count=10&state=&starred=` | Get a batch of questions |
| POST | `/study/answer` | Submit one answer → graded + state updated |
| GET | `/study/progress` | Deck-wide progress for the caller |
| POST | `/study/cards/{cardId}/star` | Toggle starred flag |

### SRS review queue (mode 2) — `/api/reviews`

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/reviews/{cardId}/save` | Add one word to the review queue — due immediately, idempotent |
| POST | `/api/reviews/decks/{deckId}/enroll` | Add every card of a deck — idempotent, returns counts |
| GET | `/api/reviews/due?limit=20` | Cards due for review right now, oldest due first |
| POST | `/api/reviews/{cardId}` | Grade a review: `{"quality": 1..4}` → SM-2 reschedules the card |
| DELETE | `/api/reviews/{cardId}` | Remove one word from the queue (MCQ progress untouched) |

### Typical session per mode

```
MCQ:  GET study/quiz → for each question: POST study/answer
SRS:  one-time: enroll deck (or save individual words)
      daily: GET reviews/due → for each card: POST reviews/{cardId} {quality}
```

### 5.1 Full learning session (example)

Learn the word **"ambition"** from deck 2:

```
Step 1 — GET /api/decks/2/study/quiz?count=2
         Authorization: Bearer <token>

Response (correct answer NOT included):
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
        { "cardId": 8, "text": "permission" } ] },
    { "cardId": 8, "prompt": "sự cho phép", "options": [ ... ] }
  ]
}

Step 2 — user picks "ambition" → POST /api/decks/2/study/answer
         { "cardId": 5, "selectedCardId": 5 }

Response:
{ "correct": true, "cardId": 5, "correctCardId": 5, "correctTerm": "ambition",
  "state": "STILL_LEARNING", "consecutiveCorrect": 1,
  "deckProgress": { "total": 4, "mastered": 0, "stillLearning": 1,
                    "notLearned": 3, "percent": 0.0 } }

Step 3 — same card answered correctly again → state MASTERED, streak 2,
         deckProgress.percent rises (1/4 = 25.0)

Step 4 — later mistake on card 5 (picked cardId 6):
         → correct: false, state STILL_LEARNING (demoted), streak 0
```

### 5.2 Request/response details

**GET `/study/quiz`** query params (all optional):

| Param | Values | Effect |
|---|---|---|
| `count` | 1–50 (default 10) | Number of questions |
| `state` | `NOT_LEARNED` / `STILL_LEARNING` / `MASTERED` | Only draw questions from cards in this state |
| `starred` | `true` / `false` | Only draw from (non-)starred cards |

Combine filters, e.g. `?starred=true&state=STILL_LEARNING` = "drill my difficult words I'm still learning".

**POST `/study/answer`** request:

| Field | Required | Notes |
|---|---|---|
| `cardId` | yes | The card asked about |
| `selectedCardId` | no | Option picked; omit/null = skip (graded incorrect) |
| `mode` | no | Defaults to `MULTIPLE_CHOICE` |

**POST `/study/cards/{cardId}/star`** — no body. Response includes new starred value. Starring works on public decks too (it is a personal note) and creates a `card_progress` row if none exists (state `NOT_LEARNED`, starred).

### 5.3 Error handling

| Situation | Result |
|---|---|
| No/invalid JWT on any study endpoint | 401/403 (matched before the public deck-read rules) |
| Deck not found, or private deck of another user | 404 |
| `cardId` not in this deck | 404 |
| Unknown `state` value in quiz filter | 400 |
| Unknown `mode` value | 400 |
| `cardId` missing in answer body | 400 |

---

## 6. How Quizzes Are Generated (server logic)

1. Resolve deck — must be public or owned by caller
2. Load caller's progress rows for the deck's cards (missing = `NOT_LEARNED`)
3. Apply `state` / `starred` filters → question pool
4. Shuffle pool, take up to `count` (max 50) questions
5. Per question: correct option + up to 3 distractors **from the same deck**, options shuffled
6. Return questions without any correctness information

Edge cases:

- Deck with < 4 cards → fewer options per question (distractor pool is the deck itself)
- Empty filtered pool → `questions: []` (HTTP 200, not an error)
- Question order and option order are randomized every call (prevents memorizing by position)

---

## 7. How Answers Are Graded (server logic)

```
1. card      = find request.cardId in this deck        (else 404)
2. selected  = find request.selectedCardId in this deck (unknown → null)
3. correct   = (selected != null && selected.id == card.id)
4. progress  = find-or-create card_progress(user, card)
5. correct ? progress.recordCorrect() : progress.recordIncorrect()   ← state machine
6. persist progress row; insert study_attempt(mode, is_correct, selected_card_id)
7. respond: correct + fresh state/streak + deck-wide progress
```

Trust rule: the client declares *what was picked*, never *whether it was right*. The server owns correctness.

---

## 8. Progress Calculation

Per deck, for the calling user:

```
mastered      = cards with state MASTERED
stillLearning = cards with state STILL_LEARNING
notLearned    = total − mastered − stillLearning   (includes never-touched cards)
percent       = round(mastered / total × 100, 1 decimal)     (empty deck = 0.0)
```

Use it for: deck dashboard, "continue studying" prompts, completion badges.

---

## 9. Client Integration Guidelines

Do:

- Store the JWT; send `Authorization: Bearer` on **every** study call (including GET quiz/progress)
- Render question: `prompt` + shuffled-already `options` (server shuffled; re-shuffling optional)
- Submit exactly one `answer` call per question; drive next-screen state from the response (`correct`, `state`, `streak`)
- Show feedback from `correctCardId`/`correctTerm` when wrong
- Use `?state=STILL_LEARNING&starred=true` for "tough words" drill mode
- Treat `questions: []` as "nothing to study" (empty state screen)

Don't:

- Never trust a client-side correct/wrong flag — impossible anyway, server won't accept one
- Don't cache quiz questions across sessions with answers marked locally — grading always server-side
- Don't guess state locally; always read `state`/`deckProgress` from the answer/progress responses

Suggested session loop:

```
while progress.percent < 100:
    quiz  = GET /study/quiz?state=STILL_LEARNING (fallback: no filter)
    for q in quiz.questions:
        answer = POST /study/answer { cardId: q.cardId, selectedCardId: picked }
        show feedback(answer)
    if quiz.questions == [] and no NOT_LEARNED left: break
```

---

## 10. Design Decisions & Rationale

| Decision | Rationale |
|---|---|
| Progress in separate `card_progress` table, not a column on `cards` | State is per user; a card is shared content. Column would break multi-learner decks |
| Missing row = NOT_LEARNED | Zero storage for untouched cards; no backfill migration needed |
| Server-side grading only | Client cannot cheat by posting `correct: true` |
| Distractors from same deck | Plausible confusions (ambition vs ambitous) — harder and more useful than random words |
| Attempt history table now, review endpoint later | Cheap to write every answer; enables "my mistakes" + SRS without backfill |
| Skip = incorrect | Matches conservative learning: only demonstrated knowledge counts |
| All study endpoints require auth | Progress is personal; anonymous studying has nothing to persist |
| Two modes with separate tables | MCQ (instant, quiz-driven) and SRS (scheduled, memory-driven) serve different demands; coupling them would force one workflow on everyone. Enrollment endpoints (`save`/`enroll`) are the explicit bridge |
| SRS enrollment due-immediately | A saved word should be revisable right away; SM-2 takes over after the first graded review |

---

## 11. Roadmap

1. **Review lists** — `GET /api/decks/{id}/study/mistakes` over `study_attempts` (schema ready)
2. **Reverse direction** — `TERM_TO_DEFINITION` MCQ (prompt = term, options = definitions)
3. **FLASHCARD / WRITING modes** — new `StudyMode` values + widen V5 CHECK in a new migration
4. **Cross-mode integration** — e.g. auto-save MCQ-mastered words into the SRS queue, or unified dashboard combining both modes' progress
