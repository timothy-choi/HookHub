"""
Learning-based decision engine for error classification.

This engine learns from historical outcomes to make intelligent decisions
about how to handle webhook delivery errors.
"""

from typing import Optional, List, Tuple
from datetime import datetime, timedelta
from sqlalchemy.orm import Session
from sqlalchemy import and_, func

from app.models.database import (
    ErrorClassification,
    Event,
    Webhook,
    ErrorDecisionEnum,
    EventStatusEnum
)
from app.schemas.request import ErrorSignature, ClassifyErrorRequest
from app.schemas.response import ClassifyErrorResponse, DecisionEvidence
from app.services.similarity import ErrorSimilarityMatcher
from app.core.config import settings


class DecisionEngine:
    """Learning-based decision engine."""
    
    def __init__(self, db: Session):
        self.db = db
        self.similarity_matcher = ErrorSimilarityMatcher()
    
    def classify_error(self, request: ClassifyErrorRequest) -> ClassifyErrorResponse:
        """
        Classify an error and recommend a decision based on historical outcomes.
        
        Args:
            request: Classification request with error signature and context
            
        Returns:
            Classification response with decision, confidence, and evidence
        """
        # Load historical outcomes
        historical_data = self._load_historical_outcomes(
            request.error_signature,
            request.webhook_health.webhook_id
        )
        
        if not historical_data:
            # No historical data - use safe fallback
            return self._fallback_decision(request)
        
        # Group similar errors
        similar_errors = self.similarity_matcher.find_similar_errors(
            request.error_signature,
            historical_data,
            threshold=settings.SIMILARITY_THRESHOLD
        )
        
        if not similar_errors:
            # No similar errors found - use fallback
            return self._fallback_decision(request)
        
        # Compute success rates per decision type
        decision_success_rates = self._compute_decision_success_rates(similar_errors)
        
        if not decision_success_rates:
            # No valid success rates - use fallback
            return self._fallback_decision(request)
        
        # Choose best decision based on confidence thresholds
        best_decision, evidence = self._choose_best_decision(
            decision_success_rates,
            similar_errors,
            request
        )
        
        # Generate explanation
        explanation = self._generate_explanation(
            best_decision,
            evidence,
            request,
            len(similar_errors)
        )
        
        return ClassifyErrorResponse(
            decision=best_decision,
            confidence_score=evidence.confidence_score,
            explanation=explanation,
            evidence=evidence,
            fallback_used=False
        )
    
    def _load_historical_outcomes(
        self,
        error_signature: ErrorSignature,
        webhook_id: int,
        days: int = None
    ) -> List[Tuple[ErrorSignature, dict]]:
        """
        Load historical error classifications and their outcomes.
        
        Args:
            error_signature: Target error signature
            webhook_id: Webhook ID
            days: Number of days to look back (default: from config)
            
        Returns:
            List of (signature, metadata) tuples where metadata contains
            decision, outcome, and other relevant information
        """
        if days is None:
            days = settings.MAX_HISTORICAL_DAYS
        
        cutoff_date = datetime.utcnow() - timedelta(days=days)
        
        # Query error classifications with their event outcomes
        query = (
            self.db.query(
                ErrorClassification,
                Event.status,
                Event.retry_count
            )
            .join(Event, ErrorClassification.event_id == Event.id)
            .filter(
                and_(
                    ErrorClassification.webhook_id == webhook_id,
                    ErrorClassification.created_at >= cutoff_date
                )
            )
            .order_by(ErrorClassification.created_at.desc())
        )
        
        # Apply filters based on error signature
        if error_signature.http_status_code is not None:
            query = query.filter(
                ErrorClassification.http_status_code == error_signature.http_status_code
            )
        
        if error_signature.error_type:
            query = query.filter(ErrorClassification.error_type == error_signature.error_type)
        
        results = query.limit(1000).all()  # Limit to prevent memory issues
        
        historical_data = []
        for classification, event_status, retry_count in results:
            # Reconstruct error signature from classification
            hist_signature = ErrorSignature(
                http_status_code=classification.http_status_code,
                error_type=classification.error_type,
                error_message_pattern=classification.error_message
            )
            
            # Determine outcome (success if event eventually succeeded)
            outcome = "success" if event_status == EventStatusEnum.SUCCESS else "failure"
            
            metadata = {
                "decision": classification.decision.value,
                "outcome": outcome,
                "event_status": event_status.value,
                "retry_count": retry_count,
                "created_at": classification.created_at
            }
            
            historical_data.append((hist_signature, metadata))
        
        return historical_data
    
    def _compute_decision_success_rates(
        self,
        similar_errors: List[Tuple[ErrorSignature, dict, float]]
    ) -> dict:
        """
        Compute success rates for each decision type.
        
        Args:
            similar_errors: List of similar errors with metadata
            
        Returns:
            Dictionary mapping decision type to (success_count, total_count, success_rate)
        """
        decision_stats = {}
        
        for signature, metadata, similarity_score in similar_errors:
            decision = metadata.get("decision")
            outcome = metadata.get("outcome")
            
            if not decision:
                continue
            
            if decision not in decision_stats:
                decision_stats[decision] = {
                    "success_count": 0,
                    "total_count": 0,
                    "successes": [],
                    "failures": []
                }
            
            decision_stats[decision]["total_count"] += 1
            
            if outcome == "success":
                decision_stats[decision]["success_count"] += 1
                decision_stats[decision]["successes"].append(similarity_score)
            else:
                decision_stats[decision]["failures"].append(similarity_score)
        
        # Calculate success rates
        success_rates = {}
        for decision, stats in decision_stats.items():
            if stats["total_count"] > 0:
                success_rate = stats["success_count"] / stats["total_count"]
                success_rates[decision] = {
                    "success_rate": success_rate,
                    "sample_size": stats["total_count"],
                    "success_count": stats["success_count"]
                }
        
        return success_rates
    
    def _choose_best_decision(
        self,
        decision_success_rates: dict,
        similar_errors: List[Tuple[ErrorSignature, dict, float]],
        request: ClassifyErrorRequest
    ) -> Tuple[str, DecisionEvidence]:
        """
        Choose the best decision based on success rates and confidence.
        
        Args:
            decision_success_rates: Success rates per decision type
            similar_errors: Similar errors for evidence
            request: Original classification request
            
        Returns:
            Tuple of (decision, evidence)
        """
        best_decision = None
        best_evidence = None
        best_score = -1.0
        
        # Calculate average similarity for evidence
        avg_similarity = (
            sum(sim for _, _, sim in similar_errors) / len(similar_errors)
            if similar_errors else 0.0
        )
        
        for decision, stats in decision_success_rates.items():
            success_rate = stats["success_rate"]
            sample_size = stats["sample_size"]
            
            # Confidence score based on sample size and success rate
            # Higher sample size and clearer success rate = higher confidence
            sample_confidence = min(1.0, sample_size / settings.MIN_SAMPLE_SIZE)
            rate_confidence = abs(success_rate - 0.5) * 2  # Distance from 0.5 (uncertainty)
            confidence_score = (sample_confidence * 0.6) + (rate_confidence * 0.4)
            
            # Decision score = success_rate * confidence
            decision_score = success_rate * confidence_score
            
            if decision_score > best_score:
                best_score = decision_score
                best_decision = decision
                best_evidence = DecisionEvidence(
                    sample_size=sample_size,
                    success_rate=success_rate,
                    decision_type=decision,
                    similarity_score=avg_similarity,
                    confidence_score=confidence_score
                )
        
        # If confidence is too low, use fallback
        if best_evidence and best_evidence.confidence_score < settings.CONFIDENCE_THRESHOLD:
            return self._fallback_decision(request)
        
        # If no decision found or sample size too small
        if not best_decision or best_evidence.sample_size < settings.MIN_SAMPLE_SIZE:
            return self._fallback_decision(request)
        
        return best_decision, best_evidence
    
    def _fallback_decision(self, request: ClassifyErrorRequest) -> ClassifyErrorResponse:
        """
        Generate a safe fallback decision when data is insufficient.
        
        Args:
            request: Classification request
            
        Returns:
            Fallback classification response
        """
        # Conservative fallback logic based on error signature
        error_signature = request.error_signature
        
        # Rate limiting - always retry
        if error_signature.http_status_code == 429:
            decision = ErrorDecisionEnum.RETRY.value
            explanation = "Rate limiting detected. Safe fallback: RETRY (rate limits are typically temporary)."
        
        # Server errors (5xx) - retry
        elif error_signature.http_status_code and 500 <= error_signature.http_status_code < 600:
            decision = ErrorDecisionEnum.RETRY.value
            explanation = "Server error detected. Safe fallback: RETRY (server errors are often transient)."
        
        # Client errors (4xx) - fail permanent
        elif error_signature.http_status_code and 400 <= error_signature.http_status_code < 500:
            decision = ErrorDecisionEnum.FAIL_PERMANENT.value
            explanation = "Client error detected. Safe fallback: FAIL_PERMANENT (client errors typically don't resolve with retry)."
        
        # Network errors - retry
        elif error_signature.error_type in ["NETWORK_ERROR", "TIMEOUT_ERROR"]:
            decision = ErrorDecisionEnum.RETRY.value
            explanation = "Network error detected. Safe fallback: RETRY (network issues are often transient)."
        
        # Default: retry (conservative)
        else:
            decision = ErrorDecisionEnum.RETRY.value
            explanation = "Insufficient historical data. Safe fallback: RETRY (conservative approach)."
        
        evidence = DecisionEvidence(
            sample_size=0,
            success_rate=0.5,  # Unknown - assume 50%
            decision_type=decision,
            similarity_score=None,
            confidence_score=0.3  # Low confidence for fallback
        )
        
        return ClassifyErrorResponse(
            decision=decision,
            confidence_score=0.3,
            explanation=explanation,
            evidence=evidence,
            fallback_used=True
        )
    
    def _generate_explanation(
        self,
        decision: str,
        evidence: DecisionEvidence,
        request: ClassifyErrorRequest,
        similar_count: int
    ) -> str:
        """
        Generate human-readable explanation for the decision.
        
        Args:
            decision: Recommended decision
            evidence: Decision evidence
            request: Original request
            similar_count: Number of similar errors found
            
        Returns:
            Human-readable explanation
        """
        success_rate_pct = int(evidence.success_rate * 100)
        confidence_pct = int(evidence.confidence_score * 100)
        
        explanation_parts = [
            f"Based on {evidence.sample_size} similar historical errors",
            f"({similar_count} matched by similarity),",
            f"{decision} has a {success_rate_pct}% success rate.",
        ]
        
        if evidence.similarity_score:
            sim_pct = int(evidence.similarity_score * 100)
            explanation_parts.append(f"Similarity score: {sim_pct}%.")
        
        # Add context about error signature
        error_desc = []
        if request.error_signature.http_status_code:
            error_desc.append(f"HTTP {request.error_signature.http_status_code}")
        if request.error_signature.error_type:
            error_desc.append(request.error_signature.error_type)
        
        if error_desc:
            explanation_parts.append(f"Error: {', '.join(error_desc)}.")
        
        explanation_parts.append(f"Confidence: {confidence_pct}%.")
        
        return " ".join(explanation_parts)

