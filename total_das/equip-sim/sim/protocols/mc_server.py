"""MC Protocol 3E Binary TCP 서버.

지원 명령:
- Batch Read  Word/Bit (Cmd 0x0401, Sub 0x0001 / 0x0000)
- Batch Write Word/Bit (Cmd 0x1401, Sub 0x0001 / 0x0000)

프레임 (요청):
    | 50 00 | NetworkNo(1) | PCNo(1) | DstIO(2 LE) | DstSt(1) |
    | ReqLen(2 LE) | Timer(2 LE) | Cmd(2 LE) | SubCmd(2 LE) | <body> |

프레임 (정상 응답):
    | D0 00 | NetworkNo | PCNo | DstIO(2) | DstSt | ResLen(2 LE) | EndCode=00 00 | <body> |

에러 시 EndCode != 0.
"""
from __future__ import annotations

import socket
import socketserver
import struct
import threading
from typing import Any, Dict, List, Tuple

from ..config import MC_DEVICE_CODES, SimConfig, TagConfig
from ..log import get_logger
from ..state import EquipmentState

log = get_logger("mcprotocol")


# ---------------------------------------------------------------------------
# 디바이스 이미지 (raw word / bit)
# ---------------------------------------------------------------------------

class DeviceImage:
    def __init__(self) -> None:
        self._lock = threading.Lock()
        self.words: Dict[str, Dict[int, int]] = {}
        self.bits: Dict[str, Dict[int, int]] = {}

    def _table(self, device: str) -> Dict[int, int]:
        _code, is_bit = MC_DEVICE_CODES[device]
        if is_bit:
            return self.bits.setdefault(device, {})
        return self.words.setdefault(device, {})

    def read_word(self, device: str, address: int) -> int:
        with self._lock:
            return self._table(device).get(address, 0) & 0xFFFF

    def read_bit(self, device: str, address: int) -> int:
        with self._lock:
            return 1 if self._table(device).get(address, 0) else 0

    def write_word(self, device: str, address: int, value: int) -> None:
        with self._lock:
            self._table(device)[address] = value & 0xFFFF

    def write_bit(self, device: str, address: int, value: int) -> None:
        with self._lock:
            self._table(device)[address] = 1 if value else 0


# ---------------------------------------------------------------------------
# tag <-> device image
# ---------------------------------------------------------------------------

def _tag_to_image(t: TagConfig, value: Any, image: DeviceImage) -> None:
    m = t.mc
    if t.data_type == "bool":
        image.write_bit(m.device, m.address, 1 if value else 0)
    elif t.data_type == "int":
        image.write_word(m.device, m.address, int(value) & 0xFFFF)
    elif t.data_type == "float":
        # IEEE-754, 2 word little-endian (low word first)
        raw = struct.pack("<f", float(value))
        lo, hi = struct.unpack("<HH", raw)
        image.write_word(m.device, m.address, lo)
        image.write_word(m.device, m.address + 1, hi)


def _image_to_tag(t: TagConfig, image: DeviceImage) -> Any:
    m = t.mc
    if t.data_type == "bool":
        return bool(image.read_bit(m.device, m.address))
    if t.data_type == "int":
        raw = image.read_word(m.device, m.address)
        return raw - 0x10000 if raw & 0x8000 else raw
    if t.data_type == "float":
        lo = image.read_word(m.device, m.address)
        hi = image.read_word(m.device, m.address + 1)
        return struct.unpack("<f", struct.pack("<HH", lo, hi))[0]
    return None


# ---------------------------------------------------------------------------
# 3E Binary
# ---------------------------------------------------------------------------

REQ_SUBHEADER = b"\x50\x00"
RES_SUBHEADER = b"\xD0\x00"

CMD_BATCH_READ = 0x0401
CMD_BATCH_WRITE = 0x1401
SUBCMD_WORD = 0x0000
SUBCMD_BIT = 0x0001

ERR_INVALID_COMMAND = 0xC059
ERR_DEVICE_ERROR = 0xC056
ERR_ACCESS_DENIED = 0xC0B4   # 서버 정의: 읽기전용 태그에 write 시도

_CODE_TO_DEVICE = {code: (dev, is_bit) for dev, (code, is_bit) in MC_DEVICE_CODES.items()}


def _make_response(end_code: int, body: bytes = b"") -> bytes:
    """ResLen = EndCode(2) + body length."""
    header = (
        RES_SUBHEADER
        + b"\x00"            # NetworkNo
        + b"\xFF"            # PCNo
        + b"\xFF\x03"        # DstIO (=0x03FF)
        + b"\x00"            # DstSt
    )
    res_len = 2 + len(body)
    return header + struct.pack("<H", res_len) + struct.pack("<H", end_code) + body


def _parse_device_field(raw: bytes) -> Tuple[str, int]:
    """3-byte LE address + 1-byte device code -> (device_char, address)"""
    if len(raw) != 4:
        raise ValueError("device field must be 4 bytes")
    addr = raw[0] | (raw[1] << 8) | (raw[2] << 16)
    code = raw[3]
    dev_info = _CODE_TO_DEVICE.get(code)
    if not dev_info:
        raise KeyError(f"unknown device code 0x{code:02X}")
    return dev_info[0], addr


# ---------------------------------------------------------------------------
# Command handler
# ---------------------------------------------------------------------------

class MCHandler:
    def __init__(self, state: EquipmentState, image: DeviceImage,
                 tags_by_addr: Dict[Tuple[str, int], TagConfig]) -> None:
        self.state = state
        self.image = image
        self.tags_by_addr = tags_by_addr

    def _owning_tag(self, device: str, address: int) -> TagConfig | None:
        t = self.tags_by_addr.get((device, address))
        if t:
            return t
        # float 의 두 번째 워드(주소+1)에 떨어진 경우
        prev = self.tags_by_addr.get((device, address - 1))
        if prev and prev.data_type == "float":
            return prev
        return None

    # ----- READ -----
    def handle_batch_read(self, subcmd: int, body: bytes) -> bytes:
        if len(body) < 6:
            return _make_response(ERR_DEVICE_ERROR)
        try:
            device, head_addr = _parse_device_field(body[:4])
        except (ValueError, KeyError) as e:
            log.warning("read: %s", e)
            return _make_response(ERR_DEVICE_ERROR)
        points = struct.unpack("<H", body[4:6])[0]
        is_bit_req = (subcmd == SUBCMD_BIT)
        _code, dev_is_bit = MC_DEVICE_CODES[device]

        if is_bit_req:
            # subcmd=bit 은 비트 디바이스만 의미가 있다.
            if not dev_is_bit:
                log.warning("read: bit subcmd on word device %s", device)
                return _make_response(ERR_INVALID_COMMAND)
            # 2 bits / byte. high nibble = first point.
            data = bytearray()
            for i in range(0, points, 2):
                b1 = self.image.read_bit(device, head_addr + i)
                b2 = self.image.read_bit(device, head_addr + i + 1) if (i + 1) < points else 0
                data.append((b1 << 4) | b2)
            log.debug("read bit %s%d x%d", device, head_addr, points)
            return _make_response(0x0000, bytes(data))

        # subcmd=word
        buf = bytearray()
        if dev_is_bit:
            # 비트 디바이스를 워드 단위로 읽기: 1 word = 16 bits (LSB=head_addr+0)
            for i in range(points):
                base = head_addr + i * 16
                word = 0
                for b in range(16):
                    if self.image.read_bit(device, base + b):
                        word |= (1 << b)
                buf += struct.pack("<H", word)
            log.debug("read bit-as-word %s%d x%d", device, head_addr, points)
        else:
            for i in range(points):
                w = self.image.read_word(device, head_addr + i)
                buf += struct.pack("<H", w)
            log.debug("read word %s%d x%d", device, head_addr, points)
        return _make_response(0x0000, bytes(buf))

    # ----- WRITE -----
    def handle_batch_write(self, subcmd: int, body: bytes) -> bytes:
        if len(body) < 6:
            return _make_response(ERR_DEVICE_ERROR)
        try:
            device, head_addr = _parse_device_field(body[:4])
        except (ValueError, KeyError) as e:
            log.warning("write: %s", e)
            return _make_response(ERR_DEVICE_ERROR)
        points = struct.unpack("<H", body[4:6])[0]
        payload = body[6:]
        is_bit_req = (subcmd == SUBCMD_BIT)
        _code, dev_is_bit = MC_DEVICE_CODES[device]

        if is_bit_req and not dev_is_bit:
            log.warning("write: bit subcmd on word device %s", device)
            return _make_response(ERR_INVALID_COMMAND)

        # 1) subcmd=word & 워드 디바이스       -> 워드 단위 쓰기 (D 등)
        # 2) subcmd=word & 비트 디바이스       -> 비트 디바이스를 워드로 패킹해서 쓰기 (M 등 묶음 쓰기)
        # 3) subcmd=bit  & 비트 디바이스       -> 비트 단위 쓰기
        word_mode = not is_bit_req
        pack_bit_as_word = word_mode and dev_is_bit

        new_word_values: List[int] = []
        new_bit_values: List[int] = []

        if word_mode:
            need = points * 2
            if len(payload) < need:
                return _make_response(ERR_DEVICE_ERROR)
            for i in range(points):
                w = struct.unpack("<H", payload[i * 2:i * 2 + 2])[0]
                new_word_values.append(w)
        else:  # bit packed
            need = (points + 1) // 2
            if len(payload) < need:
                return _make_response(ERR_DEVICE_ERROR)
            for i in range(points):
                byte = payload[i // 2]
                bit = (byte >> 4) if (i % 2 == 0) else (byte & 0x0F)
                new_bit_values.append(1 if bit else 0)

        # RO 태그 검사 + 영향받는 태그 수집
        touched_tags: set[str] = set()
        if pack_bit_as_word:
            for wi in range(points):
                base = head_addr + wi * 16
                for b in range(16):
                    t = self._owning_tag(device, base + b)
                    if t and not t.writable:
                        log.warning("write refuse: RO bit tag '%s' (device=%s addr=%d)",
                                    t.name, device, base + b)
                        return _make_response(ERR_ACCESS_DENIED)
                    if t:
                        touched_tags.add(t.name)
        else:
            for i in range(points):
                t = self._owning_tag(device, head_addr + i)
                if t and not t.writable:
                    log.warning("write refuse: RO tag '%s' (device=%s addr=%d)",
                                t.name, device, head_addr + i)
                    return _make_response(ERR_ACCESS_DENIED)
                if t:
                    touched_tags.add(t.name)

        # 이미지에 반영
        if pack_bit_as_word:
            for wi, w in enumerate(new_word_values):
                base = head_addr + wi * 16
                for b in range(16):
                    self.image.write_bit(device, base + b, (w >> b) & 1)
        elif word_mode:
            for i, v in enumerate(new_word_values):
                self.image.write_word(device, head_addr + i, v)
        else:
            for i, v in enumerate(new_bit_values):
                self.image.write_bit(device, head_addr + i, v)

        # 영향받은 태그 -> 다시 해석해서 state 에 set_external 로 흘림
        for tname in touched_tags:
            t = self.state._by_name[tname]  # noqa: SLF001 (단순 접근)
            new_val = _image_to_tag(t, self.image)
            self.state.set_external(t.name, new_val)

        mode = "bit-as-word" if pack_bit_as_word else ("word" if word_mode else "bit")
        log.debug("write %s %s%d x%d (tags=%s)",
                  mode, device, head_addr, points, sorted(touched_tags))
        return _make_response(0x0000)


# ---------------------------------------------------------------------------
# TCP server
# ---------------------------------------------------------------------------

class _ReusableTCPServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def _recv_exact(sock: socket.socket, n: int) -> bytes | None:
    buf = bytearray()
    while len(buf) < n:
        try:
            chunk = sock.recv(n - len(buf))
        except socket.timeout:
            return None
        if not chunk:
            return None
        buf.extend(chunk)
    return bytes(buf)


def _make_request_handler(handler: MCHandler):
    class _RH(socketserver.BaseRequestHandler):
        def handle(self_inner) -> None:
            sock: socket.socket = self_inner.request
            # Node-RED contrib 노드는 long-lived TCP 유지. idle timeout 은 충분히 길게.
            sock.settimeout(60.0)
            peer = sock.getpeername()
            log.info("client connected: %s", peer)
            try:
                while True:
                    # subheader(2) + NetworkNo(1) + PCNo(1) + DstIO(2) + DstSt(1) + ReqLen(2) = 9
                    head = _recv_exact(sock, 9)
                    if not head:
                        break
                    if head[:2] != REQ_SUBHEADER:
                        log.warning("bad subheader: %s", head[:2].hex())
                        break
                    req_len = struct.unpack("<H", head[7:9])[0]
                    rest = _recv_exact(sock, req_len)
                    if rest is None:
                        break
                    # rest = Timer(2) + Cmd(2) + SubCmd(2) + body
                    if len(rest) < 6:
                        sock.sendall(_make_response(ERR_DEVICE_ERROR))
                        continue
                    cmd = struct.unpack("<H", rest[2:4])[0]
                    subcmd = struct.unpack("<H", rest[4:6])[0]
                    body = rest[6:]
                    log.debug("REQ cmd=0x%04X sub=0x%04X body=%s",
                              cmd, subcmd, body.hex())
                    if cmd == CMD_BATCH_READ:
                        resp = handler.handle_batch_read(subcmd, body)
                    elif cmd == CMD_BATCH_WRITE:
                        resp = handler.handle_batch_write(subcmd, body)
                    else:
                        log.warning("unsupported cmd=0x%04X sub=0x%04X", cmd, subcmd)
                        resp = _make_response(ERR_INVALID_COMMAND)
                    sock.sendall(resp)
            except (ConnectionError, socket.timeout) as e:
                log.info("client %s closed: %s", peer, type(e).__name__)
            except Exception as e:
                log.exception("handler error: %s", e)
            finally:
                log.info("client disconnected: %s", peer)
    return _RH


# ---------------------------------------------------------------------------
# entry
# ---------------------------------------------------------------------------

def run(cfg: SimConfig, state: EquipmentState, stop_event: threading.Event) -> None:
    image = DeviceImage()
    tags_by_addr: Dict[Tuple[str, int], TagConfig] = {
        (t.mc.device, t.mc.address): t for t in cfg.tags
    }

    # 초기값 동기화
    for t in cfg.tags:
        _tag_to_image(t, state.read(t.name), image)

    log.info("=== %s mcprotocol 3E binary mapping ===", cfg.equipment_name)
    for t in cfg.tags:
        log.info(
            "  %-22s role=%-8s type=%-5s  %s%d %s",
            t.name, t.role, t.data_type, t.mc.device, t.mc.address,
            "(RW)" if t.writable else "(RO)",
        )

    handler = MCHandler(state, image, tags_by_addr)
    rh = _make_request_handler(handler)
    server = _ReusableTCPServer((cfg.host, cfg.port), rh)

    server_thread = threading.Thread(
        target=server.serve_forever, name="mcprotocol-server", daemon=True,
    )
    server_thread.start()
    log.info("mcprotocol listening on %s:%d (tags=%d, period=%.3fs)",
             cfg.host, cfg.port, len(cfg.tags), cfg.sampling_ms / 1000.0)

    period = cfg.sampling_ms / 1000.0
    try:
        while not stop_event.wait(period):
            state.tick()
            # state -> image 스탬핑: sensor / counter / alarm 만
            # (power / setpoint 는 외부 write 와 충돌하므로 덮어쓰지 않음)
            for t in cfg.tags:
                if t.role in ("power", "setpoint"):
                    continue
                _tag_to_image(t, state.read(t.name), image)
    finally:
        log.info("shutting down mcprotocol server")
        server.shutdown()
        server.server_close()
