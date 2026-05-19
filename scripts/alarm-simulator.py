#!/usr/bin/env python3
"""
간단한 알람 시뮬레이터.

매 INTERVAL_SEC 초마다 백엔드 `/api/alarms` 를 두드려서:
  - 70% 확률: 새 알람 생성 (POST)
  - 30% 확률: 가장 오래된 OPEN 알람 처리 (PATCH /resolve)

도커 컨테이너에서 실행되며, 백엔드는 호스트에서 8080 으로 돌고 있다고 가정.
환경변수:
  BACKEND_URL   기본 http://host.docker.internal:8080
  INTERVAL_SEC  기본 2
"""

from __future__ import annotations

import json
import os
import random
import time
import urllib.error
import urllib.request

BACKEND = os.environ.get("BACKEND_URL", "http://host.docker.internal:8080").rstrip("/")
INTERVAL = float(os.environ.get("INTERVAL_SEC", "2"))

EQUIPMENTS = [
    "CAST-02",
    "LINE-01_CAST-01",
    "LINE-01_CNC-02",
    "LINE-02_CNC-01",
    "LINE-02_WASH-01",
    "LINE-03_ASSEMBLY-01",
    "LINE-03_INSPECTION-01",
]

# (alarmType, severity)
ALARM_TYPES = [
    ("온도 이상", "WARNING"),
    ("온도 이상", "CRITICAL"),
    ("진동 이상", "WARNING"),
    ("진동 이상", "CRITICAL"),
    ("압력 이상", "WARNING"),
    ("토크 이상", "WARNING"),
    ("리크 압력 이상", "CRITICAL"),
    ("정기 점검", "INFO"),
]

MESSAGES = {
    "온도 이상": "온도 임계 초과 (시뮬)",
    "진동 이상": "RMS 임계 초과 (시뮬)",
    "압력 이상": "압력 편차 발생 (시뮬)",
    "토크 이상": "토크 편차 발생 (시뮬)",
    "리크 압력 이상": "리크 압력 임계 초과 (시뮬)",
    "정기 점검": "정기 점검 안내 (시뮬)",
}


def http_call(method: str, path: str, body: dict | None = None) -> tuple[int, bytes]:
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        f"{BACKEND}{path}",
        data=data,
        method=method,
        headers={"Content-Type": "application/json; charset=utf-8"},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:  # noqa: BLE001
        return -1, str(e).encode()


def now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%S")


def post_random_alarm(counter: int) -> None:
    eq = random.choice(EQUIPMENTS)
    atype, sev = random.choice(ALARM_TYPES)
    body = {
        "equipmentCode": eq,
        "alarmCode": f"SIM-{counter:06d}",
        "alarmType": atype,
        "alarmCategory": atype.replace(" 이상", ""),
        "severity": sev,
        "alarmMessage": f"{MESSAGES.get(atype, atype)} #{counter}",
        "occurredAt": now_iso(),
    }
    code, _ = http_call("POST", "/api/alarms", body)
    print(f"[sim] POST {code} {eq} {atype}/{sev}", flush=True)


def resolve_oldest_open() -> None:
    code, raw = http_call("GET", "/api/alarms?status=OPEN")
    if code != 200:
        print(f"[sim] list OPEN failed code={code}", flush=True)
        return
    try:
        arr = json.loads(raw)
    except json.JSONDecodeError:
        print("[sim] OPEN response not JSON", flush=True)
        return
    if not arr:
        print("[sim] no OPEN alarm to resolve", flush=True)
        return
    aid = arr[-1].get("alarmId")  # response 는 보통 최신순, 가장 오래된 건 마지막
    if aid is None:
        return
    code2, _ = http_call(
        "PATCH",
        f"/api/alarms/{aid}/resolve",
        {
            "resolvedBy": "시뮬레이터",
            "resolvedAt": now_iso(),
            "comment": "자동 처리 (시뮬)",
        },
    )
    print(f"[sim] PATCH {code2} resolve #{aid}", flush=True)


def main() -> None:
    print(
        f"[sim] start backend={BACKEND} interval={INTERVAL}s", flush=True
    )
    # 시작 직전에 백엔드가 살아있는지 한 번 ping
    for attempt in range(30):
        code, _ = http_call("GET", "/health")
        if code == 200:
            print(f"[sim] backend healthy after {attempt + 1} try", flush=True)
            break
        print(f"[sim] backend not ready (code={code}), retry…", flush=True)
        time.sleep(2)
    else:
        print("[sim] giving up — backend never responded 200", flush=True)
        return

    counter = 0
    while True:
        counter += 1
        try:
            if random.random() < 0.7:
                post_random_alarm(counter)
            else:
                resolve_oldest_open()
        except Exception as e:  # noqa: BLE001
            print(f"[sim] loop error: {e}", flush=True)
        time.sleep(INTERVAL)


if __name__ == "__main__":
    main()
