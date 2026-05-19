from __future__ import annotations

import asyncio
import os
import time
from dataclasses import dataclass, field
from typing import Any, Optional

from textual.app import App, ComposeResult
from textual.binding import Binding
from textual.containers import Container, Grid, Horizontal, Vertical
from textual.reactive import reactive
from textual.widgets import Static

from .client import TuiClient
from .protocol import SOCKET_PATH, TagInfo


DARK_TEXT = "#010101"
LIGHT_LABEL_BG = "#f3f3f3"
CARD_BG = "#bdbdbd"
SCREEN_BG = "#8a8a8a"
BORDER = "#efefef"
BASE_BOX_W = 25
MIN_BOX_W = 14
TARGET_BOX_W = 25
GRID_GUTTER = 1
PAIR_GAP = 1
CARD_INNER_PADDING = 2
CARD_BORDER = 2
CARD_CONTENT_EXTRA = PAIR_GAP + CARD_INNER_PADDING + CARD_BORDER
GUIDE_NORMAL = "[bold red]⇦⇧⇨⇩[/] [black]이동[/]  [bold red]Enter[/] 선택  [bold red]Q[/] 종료"
GUIDE_EDIT = "[bold red]⇦⇧⇨⇩[/] [black]이동[/]  [bold red]Enter[/] 확인  [bold red]Esc[/] 취소"


@dataclass
class FocusItem:
    kind: str
    name: str


@dataclass
class UiState:
    tags: list[TagInfo] = field(default_factory=list)
    by_name: dict[str, TagInfo] = field(default_factory=dict)
    focus_items: list[FocusItem] = field(default_factory=list)
    focus_idx: int = 0
    editing: bool = False
    edit_buf: str = ""
    notice: str = ""
    notice_until: float = 0.0
    connected: bool = False
    last_error: str = ""
    socket_path: str = SOCKET_PATH

    def current(self) -> Optional[FocusItem]:
        if not self.focus_items:
            return None
        if self.focus_idx >= len(self.focus_items):
            self.focus_idx = 0
        return self.focus_items[self.focus_idx]

    def set_notice(self, msg: str, seconds: float = 2.0) -> None:
        self.notice = msg
        self.notice_until = time.monotonic() + seconds

    def clear_expired_notice(self) -> None:
        if self.notice and time.monotonic() >= self.notice_until:
            self.notice = ""
            self.notice_until = 0.0

    def refresh_tags(self, tags: list[TagInfo]) -> None:
        prev = self.current()
        prev_key = (prev.kind, prev.name) if prev else None
        self.tags = tags
        self.by_name = {t.name: t for t in tags}
        self._build_focus_items()
        if prev_key:
            for i, item in enumerate(self.focus_items):
                if (item.kind, item.name) == prev_key:
                    self.focus_idx = i
                    break
        if self.focus_items and self.focus_idx >= len(self.focus_items):
            self.focus_idx = 0

    def _build_focus_items(self) -> None:
        items: list[FocusItem] = []
        power = next((t for t in self.tags if t.role == "power"), None)
        if power:
            items.append(FocusItem(kind="power", name=power.name))
        for t in self.tags:
            if t.role == "setpoint":
                items.append(FocusItem(kind="sp", name=t.name))
        self.focus_items = items


def _format_value(v: Any, data_type: str) -> str:
    if v is None:
        return "----"
    if data_type == "bool" or isinstance(v, bool):
        return "ON" if v else "OFF"
    if data_type == "float":
        try:
            return f"{float(v):.2f}"
        except Exception:
            return str(v)
    if data_type == "int":
        try:
            return f"{int(v)}"
        except Exception:
            return str(v)
    return str(v)


class TitleBar(Static):
    pass


class GuideBar(Static):
    pass


class StatusBar(Static):
    pass


class PowerTrack(Static):
    powered = reactive(False)
    focused = reactive(False)

    def watch_focused(self, focused: bool) -> None:
        self.set_class(focused, "-focused")

    def watch_powered(self, powered: bool) -> None:
        self.set_class(powered, "-on")
        self.set_class(not powered, "-off")
        if powered:
            self.update("[bold #202020]POWER [/][bold white on green] ON [/] ")
        else:
            self.update("[bold #202020]POWER [/][bold white on red] OFF [/] ")


class TagLabel(Static):
    pass


class TagValue(Static):
    editing = reactive(False)

    def watch_editing(self, editing: bool) -> None:
        self.set_class(editing, "-editing")


class TagColumn(Container):
    def __init__(self, label_id: str, value_id: str, box_width: int) -> None:
        super().__init__()
        self.box_width = box_width
        self.label_widget = TagLabel(id=label_id)
        self.value_gap = Static(classes="value-gap")
        self.value_widget = TagValue(id=value_id)

    def compose(self) -> ComposeResult:
        yield self.label_widget
        yield self.value_gap
        yield self.value_widget

    def set_sizes(self, box_width: int) -> None:
        self.box_width = box_width
        for widget in (self.label_widget, self.value_widget):
            widget.styles.width = box_width
            widget.styles.min_width = box_width
            widget.styles.max_width = box_width

    def set_data(self, label: str, value: str, *, markup: str, editing: bool = False) -> None:
        clipped = label[: self.box_width]
        self.label_widget.update(f"[bold {DARK_TEXT} on {LIGHT_LABEL_BG}]{clipped:<{self.box_width}}[/]")
        self.value_widget.update(markup.format(value=f"{value:^{self.box_width}}"))
        self.value_widget.editing = editing


class SpCard(Container):
    focused = reactive(False)

    def __init__(self, sp_name: str, box_width: int) -> None:
        super().__init__(id=f"card-{sp_name}")
        self.sp_name = sp_name
        self.row = Horizontal(classes="sp-row")
        self.sp_col = TagColumn(f"sp-label-{sp_name}", f"sp-value-{sp_name}", box_width)
        self.gap = Static(classes="pair-gap")
        self.actual_col = TagColumn(f"actual-label-{sp_name}", f"actual-value-{sp_name}", box_width)

    def compose(self) -> ComposeResult:
        with self.row:
            yield self.sp_col
            yield self.gap
            yield self.actual_col

    def set_sizes(self, box_width: int) -> None:
        self.sp_col.set_sizes(box_width)
        self.actual_col.set_sizes(box_width)

    def watch_focused(self, focused: bool) -> None:
        self.set_class(focused, "-focused")

    def set_data(
        self,
        *,
        sp_label: str,
        sp_value: str,
        actual_label: str,
        actual_value: str,
        has_actual: bool,
        editing: bool,
        focused: bool,
    ) -> None:
        self.focused = focused
        self.sp_col.set_data(
            sp_label,
            sp_value,
            markup="[bold yellow on black]{value}[/]" if editing else "[bold red on black]{value}[/]",
            editing=editing,
        )
        self.actual_col.display = has_actual
        self.gap.display = has_actual
        if has_actual:
            self.actual_col.set_data(
                actual_label,
                actual_value,
                markup="[bold white on black]{value}[/]",
                editing=False,
            )


class TuiApp(App):
    CSS = f"""
    Screen {{
        background: {SCREEN_BG};
        color: white;
        overflow: hidden hidden;
    }}

    #root {{
        layout: vertical;
        height: 100%;
        width: 100%;
        padding: 0 1;
        overflow: hidden hidden;
    }}

    TitleBar {{
        height: 3;
        min-height: 3;
        max-height: 3;
        border: solid {BORDER};
        background: #d7d7d7;
        color: {DARK_TEXT};
        text-style: bold;
        content-align: left middle;
        padding: 0 1;
        margin: 1 0 1 0;
    }}

    #power-wrap {{
        height: 3;
        min-height: 3;
        max-height: 3;
        margin: 0 0 1 0;
    }}

    PowerTrack {{
        width: 24;
        height: 3;
        min-height: 3;
        max-height: 3;
        border: solid {BORDER};
        background: #dadada;
        color: {DARK_TEXT};
        content-align: center middle;
        text-style: bold;
        padding: 0 1;
    }}

    PowerTrack.-focused {{
        border: solid yellow;
    }}

    PowerTrack.-on {{
        background: #d7e8d7;
    }}

    PowerTrack.-off {{
        background: #edd6d6;
    }}

    #sp-grid {{
        height: 1fr;
        min-height: 1fr;
        grid-size: 2;
        grid-columns: 1fr 1fr;
        grid-gutter: 1 1;
        overflow: hidden hidden;
    }}

    SpCard {{
        height: 6;
        min-height: 6;
        max-height: 6;
        border: solid {BORDER};
        background: {CARD_BG};
        padding: 0 1;
        overflow: hidden hidden;
    }}

    SpCard.-focused {{
        border: solid yellow;
    }}

    .sp-row {{
        layout: horizontal;
        width: 100%;
        height: 100%;
        align: center middle;
    }}

    TagColumn {{
        layout: vertical;
        width: 1fr;
        height: 100%;
        align: center middle;
        overflow: hidden hidden;
    }}

    TagLabel {{
        height: 1;
        min-height: 1;
        max-height: 1;
        background: {LIGHT_LABEL_BG};
        color: {DARK_TEXT};
        text-style: bold;
        content-align: left middle;
        overflow: hidden hidden;
    }}

    .value-gap {{
        height: 1;
        min-height: 1;
        max-height: 1;
    }}

    TagValue {{
        height: 1;
        min-height: 1;
        max-height: 1;
        background: black;
        color: white;
        text-style: bold;
        content-align: center middle;
        overflow: hidden hidden;
    }}

    TagValue.-editing {{
        color: yellow;
    }}

    .pair-gap {{
        width: 1;
        min-width: 1;
        max-width: 1;
        height: 100%;
    }}

    GuideBar {{
        height: 1;
        min-height: 1;
        max-height: 1;
        background: #efefef;
        color: {DARK_TEXT};
        padding: 0 1;
        margin: 0;
        overflow: hidden hidden;
    }}

    StatusBar {{
        height: 1;
        min-height: 1;
        max-height: 1;
        background: #d9d9d9;
        color: {DARK_TEXT};
        padding: 0 1;
        margin: 0;
        overflow: hidden hidden;
    }}
    """

    BINDINGS = [
        Binding("up,left", "prev_focus", show=False),
        Binding("down,right", "next_focus", show=False),
        Binding("enter", "activate", show=False),
        Binding("escape", "cancel_edit", show=False),
        Binding("q", "quit_app", show=False),
        Binding("w", "prev_focus", show=False),
        Binding("a", "prev_focus", show=False),
        Binding("s", "next_focus", show=False),
        Binding("d", "next_focus", show=False),
        Binding("h", "prev_focus", show=False),
        Binding("j", "next_focus", show=False),
        Binding("k", "prev_focus", show=False),
        Binding("l", "next_focus", show=False),
    ]

    def __init__(self, socket_path: str = SOCKET_PATH) -> None:
        super().__init__()
        self.socket_path = socket_path
        self.state = UiState(socket_path=socket_path)
        self.equipment_name = "equipment"
        self.poll_client: Optional[TuiClient] = None
        self.root_col = Vertical(id="root")
        self.title_bar = TitleBar()
        self.power_wrap = Horizontal(id="power-wrap")
        self.power_track = PowerTrack()
        self.sp_grid = Grid(id="sp-grid")
        self.guide_bar = GuideBar()
        self.status_bar = StatusBar()
        self.sp_cards: dict[str, SpCard] = {}
        self.box_width = BASE_BOX_W
        self._last_layout_sig: tuple[int, int] | None = None

    def compose(self) -> ComposeResult:
        with self.root_col:
            yield self.title_bar
            with self.power_wrap:
                yield self.power_track
            yield self.sp_grid
            yield self.guide_bar
            yield self.status_bar

    async def on_mount(self) -> None:
        await self._boot_connect()
        self._configure_layout(force=True)
        self._ensure_sp_cards()
        self._refresh_title_only()
        self._refresh_dynamic_parts()
        self.set_interval(1.0, self._poll_tick)
        self.set_interval(0.2, self._ui_tick)

    def on_resize(self) -> None:
        self._configure_layout()

    def _compute_layout(self) -> tuple[int, int]:
        width = max(self.size.width, 40)
        max_columns = min(5, max(1, len(self.sp_cards) or len([i for i in self.state.focus_items if i.kind == "sp"]) or 1))

        for columns in range(max_columns, 0, -1):
            available = width - ((columns - 1) * GRID_GUTTER)
            card_width = available // columns
            usable = card_width - CARD_CONTENT_EXTRA
            box_width = usable // 2
            if box_width >= TARGET_BOX_W:
                return columns, TARGET_BOX_W

        for columns in range(max_columns, 0, -1):
            available = width - ((columns - 1) * GRID_GUTTER)
            card_width = available // columns
            usable = card_width - CARD_CONTENT_EXTRA
            box_width = usable // 2
            if box_width >= MIN_BOX_W:
                return columns, box_width

        return 1, MIN_BOX_W

    def _configure_layout(self, force: bool = False) -> None:
        cols, box_width = self._compute_layout()
        sig = (cols, box_width)
        if not force and sig == self._last_layout_sig:
            return
        self._last_layout_sig = sig
        self.sp_grid.styles.grid_size_columns = cols
        self.box_width = box_width
        for card in self.sp_cards.values():
            card.set_sizes(self.box_width)
        self._refresh_dynamic_parts()

    async def _boot_connect(self) -> None:
        try:
            boot = TuiClient(socket_path=self.socket_path)
            boot.connect()
            if not boot.ping():
                self.exit(message=f"TUI wrapper 서버에 ping 실패: {self.socket_path}")
                return
            self.equipment_name = self._detect_equipment_name()
            tags = await asyncio.to_thread(boot.read)
            self.state.refresh_tags(tags)
            self.state.connected = True
            boot.close()
        except FileNotFoundError:
            self.exit(message=f"소켓이 없어요: {self.socket_path}")
            return
        except Exception as e:
            self.exit(message=f"초기 연결 실패: {e}")
            return

        self.poll_client = TuiClient(socket_path=self.socket_path)
        self.poll_client.connect()

    def _detect_equipment_name(self) -> str:
        name = os.environ.get("EQUIPMENT_NAME")
        if name:
            return name
        line = os.environ.get("LINE_ID", "")
        cfg = os.environ.get("SIM_CONFIG", "")
        if cfg:
            from pathlib import Path
            eq = Path(cfg).stem
            return f"{line}_{eq}" if line else eq
        return "equipment"

    async def _poll_tick(self) -> None:
        if self.poll_client is None:
            return
        try:
            tags = await asyncio.to_thread(self.poll_client.read)
            self.state.refresh_tags(tags)
            self.state.connected = True
            self.state.last_error = ""
            self._ensure_sp_cards()
        except Exception as e:
            self.state.connected = False
            self.state.last_error = f"{type(e).__name__}: {e}"
        self._refresh_dynamic_parts()

    def _ui_tick(self) -> None:
        self.state.clear_expired_notice()
        self._refresh_bars_only()

    def _ensure_sp_cards(self) -> None:
        current_names = [item.name for item in self.state.focus_items if item.kind == "sp"]
        current_set = set(current_names)
        mounted_set = set(self.sp_cards.keys())

        for name in current_names:
            if name not in self.sp_cards:
                card = SpCard(name, self.box_width)
                self.sp_cards[name] = card
                self.sp_grid.mount(card)
            else:
                self.sp_cards[name].set_sizes(self.box_width)

        for name in mounted_set - current_set:
            card = self.sp_cards.pop(name)
            card.remove()

        for name, card in self.sp_cards.items():
            card.display = name in current_set
            card.set_sizes(self.box_width)

    def _refresh_title_only(self) -> None:
        self.title_bar.update(f"[bold {DARK_TEXT}]{self.equipment_name[:24]}[/]")

    def _refresh_dynamic_parts(self) -> None:
        self._refresh_power()
        self._update_sp_cards()
        self._refresh_bars_only()

    def _refresh_power(self) -> None:
        power = next((t for t in self.state.tags if t.role == "power"), None)
        self.power_track.powered = bool(power.value) if power and power.value is not None else False
        cur = self.state.current()
        self.power_track.focused = cur is not None and cur.kind == "power"

    def _update_sp_cards(self) -> None:
        sensors_by_sp: dict[str, TagInfo] = {}
        for t in self.state.tags:
            if t.role == "sensor" and t.source_sp:
                sensors_by_sp.setdefault(t.source_sp, t)

        current = self.state.current()
        for name, card in self.sp_cards.items():
            sp = self.state.by_name.get(name)
            if sp is None:
                card.display = False
                continue
            actual = sensors_by_sp.get(name)
            is_current = current is not None and current.kind == "sp" and current.name == name
            card.set_data(
                sp_label=f"{sp.name}",
                sp_value=self.state.edit_buf if (self.state.editing and is_current) else _format_value(sp.value, sp.data_type),
                actual_label=actual.name if actual else "",
                actual_value=_format_value(actual.value, actual.data_type) if actual else "",
                has_actual=actual is not None,
                editing=self.state.editing and is_current,
                focused=is_current,
            )

    def _refresh_bars_only(self) -> None:
        self.guide_bar.update(GUIDE_EDIT if self.state.editing else GUIDE_NORMAL)
        if self.state.notice:
            text = f"★ {self.state.notice}"
        elif not self.state.connected:
            text = "연결끊김"
        elif self.state.last_error:
            text = f"✗ {self.state.last_error[:24]}"
        else:
            text = " "
        self.status_bar.update(text[:80])

    def action_prev_focus(self) -> None:
        if self.state.editing or not self.state.focus_items:
            return
        self.state.focus_idx = (self.state.focus_idx - 1) % len(self.state.focus_items)
        self._refresh_dynamic_parts()

    def action_next_focus(self) -> None:
        if self.state.editing or not self.state.focus_items:
            return
        self.state.focus_idx = (self.state.focus_idx + 1) % len(self.state.focus_items)
        self._refresh_dynamic_parts()

    def action_activate(self) -> None:
        cur = self.state.current()
        if cur is None:
            return
        if self.state.editing:
            self._commit_edit()
            return
        if cur.kind == "power":
            self._toggle_power(cur.name)
        elif cur.kind == "sp":
            self.state.editing = True
            self.state.edit_buf = ""
        self._refresh_dynamic_parts()

    def action_cancel_edit(self) -> None:
        if self.state.editing:
            self.state.editing = False
            self.state.edit_buf = ""
            self.state.set_notice("편집 취소")
            self._refresh_dynamic_parts()

    def action_quit_app(self) -> None:
        self.exit()

    async def on_key(self, event) -> None:
        if not self.state.editing:
            return
        if event.key == "backspace":
            self.state.edit_buf = self.state.edit_buf[:-1]
            self._refresh_dynamic_parts()
            event.prevent_default()
            return
        ch = event.character or ""
        if len(ch) == 1 and (ch.isdigit() or ch in ("-", ".")):
            self.state.edit_buf += ch
            self._refresh_dynamic_parts()
            event.prevent_default()

    def _toggle_power(self, name: str) -> None:
        tag = self.state.by_name.get(name)
        if tag is None:
            return
        new = not bool(tag.value)
        ok, msg = self._safe_write(name, new)
        self.state.set_notice((f"POWER {'ON' if new else 'OFF'}") if ok else f"power 실패: {msg}")

    def _commit_edit(self) -> None:
        cur = self.state.current()
        if cur is None:
            return
        tag = self.state.by_name.get(cur.name)
        if tag is None:
            self.state.editing = False
            self.state.edit_buf = ""
            return
        try:
            if tag.data_type == "float":
                val: Any = float(self.state.edit_buf)
            elif tag.data_type == "int":
                val = int(float(self.state.edit_buf))
            else:
                val = self.state.edit_buf
        except ValueError:
            self.state.set_notice(f"숫자 형식 오류: '{self.state.edit_buf}'")
            self.state.editing = False
            self.state.edit_buf = ""
            self._refresh_dynamic_parts()
            return
        ok, msg = self._safe_write(cur.name, val)
        self.state.set_notice(msg if ok else f"write 실패: {msg}")
        self.state.editing = False
        self.state.edit_buf = ""
        self._refresh_dynamic_parts()

    def _safe_write(self, name: str, value: Any) -> tuple[bool, str]:
        try:
            client = TuiClient(socket_path=self.socket_path)
            client.connect()
            try:
                return client.write(name, value)
            finally:
                client.close()
        except Exception as e:
            return False, f"{type(e).__name__}: {e}"


def run(socket_path: str = SOCKET_PATH) -> int:
    app = TuiApp(socket_path=socket_path)
    app.run()
    return 0