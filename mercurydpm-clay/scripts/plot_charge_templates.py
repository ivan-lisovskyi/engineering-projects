#!/usr/bin/env python3
"""Plot the raw source values and the values used by the periodic solver.

This is the small standalone script behind the source-template figure in the
Methods chapter. It uses the same region counts and interpolation as the driver.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import matplotlib.pyplot as plt


N_SILICA = 469
N_ALUMINA = 469
N_EDGE = 156
N_TOTAL = N_SILICA + N_ALUMINA + N_EDGE
REGION_NAMES = ("Silica-like face", "Alumina-like face", "Edge")
REGION_COLORS = {
    "Silica-like face": "#2166ac",
    "Alumina-like face": "#b2182b",
    "Edge": "#1b7837",
}


def source_values(ph: float) -> tuple[dict[str, float], dict[str, float], float]:
    """Return the raw values, corrected values, and raw weighted mean."""
    t = (ph - 3.0) / 7.0
    raw = {
        "Silica-like face": -0.22,
        "Alumina-like face": 0.20 - 0.40 * t,
        "Edge": 0.06 - 0.12 * t,
    }
    weighted_mean = (
        N_SILICA * raw["Silica-like face"]
        + N_ALUMINA * raw["Alumina-like face"]
        + N_EDGE * raw["Edge"]
    ) / N_TOTAL
    solver = {name: value - weighted_mean for name, value in raw.items()}
    return raw, solver, weighted_mean


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "figures",
    )
    args = parser.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)

    ph_values = [3.0 + 7.0 * i / 280.0 for i in range(281)]
    raw_series = {name: [] for name in REGION_NAMES}
    solver_series = {name: [] for name in REGION_NAMES}
    mean_series = []
    for ph in ph_values:
        raw, solver, weighted_mean = source_values(ph)
        for name in REGION_NAMES:
            raw_series[name].append(raw[name])
            solver_series[name].append(solver[name])
        mean_series.append(weighted_mean)

    plt.rcParams.update({
        "font.size": 13,
        "axes.labelsize": 13.5,
        "axes.titlesize": 14,
        "legend.fontsize": 11.5,
        "xtick.labelsize": 12,
        "ytick.labelsize": 12,
        "axes.spines.top": False,
        "axes.spines.right": False,
    })
    fig, axes = plt.subplots(1, 2, figsize=(10.8, 4.3), sharey=True)
    for name in REGION_NAMES:
        axes[0].plot(
            ph_values,
            raw_series[name],
            color=REGION_COLORS[name],
            linewidth=2.2,
            label=name,
        )
        axes[1].plot(
            ph_values,
            solver_series[name],
            color=REGION_COLORS[name],
            linewidth=2.2,
            label=name,
        )
    axes[0].plot(
        ph_values,
        mean_series,
        color="0.35",
        linewidth=1.4,
        linestyle="--",
        label="Raw weighted mean",
    )

    for ax in axes:
        ax.axhline(0.0, color="0.55", linewidth=0.8)
        ax.set_xlim(3.0, 10.0)
        ax.set_xlabel("Template pH input")
        ax.grid(True, alpha=0.22, linewidth=0.7)
    axes[0].set_ylabel("Reduced source value")
    axes[0].set_title("Raw prescribed template")
    axes[1].set_title("Template used by the periodic solver")
    axes[0].legend(frameon=False, loc="best")
    axes[1].legend(frameon=False, loc="best")
    fig.tight_layout()

    stem = args.out_dir / "charge_template_raw_and_corrected_vs_ph"
    for extension in ("png", "pdf", "svg"):
        fig.savefig(stem.with_suffix(f".{extension}"), dpi=320, bbox_inches="tight")
    plt.close(fig)


if __name__ == "__main__":
    main()
