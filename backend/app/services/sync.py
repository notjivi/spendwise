import time
from collections.abc import Callable
from typing import Any

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db import Base
from app.schemas import Rejected

MAX_CLOCK_SKEW_MS = 5 * 60 * 1000


def now_ms() -> int:
    return int(time.time() * 1000)


def sync_entities(
    db: Session,
    *,
    model: type[Base],
    user_id: str,
    changes: list[Any],
    last_sync_at: int,
    to_row: Callable[[Any, str, int], dict],
    to_dto: Callable[[Any], Any],
    clock: Callable[[], int] | None = None,
):
    """Generic last-write-wins sync used by expenses and budgets.

    Rules:
    - The row with the larger client `updated_at` wins (ties accept: makes retries idempotent).
    - Deletes are soft (`is_deleted`), so a delete is just another change that can win or lose.
      A NEWER edit beats an OLDER delete (LWW is symmetric); that's a deliberate, documented choice.
    - A row id owned by another user is rejected (never overwritten, never revealed).
    - `updated_at` far in the future is rejected, otherwise one bad clock would win forever.
    - Server->client delta is selected by the SERVER clock (`synced_at`), not the client's.
    """
    server_time = (clock or now_ms)()  # resolved at call time so tests can patch now_ms
    accepted: list[str] = []
    rejected: list[Rejected] = []
    stale_ids: list[str] = []
    seen: set[str] = set()

    for ch in changes:
        if ch.id in seen:
            rejected.append(Rejected(id=ch.id, reason="duplicate_in_batch"))
            continue
        seen.add(ch.id)
        if ch.updated_at > server_time + MAX_CLOCK_SKEW_MS:
            rejected.append(Rejected(id=ch.id, reason="updated_at_in_future"))
            continue
        existing = db.get(model, ch.id)
        if existing is not None and existing.user_id != user_id:
            rejected.append(Rejected(id=ch.id, reason="forbidden"))
            continue
        if existing is not None and ch.updated_at < existing.updated_at:
            rejected.append(Rejected(id=ch.id, reason="stale"))
            stale_ids.append(ch.id)
            continue
        values = to_row(ch, user_id, server_time)
        if existing is None:
            db.add(model(**values))
        else:
            for k, v in values.items():
                setattr(existing, k, v)
        accepted.append(ch.id)
    db.commit()

    q = select(model).where(model.user_id == user_id, model.synced_at > last_sync_at)
    rows = list(db.scalars(q))
    # Don't echo back what this client just sent and we accepted; DO send winners that beat it.
    accepted_set = set(accepted)
    server_changes = [to_dto(r) for r in rows if r.id not in accepted_set]
    have = {r.id for r in rows}
    for sid in stale_ids:
        if sid not in have:
            server_changes.append(to_dto(db.get(model, sid)))
    return accepted, rejected, server_changes, server_time
