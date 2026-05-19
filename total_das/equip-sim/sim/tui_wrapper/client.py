"""TUI wrapper UDS 클라이언트 — exec 측 (application.py 에서 사용).

순수 동기 인터페이스. application.py 의 poll 스레드에서 사용.
"""
from __future__ import annotations

import socket
from typing import Any, Optional

from ..log import get_logger
from . import protocol as p

log = get_logger("tui-wrapper.client")


def _sanitize_single_line(value: Any) -> str:
    s = "" if value is None else str(value)
    s = s.replace("\r", " ").replace("\n", " ").replace("\t", " ")
    return " ".join(s.split())

class TuiClient:
    """UDS 한 줄짜리 동기 RPC.

    사용:
        with TuiClient() as c:
            tags = c.read()
            c.write("injection_pressure_sp", 75.0)
    """

    def __init__(self, socket_path: str = p.SOCKET_PATH, timeout: float = 2.0) -> None:
        self.socket_path = socket_path
        self.timeout = timeout
        self._sock: Optional[socket.socket] = None
        self._buf = bytearray()

    # ----- 연결 관리 -----
    def connect(self) -> None:
        s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        s.settimeout(self.timeout)
        s.connect(self.socket_path)
        self._sock = s
        self._buf = bytearray()

    def close(self) -> None:
        if self._sock is not None:
            try:
                self._sock.close()
            finally:
                self._sock = None

    def __enter__(self) -> "TuiClient":
        self.connect()
        return self

    def __exit__(self, *exc) -> None:
        self.close()

    # ----- RPC -----
    def _round_trip(self, msg: dict[str, Any]) -> dict[str, Any]:
        if self._sock is None:
            raise RuntimeError("client not connected")
        p.send_msg(self._sock, msg)
        line = p.recv_line(self._sock, self._buf)
        if line is None:
            raise ConnectionError("server closed during request")
        return p.decode_line(line)

    def ping(self) -> bool:
        try:
            resp = self._round_trip(p.req_ping())
            return resp.get("status") == p.STATUS_OK
        except Exception:
            return False

    def read(self) -> list[p.TagInfo]:
        """모든 태그의 메타+값 수신. 실패 시 예외."""
        resp = self._round_trip(p.req_read())
        if resp.get("status") != p.STATUS_OK:
            raise RuntimeError(f"read failed: {resp.get('reason')}")
        return [p.TagInfo.from_json(d) for d in resp.get("tags", [])]
    
    
    def write(self, name: str, value: Any) -> tuple[bool, str]:
        """단일 태그 쓰기. (ok, message) 반환.

        ok=False 시 message 에 사유 (예: 'read-only', 'unknown tag').
        """
        resp = self._round_trip(p.req_write(name, value))
        if resp.get("status") == p.STATUS_OK:
            return True, _sanitize_single_line(f"{name} ⇦ {resp.get('value')}")
        return False, str(resp.get("reason", "write failed"))
