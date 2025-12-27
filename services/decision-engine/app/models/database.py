"""
SQLAlchemy models for database tables.

These models map to the existing MySQL tables created by the Java Spring Boot service.
"""

from sqlalchemy import Column, BigInteger, Integer, String, Text, DateTime, Enum, Boolean
from sqlalchemy.sql import func

from app.core.database import Base
import enum


class ErrorDecisionEnum(str, enum.Enum):
    """Error decision enum matching Java service."""
    RETRY = "RETRY"
    FAIL_PERMANENT = "FAIL_PERMANENT"
    PAUSE_WEBHOOK = "PAUSE_WEBHOOK"
    ESCALATE = "ESCALATE"


class EventStatusEnum(str, enum.Enum):
    """Event status enum matching Java service."""
    PENDING = "PENDING"
    PROCESSING = "PROCESSING"
    SUCCESS = "SUCCESS"
    RETRY_PENDING = "RETRY_PENDING"
    FAILURE = "FAILURE"
    PAUSED = "PAUSED"


class CircuitBreakerStateEnum(str, enum.Enum):
    """Circuit breaker state enum matching Java service."""
    CLOSED = "CLOSED"
    OPEN = "OPEN"
    HALF_OPEN = "HALF_OPEN"


class ErrorClassification(Base):
    """Error classification table model."""
    __tablename__ = "error_classifications"
    
    id = Column(BigInteger, primary_key=True, index=True)
    event_id = Column(BigInteger, nullable=False, index=True)
    webhook_id = Column(BigInteger, nullable=False, index=True)
    http_status_code = Column(Integer, nullable=True)
    error_message = Column(Text, nullable=True)
    decision = Column(Enum(ErrorDecisionEnum), nullable=False)
    explanation = Column(Text, nullable=True)
    error_type = Column(String(50), nullable=True, index=True)
    retry_after_seconds = Column(Integer, nullable=True)
    created_at = Column(DateTime, nullable=False, server_default=func.now())


class Event(Base):
    """Event table model."""
    __tablename__ = "events"
    
    id = Column(BigInteger, primary_key=True, index=True)
    webhook_id = Column(BigInteger, nullable=False, index=True)
    payload = Column(Text, nullable=True)
    status = Column(Enum(EventStatusEnum), nullable=False)
    retry_count = Column(Integer, nullable=False, default=0)
    created_at = Column(DateTime, nullable=False, server_default=func.now())
    updated_at = Column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now())


class Webhook(Base):
    """Webhook table model."""
    __tablename__ = "webhooks"
    
    id = Column(BigInteger, primary_key=True, index=True)
    url = Column(String(512), nullable=False)
    metadata = Column(Text, nullable=True)
    circuit_breaker_state = Column(Enum(CircuitBreakerStateEnum), nullable=True)
    consecutive_failures = Column(Integer, default=0)
    circuit_opened_at = Column(DateTime, nullable=True)
    last_failure_time = Column(DateTime, nullable=True)
    total_failures = Column(BigInteger, default=0)
    total_successes = Column(BigInteger, default=0)
    paused_until = Column(DateTime, nullable=True)
    is_disabled = Column(Boolean, default=False)
    created_at = Column(DateTime, nullable=False, server_default=func.now())
    updated_at = Column(DateTime, nullable=False, server_default=func.now(), onupdate=func.now())

