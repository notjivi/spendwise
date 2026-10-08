import logging

import pytest
from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient
from firebase_admin import auth as fb_auth

from app.auth import CurrentUser, firebase_verifier, get_current_user


def make_client(verifier):
    app = FastAPI()

    @app.get("/me")
    def me(u: CurrentUser = Depends(get_current_user)):
        return {"uid": u.uid, "email": u.email}

    app.dependency_overrides[firebase_verifier] = lambda: verifier
    return TestClient(app)


def ok_verifier(_t):
    return {"uid": "u1", "email": "a@b.c"}


def test_health_is_public(client):
    assert client.get("/health").json() == {"status": "ok"}


def test_valid_token_returns_user():
    r = make_client(ok_verifier).get("/me", headers={"Authorization": "Bearer abc"})
    assert r.status_code == 200 and r.json() == {"uid": "u1", "email": "a@b.c"}


def test_falls_back_to_sub_claim():
    c = make_client(lambda _t: {"sub": "u9"})
    assert c.get("/me", headers={"Authorization": "Bearer abc"}).json()["uid"] == "u9"


@pytest.mark.parametrize("headers", [{}, {"Authorization": "Basic abc"}, {"Authorization": "Bearer"}])
def test_missing_or_malformed_header_is_401(headers):
    r = make_client(ok_verifier).get("/me", headers=headers)
    assert r.status_code == 401
    assert r.headers["www-authenticate"] == "Bearer"


@pytest.mark.parametrize(
    "exc",
    [ValueError("garbage"), fb_auth.InvalidIdTokenError("bad"), fb_auth.ExpiredIdTokenError("old", None)],
)
def test_bad_or_expired_token_is_401(exc):
    def boom(_t):
        raise exc

    assert make_client(boom).get("/me", headers={"Authorization": "Bearer x"}).status_code == 401


def test_cert_fetch_failure_is_503_not_401():
    def boom(_t):
        raise fb_auth.CertificateFetchError("net", None)

    assert make_client(boom).get("/me", headers={"Authorization": "Bearer x"}).status_code == 503


def test_token_without_uid_is_401():
    assert make_client(lambda _t: {}).get("/me", headers={"Authorization": "Bearer x"}).status_code == 401


def test_token_never_logged(caplog):
    secret = "SUPER-SECRET-TOKEN-VALUE"

    def boom(_t):
        raise ValueError(f"bad {secret}")

    with caplog.at_level(logging.DEBUG):
        make_client(boom).get("/me", headers={"Authorization": f"Bearer {secret}"})
    assert secret not in caplog.text
