from datetime import datetime

<<<<<<< HEAD
from sqlalchemy import BigInteger, DateTime, Double, Integer, String, func
=======
from sqlalchemy import BigInteger, DateTime, Double, Integer, String, Text, func
>>>>>>> feature/develop_before
from sqlalchemy.orm import Mapped, mapped_column

from app.db.database import Base


<<<<<<< HEAD
=======
class Equipment(Base):
    __tablename__ = "equipment"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    equipment_code: Mapped[str] = mapped_column(String(50), nullable=False, unique=True)
    equipment_name: Mapped[str] = mapped_column(String(100), nullable=False)
    process_type: Mapped[str | None] = mapped_column(String(50))
    model: Mapped[str | None] = mapped_column(String(100))
    location: Mapped[str | None] = mapped_column(String(100))
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())


class VibrationWindow(Base):
    __tablename__ = "vibration_window"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    equipment_code: Mapped[str] = mapped_column(String(50), nullable=False)
    measured_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    sampling_rate: Mapped[int] = mapped_column(Integer, nullable=False)
    rpm: Mapped[int | None] = mapped_column(Integer)
    window_size: Mapped[int] = mapped_column(Integer, nullable=False)
    window_index: Mapped[int] = mapped_column(BigInteger, nullable=False)
    sensor_temperature: Mapped[float | None] = mapped_column(Double)
    sensor_current: Mapped[float | None] = mapped_column(Double)
    sensor_voltage: Mapped[float | None] = mapped_column(Double)
    sensor_vibration: Mapped[float | None] = mapped_column(Double)
    sensor_snapshot_json: Mapped[str | None] = mapped_column(Text)
    values_json: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())


>>>>>>> feature/develop_before
class AnalysisResult(Base):
    __tablename__ = "analysis_result"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    vibration_window_id: Mapped[int | None] = mapped_column(BigInteger)
    equipment_code: Mapped[str] = mapped_column(String(50), nullable=False)
<<<<<<< HEAD
=======
    analysis_type: Mapped[str | None] = mapped_column(String(50))
>>>>>>> feature/develop_before
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
<<<<<<< HEAD
=======


class AlarmHistory(Base):
    __tablename__ = "alarm_history"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    equipment_code: Mapped[str] = mapped_column(String(50), nullable=False)
    analysis_result_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    alarm_level: Mapped[str] = mapped_column(String(20), nullable=False)
    status: Mapped[str] = mapped_column(String(20), nullable=False)
    message: Mapped[str] = mapped_column(String(255), nullable=False)
    occurred_at: Mapped[datetime] = mapped_column(DateTime, nullable=False)
    ended_at: Mapped[datetime | None] = mapped_column(DateTime)
    duration_seconds: Mapped[int | None] = mapped_column(BigInteger)
    updated_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now(), onupdate=func.now())
>>>>>>> feature/develop_before
