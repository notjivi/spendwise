"""Optional accuracy check against the REAL model. Run: pytest -m live (needs env vars)."""
import os
from datetime import date

import pytest

from app.services.gemini import GeminiParser

pytestmark = [
    pytest.mark.live,
    pytest.mark.skipif(not (os.getenv("GEMINI_API_KEY") and os.getenv("GEMINI_MODEL")), reason="no Gemini env"),
]


async def test_live_swiggy():
    out = await GeminiParser().parse("Rs 450 spent at Swiggy on 12 Oct via UPI", date.today())
    assert out.is_expense and out.amount_minor == 45000 and out.category == "FOOD"


async def test_live_refund_is_not_expense():
    out = await GeminiParser().parse("Refund of Rs 899 credited to your account", date.today())
    assert not out.is_expense


async def test_live_balance_is_not_the_amount():
    out = await GeminiParser().parse("Rs 1,250 debited at AMAZON. Avl bal Rs 15,430.50", date.today())
    assert out.amount_minor == 125000
