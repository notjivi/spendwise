import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app import models  # noqa: F401
from app.auth import firebase_verifier
from app.db import Base, get_db
from app.main import create_app
from app.ratelimit import SlidingWindowLimiter, get_limiter
from app.schemas import LlmExpense
from app.services.gemini import get_parser

USERS = {"tok-alice": "alice", "tok-bob": "bob"}


def fake_verifier(token: str) -> dict:
    if token not in USERS:
        raise ValueError("bad token")
    return {"uid": USERS[token], "email": f"{USERS[token]}@example.com"}


class FakeParser:
    """Stands in for GeminiParser. `result` is what 'the model' returned; may be an exception."""

    def __init__(self):
        self.result: LlmExpense | Exception | None = None
        self.calls: list[str] = []

    async def parse(self, text, today):
        self.calls.append(text)
        if isinstance(self.result, Exception):
            raise self.result
        return self.result


@pytest.fixture
def parser():
    return FakeParser()


@pytest.fixture
def limiter():
    return SlidingWindowLimiter(limit=20)


@pytest.fixture
def client(parser, limiter):
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    Session = sessionmaker(bind=engine, expire_on_commit=False)

    def _db():
        with Session() as s:
            yield s

    app = create_app()
    app.dependency_overrides[get_db] = _db
    app.dependency_overrides[firebase_verifier] = lambda: fake_verifier
    app.dependency_overrides[get_parser] = lambda: parser
    app.dependency_overrides[get_limiter] = lambda: limiter
    return TestClient(app)


def as_user(name: str) -> dict:
    return {"Authorization": f"Bearer tok-{name}"}
