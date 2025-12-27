"""
Error similarity matching using structured similarity metrics.

This module provides deterministic similarity scoring for error signatures
without using machine learning.
"""

from typing import Optional
from app.schemas.request import ErrorSignature


class ErrorSimilarityMatcher:
    """Matches errors using structured similarity metrics."""
    
    @staticmethod
    def calculate_similarity(
        signature1: ErrorSignature,
        signature2: ErrorSignature
    ) -> float:
        """
        Calculate similarity score between two error signatures.
        
        Returns a score between 0.0 and 1.0, where 1.0 is identical.
        
        Args:
            signature1: First error signature
            signature2: Second error signature
            
        Returns:
            Similarity score (0.0-1.0)
        """
        scores = []
        weights = []
        
        # HTTP status code similarity (exact match = 1.0, different = 0.0)
        if signature1.http_status_code is not None and signature2.http_status_code is not None:
            if signature1.http_status_code == signature2.http_status_code:
                scores.append(1.0)
            else:
                # Partial credit for same category (4xx, 5xx, etc.)
                category1 = signature1.http_status_code // 100
                category2 = signature2.http_status_code // 100
                if category1 == category2:
                    scores.append(0.5)
                else:
                    scores.append(0.0)
            weights.append(0.4)
        elif signature1.http_status_code is None and signature2.http_status_code is None:
            scores.append(1.0)
            weights.append(0.4)
        else:
            # One is None, one is not - partial match
            scores.append(0.2)
            weights.append(0.4)
        
        # Error type similarity (exact match = 1.0)
        if signature1.error_type and signature2.error_type:
            if signature1.error_type == signature2.error_type:
                scores.append(1.0)
            else:
                scores.append(0.0)
            weights.append(0.4)
        elif not signature1.error_type and not signature2.error_type:
            scores.append(1.0)
            weights.append(0.4)
        else:
            scores.append(0.0)
            weights.append(0.4)
        
        # Error message pattern similarity (simple keyword matching)
        if signature1.error_message_pattern and signature2.error_message_pattern:
            pattern1_lower = signature1.error_message_pattern.lower()
            pattern2_lower = signature2.error_message_pattern.lower()
            
            # Check if patterns contain common keywords
            words1 = set(pattern1_lower.split())
            words2 = set(pattern2_lower.split())
            
            if words1 and words2:
                common_words = words1.intersection(words2)
                all_words = words1.union(words2)
                if all_words:
                    jaccard_similarity = len(common_words) / len(all_words)
                    scores.append(jaccard_similarity)
                else:
                    scores.append(0.0)
            else:
                # Exact match check
                if pattern1_lower == pattern2_lower:
                    scores.append(1.0)
                elif pattern1_lower in pattern2_lower or pattern2_lower in pattern1_lower:
                    scores.append(0.7)
                else:
                    scores.append(0.0)
            weights.append(0.2)
        elif not signature1.error_message_pattern and not signature2.error_message_pattern:
            scores.append(1.0)
            weights.append(0.2)
        else:
            scores.append(0.0)
            weights.append(0.2)
        
        # Calculate weighted average
        if not weights or sum(weights) == 0:
            return 0.0
        
        weighted_sum = sum(score * weight for score, weight in zip(scores, weights))
        total_weight = sum(weights)
        
        return weighted_sum / total_weight if total_weight > 0 else 0.0
    
    @staticmethod
    def find_similar_errors(
        target_signature: ErrorSignature,
        historical_signatures: list[tuple[ErrorSignature, dict]],
        threshold: float = 0.8
    ) -> list[tuple[ErrorSignature, dict, float]]:
        """
        Find similar errors from historical data.
        
        Args:
            target_signature: Error signature to match
            historical_signatures: List of (signature, metadata) tuples
            threshold: Minimum similarity score (default: 0.8)
            
        Returns:
            List of (signature, metadata, similarity_score) tuples, sorted by similarity
        """
        similar = []
        
        for signature, metadata in historical_signatures:
            similarity = ErrorSimilarityMatcher.calculate_similarity(
                target_signature,
                signature
            )
            
            if similarity >= threshold:
                similar.append((signature, metadata, similarity))
        
        # Sort by similarity score (descending)
        similar.sort(key=lambda x: x[2], reverse=True)
        
        return similar

