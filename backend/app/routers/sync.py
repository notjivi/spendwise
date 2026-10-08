from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth import CurrentUser, get_current_user
from app.db import get_db
from app.models import BudgetRow, ExpenseRow
from app.schemas import BudgetDto, ExpenseDto, Rejected, SyncRequest, SyncResponse
from app.services.sync import sync_entities

router = APIRouter(prefix="/sync", tags=["sync"])


def _expense_row(c: ExpenseDto, uid: str, server_time: int) -> dict:
    return dict(
        id=c.id, user_id=uid, amount_minor=c.amount_minor, currency=c.currency.upper(),
        category=c.category.value, merchant=c.merchant, note=c.note,
        payment_method=c.payment_method.value, spent_at=c.spent_at, created_at=c.created_at,
        updated_at=c.updated_at, is_deleted=c.is_deleted, synced_at=server_time,
    )


def _expense_dto(r: ExpenseRow) -> ExpenseDto:
    return ExpenseDto(
        id=r.id, amount_minor=r.amount_minor, currency=r.currency, category=r.category,
        merchant=r.merchant, note=r.note, payment_method=r.payment_method, spent_at=r.spent_at,
        created_at=r.created_at, updated_at=r.updated_at, is_deleted=r.is_deleted,
    )


def _budget_row(c: BudgetDto, uid: str, server_time: int) -> dict:
    return dict(
        id=c.id, user_id=uid, category=c.category.value, monthly_limit_minor=c.monthly_limit_minor,
        updated_at=c.updated_at, is_deleted=c.is_deleted, synced_at=server_time,
    )


def _budget_dto(r: BudgetRow) -> BudgetDto:
    return BudgetDto(
        id=r.id, category=r.category, monthly_limit_minor=r.monthly_limit_minor,
        updated_at=r.updated_at, is_deleted=r.is_deleted,
    )


@router.post("/expenses", response_model=SyncResponse[ExpenseDto])
def sync_expenses(
    body: SyncRequest[ExpenseDto],
    user: CurrentUser = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    acc, rej, srv, t = sync_entities(
        db, model=ExpenseRow, user_id=user.uid, changes=body.changes,
        last_sync_at=body.last_sync_at, to_row=_expense_row, to_dto=_expense_dto,
    )
    return SyncResponse[ExpenseDto](accepted=acc, rejected=rej, server_changes=srv, server_time=t)


@router.post("/budgets", response_model=SyncResponse[BudgetDto])
def sync_budgets(
    body: SyncRequest[BudgetDto],
    user: CurrentUser = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    # Budget ids are "<uid>:<CATEGORY>": the server enforces that so one user can't squat another's ids.
    valid = lambda c: c.id == f"{user.uid}:{c.category.value}"  # noqa: E731
    bad = [c for c in body.changes if not valid(c)]
    ok = [c for c in body.changes if valid(c)]
    acc, rej, srv, t = sync_entities(
        db, model=BudgetRow, user_id=user.uid, changes=ok,
        last_sync_at=body.last_sync_at, to_row=_budget_row, to_dto=_budget_dto,
    )
    rej += [Rejected(id=c.id, reason="invalid_id") for c in bad]
    return SyncResponse[BudgetDto](accepted=acc, rejected=rej, server_changes=srv, server_time=t)
