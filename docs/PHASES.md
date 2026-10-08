# Phase guide: verify it, explain it, break it

For every phase: **Verify** (what you should see), **5 interview questions** with short model answers, and **one bug exercise**.
Do the bug exercises. Debugging something you broke on purpose is the fastest way to truly own the code.

---
## Phase 1: Backend skeleton (config, auth, DB, migration, Docker)
**Verify**
```bash
cd backend && pytest tests/test_auth.py tests/test_migrations.py -q       # all green
alembic upgrade head && uvicorn app.main:app --port 8000
curl localhost:8000/health   # {"status":"ok"}   curl -i localhost:8000/sync/expenses -X POST  # 401
```
**Interview Qs**
1. *How does the server authenticate users?* The app sends a Firebase ID token as a Bearer header; `firebase_admin.auth.verify_id_token` checks the signature against Google's public keys plus expiry, audience and issuer. The `uid` claim becomes the user id.
2. *Why is the verifier injected as a FastAPI dependency?* So tests can replace it with a fake and never touch Firebase; it's also a single choke point for auth.
3. *Why 503 for `CertificateFetchError` but 401 for a bad token?* 401 means "your credentials are wrong"; failing to fetch Google's keys is our infrastructure problem, and clients shouldn't sign the user out for it.
4. *Why Alembic instead of `create_all`?* `create_all` can't alter existing tables. Migrations are versioned, reviewable and reversible (the test runs upgrade and downgrade).
5. *How is it "Postgres-ready"?* The URL normaliser accepts Render's `postgres://`, `psycopg` is installed, and the models use portable types (BigInteger, String) with no SQLite-only features.

**Bug exercise:** in `auth.py` change `except (ValueError, FirebaseError)` to `except ValueError`. *Symptom:* an expired token now raises an unhandled `ExpiredIdTokenError` → HTTP 500 instead of 401; `test_bad_or_expired_token_is_401[...Expired...]` fails. *Debug:* read the pytest traceback, check which exception class escaped, and look at the exception hierarchy (`FirebaseError` is not a `ValueError`).

---
## Phase 2: AI endpoint (Gemini structured output)
**Verify**
```bash
pytest tests/test_ai.py -q      # 8 sample texts, refunds, garbage model output, rate limit
# optional, costs a few tokens: set GEMINI_API_KEY + GEMINI_MODEL, then
pytest -m live -q
```
**Interview Qs**
1. *How do you stop the LLM returning garbage?* Request JSON with `response_schema`, then validate with Pydantic (enum categories, bounds on amount/confidence). Any failure → 422 with a clear message, never a 500.
2. *How do you handle prompt injection?* The pasted text is wrapped in `<untrusted_text>` and the system prompt says it is data. More importantly, output is schema-validated and only pre-fills a form the user confirms, so an injected instruction can't take any action.
3. *Why `amountMinor` and not a float from the model?* Same reason as the DB: no float drift. The prompt asks for minor units; Pydantic bounds it (0 to 10^12).
4. *Why a per-user limiter instead of per-IP?* Mobile users share carrier NAT IPs; the verified uid is a fairer key. In-memory means per-process; with several instances you'd use Redis.
5. *Your tests mock Gemini. What do they prove?* Our mapping, validation, error handling and limits, not the model's accuracy. Accuracy is covered by the optional `-m live` tests; I say that openly.

**Bug exercise:** remove `temperature=0` and the `<untrusted_text>` wrapper. *Symptom:* nothing fails in mocked tests, but live parsing becomes inconsistent and "ignore previous instructions" strings sometimes win. *Lesson/debug:* mocked tests can't catch prompt regressions, which is exactly why `test_parser_wraps_text_as_untrusted...` asserts on the request sent to the SDK.

---
## Phase 3: Sync endpoints
**Verify** `pytest tests/test_sync.py -q` (conflicts, tombstones, isolation, future timestamps, deltas).
**Interview Qs**
1. *Explain your conflict rule.* Last-write-wins on the client `updatedAt`: higher wins, equal is accepted (idempotent retry), lower is rejected as `stale` and the winning row is sent back.
2. *Why two timestamps?* `updatedAt` (client) decides who wins; `synced_at` (server) decides what other devices need to download. A late-arriving old edit still has a fresh `synced_at`, so every device receives it.
3. *Why soft deletes?* A hard delete leaves nothing to sync; other devices would keep the row forever. The tombstone is just another LWW change.
4. *What can go wrong with LWW?* Clock skew and silently lost concurrent edits. Mitigations here: reject timestamps >5 min in the future; per-row granularity; documented trade-off (CRDTs/field-level merge are the heavier alternative).
5. *Security of sync?* `userId` comes from the token; an id owned by someone else is rejected as `forbidden` (never overwritten); budgets must use `uid:CATEGORY` ids.

**Bug exercise:** change `ch.updated_at < existing.updated_at` to `<=`. *Symptom:* a retried identical request is rejected as `stale` → `test_retry_is_idempotent` fails; on a real phone a flaky network would produce spurious FAILED rows. *Debug:* run that one test, read the assertion diff.

---
## Phase 4: Android setup (Gradle, Hilt, theme, nav, auth)
**Verify** Create the project (android/README), run on an emulator: splash → Login. Register → lands on the Expenses tab; kill & reopen the app → still signed in; Settings → Sign out → back to Login.
**Interview Qs**
1. *Why a version catalog?* One place for versions, type-safe accessors, easy bumps, shared plugin versions.
2. *How does the auth gate work?* `AuthState` (Loading/SignedOut/SignedIn) from a Firebase listener turns into a `StateFlow`; the root composable swaps whole nav graphs. Sign-out can't leave stale screens.
3. *Why is the splash held?* `setKeepOnScreenCondition` keeps it until auth and lock state are known, otherwise users see a login flash then the app.
4. *Why Credential Manager?* `GoogleSignIn` is deprecated; Credential Manager is the unified API; the Google ID token is exchanged for a Firebase credential.
5. *Why KSP not kapt?* KSP is faster and the supported path; kapt is maintenance mode.

**Bug exercise:** use a SHA-1 from a different keystore in Firebase (see Phase 0). *Symptom:* email login works, Google sign-in fails. *Debug:* `./gradlew signingReport`, compare fingerprints.

---
## Phase 5: Room layer
**Verify** `./gradlew testDebugUnitTest --tests "*ExpenseDaoTest"` (12 tests).
**Interview Qs**
1. *Why Long minor units?* Exact arithmetic; floats can't represent 0.10. Format only at the UI edge.
2. *Why client-generated UUID keys?* Create offline with no server; no collisions to resolve.
3. *Why `markSyncedIfUnchanged`?* If the user edits while the request is in flight, a plain "mark synced" would hide the new edit; the `updatedAt` guard keeps it PENDING.
4. *Why no `fallbackToDestructiveMigration`?* It would wipe unsynced expenses. We export schemas and write real migrations.
5. *Why bucket months in Kotlin?* SQLite's `localtime` is unreliable on Android; `java.time` with the device zone is correct and testable.

**Bug exercise:** change `spentAt < :toMs` to `<=` in a query. *Symptom:* an expense at exactly midnight on the 1st is counted in two months. `categoryTotalsExcludeDeletedAndOutOfRange` doesn't catch every case, so add a test at the boundary: that's the point of the exercise.

---
## Phase 6: Expenses UI
**Verify** Add/edit/delete; swipe left → snackbar with Undo; type in search (results update ~250 ms later); pick a date range; rotate the screen mid-search (state survives). `./gradlew testDebugUnitTest --tests "*ViewModel*"`.
**Interview Qs**
1. *Explain UDF.* Events go up (callbacks), state flows down (`StateFlow<UiState>`); the screen is a pure function of state.
2. *Why a Channel for the Undo snackbar?* It's a one-off effect; in `UiState` it would re-show after rotation.
3. *Why `collectAsStateWithLifecycle`?* It stops collecting when the UI is stopped, saving work and avoiding leaks.
4. *Why `flatMapLatest` + debounce?* New filters cancel the previous DB query; debounce avoids a query per keystroke.
5. *Why `WhileSubscribed(5000)`?* Survives rotation (5 s grace) but stops upstream work when the screen is really gone.

**Bug exercise:** remove `key = { it.id }` from `items(...)`. *Symptom:* after swipe-delete and undo, rows flicker or a swiped row appears "stuck" half-dismissed, because Compose reuses state by position. *Debug:* reproduce, then restore the key.

---
## Phase 7: Networking and Smart Add
**Verify** With backend + real Gemini key: Add → paste `Rs 450 spent at Swiggy on 12 Oct via UPI` → *Fill form* pre-fills amount 450.00, Swiggy, Food. Paste a refund → red error message. Turn on airplane mode → "No internet connection". Tests: `SafeApiCallTest`.
**Interview Qs**
1. *How does 401 handling work?* `TokenAuthenticator` runs on a 401, force-refreshes the Firebase token and retries once; guards stop loops (prior response, same token).
2. *Interceptor vs Authenticator?* The interceptor attaches the token to every request; the authenticator reacts to a 401 challenge.
3. *Why a sealed `AppResult`/`AppError`?* Callers must handle every failure type explicitly; no stringly-typed errors, exhaustive `when`.
4. *Why must `safeApiCall` rethrow `CancellationException`?* Swallowing it breaks structured concurrency (cancelled coroutines keep running).
5. *Why never auto-save AI output?* LLMs are probabilistic; money data needs human confirmation. Confidence under 0.6 shows a warning.

**Bug exercise:** in `safeApiCall` add `catch (e: Exception)` above the others. *Symptom:* leaving the screen during a request logs a bogus "Unknown error" and may update dead UI state; the cancellation test fails. *Debug:* `cancellationIsNeverSwallowed`.

---
## Phase 8: Sync
**Verify** Airplane mode → add 3 expenses (cloud icon = pending) → turn network on → icons disappear within seconds (WorkManager runs when CONNECTED). `adb shell dumpsys jobscheduler | grep spendwise` shows queued work. Tests: `SyncRepositoryTest` (10).
**Interview Qs**
1. *Why WorkManager?* Guaranteed, constraint-aware (CONNECTED), survives process death and reboot, built-in backoff.
2. *Why `APPEND_OR_REPLACE`?* An edit during a running sync must get its own pass, otherwise it waits for the 15-min periodic job.
3. *When do you retry vs fail?* Offline/5xx/429/401 → `Result.retry()` with exponential backoff (max 5); 4xx validation → mark FAILED, don't retry forever.
4. *Why the Mutex?* Periodic and one-shot workers can overlap and race on the cursor.
5. *What if the cursor and rows disagree?* The cursor only advances after a successful response and applying changes; re-delivery is harmless because applying is idempotent (strictly-newer rule).

**Bug exercise:** advance the cursor before applying server changes, then kill the app in between. *Symptom:* changes from other devices are permanently skipped. *Debug:* reason about ordering; this is why `putString` is last.

---
## Phase 9: Dashboard and budgets
**Verify** Add expenses across months; Dashboard shows donut, 6 bars, top 3 merchants. Set a Food budget of ₹1000, add ₹850 → notification at 80%; add ₹200 → 100% (grant the notification permission on Android 13+).
**Interview Qs**
1. *Why Vico + custom donut?* Vico is Compose-native and maintained for bars; it has no pie chart, a donut is ~20 lines of Canvas.
2. *Why convert paise to Double only in the chart?* Display-only; all totals stay integer.
3. *How do you avoid duplicate notifications?* We store the last notified level per (user, category, month) and only notify when the level increases.
4. *Why integer math for thresholds?* `spent*100 >= limit*80` avoids float rounding at exactly 80%.
5. *Why is `Budget.id = uid:CATEGORY`?* Two offline devices can't create duplicate budgets for one category; LWW resolves it.

**Bug exercise:** compare with `>` instead of `>=` for the 100% rule. *Symptom:* spending exactly the limit shows no alert; `alertThresholdsAtExactBoundaries` fails. Boundary bugs are what interviewers probe.

---
## Phase 10: Security and polish
**Verify** Settings → enable lock → background the app 30+ s → reopen → prompt. Release build: `adb shell run-as com.spendwise.app` fails; cleartext to a plain-http host fails. `./gradlew assembleRelease` succeeds with R8 and the app still parses responses.
**Interview Qs**
1. *Where are secrets stored?* Firebase manages its own tokens; our app state is encrypted with Tink AES-GCM, with the key wrapped by the Android Keystore. We don't duplicate Firebase tokens.
2. *Why disable backups?* Financial data would be copied to the cloud/other devices; the Tink key can't be restored anyway.
3. *What does the network security config do?* HTTPS only, system CAs only; a debug-only override permits `10.0.2.2`.
4. *What does R8 break and how did you handle it?* Reflection-based serialization/generics; keep rules for DTO serializers and generic signatures.
5. *Why a grace period on the lock?* System dialogs (file picker) background the app and would otherwise re-lock it.

**Bug exercise:** remove the `-keep` rules for `data.remote`. *Symptom:* debug works, release crashes with a serialization error on the first API call. *Debug:* R8 mapping file + `adb logcat`; always smoke-test the release build.

---
## Phase 11: Testing pass and release
**Verify** `./gradlew testDebugUnitTest` (71 tests) and `pytest` (63); push a branch and watch CI go green; tag `v1.0.0` for the signed APK.
**Interview Qs**
1. *What's your test strategy?* Pure logic as plain JVM tests; Room with real SQLite via Robolectric; ViewModels with a fake repo and Turbine; one UI test per critical stateless composable; backend with dependency overrides.
2. *Why Robolectric for DAO tests?* Real SQL on the JVM: fast CI, no emulator.
3. *How do you test debounce?* `runTest` virtual time + `advanceTimeBy`.
4. *How does CI build without secrets?* A placeholder `google-services.json` (with an oauth client so `default_web_client_id` is generated); releases restore real secrets from GitHub Secrets.
5. *How is the APK signed safely?* Keystore as a base64 secret, decoded in CI into git-ignored files.

**Bug exercise:** delete the placeholder `google-services.json` step from CI. *Symptom:* "File google-services.json is missing" in the Gradle log. *Debug:* read the failing step's log.
