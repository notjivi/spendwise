# SpendWise

Offline-first Android expense tracker with AI-assisted entry. Paste an SMS or receipt and Gemini
extracts amount, merchant, category and date. You confirm, then it's saved locally and synced when online.

> Screenshots: _add after first run_ → `docs/screenshots/` (expenses, smart add, dashboard, budgets).

| | |
|---|---|
| **Android** | Kotlin, Jetpack Compose + Material 3, MVVM + UDF (StateFlow), Hilt, Room, Retrofit/OkHttp, kotlinx.serialization, WorkManager, Firebase Auth (Credential Manager), Tink + DataStore, BiometricPrompt, Vico |
| **Backend** | FastAPI, Pydantic v2, SQLAlchemy 2.0 + Alembic, firebase-admin, google-genai (Gemini structured output) |
| **Tests** | 63 backend (pytest) · 71 Android unit (JUnit, MockK, Turbine, Robolectric/Room) · 3 Compose UI |

## Features
Email + Google sign-in · add/edit/delete expenses · **Smart add** from pasted text · day-grouped list with
search, category and date-range filters · swipe-to-delete with undo · dashboard (donut, 6-month bars, top merchants) ·
budgets with 80% / 100% notifications · full offline CRUD with background sync (last-write-wins, soft deletes) ·
CSV export (Storage Access Framework) · dark mode + dynamic color · optional biometric app lock.

## Architecture
See [docs/architecture.md](docs/architecture.md). API docs: run the backend and open `/docs`.

## Setup
1. **Firebase**: follow the Phase 0 steps (project, Email + Google providers, Android app, SHA-1, service account).
2. **Backend** (see [backend/README.md](backend/README.md)):
   ```bash
   cd backend && python -m venv .venv && source .venv/bin/activate
   pip install -r requirements-dev.txt && cp .env.example .env   # fill in values
   alembic upgrade head && uvicorn app.main:app --reload
   pytest
   ```
3. **Android** (see [android/README.md](android/README.md)): open `android/` in Android Studio, add `app/google-services.json`, run on an emulator
   (debug builds call `http://10.0.2.2:8000`).

## Design decisions
| Decision | Why |
|---|---|
| Room is the single source of truth | UI works offline and never shows half-loaded network state |
| Money as `Long` paise | exact arithmetic; floats drift |
| Client UUIDs | rows can be created offline without a server round trip |
| LWW with `updatedAt` + soft deletes | simple, predictable; tombstones let deletes propagate |
| Separate server `synced_at` | client clocks can't be trusted to decide what other devices receive |
| Deterministic budget id `uid:CATEGORY` | offline duplicates are impossible by construction |
| Hilt over manual DI | worker + ViewModel injection with little boilerplate; compile-time graph |
| Vico for bars, Canvas for donut | Vico has no pie chart; a donut is ~20 lines of Canvas |
| Tink + DataStore, not EncryptedSharedPreferences | `security-crypto` is deprecated |
| AI output only pre-fills the form | model errors can't silently corrupt data; schema-validated on the server |
| In-memory rate limiter | one free-tier instance; swap for Redis when scaling out |

## Security notes
HTTPS-only release (cleartext allowed only for the emulator host in debug) · backups disabled · R8 on ·
server verifies Firebase ID tokens and derives the user from them · Gemini key never ships in the APK ·
prompt-injection: input treated as data + strict schema validation · no token/body logging · CSV formula-injection guard.

## Release
Tag `v1.0.0` → GitHub Actions builds, tests and attaches a signed APK to the Release (see `.github/workflows/release.yml`).
Required repo secrets: `GOOGLE_SERVICES_JSON_B64`, `RELEASE_KEYSTORE_B64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`; variable `SPENDWISE_API_URL`.
