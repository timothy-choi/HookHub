"""
Application configuration using Pydantic Settings.
"""

from pydantic_settings import BaseSettings
from typing import List


class Settings(BaseSettings):
    """Application settings."""
    
    # Database
    DB_HOST: str = "localhost"
    DB_PORT: int = 3306
    DB_NAME: str = "webhookdb"
    DB_USER: str = "root"
    DB_PASSWORD: str = ""
    
    # API
    API_V1_PREFIX: str = "/api/v1"
    
    # CORS
    CORS_ORIGINS: List[str] = ["*"]
    
    # Decision Engine
    MIN_SAMPLE_SIZE: int = 5  # Minimum samples for confident decision
    CONFIDENCE_THRESHOLD: float = 0.6  # Minimum confidence to use learned decision
    SIMILARITY_THRESHOLD: float = 0.8  # Threshold for error similarity matching
    MAX_HISTORICAL_DAYS: int = 90  # Look back 90 days for historical data
    
    # Logging
    LOG_LEVEL: str = "INFO"
    
    class Config:
        env_file = ".env"
        case_sensitive = True


settings = Settings()

