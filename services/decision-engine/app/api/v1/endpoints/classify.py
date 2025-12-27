"""
Error classification endpoint.
"""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.schemas.request import ClassifyErrorRequest
from app.schemas.response import ClassifyErrorResponse
from app.services.decision_engine import DecisionEngine

router = APIRouter()


@router.post("/error", response_model=ClassifyErrorResponse)
async def classify_error(
    request: ClassifyErrorRequest,
    db: Session = Depends(get_db)
) -> ClassifyErrorResponse:
    """
    Classify an error and recommend a decision based on historical outcomes.
    
    This endpoint:
    - Loads historical error classifications and outcomes
    - Groups similar errors using structured similarity
    - Computes success rates per decision type
    - Chooses the best decision based on confidence thresholds
    - Returns decision, confidence, explanation, and evidence
    
    Args:
        request: Classification request with error signature and context
        db: Database session
        
    Returns:
        Classification response with recommended decision
        
    Raises:
        HTTPException: If classification fails
    """
    try:
        engine = DecisionEngine(db)
        response = engine.classify_error(request)
        return response
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Error classification failed: {str(e)}"
        )

