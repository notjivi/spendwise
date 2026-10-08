from functools import lru_cache

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    firebase_project_id: str = ""
    firebase_credentials_path: str = ""
    firebase_credentials_b64: str = ""

    gemini_api_key: str = ""
    gemini_model: str = ""

    database_url: str = "sqlite:///./spendwise.db"
    cors_origins: list[str] = []
    ai_rate_limit_per_min: int = 20
    ai_max_input_chars: int = 1000

    @field_validator("database_url")
    @classmethod
    def _normalize_db_url(cls, v: str) -> str:
        # Render/Railway hand out "postgres://..." which SQLAlchemy 2 rejects;
        # also force the psycopg3 driver we ship.
        if v.startswith("postgres://"):
            v = "postgresql://" + v[len("postgres://"):]
        if v.startswith("postgresql://"):
            v = "postgresql+psycopg://" + v[len("postgresql://"):]
        return v


@lru_cache
def get_settings() -> Settings:
    return Settings()
