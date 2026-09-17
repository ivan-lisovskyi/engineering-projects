#!/usr/bin/env python3
"""Calculate the final neighbor-based stack-like groups.

Two platelets are linked when their periodic center distance is below 0.08 and
their unoriented normals differ by at most 20 degrees. This is a geometric
check, not a pebble-contact classifier. The CSV contains one summary row per
run followed by one row for each group in that run.
"""

from __future__ import annotations

import csv
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np

SEEDS = [73019, 11234, 101098, 333, 112233, 1337212]
PH_VALUES = [3, 10]
RUNS_BASE = Path.home() / "MercuryRuns"
RUN_TEMPLATE = "ph{ph}_anisotropy_updated_clean_N50_1s_seed{seed}"

STACK_DISTANCE = 0.08
STACK_ANGLE_DEG = 20.0


def read_vtp(path: Path) -> tuple[np.ndarray, np.ndarray]:
    """Read platelet centers and normals from the driver's ASCII VTP file."""
    root = ET.parse(path).getroot()
    points_array = root.find(".//Points/DataArray")
    normals_array = root.find(".//PointData/DataArray[@Name='platelet_normal']")
    if points_array is None or normals_array is None:
        raise ValueError(f"cannot find points or platelet normals in {path}")
    points = np.fromstring(points_array.text or "", sep=" ").reshape(-1, 3)
    normals = np.fromstring(normals_array.text or "", sep=" ").reshape(-1, 3)
    if len(points) != len(normals):
        raise ValueError(f"{path}: {len(points)} points but {len(normals)} normals")
    norms = np.linalg.norm(normals, axis=1)
    if np.any(norms <= 1.0e-15):
        raise ValueError(f"zero-length platelet normal in {path}")
    return points, normals / norms[:, None]


def group_platelets(
    points: np.ndarray,
    normals: np.ndarray,
    threshold: float,
    max_angle_deg: float,
) -> list[list[int]]:
    """Return connected groups that pass the distance and angle tests."""
    n = len(points)
    parent = list(range(n))
    min_abs_dot = np.cos(np.deg2rad(max_angle_deg))

    def find(x: int) -> int:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for i in range(n):
        for j in range(i + 1, n):
            delta = points[i] - points[j]
            delta -= np.round(delta)
            close_enough = np.linalg.norm(delta) < threshold
            parallel_enough = abs(np.dot(normals[i], normals[j])) >= min_abs_dot
            if close_enough and parallel_enough:
                parent[find(i)] = find(j)

    groups: dict[int, list[int]] = {}
    for i in range(n):
        groups.setdefault(find(i), []).append(i)
    return sorted(groups.values(), key=len, reverse=True)


def group_order_parameter(normals: np.ndarray) -> float:
    """Return the tensor order parameter S for one group of normals."""
    tensor = (normals[:, :, None] * normals[:, None, :]).mean(axis=0)
    lam_max = np.linalg.eigvalsh(tensor)[-1]
    return (3.0 * lam_max - 1.0) / 2.0


def latest_vtp(run_dir: Path) -> Path:
    files = list(run_dir.glob("clay_anisotropy_updated_normals_*.vtp"))
    if not files:
        raise FileNotFoundError(f"no normals VTP in {run_dir}")
    return max(files, key=lambda path: int(path.stem.rsplit("_", 1)[-1]))


def main() -> None:
    out_path = Path(__file__).resolve().parents[1] / "figures" / "stacking_summary.csv"
    rows = []

    for ph in PH_VALUES:
        for seed in SEEDS:
            run_dir = RUNS_BASE / RUN_TEMPLATE.format(ph=ph, seed=seed)
            points, normals = read_vtp(latest_vtp(run_dir))

            stacks = [
                component
                for component in group_platelets(
                    points,
                    normals,
                    STACK_DISTANCE,
                    STACK_ANGLE_DEG,
                )
                if len(component) >= 2
            ]
            stack_s = [group_order_parameter(normals[component]) for component in stacks]

            summary = {
                "pH": ph,
                "seed": seed,
                "row_type": "summary",
                "stacks": len(stacks),
                "platelets_in_stacks": sum(len(component) for component in stacks),
                "largest_stack": max((len(component) for component in stacks), default=0),
                "stack_S_internal": "",
            }
            rows.append(summary)
            for group_index, (component, s_value) in enumerate(zip(stacks, stack_s)):
                rows.append({
                    "pH": ph,
                    "seed": seed,
                    "row_type": f"stack_{group_index}",
                    "stacks": "",
                    "platelets_in_stacks": len(component),
                    "largest_stack": "",
                    "stack_S_internal": f"{s_value:.4f}",
                })
            print(
                f"pH {ph:>2} seed {seed:>6}: {summary['stacks']} stacks, "
                f"{summary['platelets_in_stacks']} platelets in stacks, "
                f"largest {summary['largest_stack']}"
            )

    with out_path.open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {out_path}")


if __name__ == "__main__":
    main()
