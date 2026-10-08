import time
from collections import defaultdict, deque
from threading import Lock

from fastapi import Depends, HTTPException, status

from app.auth import CurrentUser, get_current_user
from app.config import get_settings


class SlidingWindowLimiter:
    """Per-key sliding window. In-memory => per-process; fine for one free-tier instance.
    With several instances you'd move this to Redis (documented trade-off)."""

    def __init__(self, limit: int, window_s: float = 60.0, clock=time.monotonic):
        self.limit, self.window_s, self.clock = limit, window_s, clock
        self._hits: dict[str, deque[float]] = defaultdict(deque)
        self._lock = Lock()

    def check(self, key: str) -> float | None:
        """Return None if allowed, else seconds until the next slot frees up."""
        now = self.clock()
        with self._lock:
            q = self._hits[key]
            while q and now - q[0] >= self.window_s:
                q.popleft()
            if len(q) >= self.limit:
                return self.window_s - (now - q[0])
            q.append(now)
            return None


_limiter: SlidingWindowLimiter | None = None


def get_limiter() -> SlidingWindowLimiter:
    global _limiter
    if _limiter is None:
        _limiter = SlidingWindowLimiter(get_settings().ai_rate_limit_per_min)
    return _limiter


def ai_rate_limit(
    user: CurrentUser = Depends(get_current_user),
    limiter: SlidingWindowLimiter = Depends(get_limiter),
) -> CurrentUser:
    # Keyed by verified uid (not IP): mobile users share carrier NAT IPs.
    retry = limiter.check(user.uid)
    if retry is not None:
        raise HTTPException(
            status.HTTP_429_TOO_MANY_REQUESTS,
            "Too many parse requests, slow down",
            headers={"Retry-After": str(int(retry) + 1)},
        )
    return user
