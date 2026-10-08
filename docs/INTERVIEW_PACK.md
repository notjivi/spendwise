# Interview pack

## Before you use any of this: make every claim true
I could run the backend (63 tests pass) but **could not compile or run the Android app** while writing this.
Before an interview: build it, fix any compile errors (paste them to Claude), run `./gradlew testDebugUnitTest`,
use the app on an emulator, and replace the numbers below with what YOU measured. Never claim what you haven't run.

## 2-minute walkthrough
"SpendWise is an offline-first expense tracker I built end to end: a Kotlin/Compose app and a FastAPI backend.
The core idea: **Room is the single source of truth**. Every create/edit/delete writes locally first and is marked
pending, so the app is fully usable offline. WorkManager syncs when there's a connection using a last-write-wins
protocol with soft deletes. The interesting feature is **Smart add**: you paste a bank SMS, my backend asks Gemini for
structured JSON, validates it against a strict schema, and the app pre-fills the form. It never auto-saves AI output.
Money is stored as Long paise to avoid float errors. Auth is Firebase; the backend verifies the ID token and derives the user
from it. I wrote [N] tests. The part I'm proudest of is the sync design: two clocks, idempotent retries, and an
in-flight-edit guard, and I can walk through the edge cases."

## 10-minute deep dive outline
1. **0-1** Problem and constraints (offline, money correctness, AI is untrusted).
2. **1-3** Architecture: Compose → ViewModel(StateFlow) → Repository → Room; network only through sync. Draw `docs/architecture.md`.
3. **3-6** Sync deep dive: PENDING/SYNCED/FAILED; `updatedAt` vs `synced_at`; stale/forbidden/future rejections; `markSyncedIfUnchanged`; cursor ordering; WorkManager policies.
4. **6-8** Smart add: structured output, Pydantic validation, injection stance, 422/502 mapping, confirm-before-save, rate limiting.
5. **8-9** Security: token verification, no userId from body, Tink + Keystore, network config, R8, backups, CSV injection.
6. **9-10** Testing and what I'd do next (CRDT/field-level merge, Redis limiter, Paging 3, Compose UI tests, Crashlytics).

## 25 Q&As
**Architecture**
1. *Why MVVM + UDF?* Screens are functions of one immutable state; easy to test (feed events, assert states) and survives config changes.
2. *Why no separate domain layer / use cases?* Logic is thin; repositories already encapsulate it. I'd add use cases when logic is shared by several ViewModels. (State the trade-off honestly.)
3. *Why Repository pattern?* One place decides local vs remote and hides Room/Retrofit from ViewModels; swap fakes in tests.
4. *Why a single Activity?* Navigation Compose handles destinations; cheaper than multiple Activities, shared theme/state.
5. *How does the auth gate avoid stale screens?* Separate nav graphs swapped by `AuthState`.

**Room**
6. *Why Room over DataStore?* Relational queries (filter/search/aggregates) and Flow observation. DataStore is for small key-value settings.
7. *Why store enums via converters?* Explicit control and safe fallback (`fromWire` → OTHER) if a newer version adds a value.
8. *How do Room Flows work?* Room tracks table invalidation and re-runs the query, emitting a new list.
9. *What's `@Upsert`?* Insert-or-update by primary key in one call.
10. *How would you migrate the schema?* Increase version, write a `Migration`, test it with exported schema JSON; never destructive fallback because rows hold unsynced data.

**Coroutines / Flow**
11. *`stateIn(WhileSubscribed(5000))`?* Hot StateFlow sharing the upstream; survives rotation, stops after 5 s with no collectors.
12. *`flatMapLatest` vs `flatMapConcat`?* Latest cancels the stale query when filters change.
13. *Why Channel for events?* Exactly-once delivery of one-off effects; StateFlow would replay them.
14. *Where is blocking code?* Only in OkHttp interceptors (on OkHttp threads) via `Tasks.await`; everything else suspends.
15. *CancellationException?* Must be rethrown; otherwise structured concurrency breaks.

**Compose state**
16. *State hoisting?* `AuthForm` is stateless (state in, events out) → previewable and UI-testable.
17. *`remember` vs `rememberSaveable`?* `remember` dies with the composition; saveable survives rotation/process death. Durable form state lives in the ViewModel.
18. *Why keys in LazyColumn?* Stable identity for animations and state with insertions/deletions.

**DI**
19. *Why Hilt?* Compile-time checked graph, `@HiltViewModel`, `@HiltWorker` support with minimal boilerplate. Manual DI is fine for tiny apps but gets noisy with workers/ViewModels.
20. *What's `@Binds` for?* Binds an interface to an implementation without writing a provider body (`TokenProvider`, `SyncTrigger`).

**Sync**
21. *Explain LWW and its weaknesses.* Higher `updatedAt` wins; concurrent edits to the same row lose one side silently; clock skew mitigated by future-timestamp rejection. Field-level merge/CRDTs are the upgrade.
22. *What if the same expense is edited on two offline devices?* Both push; the later `updatedAt` wins; the other device receives the winner as `stale` rejection + serverChange and converges.
23. *Idempotency?* Retried batches are safe: equal `updatedAt` is accepted and applying is "strictly newer only".

**Security**
24. *How do you protect the Gemini key and the user's data?* Key only on the server; HTTPS; token verified server-side; per-user data isolation; rate limit; no sensitive logs; Keystore-backed encryption; no backups.
25. *Prompt injection?* Treat input as data, strict schema validation, human confirmation, and the model has no tools or side effects.

## 5 resume bullets (verify each number first)
- Built **SpendWise**, an offline-first Android expense tracker (Kotlin, Jetpack Compose, Room, Hilt, WorkManager) with **8 screens** and an AI "smart add" feature that turns pasted bank SMS into structured expenses.
- Designed a last-write-wins sync protocol with soft deletes and idempotent retries across **2 synced entity types** and **5 REST endpoints** (FastAPI, SQLAlchemy 2, Alembic), covered by **63 backend tests** (conflicts, isolation, clock-skew).
- Integrated **Gemini structured output** with Pydantic validation, per-user rate limiting and prompt-injection safeguards; AI results require user confirmation before saving.
- Secured the stack: Firebase ID-token verification on the server, Tink/Keystore-encrypted local state, HTTPS-only network config, R8, biometric lock, CSV-injection guard.
- Wrote **71 JVM unit tests + 3 Compose UI tests** (JUnit, MockK, Turbine, Robolectric) and a GitHub Actions pipeline that runs both test suites and publishes a signed APK on release tags.
