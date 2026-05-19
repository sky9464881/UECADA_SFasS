from __future__ import annotations

import logging

from sqlalchemy import inspect, text

from app.db.database import engine

logger = logging.getLogger("uvicorn.error")


VIBRATION_WINDOW_COLUMNS: dict[str, str] = {
    "sensor_temperature": "DOUBLE NULL",
    "sensor_current": "DOUBLE NULL",
    "sensor_voltage": "DOUBLE NULL",
    "sensor_vibration": "DOUBLE NULL",
    "sensor_snapshot_json": "LONGTEXT NULL",
}


def ensure_runtime_schema() -> None:
    """Keep long-lived local Docker volumes compatible with the current app."""
    try:
        inspector = inspect(engine)
        existing = {column["name"] for column in inspector.get_columns("vibration_window")}
    except Exception as exc:  # pragma: no cover - startup resilience
        logger.warning("Skipping runtime DB schema check: %s", exc)
        return

    missing = {
        column_name: ddl
        for column_name, ddl in VIBRATION_WINDOW_COLUMNS.items()
        if column_name not in existing
    }
    if not missing:
        return

    with engine.begin() as connection:
        for column_name, ddl in missing.items():
            connection.execute(text(f"ALTER TABLE vibration_window ADD COLUMN {column_name} {ddl}"))
            logger.info("Added missing vibration_window.%s column", column_name)
