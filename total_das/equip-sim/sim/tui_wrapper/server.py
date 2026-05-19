"""TUI wrapper UDS 서버 — 시뮬 PID 1 측에서 daemon thread 로 가동.

main.py 에서 한 줄로 띄움:

    from sim.tui_wrapper.server import start_in_background
    start_in_background(state)

설계:
    - threading.Thread(daemon=True). 시뮬 본체에 영향 없고 종료 시 자동 정리.
    - 멀티 클라이언트(여러 exec 창) 동시 지원: 클라이언트마다 워커 스레드 1개.
    - 모든 write 는 state.set_external() 경유 → RO 검사·coerce·로깅 일관성.
    - 소켓 권한 0600. 같은 컨테이너 안에서 같은 UID 만 접근.
"""
from __future__ import annotations

import logging
import os
import socket
import socketserver
import threading
from pathlib import Path
from typing import Any

from ..config import TagConfig
from ..log import get_logger
from ..state import EquipmentState
from . import protocol as p

log = get_logger("tui-wrapper.server")


# ---------------------------------------------------------------------------
# 핸들러
# ---------------------------------------------------------------------------
class _Handler(socketserver.BaseRequestHandler):
    """클라이언트 1개 = 핸들러 1개 = 스레드 1개 (ThreadingMixIn)."""

    # 동일 서버 인스턴스가 공유. start_in_background 가 채워준다.
    state: EquipmentState

    def handle(self) -> None:
        sock: socket.socket = self.request
        sock.settimeout(60.0)
        peer = self._peer_label()
        log.info("client connected (%s)", peer)
        buf = bytearray()
        try:
            while True:
                line = p.recv_line(sock, buf)
                if line is None:
                    break
                try:
                    msg = p.decode_line(line)
                except Exception as e:
                    p.send_msg(sock, p.resp_err(f"bad json: {e}"))
                    continue
                resp = self._dispatch(msg)
                p.send_msg(sock, resp)
        except (ConnectionError, socket.timeout) as e:
            log.info("client %s closed: %s", peer, type(e).__name__)
        except Exception as e:
            log.exception("handler error: %s", e)
        finally:
            log.info("client disconnected (%s)", peer)

    def _peer_label(self) -> str:
        try:
            # UDS 의 peer cred 는 SO_PEERCRED 로만 얻을 수 있음. 단순 라벨링만.
            return f"fd={self.request.fileno()}"
        except Exception:
            return "unknown"

    # ----- dispatch -----
    def _dispatch(self, msg: dict[str, Any]) -> dict[str, Any]:
        op = msg.get("op")
        if op == p.OP_PING:
            return p.resp_ok({"pong": True})
        if op == p.OP_READ:
            return p.resp_ok({"tags": self._read_tags()})
        if op == p.OP_WRITE:
            name = msg.get("name")
            value = msg.get("value")
            if not isinstance(name, str):
                return p.resp_err("write: missing 'name'")
            ok = self.state.set_external(name, value)
            if not ok:
                return p.resp_err(f"write rejected: '{name}' (unknown or read-only)")
            return p.resp_ok({"name": name, "value": self.state.read(name)})
        return p.resp_err(f"unknown op: {op!r}")

    def _read_tags(self) -> list[dict[str, Any]]:
        values = self.state.read_all()
        out: list[dict[str, Any]] = []
        for t in self.state.cfg.tags:  # type: TagConfig
            info = p.TagInfo(
                name=t.name,
                role=t.role,
                data_type=t.data_type,
                writable=t.writable,
                source_sp=t.source_sp,
                value=values.get(t.name),
            )
            out.append(info.to_json())
        return out


# ---------------------------------------------------------------------------
# 서버 (ThreadingUnixStreamServer 가 stdlib 에 없어서 직접 합성)
# ---------------------------------------------------------------------------
class _ThreadingUDSServer(socketserver.ThreadingMixIn, socketserver.UnixStreamServer):
    allow_reuse_address = True
    daemon_threads = True


def _prepare_socket_path(path: str) -> None:
    """기존 소켓 정리. 디렉토리 권한은 그대로 (/tmp 는 1777)."""
    sp = Path(path)
    if sp.exists() or sp.is_socket():
        try:
            sp.unlink()
        except FileNotFoundError:
            pass
        except OSError as e:
            log.warning("could not unlink existing socket %s: %s", path, e)


def start_in_background(
    state: EquipmentState,
    socket_path: str = p.SOCKET_PATH,
) -> threading.Thread:
    """시뮬 본체에서 호출. daemon 스레드로 UDS 서버 가동.

    같은 컨테이너 안에서만 의미가 있으므로, 실패해도 시뮬은 그대로 둔다
    (예: 권한, 파일시스템 read-only 등). 실패 시 경고만 남기고 None 반환.
    """
    try:
        _prepare_socket_path(socket_path)
        _Handler.state = state
        server = _ThreadingUDSServer(socket_path, _Handler)
        # 같은 UID 만 접근 가능하도록 0600.
        try:
            os.chmod(socket_path, 0o600)
        except OSError as e:
            log.warning("chmod %s failed: %s", socket_path, e)

        t = threading.Thread(
            target=server.serve_forever,
            name="tui-wrapper-server",
            daemon=True,
        )
        t.start()
        log.info("tui-wrapper UDS server listening on %s", socket_path)
        return t
    except Exception as e:
        log.warning("tui-wrapper server failed to start (%s) — simulator continues", e)
        # 시뮬 본체에 영향 주지 않음
        return None  # type: ignore[return-value]
