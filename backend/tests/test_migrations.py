from alembic import command
from alembic.config import Config
from sqlalchemy import create_engine, inspect


def test_alembic_upgrade_and_downgrade(tmp_path):
    url = f"sqlite:///{tmp_path/'t.db'}"
    cfg = Config("alembic.ini")
    cfg.set_main_option("sqlalchemy.url", url)
    command.upgrade(cfg, "head")
    insp = inspect(create_engine(url))
    assert {"expenses", "budgets"} <= set(insp.get_table_names())
    assert {"synced_at", "is_deleted"} <= {c["name"] for c in insp.get_columns("expenses")}
    command.downgrade(cfg, "base")
    assert not {"expenses", "budgets"} & set(inspect(create_engine(url)).get_table_names())


def test_postgres_url_is_normalised():
    from app.config import Settings

    assert Settings(database_url="postgres://u:p@h/db").database_url == "postgresql+psycopg://u:p@h/db"
