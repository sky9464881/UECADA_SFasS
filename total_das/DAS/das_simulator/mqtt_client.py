from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Callable


@dataclass(frozen=True)
class MqttConfig:
    host: str
    port: int
    topic_prefix: str
    client_id: str
    username: str | None = None
    password: str | None = None
    qos: int = 0
    retain: bool = False


class MqttPublisher:
    def __init__(self, config: MqttConfig) -> None:
        try:
            import paho.mqtt.client as mqtt
        except ImportError as exc:
            raise RuntimeError(
                "paho-mqtt is not installed. Install dependencies with: py -3 -m pip install -r requirements.txt"
            ) from exc

        self.config = config
        self._mqtt = mqtt
        self._client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=config.client_id)
        self._subscriptions: dict[str, Callable[[str, str], None]] = {}
        if config.username:
            self._client.username_pw_set(config.username, config.password)
        self._client.on_message = self._on_message

    def connect(self, retries: int = 30, retry_delay_sec: float = 1.0) -> None:
        last_error: Exception | None = None
        for attempt in range(1, retries + 1):
            try:
                self._client.connect(self.config.host, self.config.port, keepalive=60)
                self._client.loop_start()
                return
            except OSError as exc:
                last_error = exc
                if attempt == retries:
                    break
                time.sleep(retry_delay_sec)
        raise RuntimeError(
            f"Could not connect to MQTT broker {self.config.host}:{self.config.port}"
        ) from last_error

    def close(self) -> None:
        self._client.loop_stop()
        self._client.disconnect()

    def publish(self, line_id: str, equipment_id: str, payload: str) -> str:
        topic = f"{self.config.topic_prefix.rstrip('/')}/{line_id}/{equipment_id}/window"
        info = self._client.publish(topic, payload, qos=self.config.qos, retain=self.config.retain)
        info.wait_for_publish()
        return topic

    def subscribe(self, topic: str, callback: Callable[[str, str], None]) -> None:
        self._subscriptions[topic] = callback
        self._client.subscribe(topic, qos=self.config.qos)

    def _on_message(self, _client: object, _userdata: object, message: object) -> None:
        topic = getattr(message, "topic", "")
        payload_bytes = getattr(message, "payload", b"")
        callback = self._subscriptions.get(topic)
        if callback is None:
            return
        try:
            payload = payload_bytes.decode("utf-8")
        except UnicodeDecodeError:
            payload = payload_bytes.decode("utf-8", errors="replace")
        callback(topic, payload)
