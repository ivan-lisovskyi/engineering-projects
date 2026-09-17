"""September 2026 reproducibility wrapper; original solver is unchanged."""

import argparse
from contextlib import chdir
import json
from pathlib import Path

import numpy as np

from PIE2023 import ParticleSimulation, SimulationConfig


def make_simulation(config_path):
    """Load a small explicit fixture and validate runner-specific requirements."""
    with Path(config_path).open(encoding="utf-8") as stream:
        fixture = json.load(stream)
    config = SimulationConfig(**fixture["parameters"])
    if config.timesteps < 5:
        raise ValueError("The original snapshot loop requires at least 5 steps.")
    values = [config.k, config.m, config.radius, config.boundary_min,
              config.boundary_max, config.dt]
    if not np.isfinite(values).all():
        raise ValueError("Demo parameters must be finite.")
    if config.boundary_max - config.boundary_min <= 2 * config.radius:
        raise ValueError("The demo box must be wider than one particle diameter.")
    positions = np.asarray(fixture["initial_positions"], dtype=float)
    if positions.shape != (config.num_particles, 2) or not np.isfinite(positions).all():
        raise ValueError("Provide one finite [x, y] position per particle.")
    if ((positions < config.boundary_min + config.radius).any()
            or (positions > config.boundary_max - config.radius).any()):
        raise ValueError("Initial particle centres must be at least a radius from walls.")
    np.random.seed(fixture["random_seed"])
    simulation = ParticleSimulation(config)
    simulation.positions = positions.copy()
    simulation.computeForces()
    return simulation


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path,
                        default=Path(__file__).with_name("demo.json"))
    parser.add_argument("--output", type=Path, default=Path("outputs/demo"))
    args = parser.parse_args()
    simulation = make_simulation(args.config)
    output_dir = args.output.resolve()
    output_file = output_dir / "simulation_results.txt"
    if output_file.exists():
        parser.error(f"Output already exists; choose another --output directory: {output_file}")
    output_dir.mkdir(parents=True, exist_ok=True)
    with chdir(output_dir):
        simulation.start_simulation()
    if not output_file.is_file() or output_file.stat().st_size == 0:
        raise RuntimeError("The original solver did not produce a non-empty output file.")
    if not (np.isfinite(simulation.positions).all()
            and np.isfinite(simulation.velocities).all()):
        raise RuntimeError("The demo produced non-finite values.")
    print(f"Demo output: {output_file}")


if __name__ == "__main__":
    main()
