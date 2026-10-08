import uuid

import pytest

from app.services import sync as sync_mod
from tests.conftest import as_user

T0 = 1_760_000_000_000  # fixed "server now"


@pytest.fixture(autouse=True)
def frozen_server_clock(monkeypatch):
    state = {"now": T0}
    monkeypatch.setattr(sync_mod, "now_ms", lambda: state["now"])
    return state


def exp(id=None, *, updated=T0 - 1000, amount=45000, deleted=False, merchant="Swiggy"):
    return {
        "id": id or str(uuid.uuid4()), "amountMinor": amount, "currency": "INR", "category": "FOOD",
        "merchant": merchant, "note": None, "paymentMethod": "UPI", "spentAt": T0 - 5000,
        "createdAt": T0 - 9000, "updatedAt": updated, "isDeleted": deleted,
    }


def push(client, user, changes, last=0, path="expenses"):
    r = client.post(f"/sync/{path}", json={"changes": changes, "lastSyncAt": last}, headers=as_user(user))
    assert r.status_code == 200, r.text
    return r.json()


def test_requires_auth(client):
    assert client.post("/sync/expenses", json={"changes": [], "lastSyncAt": 0}).status_code == 401


def test_new_expense_is_accepted_and_not_echoed(client):
    e = exp()
    out = push(client, "alice", [e])
    assert out["accepted"] == [e["id"]] and out["rejected"] == [] and out["serverChanges"] == []
    assert out["serverTime"] == T0


def test_retry_is_idempotent(client):
    e = exp()
    push(client, "alice", [e])
    out = push(client, "alice", [e])  # same payload again (client never saw the first response)
    assert out["accepted"] == [e["id"]]
    assert len(push(client, "alice", [], last=0)["serverChanges"]) == 1  # still exactly one row


def test_newer_edit_wins(client):
    e = exp(updated=T0 - 2000, amount=100)
    push(client, "alice", [e])
    out = push(client, "alice", [{**e, "updatedAt": T0 - 1000, "amountMinor": 200}])
    assert out["accepted"] == [e["id"]]
    row = push(client, "alice", [], last=0)["serverChanges"][0]
    assert row["amountMinor"] == 200


def test_stale_edit_is_rejected_and_winner_is_sent_back(client):
    e = exp(updated=T0 - 1000, amount=200)
    push(client, "alice", [e])
    out = push(client, "alice", [{**e, "updatedAt": T0 - 5000, "amountMinor": 100}], last=T0 + 1)
    assert out["rejected"] == [{"id": e["id"], "reason": "stale"}]
    assert out["accepted"] == []
    # lastSyncAt is after the row was stored, so only the explicit "winner" path can deliver it
    assert [c["amountMinor"] for c in out["serverChanges"]] == [200]


def test_soft_delete_is_kept_and_propagates_as_tombstone(client):
    e = exp(updated=T0 - 2000)
    push(client, "alice", [e])
    push(client, "alice", [{**e, "updatedAt": T0 - 1000, "isDeleted": True}])
    got = push(client, "alice", [], last=0)["serverChanges"]
    assert len(got) == 1 and got[0]["isDeleted"] is True  # other devices learn about the delete


def test_newer_edit_beats_older_delete(client):
    e = exp(updated=T0 - 3000)
    push(client, "alice", [{**e, "isDeleted": True}])
    out = push(client, "alice", [{**e, "updatedAt": T0 - 1000, "isDeleted": False}])
    assert out["accepted"] == [e["id"]]
    assert push(client, "alice", [], last=0)["serverChanges"][0]["isDeleted"] is False


def test_older_edit_cannot_resurrect_deleted(client):
    e = exp(updated=T0 - 1000)
    push(client, "alice", [{**e, "isDeleted": True}])
    out = push(client, "alice", [{**e, "updatedAt": T0 - 4000, "isDeleted": False}])
    assert out["rejected"][0]["reason"] == "stale"


def test_second_device_receives_delta_only_after_cursor(client, frozen_server_clock):
    a, b = exp(), exp()
    push(client, "alice", [a])            # stored at T0
    frozen_server_clock["now"] = T0 + 10_000
    push(client, "alice", [b])            # stored at T0+10s
    delta = push(client, "alice", [], last=T0)["serverChanges"]
    assert [c["id"] for c in delta] == [b["id"]]


def test_users_are_isolated(client):
    e = exp()
    push(client, "alice", [e])
    assert push(client, "bob", [], last=0)["serverChanges"] == []


def test_cannot_overwrite_another_users_row(client):
    e = exp(amount=100)
    push(client, "alice", [e])
    out = push(client, "bob", [{**e, "updatedAt": T0 - 10, "amountMinor": 999999}])
    assert out["rejected"] == [{"id": e["id"], "reason": "forbidden"}] and out["accepted"] == []
    assert push(client, "alice", [], last=0)["serverChanges"][0]["amountMinor"] == 100


def test_future_timestamp_rejected(client):
    out = push(client, "alice", [exp(updated=T0 + 3_600_000)])
    assert out["rejected"][0]["reason"] == "updated_at_in_future"


def test_duplicate_ids_in_one_batch(client):
    e = exp()
    out = push(client, "alice", [e, {**e, "amountMinor": 7}])
    assert out["accepted"] == [e["id"]] and out["rejected"][0]["reason"] == "duplicate_in_batch"


@pytest.mark.parametrize(
    "patch",
    [{"amountMinor": 0}, {"amountMinor": -5}, {"category": "CRYPTO"}, {"currency": "RUPEES"},
     {"paymentMethod": "BARTER"}, {"id": "short"}],
)
def test_invalid_payloads_are_422(client, patch):
    r = client.post("/sync/expenses", json={"changes": [{**exp(), **patch}], "lastSyncAt": 0},
                    headers=as_user("alice"))
    assert r.status_code == 422


def test_user_id_in_body_is_ignored(client):
    e = {**exp(), "userId": "bob"}
    push(client, "alice", [e])
    assert push(client, "bob", [], last=0)["serverChanges"] == []
    assert len(push(client, "alice", [], last=0)["serverChanges"]) == 1


# ---- budgets ----
def bud(uid, category="FOOD", limit=500000, updated=T0 - 1000):
    return {"id": f"{uid}:{category}", "category": category, "monthlyLimitMinor": limit,
            "updatedAt": updated, "isDeleted": False}


def test_budget_roundtrip_and_lww(client):
    push(client, "alice", [bud("alice", limit=100)], path="budgets")
    out = push(client, "alice", [bud("alice", limit=50, updated=T0 - 9000)], path="budgets")
    assert out["rejected"][0]["reason"] == "stale"
    out = push(client, "alice", [bud("alice", limit=300, updated=T0 - 10)], path="budgets")
    assert out["accepted"] == ["alice:FOOD"]
    assert push(client, "alice", [], path="budgets")["serverChanges"][0]["monthlyLimitMinor"] == 300


def test_budget_id_must_match_uid_and_category(client):
    out = push(client, "alice", [bud("bob"), {**bud("alice"), "category": "TRAVEL"}], path="budgets")
    assert [r["reason"] for r in out["rejected"]] == ["invalid_id", "invalid_id"] and out["accepted"] == []


# ---- summary ----
def test_monthly_summary_excludes_deleted_and_other_months(client):
    from datetime import datetime, timedelta, timezone

    ist = timezone(timedelta(hours=5, minutes=30))
    ms = lambda y, m, d: int(datetime(y, m, d, 12, tzinfo=ist).timestamp() * 1000)  # noqa: E731
    rows = [
        {**exp(amount=1000), "spentAt": ms(2025, 10, 5)},
        {**exp(amount=2500), "spentAt": ms(2025, 10, 31)},
        {**exp(amount=9999), "spentAt": ms(2025, 11, 1)},
        {**exp(amount=7777, deleted=True), "spentAt": ms(2025, 10, 6)},
    ]
    push(client, "alice", rows)
    r = client.get("/summary/monthly?month=2025-10", headers=as_user("alice"))
    assert r.json() == {"month": "2025-10", "totals": [{"category": "FOOD", "totalMinor": 3500}],
                        "grandTotalMinor": 3500}
    assert client.get("/summary/monthly?month=2025-13", headers=as_user("alice")).status_code == 422
    assert client.get("/summary/monthly?month=2025-10", headers=as_user("bob")).json()["grandTotalMinor"] == 0
