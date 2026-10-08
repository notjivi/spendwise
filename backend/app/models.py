from sqlalchemy import BigInteger, Boolean, Index, String
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base

# Timestamps are epoch millis in BigInteger: same representation as the Android client,
# no timezone ambiguity, and trivially comparable for last-write-wins.


class ExpenseRow(Base):
    __tablename__ = "expenses"

    id: Mapped[str] = mapped_column(String(36), primary_key=True)  # client-generated UUID
    user_id: Mapped[str] = mapped_column(String(128), index=True)
    amount_minor: Mapped[int] = mapped_column(BigInteger)
    currency: Mapped[str] = mapped_column(String(3))
    category: Mapped[str] = mapped_column(String(20))
    merchant: Mapped[str | None] = mapped_column(String(200), nullable=True)
    note: Mapped[str | None] = mapped_column(String(1000), nullable=True)
    payment_method: Mapped[str] = mapped_column(String(20))
    spent_at: Mapped[int] = mapped_column(BigInteger)
    created_at: Mapped[int] = mapped_column(BigInteger)
    updated_at: Mapped[int] = mapped_column(BigInteger)  # CLIENT clock: used to decide the winner
    is_deleted: Mapped[bool] = mapped_column(Boolean, default=False)
    # SERVER clock: when we stored it. Used to decide what to send to other devices.
    synced_at: Mapped[int] = mapped_column(BigInteger)

    __table_args__ = (Index("ix_expenses_user_synced", "user_id", "synced_at"),)


class BudgetRow(Base):
    __tablename__ = "budgets"

    id: Mapped[str] = mapped_column(String(160), primary_key=True)  # "<uid>:<CATEGORY>"
    user_id: Mapped[str] = mapped_column(String(128), index=True)
    category: Mapped[str] = mapped_column(String(20))
    monthly_limit_minor: Mapped[int] = mapped_column(BigInteger)
    updated_at: Mapped[int] = mapped_column(BigInteger)
    is_deleted: Mapped[bool] = mapped_column(Boolean, default=False)
    synced_at: Mapped[int] = mapped_column(BigInteger)

    __table_args__ = (Index("ix_budgets_user_synced", "user_id", "synced_at"),)
