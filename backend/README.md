# SpendWise backend

FastAPI + SQLAlchemy 2 + Alembic. Python 3.12.

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env            # fill GEMINI_*, FIREBASE_*
alembic upgrade head
uvicorn app.main:app --reload   # http://localhost:8000/docs
pytest                          # 63 tests, no network, no Firebase, no Gemini needed
pytest -m live                  # optional: real-Gemini accuracy checks
```

Docker: `docker build -t spendwise-api . && docker run --env-file .env -p 8000:8000 spendwise-api`

Deploy (Render): New Web Service -> Docker -> root dir `backend`. Env vars from `.env.example`;
set `FIREBASE_CREDENTIALS_B64` to `base64 -w0 firebase-sa.json`. For persistence use a Render Postgres
and set `DATABASE_URL` (the `postgres://` form is accepted). SQLite on the free tier is wiped on redeploy.
