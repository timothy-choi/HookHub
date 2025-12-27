"""
API v1 routes.
"""

from fastapi import APIRouter

from app.api.v1.endpoints import classify

router = APIRouter()

router.include_router(classify.router, prefix="/classify", tags=["classification"])

