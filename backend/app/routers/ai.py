from datetime import date, datetime, time, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, status

from app.auth import CurrentUser
from app.config import get_settings
from app.ratelimit import ai_rate_limit
from app.schemas import ParseExpenseRequest, ParseExpenseResponse
from app.services.gemini import GeminiParser, ParseFailure, UpstreamFailure, get_parser

router = APIRouter(prefix="/ai", tags=["ai"])
IST = timezone(timedelta(hours=5, minutes=30))


def _date_to_millis(iso: str | None) -> int | None:
    if not iso:
        return None
    try:
        d = date.fromisoformat(iso)
    except ValueError:
        return None  # a bad date shouldn't sink an otherwise good parse; user can pick one
    return int(datetime.combine(d, time(12, 0), tzinfo=IST).timestamp() * 1000)  # noon: safe from tz shifts


@router.post("/parse-expense", response_model=ParseExpenseResponse)
async def parse_expense(
    body: ParseExpenseRequest,
    _user: CurrentUser = Depends(ai_rate_limit),
    parser: GeminiParser = Depends(get_parser),
):
    text = body.text.strip()
    if not text:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, "Text is empty")
    if len(text) > get_settings().ai_max_input_chars:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, "Text is too long (max 1000 characters)")

    try:
        parsed = await parser.parse(text, today=datetime.now(IST).date())
    except ParseFailure as e:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, str(e))
    except UpstreamFailure:
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, "AI service unavailable, try again")

    if not parsed.is_expense or parsed.amount_minor <= 0:
        raise HTTPException(
            status.HTTP_422_UNPROCESSABLE_CONTENT,
            "This doesn't look like an expense (refund, credit or no amount found)",
        )
    return ParseExpenseResponse(
        amount_minor=parsed.amount_minor,
        currency=parsed.currency.upper(),
        merchant=(parsed.merchant or None),
        category=parsed.category,
        spent_at=_date_to_millis(parsed.spent_on),
        confidence=parsed.confidence,
        raw_echo=text[:200],
    )
