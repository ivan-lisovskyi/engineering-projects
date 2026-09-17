#!/usr/bin/env python3
"""Rebuild the six-seed pH 3 and pH 10 figures used in the thesis.

The script reads the completed MercuryDPM runs, plots the energy and fabric
measures, and repeats the stack-like check at every saved structural frame.
It also writes the numerical values behind each plot to CSV files.
"""

from __future__ import annotations

import argparse
import csv
import math
import xml.etree.ElementTree as ET
from pathlib import Path
from statistics import mean, stdev
from typing import Dict, List, Sequence, Tuple

import matplotlib.pyplot as plt

SEEDS = [73019, 11234, 101098, 333, 112233, 1337212]
PH_CASES = {
    3: {"label": "pH 3", "color": "#1f77b4"},
    10: {"label": "pH 10", "color": "#d62728"},
}
SEED_COLORS = {
    73019: "#e377c2",
    333: "#2ca02c",
    11234: "#9467bd",
    101098: "#ff7f0e",
    112233: "#17becf",
    1337212: "#8c564b",
}
T0_VALUES = {"S_tensor": 1.0, "S_z": 1.0, "lambda_max": 1.0}
STACK_CUTOFF = 0.08
STACK_MAX_ANGLE_DEG = 20.0
DISTANCE_SENSITIVITY = [0.06, 0.07, 0.08, 0.09, 0.10]
ANGLE_SENSITIVITY_DEG = [10.0, 20.0, 30.0]

RunData = Dict[str, List[float]]
AllData = Dict[int, Dict[int, RunData]]


def run_folder(ph: int, seed: int) -> Path:
    name = f"ph{ph}_anisotropy_updated_clean_N50_1s_seed{seed}"
    return Path.home() / "MercuryRuns" / name


def read_anisotropy(path: Path) -> Dict[str, List[float]]:
    """Read the scalar orientation log for one run."""
    with (path / "AnisotropyLog.csv").open(newline="") as f:
        rows = list(csv.DictReader(f))
    if not rows:
        raise ValueError(f"Empty AnisotropyLog.csv in {path}")
    data: Dict[str, List[float]] = {key: [] for key in rows[0].keys()}
    for row in rows:
        for key, value in row.items():
            data[key].append(float(value))
    return data


def read_lr_potential(path: Path) -> List[float]:
    """Read the logged total long-range energy values."""
    values: List[float] = []
    with (path / "LRPotential.txt").open() as f:
        for line in f:
            line = line.strip()
            if line:
                values.append(float(line))
    return values


def read_kinetic(path: Path) -> Tuple[List[float], List[float]]:
    """Read translational plus rotational kinetic energy from the ene file."""
    energy_file = path / "clay_anisotropy_updated.ene"
    lines = [
        line.split()
        for line in energy_file.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    header = lines[0]
    time_idx = header.index("time")
    tra_idx = header.index("traKineticEnergy")
    rot_idx = header.index("rotKineticEnergy")
    times: List[float] = []
    kinetic: List[float] = []
    for parts in lines[1:]:
        times.append(float(parts[time_idx]))
        kinetic.append(float(parts[tra_idx]) + float(parts[rot_idx]))
    return times, kinetic


def read_run(path: Path) -> RunData:
    """Collect the time series needed for the plots from one run folder."""
    anis = read_anisotropy(path)
    lr = read_lr_potential(path)
    ene_t, kinetic = read_kinetic(path)
    time = anis["time"]
    if len(lr) != len(time):
        raise ValueError(f"{path}: LRPotential length {len(lr)} != anisotropy length {len(time)}")
    if len(kinetic) == len(time) + 1 and abs(ene_t[0]) < 1e-15:
        ene_t = ene_t[1:]
        kinetic = kinetic[1:]
    if len(kinetic) != len(time) or len(ene_t) != len(time):
        raise ValueError(
            f"{path}: kinetic/time lengths {len(kinetic)}/{len(ene_t)} "
            f"!= anisotropy length {len(time)}"
        )
    lr_delta = [value - lr[0] for value in lr]
    return {
        "time": time,
        "kinetic_time": ene_t,
        "lr_total": lr,
        "lr_delta": lr_delta,
        "kinetic": kinetic,
        "S_tensor": anis["S_tensor"],
        "S_z": anis["S_z"],
        "lambda_max": anis["lambda_max"],
    }


def load_all() -> AllData:
    data: AllData = {}
    for ph in PH_CASES:
        data[ph] = {}
        for seed in SEEDS:
            path = run_folder(ph, seed)
            if not path.exists():
                raise FileNotFoundError(path)
            data[ph][seed] = read_run(path)
    return data


def vector_mean(series: Sequence[Sequence[float]]) -> List[float]:
    rows = list(series)
    return [mean(values) for values in zip(*rows)]


def with_t0(
    time: Sequence[float],
    values: Sequence[float],
    key: str,
) -> Tuple[List[float], List[float]]:
    """Prepend the known aligned t=0 value where that value is defined."""
    if key in T0_VALUES:
        return [0.0] + list(time), [T0_VALUES[key]] + list(values)
    return list(time), list(values)


def trim_series(
    time: Sequence[float],
    values: Sequence[float],
    tmax: float,
) -> Tuple[List[float], List[float]]:
    t_out, v_out = [], []
    for t, v in zip(time, values):
        if t <= tmax:
            t_out.append(t)
            v_out.append(v)
    return t_out, v_out


def setup_style() -> None:
    plt.rcParams.update({
        "font.size": 10.5,
        "axes.labelsize": 11,
        "axes.titlesize": 11.5,
        "legend.fontsize": 9.5,
        "xtick.labelsize": 9.5,
        "ytick.labelsize": 9.5,
        "figure.dpi": 130,
        "savefig.dpi": 320,
        "axes.spines.top": False,
        "axes.spines.right": False,
    })


def style_axes(
    ax,
    ylabel: str | None = None,
    xlabel: str = "Simulation time [model units]",
) -> None:
    if ylabel:
        ax.set_ylabel(ylabel)
    ax.set_xlabel(xlabel)
    ax.grid(True, alpha=0.22, linewidth=0.7)


def save(fig, out_dir: Path, name: str) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    for ext in ("png", "pdf", "svg"):
        fig.savefig(out_dir / f"{name}.{ext}", bbox_inches="tight")
    plt.close(fig)


def plot_ph_lines(
    ax,
    all_data: AllData,
    key: str,
    *,
    time_key: str = "time",
    zoom: float | None = None,
    ylabel: str | None = None,
) -> None:
    for ph, spec in PH_CASES.items():
        color = spec["color"]
        label = spec["label"]
        seeds = SEEDS
        time = all_data[ph][seeds[0]][time_key]
        values = [all_data[ph][seed][key] for seed in seeds]
        avg = vector_mean(values)

        for seed in seeds:
            t, v = with_t0(time, all_data[ph][seed][key], key)
            if zoom is not None:
                t, v = trim_series(t, v, zoom)
            ax.plot(t, v, color=color, alpha=0.22, linewidth=0.9)

        t_avg, v_avg = with_t0(time, avg, key)
        if zoom is not None:
            t_avg, v_avg = trim_series(t_avg, v_avg, zoom)
        ax.plot(t_avg, v_avg, color=color, linewidth=2.7, label=f"{label} mean")

    if ylabel:
        ax.set_ylabel(ylabel)


def plot_lr_full_zoom(all_data: AllData, out_dir: Path) -> None:
    fig, ax = plt.subplots(figsize=(8.4, 4.4))
    plot_ph_lines(ax, all_data, "lr_delta")
    ax.axhline(0, color="0.45", linestyle="--", linewidth=0.8)
    ax.set_xlim(0, 1.0)
    style_axes(ax, r"Energy change $\Delta U_{lr}$")
    ax.set_title("Long-range energy change from the first saved state")
    ax.legend(frameon=False)
    fig.tight_layout()
    save(fig, out_dir, "01_energy_change_six_seeds")


def plot_kinetic(all_data: AllData, out_dir: Path) -> None:
    fig, ax = plt.subplots(figsize=(8.4, 4.4))
    plot_ph_lines(ax, all_data, "kinetic", time_key="kinetic_time")
    ax.set_yscale("log")
    ax.set_xlim(0, 1.0)
    style_axes(ax, "Residual kinetic energy")
    ax.set_title("Relaxation check: kinetic energy")
    ax.legend(frameon=False)
    fig.tight_layout()
    save(fig, out_dir, "02_residual_kinetic_energy_log_six_seeds")


def plot_orientation(all_data: AllData, out_dir: Path) -> None:
    fig, axes = plt.subplots(3, 1, figsize=(8.0, 8.8), sharex=True)
    specs = [
        ("S_tensor", r"Tensor order parameter $S$", (0.0, 1.02), None),
        ("lambda_max", r"Largest eigenvalue $\lambda_{max}$", (1/3, 1.02), 1/3),
        ("S_z", r"Vertical alignment $S_z$", (-0.5, 1.02), 0.0),
    ]
    for i, (ax, (key, ylabel, ylim, ref)) in enumerate(zip(axes, specs)):
        plot_ph_lines(ax, all_data, key, ylabel=ylabel)
        ax.set_ylim(*ylim)
        if ref is not None:
            ax.axhline(ref, color="0.45", linestyle="--", linewidth=0.8)
        style_axes(ax, ylabel, xlabel="Simulation time [model units]" if i == len(specs) - 1 else "")
    axes[-1].set_xlim(0, 1.0)
    handles, labels = axes[0].get_legend_handles_labels()
    fig.legend(
        handles,
        labels,
        loc="lower center",
        ncol=2,
        frameon=False,
        bbox_to_anchor=(0.5, -0.045),
    )
    fig.suptitle("Orientational fabric metrics: six-seed comparison", y=1.005)
    fig.tight_layout()
    save(fig, out_dir, "03_orientation_metrics_six_seeds")


def read_vectors(block: str | None, source: Path) -> List[Tuple[float, float, float]]:
    """Convert an ASCII VTK data block into a list of three-component vectors."""
    nums = [float(x) for x in (block or "").split()]
    if len(nums) % 3:
        raise ValueError(f"Incomplete vector data in {source}")
    return [tuple(nums[i:i+3]) for i in range(0, len(nums), 3)]


def normal_vtp_files(path: Path) -> List[Path]:
    vtps = list(path.glob("*normals_*.vtp"))
    if not vtps:
        raise FileNotFoundError(f"No normals VTP files in {path}")
    return sorted(vtps, key=lambda p: int(p.stem.rsplit("_", 1)[-1]))


def parse_vtp(
    vtp: Path,
) -> Tuple[List[Tuple[float, float, float]], List[Tuple[float, float, float]]]:
    root = ET.parse(vtp).getroot()
    points_array = root.find(".//Points/DataArray")
    normals_array = root.find(".//PointData/DataArray[@Name='platelet_normal']")
    if points_array is None or normals_array is None:
        raise ValueError(f"Cannot parse {vtp}")
    points = read_vectors(points_array.text, vtp)
    normals = read_vectors(normals_array.text, vtp)
    if len(points) != len(normals):
        raise ValueError(f"{vtp}: {len(points)} points but {len(normals)} normals")
    return points, normals


def parse_final_vtp(
    path: Path,
) -> Tuple[List[Tuple[float, float, float]], List[Tuple[float, float, float]]]:
    return parse_vtp(normal_vtp_files(path)[-1])


def parse_initial_clump_state(
    path: Path,
) -> Tuple[List[Tuple[float, float, float]], List[Tuple[float, float, float]]]:
    files = list(path.glob("*Particle_0.vtu"))
    if len(files) != 1:
        raise FileNotFoundError(
            f"Expected one initial Particle_0.vtu in {path}, found {len(files)}"
        )
    vtu = files[0]
    root = ET.parse(vtu).getroot()
    points_array = root.find(".//Points/DataArray")
    charge_array = root.find(".//PointData/DataArray[@Name='Charge']")
    if points_array is None or charge_array is None:
        raise ValueError(f"Cannot parse initial clump state from {vtu}")
    points = read_vectors(points_array.text, vtu)
    charges = [float(x) for x in (charge_array.text or "").split()]
    if len(points) != len(charges):
        raise ValueError(f"{vtu}: {len(points)} points but {len(charges)} charge values")

    # The clump master has zero source; all pebbles carry a non-zero endpoint source.
    centers = [point for point, charge in zip(points, charges) if abs(charge) <= 1.0e-12]
    if len(centers) != 50:
        raise ValueError(f"{vtu}: expected 50 clump centers, found {len(centers)}")
    normals = [(0.0, 0.0, 1.0)] * len(centers)
    return centers, normals


def periodic_distance(a: Sequence[float], b: Sequence[float]) -> float:
    total = 0.0
    for x, y in zip(a, b):
        d = abs(x - y)
        d = min(d, 1.0 - d)
        total += d * d
    return math.sqrt(total)


def stack_components(
    points: Sequence[Sequence[float]],
    normals: Sequence[Sequence[float]],
    distance_cutoff: float,
    max_angle_deg: float,
) -> List[List[int]]:
    """Group platelets that pass both the distance and normal-angle tests."""
    parent = list(range(len(points)))
    min_abs_dot = math.cos(math.radians(max_angle_deg))

    def find(x: int) -> int:
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a: int, b: int) -> None:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    for i in range(len(points)):
        for j in range(i + 1, len(points)):
            norm_i = math.sqrt(sum(value * value for value in normals[i]))
            norm_j = math.sqrt(sum(value * value for value in normals[j]))
            if norm_i <= 1e-15 or norm_j <= 1e-15:
                continue
            dot = abs(sum(a * b for a, b in zip(normals[i], normals[j]))) / (norm_i * norm_j)
            if periodic_distance(points[i], points[j]) < distance_cutoff and dot >= min_abs_dot:
                union(i, j)

    groups: Dict[int, List[int]] = {}
    for i in range(len(points)):
        groups.setdefault(find(i), []).append(i)
    return list(groups.values())


def stack_order(normals: Sequence[Sequence[float]], component: Sequence[int]) -> float:
    if len(component) < 2:
        return 0.0
    axx = axy = axz = ayy = ayz = azz = 0.0
    valid_count = 0
    for idx in component:
        nx, ny, nz = normals[idx]
        norm = math.sqrt(nx * nx + ny * ny + nz * nz)
        if norm <= 1e-15:
            continue
        nx, ny, nz = nx / norm, ny / norm, nz / norm
        valid_count += 1
        axx += nx * nx
        axy += nx * ny
        axz += nx * nz
        ayy += ny * ny
        ayz += ny * nz
        azz += nz * nz
    if valid_count < 2:
        return 0.0
    inv = 1.0 / valid_count
    mat = [
        [axx * inv, axy * inv, axz * inv],
        [axy * inv, ayy * inv, ayz * inv],
        [axz * inv, ayz * inv, azz * inv],
    ]
    lam = largest_eigenvalue_3x3(mat)
    return 0.5 * (3.0 * lam - 1.0)


def largest_eigenvalue_3x3(m: Sequence[Sequence[float]]) -> float:
    vx = vy = vz = 1.0 / math.sqrt(3.0)
    for _ in range(80):
        wx = m[0][0] * vx + m[0][1] * vy + m[0][2] * vz
        wy = m[1][0] * vx + m[1][1] * vy + m[1][2] * vz
        wz = m[2][0] * vx + m[2][1] * vy + m[2][2] * vz
        norm = math.sqrt(wx * wx + wy * wy + wz * wz)
        if norm <= 1e-15:
            return 0.0
        vx, vy, vz = wx / norm, wy / norm, wz / norm
    return (
        vx * (m[0][0] * vx + m[0][1] * vy + m[0][2] * vz)
        + vy * (m[1][0] * vx + m[1][1] * vy + m[1][2] * vz)
        + vz * (m[2][0] * vx + m[2][1] * vy + m[2][2] * vz)
    )


def compute_stacking() -> List[Dict[str, float | int]]:
    rows: List[Dict[str, float | int]] = []
    for ph in PH_CASES:
        for seed in SEEDS:
            points, normals = parse_final_vtp(run_folder(ph, seed))
            components = [
                component
                for component in stack_components(
                    points,
                    normals,
                    STACK_CUTOFF,
                    STACK_MAX_ANGLE_DEG,
                )
                if len(component) >= 2
            ]
            orders = [stack_order(normals, component) for component in components]
            rows.append({
                "pH": ph,
                "seed": seed,
                "n_stacks": len(components),
                "platelets_in_stacks": sum(len(component) for component in components),
                "largest_stack": max([len(component) for component in components] or [0]),
                "mean_stack_order": mean(orders) if orders else 0.0,
            })
    return rows


def component_metrics(
    points: Sequence[Sequence[float]],
    normals: Sequence[Sequence[float]],
) -> Tuple[int, int, int]:
    components = [
        component
        for component in stack_components(points, normals, STACK_CUTOFF, STACK_MAX_ANGLE_DEG)
        if len(component) >= 2
    ]
    return (
        len(components),
        sum(len(component) for component in components),
        max([len(component) for component in components] or [0]),
    )


def compute_stacking_time_series(all_data: AllData) -> List[Dict[str, float | int]]:
    rows: List[Dict[str, float | int]] = []
    initial_centers: Dict[int, List[Tuple[float, float, float]]] = {}
    for ph in PH_CASES:
        for seed in SEEDS:
            path = run_folder(ph, seed)
            times = [0.0] + list(all_data[ph][seed]["time"])
            initial_state = parse_initial_clump_state(path)
            if seed in initial_centers:
                reference = initial_centers[seed]
                if any(
                    abs(a - b) > 1.0e-7
                    for point, other in zip(initial_state[0], reference)
                    for a, b in zip(point, other)
                ):
                    raise ValueError(
                        "Matched pH cases do not share the same t=0 centers "
                        f"for seed {seed}"
                    )
            else:
                initial_centers[seed] = initial_state[0]
            states = [initial_state]
            states.extend(parse_vtp(vtp) for vtp in normal_vtp_files(path))
            if len(states) != len(times):
                raise ValueError(
                    f"{path}: {len(states)} structural states but "
                    f"{len(times)} time values"
                )
            for time, (points, normals) in zip(times, states):
                n_stacks, platelets, largest = component_metrics(points, normals)
                rows.append({
                    "pH": ph,
                    "seed": seed,
                    "time": time,
                    "n_stacks": n_stacks,
                    "platelets_in_stacks": platelets,
                    "largest_stack": largest,
                })
    return rows


def compute_stacking_sensitivity() -> List[Dict[str, float | int | str]]:
    rows: List[Dict[str, float | int | str]] = []
    criteria = [
        ("distance", cutoff, STACK_MAX_ANGLE_DEG)
        for cutoff in DISTANCE_SENSITIVITY
    ] + [
        ("angle", STACK_CUTOFF, angle)
        for angle in ANGLE_SENSITIVITY_DEG
    ]
    for criterion, cutoff, angle in criteria:
        for ph in PH_CASES:
            for seed in SEEDS:
                points, normals = parse_final_vtp(run_folder(ph, seed))
                components = [
                    component
                    for component in stack_components(points, normals, cutoff, angle)
                    if len(component) >= 2
                ]
                rows.append({
                    "criterion": criterion,
                    "distance_cutoff": cutoff,
                    "max_angle_deg": angle,
                    "pH": ph,
                    "seed": seed,
                    "n_stacks": len(components),
                    "platelets_in_stacks": sum(len(component) for component in components),
                    "largest_stack": max([len(component) for component in components] or [0]),
                })
    return rows


def plot_stacking(stacking_rows: List[Dict[str, float | int]], out_dir: Path) -> None:
    fig, axes = plt.subplots(1, 3, figsize=(11.2, 3.8))
    metrics = [
        ("n_stacks", "Number of stack-like groups"),
        ("platelets_in_stacks", "Platelets in stack-like groups"),
        ("largest_stack", "Largest stack-like group"),
    ]
    jitter = {seed: (i - (len(SEEDS) - 1) / 2) * 0.035 for i, seed in enumerate(SEEDS)}
    for ax, (metric, ylabel) in zip(axes, metrics):
        for ph, spec in PH_CASES.items():
            rows = [r for r in stacking_rows if r["pH"] == ph]
            x0 = 0 if ph == 3 else 1
            vals = [float(r[metric]) for r in rows]
            ax.bar(x0, mean(vals), color=spec["color"], alpha=0.22, width=0.55)
            for row in rows:
                seed = int(row["seed"])
                ax.scatter(
                    x0 + jitter[seed],
                    float(row[metric]),
                    color=SEED_COLORS[seed],
                    s=45,
                    marker="o",
                    edgecolor="white",
                    linewidth=0.6,
                    zorder=3,
                    label=f"seed {seed}" if metric == "n_stacks" and ph == 3 else None,
                )
        ax.set_xticks([0, 1], ["pH 3", "pH 10"])
        ax.set_ylabel(ylabel)
        ax.grid(True, axis="y", alpha=0.22, linewidth=0.7)
    handles, labels = axes[0].get_legend_handles_labels()
    fig.legend(
        handles,
        labels,
        loc="lower center",
        ncol=3,
        frameon=False,
        bbox_to_anchor=(0.5, 0.01),
    )
    fig.suptitle("Final neighbor-based stack-like metrics", y=1.03)
    fig.tight_layout(rect=[0, 0.16, 1, 0.96])
    save(fig, out_dir, "04_stacking_summary_six_seeds")


def plot_stacking_evolution(
    stacking_time_rows: List[Dict[str, float | int]],
    out_dir: Path,
) -> None:
    fig, ax = plt.subplots(figsize=(8.4, 4.3))
    for ph, spec in PH_CASES.items():
        series = []
        for seed in SEEDS:
            rows = [r for r in stacking_time_rows if r["pH"] == ph and r["seed"] == seed]
            times = [float(r["time"]) for r in rows]
            values = [float(r["platelets_in_stacks"]) for r in rows]
            series.append(values)
            ax.plot(times, values, color=spec["color"], alpha=0.20, linewidth=0.9)
        avg = vector_mean(series)
        ax.plot(times, avg, color=spec["color"], linewidth=2.7, label=f"{spec['label']} mean")

    ax.set_xlim(0.0, 1.0)
    ax.set_ylim(0.0, 50.0)
    ax.set_yticks(range(0, 51, 10))
    ax.set_xlabel("Simulation time [model units]")
    ax.set_ylabel("Platelets in stack-like groups")
    ax.set_title("Platelets in stack-like groups over time")
    ax.grid(True, alpha=0.22, linewidth=0.7)
    ax.legend(frameon=False)
    fig.tight_layout()
    save(fig, out_dir, "04_stacking_evolution_six_seeds")


def plot_final_scatter(
    all_data: AllData,
    stacking_rows: List[Dict[str, float | int]],
    out_dir: Path,
) -> None:
    fig, axes = plt.subplots(1, 4, figsize=(13.0, 3.7))
    metrics = [
        ("lr_delta", r"Final $\Delta U_{lr}$"),
        ("S_tensor", r"Final $S$"),
        ("S_z", r"Final $S_z$"),
        ("platelets_in_stacks", "Platelets in stack-like groups"),
    ]
    jitter = {seed: (i - (len(SEEDS) - 1) / 2) * 0.035 for i, seed in enumerate(SEEDS)}
    stack_lookup = {(int(r["pH"]), int(r["seed"])): r for r in stacking_rows}
    for ax, (metric, ylabel) in zip(axes, metrics):
        for ph, spec in PH_CASES.items():
            x0 = 0 if ph == 3 else 1
            vals = []
            for seed in SEEDS:
                if metric == "platelets_in_stacks":
                    val = float(stack_lookup[(ph, seed)][metric])
                else:
                    val = all_data[ph][seed][metric][-1]
                vals.append(val)
                ax.scatter(
                    x0 + jitter[seed],
                    val,
                    color=SEED_COLORS[seed],
                    s=45,
                    marker="o",
                    edgecolor="white",
                    linewidth=0.6,
                    zorder=3,
                    label=f"seed {seed}" if metric == "lr_delta" and ph == 3 else None,
                )
            ax.hlines(mean(vals), x0 - 0.22, x0 + 0.22, color=spec["color"], linewidth=3.0)
        ax.set_xticks([0, 1], ["pH 3", "pH 10"])
        ax.set_ylabel(ylabel)
        ax.grid(True, axis="y", alpha=0.22, linewidth=0.7)
    handles, labels = axes[0].get_legend_handles_labels()
    fig.legend(
        handles,
        labels,
        loc="lower center",
        ncol=3,
        frameon=False,
        bbox_to_anchor=(0.5, 0.01),
    )
    fig.suptitle("Final values: individual seeds and six-seed mean", y=1.04)
    fig.tight_layout(rect=[0, 0.14, 1, 0.96])
    save(fig, out_dir, "05_final_values_scatter_six_seeds")


def write_csvs(
    all_data: AllData,
    stacking_rows: List[Dict[str, float | int]],
    stacking_time_rows: List[Dict[str, float | int]],
    sensitivity_rows: List[Dict[str, float | int | str]],
    out_dir: Path,
) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    fields = [
        "pH", "seed", "time", "kinetic_time", "U_lr_total", "delta_U_lr",
        "kinetic", "S", "S_z", "lambda_max",
    ]
    with (out_dir / "final_values_six_seed_summary.csv").open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for ph in sorted(all_data):
            for seed in SEEDS:
                run = all_data[ph][seed]
                writer.writerow({
                    "pH": ph,
                    "seed": seed,
                    "time": run["time"][-1],
                    "kinetic_time": run["kinetic_time"][-1],
                    "U_lr_total": run["lr_total"][-1],
                    "delta_U_lr": run["lr_delta"][-1],
                    "kinetic": run["kinetic"][-1],
                    "S": run["S_tensor"][-1],
                    "S_z": run["S_z"][-1],
                    "lambda_max": run["lambda_max"][-1],
                })
    with (out_dir / "stacking_six_seed_summary.csv").open("w", newline="") as f:
        fieldnames = [
            "pH",
            "seed",
            "n_stacks",
            "platelets_in_stacks",
            "largest_stack",
            "mean_stack_order",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(stacking_rows)
    with (out_dir / "stacking_time_series_six_seed.csv").open("w", newline="") as f:
        fieldnames = ["pH", "seed", "time", "n_stacks", "platelets_in_stacks", "largest_stack"]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(stacking_time_rows)
    with (out_dir / "stacking_sensitivity_six_seed_summary.csv").open("w", newline="") as f:
        fieldnames = [
            "criterion",
            "distance_cutoff",
            "max_angle_deg",
            "pH",
            "seed",
            "n_stacks",
            "platelets_in_stacks",
            "largest_stack",
        ]
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(sensitivity_rows)

    # This short report is useful for checking the numbers without opening a CSV.
    with (out_dir / "six_seed_means.txt").open("w") as f:
        for ph in sorted(all_data):
            f.write(f"pH {ph}\n")
            for key in ["lr_total", "lr_delta", "kinetic", "S_tensor", "S_z", "lambda_max"]:
                vals = [all_data[ph][seed][key][-1] for seed in SEEDS]
                f.write(f"  {key}: mean={mean(vals):.6g}, sd={stdev(vals):.6g}\n")
            rows = [r for r in stacking_rows if r["pH"] == ph]
            for key in ["n_stacks", "platelets_in_stacks", "largest_stack"]:
                vals = [float(r[key]) for r in rows]
                f.write(f"  {key}: mean={mean(vals):.6g}, sd={stdev(vals):.6g}\n")
            f.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "figures" / "six_seed_clean",
    )
    args = parser.parse_args()
    setup_style()
    all_data = load_all()
    stacking_rows = compute_stacking()
    stacking_time_rows = compute_stacking_time_series(all_data)
    sensitivity_rows = compute_stacking_sensitivity()
    plot_lr_full_zoom(all_data, args.out_dir)
    plot_kinetic(all_data, args.out_dir)
    plot_orientation(all_data, args.out_dir)
    plot_stacking_evolution(stacking_time_rows, args.out_dir)
    plot_stacking(stacking_rows, args.out_dir)
    plot_final_scatter(all_data, stacking_rows, args.out_dir)
    write_csvs(
        all_data,
        stacking_rows,
        stacking_time_rows,
        sensitivity_rows,
        args.out_dir,
    )
    print(f"Wrote clean six-seed figures and CSV summaries to {args.out_dir}")


if __name__ == "__main__":
    main()
