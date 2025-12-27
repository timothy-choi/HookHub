"""
FastAPI application entry point for HookHub Decision Engine Service.

This service provides learning-based error classification decisions
by analyzing historical outcomes from the webhook delivery platform.
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1 import router as v1_router
from app.core.config import settings

app = FastAPI(
    title="HookHub Decision Engine",
    description="Learning-based decision engine for webhook error classification",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(v1_router, prefix="/api/v1")


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "service": "decision-engine",
        "version": "1.0.0"
    }


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "service": "HookHub Decision Engine",
        "version": "1.0.0",
        "docs": "/docs"
    }

