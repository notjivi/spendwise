import base64
import json
import logging
from collections.abc import Callable
from dataclasses import dataclass
from functools import lru_cache

import firebase_admin
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from firebase_admin import auth as fb_auth
from firebase_admin import credentials
from firebase_admin.exceptions import FirebaseError

from app.config import Settings, get_settings

log = logging.getLogger("spendwise.auth")
_bearer = HTTPBearer(auto_error=False)

# A verifier turns a raw ID token into its claims dict, or raises.
# Keeping it a plain callable lets tests swap it without touching Firebase.
Verifier = Callable[[str], dict]


@dataclass(frozen=True)
class CurrentUser:
    uid: str
    email: str | None = None


def _init_firebase(settings: Settings) -> None:
    if firebase_admin._apps:
        return
    if settings.firebase_credentials_b64:
        info = json.loads(base64.b64decode(settings.firebase_credentials_b64))
        cred = credentials.Certificate(info)
    elif settings.firebase_credentials_path:
        cred = credentials.Certificate(settings.firebase_credentials_path)
    else:
        cred = credentials.ApplicationDefault()
    firebase_admin.initialize_app(cred, {"projectId": settings.firebase_project_id or None})


@lru_cache
def firebase_verifier() -> Verifier:
    _init_firebase(get_settings())

    def verify(token: str) -> dict:
        return fb_auth.verify_id_token(token)  # checks signature, expiry, audience, issuer

    return verify


def get_current_user(
    creds: HTTPAuthorizationCredentials | None = Depends(_bearer),
    verifier: Verifier = Depends(firebase_verifier),
) -> CurrentUser:
    unauthorized = HTTPException(
        status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or missing credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    if creds is None or creds.scheme.lower() != "bearer":
        raise unauthorized
    try:
        claims = verifier(creds.credentials)
    except fb_auth.CertificateFetchError as e:
        # Google's public keys unreachable: our problem, not the client's.
        log.error("firebase cert fetch failed: %s", type(e).__name__)
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Auth temporarily unavailable")
    except (ValueError, FirebaseError) as e:
        # Never log the token itself; only the error class.
        log.info("token rejected: %s", type(e).__name__)
        raise unauthorized
    uid = claims.get("uid") or claims.get("sub")
    if not uid:
        raise unauthorized
    return CurrentUser(uid=uid, email=claims.get("email"))
