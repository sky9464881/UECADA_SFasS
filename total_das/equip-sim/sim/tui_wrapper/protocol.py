"""TUI wrapper IPC 프로토콜 (JSON line over UDS).

소켓 경로:
    /tmp/sim-tui.sock   (컨테이너 내부 전용)

프레이밍:
    각 메시지 = 한 줄 JSON + '\\n'.
    line-buffered 라서 구현이 단순하고 디버그가 쉽다.

요청 / 응답:
    READ  ── 모든 태그 현재값과 메타정보를 한 번에 받기.
    WRITE ── 단일 태그에 값을 쓰기. set_external() 로 들어가므로 RO 자동 거절.
    PING  ── 연결 확인.

향후 확장:
    - SUBSCRIBE / EVENT: 서버 push (현재는 클라이언트 풀링으로 충분)
    - SNAPSHOT (binary) : msgpack 등. 현재 규모(태그 수십개)에선 불필요.
"""
from __future__ import annotations

import json
import socket
from dataclasses import dataclass
from typing import Any, Optional

# 컨테이너 안에서만 보이는 경로. compose 가 마운트하지 않아 호스트로 새 나가지 않음.
SOCKET_PATH = "/tmp/sim-tui.sock"

# 단일 메시지 최대 길이 (한 줄). 태그 수십개 × 메타 포함 페이로드 여유.
MAX_LINE = 64 * 1024


# ---------------------------------------------------------------------------
# 메시지 종류
# ---------------------------------------------------------------------------
OP_PING = "ping"
OP_READ = "read"
OP_WRITE = "write"

STATUS_OK = "ok"
STATUS_ERR = "err"


@dataclass
class TagInfo:
    """READ 응답에 실리는 태그 1개 분의 정적 메타 + 현재값."""
    name: str
    role: str            # power / setpoint / sensor / counter / alarm
    data_type: str       # bool / int / float
    writable: bool
    source_sp: Optional[str]
    value: Any

    def to_json(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "role": self.role,
            "data_type": self.data_type,
            "writable": self.writable,
            "source_sp": self.source_sp,
            "value": self.value,
        }

    @classmethod
    def from_json(cls, d: dict[str, Any]) -> "TagInfo":
        return cls(
            name=d["name"],
            role=d["role"],
            data_type=d["data_type"],
            writable=bool(d["writable"]),
            source_sp=d.get("source_sp"),
            value=d.get("value"),
        )


# ---------------------------------------------------------------------------
# encode / decode
# ---------------------------------------------------------------------------
def encode(msg: dict[str, Any]) -> bytes:
    """dict -> JSON line bytes."""
    return (json.dumps(msg, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")


def decode_line(line: bytes) -> dict[str, Any]:
    return json.loads(line.decode("utf-8"))


# ---------------------------------------------------------------------------
# 소켓 헬퍼 (양쪽이 같은 프레이밍을 쓰도록)
# ---------------------------------------------------------------------------
def recv_line(sock: socket.socket, buf: bytearray) -> Optional[bytes]:
    """sock 에서 '\\n' 종료 한 줄을 읽는다. EOF 면 None.

    buf 는 호출자가 보관하는 상태 (반복 호출 시 잔여 데이터 누적).
    """
    while b"\n" not in buf:
        chunk = sock.recv(4096)
        if not chunk:
            return None
        buf.extend(chunk)
        if len(buf) > MAX_LINE:
            raise ValueError(f"line too long ({len(buf)} > {MAX_LINE})")
    nl = buf.index(b"\n")
    line = bytes(buf[:nl])
    del buf[: nl + 1]
    return line


def send_msg(sock: socket.socket, msg: dict[str, Any]) -> None:
    sock.sendall(encode(msg))


# ---------------------------------------------------------------------------
# 요청 / 응답 빌더 (양쪽이 같은 schema 를 쓰도록)
# ---------------------------------------------------------------------------
def req_ping() -> dict[str, Any]:
    return {"op": OP_PING}


def req_read() -> dict[str, Any]:
    return {"op": OP_READ}


def req_write(name: str, value: Any) -> dict[str, Any]:
    return {"op": OP_WRITE, "name": name, "value": value}


def resp_ok(payload: Optional[dict[str, Any]] = None) -> dict[str, Any]:
    out: dict[str, Any] = {"status": STATUS_OK}
    if payload:
        out.update(payload)
    return out


def resp_err(reason: str) -> dict[str, Any]:
    return {"status": STATUS_ERR, "reason": reason}
