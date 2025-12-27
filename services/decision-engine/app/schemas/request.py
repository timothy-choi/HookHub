"""
Request schemas for API endpoints.
"""

from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime


class ErrorSignature(BaseModel):
    """Error signature for similarity matching."""
    http_status_code: Optional[int] = Field(None, description="HTTP status code")
    error_type: Optional[str] = Field(None, description="Error type (e.g., RATE_LIMIT, SERVER_ERROR)")
    error_message_pattern: Optional[str] = Field(None, description="Error message pattern/keywords")
    
    class Config:
        json_schema_extra = {
            "example": {
                "http_status_code": 429,
                "error_type": "RATE_LIMIT",
                "error_message_pattern": "rate limit"
            }
        }


class WebhookHealth(BaseModel):
    """Webhook health metrics."""
    webhook_id: int = Field(..., description="Webhook ID")
    total_failures: int = Field(0, description="Total failures")
    total_successes: int = Field(0, description="Total successes")
    consecutive_failures: int = Field(0, description="Consecutive failures")
    circuit_breaker_state: Optional[str] = Field(None, description="Circuit breaker state")
    last_failure_time: Optional[datetime] = Field(None, description="Last failure timestamp")
    
    class Config:
        json_schema_extra = {
            "example": {
                "webhook_id": 1,
                "total_failures": 10,
                "total_successes": 90,
                "consecutive_failures": 3,
                "circuit_breaker_state": "CLOSED",
                "last_failure_time": "2024-01-15T10:30:00"
            }
        }


class ClassifyErrorRequest(BaseModel):
    """Request for error classification."""
    error_signature: ErrorSignature = Field(..., description="Error signature")
    retry_count: int = Field(0, ge=0, description="Current retry count")
    recent_failure_rate: float = Field(0.0, ge=0.0, le=1.0, description="Recent failure rate (0.0-1.0)")
    webhook_health: WebhookHealth = Field(..., description="Webhook health metrics")
    
    class Config:
        json_schema_extra = {
            "example": {
                "error_signature": {
                    "http_status_code": 429,
                    "error_type": "RATE_LIMIT",
                    "error_message_pattern": "rate limit"
                },
                "retry_count": 2,
                "recent_failure_rate": 0.3,
                "webhook_health": {
                    "webhook_id": 1,
                    "total_failures": 10,
                    "total_successes": 90,
                    "consecutive_failures": 3,
                    "circuit_breaker_state": "CLOSED"
                }
            }
        }

