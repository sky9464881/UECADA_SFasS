from __future__ import annotations

import argparse
from pathlib import Path


def read_lines(path: Path) -> list[str]:
    with path.open("r", encoding="utf-8") as file:
        return [line.rstrip("\n") for line in file if line.strip()]


def interleave_jsonl(inputs: list[Path], output: Path) -> int:
    if len(inputs) < 2:
        raise ValueError("Pass at least two input JSONL files.")

    groups = [read_lines(path) for path in inputs]
    if any(len(group) == 0 for group in groups):
        raise ValueError("All input JSONL files must contain at least one row.")

    output.parent.mkdir(parents=True, exist_ok=True)
    written = 0
    with output.open("w", encoding="utf-8") as file:
        for row_index in range(max(len(group) for group in groups)):
            for group in groups:
                if row_index < len(group):
                    file.write(group[row_index])
                    file.write("\n")
                    written += 1

    return written


def main() -> None:
    parser = argparse.ArgumentParser(description="Interleave multiple JSONL replay files row by row.")
    parser.add_argument("--input", required=True, nargs="+", type=Path, help="Input JSONL files.")
    parser.add_argument("--output", required=True, type=Path, help="Output JSONL file.")
    args = parser.parse_args()

    count = interleave_jsonl(args.input, args.output)
    print(f"wrote {count} interleaved rows to {args.output}")


if __name__ == "__main__":
    main()
