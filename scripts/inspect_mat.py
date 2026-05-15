from __future__ import annotations

import argparse
from pathlib import Path

from scipy.io import loadmat


def main() -> None:
    parser = argparse.ArgumentParser(description="Inspect MATLAB MAT file fields.")
    parser.add_argument("mat_file", type=Path)
    args = parser.parse_args()

    mat_file = args.mat_file
    mat = loadmat(mat_file)

    print(f"file: {mat_file}")
    for key, value in mat.items():
        if key.startswith("__"):
            continue

        shape = getattr(value, "shape", None)
        dtype = getattr(value, "dtype", None)
        print(f"{key}: shape={shape}, dtype={dtype}")


if __name__ == "__main__":
    main()
