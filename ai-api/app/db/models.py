from datetime import datetime

from sqlalchemy import BigInteger, DateTime, Double, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column

from app.db.database import Base


class AnalysisResult(Base):
    __tablename__ = "analysis_result"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    vibration_window_id: Mapped[int | None] = mapped_column(BigInteger)
    equipment_code: Mapped[str] = mapped_column(String(50), nullable=False)
    rms: Mapped[float | None] = mapped_column(Double)
    peak_frequency: Mapped[float | None] = mapped_column(Double)
    peak_to_peak: Mapped[float | None] = mapped_column(Double)
    crest_factor: Mapped[float | None] = mapped_column(Double)
    kurtosis: Mapped[float | None] = mapped_column(Double)
    prediction: Mapped[str | None] = mapped_column(String(50))
    confidence: Mapped[float | None] = mapped_column(Double)
    model_version: Mapped[str | None] = mapped_column(String(100))
    model_input_type: Mapped[str | None] = mapped_column(String(50))
    model_input_size: Mapped[int | None] = mapped_column(Integer)
    model_expected_input_size: Mapped[int | None] = mapped_column(Integer)
    model_input_strategy: Mapped[str | None] = mapped_column(String(150))
    model_status: Mapped[str | None] = mapped_column(String(50))
    anomaly_score: Mapped[float | None] = mapped_column(Double)
    alarm_level: Mapped[str | None] = mapped_column(String(20))
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
