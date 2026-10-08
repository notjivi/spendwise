"""create expenses and budgets

Revision ID: 0001
Revises:
"""
import sqlalchemy as sa
from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "expenses",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("user_id", sa.String(128), nullable=False),
        sa.Column("amount_minor", sa.BigInteger, nullable=False),
        sa.Column("currency", sa.String(3), nullable=False),
        sa.Column("category", sa.String(20), nullable=False),
        sa.Column("merchant", sa.String(200)),
        sa.Column("note", sa.String(1000)),
        sa.Column("payment_method", sa.String(20), nullable=False),
        sa.Column("spent_at", sa.BigInteger, nullable=False),
        sa.Column("created_at", sa.BigInteger, nullable=False),
        sa.Column("updated_at", sa.BigInteger, nullable=False),
        sa.Column("is_deleted", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("synced_at", sa.BigInteger, nullable=False),
    )
    op.create_index("ix_expenses_user_id", "expenses", ["user_id"])
    op.create_index("ix_expenses_user_synced", "expenses", ["user_id", "synced_at"])
    op.create_table(
        "budgets",
        sa.Column("id", sa.String(160), primary_key=True),
        sa.Column("user_id", sa.String(128), nullable=False),
        sa.Column("category", sa.String(20), nullable=False),
        sa.Column("monthly_limit_minor", sa.BigInteger, nullable=False),
        sa.Column("updated_at", sa.BigInteger, nullable=False),
        sa.Column("is_deleted", sa.Boolean, nullable=False, server_default=sa.false()),
        sa.Column("synced_at", sa.BigInteger, nullable=False),
    )
    op.create_index("ix_budgets_user_id", "budgets", ["user_id"])
    op.create_index("ix_budgets_user_synced", "budgets", ["user_id", "synced_at"])


def downgrade() -> None:
    op.drop_table("budgets")
    op.drop_table("expenses")
