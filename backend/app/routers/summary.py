from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.auth import CurrentUser, get_current_user
from app.db import get_db
from app.models import ExpenseRow
from app.schemas import CategoryTotal, MonthlySummary

router = APIRouter(prefix="/summary", tags=["summary"])
IST = timezone(timedelta(hours=5, minutes=30))


@router.get("/monthly", response_model=MonthlySummary)
def monthly(
    month: str = Query(pattern=r"^\d{4}-(0[1-9]|1[0-2])$"),
    user: CurrentUser = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    y, m = map(int, month.split("-"))
    start = datetime(y, m, 1, tzinfo=IST)
    end = datetime(y + (m == 12), 1 if m == 12 else m + 1, 1, tzinfo=IST)
    q = (
        select(ExpenseRow.category, func.sum(ExpenseRow.amount_minor))
        .where(
            ExpenseRow.user_id == user.uid,
            ExpenseRow.is_deleted.is_(False),
            ExpenseRow.spent_at >= int(start.timestamp() * 1000),
            ExpenseRow.spent_at < int(end.timestamp() * 1000),
        )
        .group_by(ExpenseRow.category)
        .order_by(ExpenseRow.category)
    )
    totals = [CategoryTotal(category=c, total_minor=int(t)) for c, t in db.execute(q)]
    return MonthlySummary(month=month, totals=totals, grand_total_minor=sum(t.total_minor for t in totals))
