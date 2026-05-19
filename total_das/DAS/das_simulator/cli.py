from __future__ import annotations

import argparse
import time
from pathlib import Path

from .mqtt_client import MqttConfig, MqttPublisher
from .simulator import SensorWindowSimulator, payload_to_json
from .specs import equipment_by_id
from .vibration import BearingWindowSource, VibrationGenerator


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Generate 1-second sensor/DAQ windows and publish them to MQTT for Node-RED DAS."
    )
    parser.add_argument("--bearing-root", default="BearingType_DeepGrooveBall", help="Bearing .mat data root.")
    parser.add_argument("--sample-rate", type=int, default=16000, help="Preferred bearing sampling rate.")
    parser.add_argument("--rotating-speed", type=int, default=1200, help="Preferred bearing rotating speed in rpm.")
    parser.add_argument("--window-sec", type=float, default=2.0, help="Vibration model window duration in seconds.")
    parser.add_argument(
        "--stride-samples",
        type=int,
        default=None,
        help="Vibration window stride in samples. Default is one second at the selected sample rate.",
    )
    parser.add_argument(
        "--equipment",
        action="append",
        help="Equipment, line, or instance ID to simulate. Examples: CAST-01, LINE-01, LINE-01-CAST-01.",
    )
    parser.add_argument(
        "--operating-state",
        choices=["OFF", "STANDBY", "RUN", "AUTO"],
        default="RUN",
        help="Operating state for all equipment.",
    )
    parser.add_argument(
        "--health-state",
        choices=["NORMAL", "WARNING", "DANGER", "AUTO"],
        default="NORMAL",
        help="Health state for RUN windows.",
    )
    parser.add_argument("--warning-rate", type=float, default=0.02, help="AUTO warning probability per window.")
    parser.add_argument("--danger-rate", type=float, default=0.004, help="AUTO danger probability per window.")
    parser.add_argument("--interval-sec", type=float, default=1.0, help="Publish interval.")
    parser.add_argument("--once", action="store_true", help="Generate one window per equipment and exit.")
    parser.add_argument("--max-windows", type=int, default=0, help="Stop after N window ticks. 0 means infinite.")
    parser.add_argument("--compact", action="store_true", help="Do not include vibration waveform arrays.")
    parser.add_argument("--pretty", action="store_true", help="Pretty-print JSON in dry-run mode.")
    parser.add_argument("--precision", type=int, default=4, help="Decimal precision for numeric values.")
    parser.add_argument("--seed", type=int, default=None, help="Random seed for reproducible data.")
    parser.add_argument("--dry-run", action="store_true", help="Print generated MQTT messages instead of publishing.")
    parser.add_argument("--mqtt-host", default="localhost", help="MQTT broker host.")
    parser.add_argument("--mqtt-port", type=int, default=1883, help="MQTT broker port.")
    parser.add_argument("--mqtt-username", default=None, help="MQTT username.")
    parser.add_argument("--mqtt-password", default=None, help="MQTT password.")
    parser.add_argument("--mqtt-client-id", default="das-sensor-simulator", help="MQTT client ID.")
    parser.add_argument("--topic-prefix", default="das/simulator", help="MQTT topic prefix.")
    parser.add_argument("--qos", type=int, choices=[0, 1, 2], default=0, help="MQTT QoS.")
    parser.add_argument("--retain", action="store_true", help="Publish MQTT messages with retain flag.")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    equipment = equipment_by_id(set(args.equipment) if args.equipment else None)
    if not equipment:
        requested = ", ".join(args.equipment or [])
        raise SystemExit(f"No equipment matched: {requested}")

    bearing_source = BearingWindowSource(
        root=Path(args.bearing_root),
        sample_rate_hz=args.sample_rate,
        rotating_speed_rpm=args.rotating_speed,
        window_seconds=args.window_sec,
        stride_samples=args.stride_samples,
    )
    vibration = VibrationGenerator(bearing_source, seed=args.seed)
    simulator = SensorWindowSimulator(
        vibration=vibration,
        operating_state=args.operating_state,
        health_state=args.health_state,
        seed=args.seed,
        warning_rate=args.warning_rate,
        danger_rate=args.danger_rate,
        precision=args.precision,
        include_waveform=not args.compact,
    )

    publisher = None
    if not args.dry_run:
        publisher = MqttPublisher(
            MqttConfig(
                host=args.mqtt_host,
                port=args.mqtt_port,
                topic_prefix=args.topic_prefix,
                client_id=args.mqtt_client_id,
                username=args.mqtt_username,
                password=args.mqtt_password,
                qos=args.qos,
                retain=args.retain,
            )
        )
        publisher.connect()

    tick = 0
    try:
        while True:
            started = time.monotonic()
            for item in equipment:
                payload = simulator.next_payload(item)
                encoded = payload_to_json(payload, pretty=args.pretty and args.dry_run)
                topic = f"{args.topic_prefix.rstrip('/')}/{item.line_id}/{item.equipment_id}/window"
                if publisher is None:
                    print(f"\n# topic: {topic}")
                    print(encoded)
                else:
                    publisher.publish(item.line_id, item.equipment_id, encoded)

            tick += 1
            if args.once or (args.max_windows and tick >= args.max_windows):
                break
            elapsed = time.monotonic() - started
            time.sleep(max(0.0, args.interval_sec - elapsed))
    finally:
        if publisher is not None:
            publisher.close()
    return 0
