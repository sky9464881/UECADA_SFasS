"""`python -m sim.tui_wrapper` 진입점.

사용:
    docker exec -it <equipment_container> python -m sim.tui_wrapper
    docker exec -it <equipment_container> python -m sim.tui_wrapper --socket /tmp/sim-tui.sock

종료:
    Q 또는 Ctrl-C — exec 프로세스만 종료. 시뮬 본체(PID 1) 는 그대로 동작.
"""
from __future__ import annotations

import argparse
import logging
import sys

from .application import run
from .protocol import SOCKET_PATH


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="python -m sim.tui_wrapper",
        description="컨테이너 내부 TUI 제어반 (시뮬 EquipmentState 직접 접근)",
    )
    parser.add_argument(
        "--socket", default=SOCKET_PATH,
        help=f"UDS 소켓 경로 (기본 {SOCKET_PATH})",
    )
    parser.add_argument("-v", "--verbose", action="store_true")
    args = parser.parse_args(argv)

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.WARNING,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    return run(socket_path=args.socket)


if __name__ == "__main__":
    sys.exit(main())
