#!/usr/bin/env python3
"""Check the small clump overlaps present at the start of the final runs.

The driver allowed initial overlaps. For each seed, this script reads the
saved t=0 particle file, applies periodic distances, and reports the number
of overlapping clump pairs and the largest pebble penetration. The matched
pH 3 and pH 10 runs start from the same positions, so only pH 3 is read.
"""

import csv
import xml.etree.ElementTree as ET
from pathlib import Path

import numpy as np

SEEDS = [73019, 11234, 101098, 333, 112233, 1337212]
RUN_BASE = Path.home() / "MercuryRuns"
RUN_PATTERN = "ph3_anisotropy_updated_clean_N50_1s_seed{seed}"
OUT_CSV = (
    Path(__file__).resolve().parents[1]
    / "figures"
    / "six_seed_clean"
    / "initial_overlap_summary.csv"
)

N_CLUMPS = 50
N_PEBBLES = 1094
R_PEBBLE = 0.008
TOUCH = 2.0 * R_PEBBLE


def load_initial_pebbles(run_dir: Path) -> tuple[np.ndarray, np.ndarray]:
    """Read pebble positions and clump centers from the t=0 VTU file."""
    files = list(run_dir.glob("*Particle_0.vtu"))
    if len(files) != 1:
        raise FileNotFoundError(f"Expected one Particle_0.vtu in {run_dir}, found {len(files)}")

    root = ET.parse(files[0]).getroot()
    points_array = root.find(".//Points/DataArray")
    charge_array = root.find(".//PointData/DataArray[@Name='Charge']")
    if points_array is None or charge_array is None:
        raise ValueError(f"Cannot find points or charge values in {files[0]}")

    points = np.fromstring(points_array.text or "", sep=" ").reshape(-1, 3)
    charge = np.fromstring(charge_array.text or "", sep=" ")
    pebble_mask = np.abs(charge) > 1.0e-12
    centers = points[~pebble_mask]
    pebbles = points[pebble_mask]
    if len(centers) != N_CLUMPS or len(pebbles) != N_CLUMPS * N_PEBBLES:
        raise ValueError(
            f"{files[0]}: unexpected layout "
            f"({len(centers)} masters, {len(pebbles)} pebbles)"
        )
    return pebbles.reshape(N_CLUMPS, N_PEBBLES, 3), centers


def min_image(delta: np.ndarray) -> np.ndarray:
    """Apply the periodic minimum-image convention for the unit box."""
    return delta - np.round(delta)


def overlap_stats(pebbles: np.ndarray, centers: np.ndarray) -> tuple[int, float]:
    """Count overlapping clump pairs and the largest pebble penetration.

    Only clump pairs whose centers are within twice the clump circumradius
    (plus the touch distance) are tested in detail; farther pairs cannot
    have touching pebbles.
    """
    circumradius = max(
        np.linalg.norm(pebbles[i] - centers[i], axis=1).max() for i in range(N_CLUMPS)
    )
    center_delta = min_image(centers[:, None, :] - centers[None, :, :])
    center_dist = np.sqrt((center_delta ** 2).sum(-1))
    cutoff = 2.0 * circumradius + TOUCH

    n_pairs = 0
    max_penetration = 0.0
    for i in range(N_CLUMPS):
        for j in range(i + 1, N_CLUMPS):
            if center_dist[i, j] >= cutoff:
                continue
            delta = min_image(pebbles[i][:, None, :] - pebbles[j][None, :, :])
            min_dist = np.sqrt((delta ** 2).sum(-1)).min()
            if min_dist < TOUCH:
                n_pairs += 1
                max_penetration = max(max_penetration, TOUCH - min_dist)
    return n_pairs, max_penetration


def main() -> None:
    rows = []
    for seed in SEEDS:
        run_dir = RUN_BASE / RUN_PATTERN.format(seed=seed)
        pebbles, centers = load_initial_pebbles(run_dir)
        n_pairs, penetration = overlap_stats(pebbles, centers)
        rows.append(
            {
                "seed": seed,
                "overlapping_clump_pairs": n_pairs,
                "max_pebble_penetration": f"{penetration:.4e}",
                "penetration_pct_of_diameter": f"{100.0 * penetration / TOUCH:.1f}",
            }
        )
        print(
            f"seed {seed}: {n_pairs} overlapping clump pairs, "
            f"max penetration {penetration:.3e} "
            f"({100.0 * penetration / TOUCH:.1f}% of the pebble diameter)"
        )

    counts = [r["overlapping_clump_pairs"] for r in rows]
    worst = max(float(r["max_pebble_penetration"]) for r in rows)
    print(
        f"range: {min(counts)}-{max(counts)} pairs per seed, "
        f"largest penetration {worst:.3e} "
        f"({100.0 * worst / TOUCH:.1f}% of the pebble diameter)"
    )

    OUT_CSV.parent.mkdir(parents=True, exist_ok=True)
    with OUT_CSV.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    print(f"Wrote {OUT_CSV}")


if __name__ == "__main__":
    main()
