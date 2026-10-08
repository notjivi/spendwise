from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import get_settings
from app.routers import ai, health, summary, sync


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(title="SpendWise API", version="1.0.0")
    # Native app => no browser CORS needed. Empty list = no cross-origin access at all.
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_methods=["GET", "POST"],
        allow_headers=["Authorization", "Content-Type"],
    )
    app.include_router(health.router)
    app.include_router(ai.router)
    app.include_router(sync.router)
    app.include_router(summary.router)
    return app


app = create_app()
