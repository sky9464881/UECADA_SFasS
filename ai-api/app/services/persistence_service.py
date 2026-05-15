from __future__ import annotations

import json
from datetime import datetime, timedelta

from sqlalchemy import func
from sqlalchemy.orm import Session

from app.core.config import settings
from app.db.models import AlarmHistory, Equipment, VibrationWindow


def ensure_equipment_exists(db: Session, equipment_code: str) -> None:
    exists = db.query(Equipment.id).filter(Equipment.equipment_code == equipment_code).first()
    if exists:
        return

    db.add(
        Equipment(
            equipment_code=equipment_code,
            equipment_name=equipment_code,
            location="auto-registered",
        )
    )
    db.flush()


def save_raw_window_if_due(
    db: Session,
    *,
    equipment_code: str,
    measured_at: datetime,
    sampling_rate: int,
    rpm: int | None,
    window_size: int,
    window_index: int,
    values: list[float],
    force: bool = False,
) -> tuple[VibrationWindow | None, bool]:
    latest_saved_at = (
        db.query(func.max(VibrationWindow.measured_at))
        .filter(VibrationWindow.equipment_code == equipment_code)
        .scalar()
    )
    save_interval = timedelta(minutes=max(0, settings.raw_window_save_interval_minutes))
    should_save = force or latest_saved_at is None or measured_at >= latest_saved_at + save_interval
    if not should_save:
        return None, False

    vibration_window = VibrationWindow(
        equipment_code=equipment_code,
        measured_at=measured_at,
        sampling_rate=sampling_rate,
        rpm=rpm,
        window_size=window_size,
        window_index=window_index,
        values_json=json.dumps(values, separators=(",", ":")),
    )
    db.add(vibration_window)
    db.flush()
    return vibration_window, True


def has_open_alarm(db: Session, *, equipment_code: str) -> bool:
    return _latest_open_alarm(db, equipment_code) is not None


def save_alarm_state_if_needed(
    db: Session,
    *,
    analysis_result_id: int,
    equipment_code: str,
    alarm_level: str,
    anomaly_score: float,
    prediction: str,
    measured_at: datetime,
) -> bool:
    if _is_alarm_level(alarm_level):
        return _open_or_update_alarm(
            db,
            analysis_result_id=analysis_result_id,
            equipment_code=equipment_code,
            alarm_level=alarm_level,
            anomaly_score=anomaly_score,
            prediction=prediction,
            measured_at=measured_at,
        )

    _close_open_alarm_if_needed(db, equipment_code=equipment_code, measured_at=measured_at)
    return False


def _open_or_update_alarm(
    db: Session,
    *,
    analysis_result_id: int,
    equipment_code: str,
    alarm_level: str,
    anomaly_score: float,
    prediction: str,
    measured_at: datetime,
) -> bool:
    alarm = _latest_open_alarm(db, equipment_code)
    if alarm is not None:
        alarm.alarm_level = _worse_alarm_level(alarm.alarm_level, alarm_level)
        alarm.analysis_result_id = analysis_result_id
        alarm.message = _alarm_message(equipment_code, alarm_level, anomaly_score, prediction, alarm.occurred_at)
        db.flush()
        return False

    db.add(
        AlarmHistory(
            equipment_code=equipment_code,
            analysis_result_id=analysis_result_id,
            alarm_level=alarm_level,
            status="open",
            occurred_at=measured_at,
            message=_alarm_message(equipment_code, alarm_level, anomaly_score, prediction, measured_at),
        )
    )
    db.flush()
    return True


def _close_open_alarm_if_needed(db: Session, *, equipment_code: str, measured_at: datetime) -> None:
    alarm = _latest_open_alarm(db, equipment_code)
    if alarm is None:
        return

    duration_seconds = max(0, int((measured_at - alarm.occurred_at).total_seconds()))
    alarm.status = "closed"
    alarm.ended_at = measured_at
    alarm.duration_seconds = duration_seconds
    alarm.message = (
        f"Vibration anomaly closed: equipmentCode={equipment_code}, "
        f"peakAlarmLevel={alarm.alarm_level}, durationSeconds={duration_seconds}"
    )
    db.flush()


def _latest_open_alarm(db: Session, equipment_code: str) -> AlarmHistory | None:
    return (
        db.query(AlarmHistory)
        .filter(AlarmHistory.equipment_code == equipment_code, AlarmHistory.status == "open")
        .order_by(AlarmHistory.occurred_at.desc())
        .first()
    )


def _is_alarm_level(alarm_level: str | None) -> bool:
    return str(alarm_level or "").lower() in {"warning", "danger"}


def _worse_alarm_level(current_level: str | None, next_level: str) -> str:
    if str(current_level or "").lower() == "danger" or next_level.lower() == "danger":
        return "danger"
    return "warning"


def _alarm_message(
    equipment_code: str,
    alarm_level: str,
    anomaly_score: float,
    prediction: str,
    started_at: datetime,
) -> str:
    return (
        f"Vibration anomaly active: equipmentCode={equipment_code}, alarmLevel={alarm_level}, "
        f"anomalyScore={anomaly_score}, prediction={prediction}, startedAt={started_at}"
    )
