"""Equipment simulator — role 기반, 헤드리스.

stdout 으로 로그만 흘리는 단순 시뮬레이터.
종료 시 SIGINT/SIGTERM 으로 graceful shutdown.

호스트에서 제어반 UI 가 필요하면 `host_tui/` 패키지를 별도 실행.
"""
from __future__ import annotations

import os
import signal
import sys
import threading

from sim.config import load_config
from sim.log import get_logger
from sim.protocols import mc_server, modbus_server, opcua_server
from sim.state import EquipmentState
from sim.tui_wrapper.server import start_in_background as start_tui_ipc

log = get_logger("main")


RUNNERS = {
    "modbus": modbus_server.run,
    "modbus-rtu": modbus_server.run,
    "modbus-rtu-tcp": modbus_server.run,
    "opcua": opcua_server.run,
    "mcprotocol": mc_server.run,
}


def main() -> int:
    cfg_path = (sys.argv[1] if len(sys.argv) > 1 else None) or os.environ.get("SIM_CONFIG")
    if not cfg_path:
        print("usage: python main.py <config.json>", file=sys.stderr)
        return 2

    log.info("loading config: %s", cfg_path)
    cfg = load_config(cfg_path)
    log.info(
        "equipment=%s protocol=%s host=%s port=%s sampling_ms=%s tags=%d",
        cfg.equipment_name, cfg.protocol, cfg.host, cfg.port,
        cfg.sampling_ms, len(cfg.tags),
    )

    if cfg.protocol not in RUNNERS:
        log.error("unknown protocol: %s", cfg.protocol)
        return 2

    state = EquipmentState(cfg)
    stop_event = threading.Event()

    # 컨테이너 내부 TUI(`docker exec -it ... python -m sim.tui_wrapper`) 용 IPC 서버.
    # 시작 실패해도 시뮬 본체에는 영향 없음 (daemon thread, 경고만 남김).
    start_tui_ipc(state)

    def _handle(signum, _f):
        log.info("received signal %s, shutting down...", signum)
        stop_event.set()

    signal.signal(signal.SIGINT, _handle)
    signal.signal(signal.SIGTERM, _handle)

    runner = RUNNERS[cfg.protocol]
    try:
        runner(cfg, state, stop_event)
    except KeyboardInterrupt:
        stop_event.set()
    except Exception as e:
        log.exception("runner crashed: %s", e)
        return 1
    log.info("bye")
    return 0


if __name__ == "__main__":
    sys.exit(main())
