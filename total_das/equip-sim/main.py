"""Equipment simulator (role 기반)."""
from __future__ import annotations

import os
import signal
import sys
import threading

from sim.config import load_config
from sim.log import get_logger
from sim.protocols import mc_server, modbus_server, opcua_server

log = get_logger("main")


def main() -> int:
    cfg_path = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("SIM_CONFIG")
    if not cfg_path:
        log.error("usage: python main.py <config.json>  (or set SIM_CONFIG)")
        return 2
    log.info("loading config: %s", cfg_path)
    cfg = load_config(cfg_path)
    log.info(
        "equipment=%s protocol=%s host=%s port=%s sampling_ms=%s tags=%d",
        cfg.equipment_name, cfg.protocol, cfg.host, cfg.port,
        cfg.sampling_ms, len(cfg.tags),
    )

    stop_event = threading.Event()

    def _handle(signum, _f):
        log.info("received signal %s, shutting down...", signum)
        stop_event.set()

    signal.signal(signal.SIGINT, _handle)
    signal.signal(signal.SIGTERM, _handle)

    runners = {
        "modbus": modbus_server.run,
        "opcua": opcua_server.run,
        "mcprotocol": mc_server.run,
    }
    try:
        runners[cfg.protocol](cfg, stop_event)
    except KeyboardInterrupt:
        stop_event.set()
    except Exception as e:
        log.exception("runner crashed: %s", e)
        return 1
    log.info("bye")
    return 0


if __name__ == "__main__":
    sys.exit(main())
