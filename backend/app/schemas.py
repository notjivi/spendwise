from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel


class Category(StrEnum):
    FOOD = "FOOD"
    TRANSPORT = "TRANSPORT"
    SHOPPING = "SHOPPING"
    BILLS = "BILLS"
    ENTERTAINMENT = "ENTERTAINMENT"
    HEALTH = "HEALTH"
    EDUCATION = "EDUCATION"
    TRAVEL = "TRAVEL"
    OTHER = "OTHER"


class PaymentMethod(StrEnum):
    CASH = "CASH"
    UPI = "UPI"
    CARD = "CARD"
    NET_BANKING = "NET_BANKING"
    OTHER = "OTHER"


class CamelModel(BaseModel):
    """JSON on the wire is camelCase (matches Kotlin); Python code stays snake_case."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


MAX_MINOR = 10**12  # 10 billion rupees in paise; rejects absurd/overflow values


# ---------- AI ----------
class ParseExpenseRequest(CamelModel):
    text: str = Field(min_length=1)


class LlmExpense(BaseModel):
    """What we ASK Gemini to produce (also used as its response_schema).
    spent_on is an ISO date string because LLMs are far better at dates than epoch math."""

    is_expense: bool
    amount_minor: int = Field(ge=0, le=MAX_MINOR)
    currency: str = Field(min_length=3, max_length=3)
    merchant: str | None = None
    category: Category
    spent_on: str | None = None  # YYYY-MM-DD or null
    confidence: float = Field(ge=0, le=1)


class ParseExpenseResponse(CamelModel):
    amount_minor: int
    currency: str
    merchant: str | None
    category: Category
    spent_at: int | None
    confidence: float
    raw_echo: str


# ---------- Sync ----------
class ExpenseDto(CamelModel):
    id: str = Field(min_length=36, max_length=36)  # UUID string
    amount_minor: int = Field(gt=0, le=MAX_MINOR)
    currency: str = Field(min_length=3, max_length=3)
    category: Category
    merchant: str | None = Field(default=None, max_length=200)
    note: str | None = Field(default=None, max_length=1000)
    payment_method: PaymentMethod
    spent_at: int
    created_at: int
    updated_at: int
    is_deleted: bool = False


class BudgetDto(CamelModel):
    id: str = Field(min_length=1, max_length=160)
    category: Category
    monthly_limit_minor: int = Field(ge=0, le=MAX_MINOR)
    updated_at: int
    is_deleted: bool = False


class Rejected(CamelModel):
    id: str
    reason: str


class SyncRequest[T](CamelModel):
    changes: list[T] = Field(max_length=500)
    last_sync_at: int = Field(ge=0)


class SyncResponse[T](CamelModel):
    accepted: list[str]
    rejected: list[Rejected]
    server_changes: list[T]
    server_time: int


class CategoryTotal(CamelModel):
    category: Category
    total_minor: int


class MonthlySummary(CamelModel):
    month: str
    totals: list[CategoryTotal]
    grand_total_minor: int
