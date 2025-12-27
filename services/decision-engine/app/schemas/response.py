"""
Response schemas for API endpoints.
"""

from pydantic import BaseModel, Field
from typing import Optional


class DecisionEvidence(BaseModel):
    """Evidence supporting the decision."""
    sample_size: int = Field(..., description="Number of historical samples analyzed")
    success_rate: float = Field(..., ge=0.0, le=1.0, description="Success rate for this decision")
    decision_type: str = Field(..., description="Decision type analyzed")
    similarity_score: Optional[float] = Field(None, description="Similarity score to matched errors")
    confidence_score: float = Field(..., ge=0.0, le=1.0, description="Confidence in the evidence")
    
    class Config:
        json_schema_extra = {
            "example": {
                "sample_size": 25,
                "success_rate": 0.85,
                "decision_type": "RETRY",
                "similarity_score": 0.92,
                "confidence_score": 0.85
            }
        }


class ClassifyErrorResponse(BaseModel):
    """Response for error classification."""
    decision: str = Field(..., description="Recommended decision (RETRY, FAIL_PERMANENT, PAUSE_WEBHOOK, ESCALATE)")
    confidence_score: float = Field(..., ge=0.0, le=1.0, description="Confidence in the decision (0.0-1.0)")
    explanation: str = Field(..., description="Human-readable explanation")
    evidence: DecisionEvidence = Field(..., description="Evidence supporting the decision")
    fallback_used: bool = Field(False, description="Whether fallback logic was used")
    
    class Config:
        json_schema_extra = {
            "example": {
                "decision": "RETRY",
                "confidence_score": 0.85,
                "explanation": "Based on 25 similar historical errors, RETRY has 85% success rate. Similar errors (429 rate limit) typically succeed on retry.",
                "evidence": {
                    "sample_size": 25,
                    "success_rate": 0.85,
                    "decision_type": "RETRY",
                    "similarity_score": 0.92,
                    "confidence_score": 0.85
                },
                "fallback_used": False
            }
        }

