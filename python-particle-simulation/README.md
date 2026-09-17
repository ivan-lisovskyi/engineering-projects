# 2D particle simulation in Python

I wrote this small particle simulation for Programming in Engineering at the University of Twente. It follows particles moving in a 2D box under gravity and calculates a repulsive force when they overlap. The report was submitted in July 2024; [PIE2023.py](PIE2023.py) still has its original course filename.

The code puts a few mechanics ideas into practice: forces between particles, numerical time steps and boundary checks. NumPy holds the particle data, and a velocity-Verlet loop updates the positions and velocities. This is a learning project, separate from my later MercuryDPM thesis, rather than a validated DEM solver.

## What I implemented

- A `SimulationConfig` class to store and check the main settings.
- Contact checks between particle pairs, with equal-and-opposite forces.
- A time-stepping loop and simple reflecting boundaries.
- Text output with particle positions and velocities at regular intervals.

## Quick start

Run these commands from this project folder. The demo was checked with Python 3.12 and NumPy 2.3.5. The original submission did not specify dependency versions.

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
python demo.py
python -m unittest discover -s tests -v
```

On Windows, activate with `.venv\Scripts\activate` instead of `source ...`.

The demo runs eight particles for 100 steps and saves `outputs/demo/simulation_results.txt`. It prints progress as it runs. The runner will not overwrite an existing result, so choose another folder for a second run:

```bash
python demo.py --output outputs/second-run
```

The demo runner, its settings and the tests were added in September 2026 to make the saved project easier to try. They are not part of the original submission. The solver itself is unchanged. The demo starts with small, separated particles inside the box instead of the original random positions.

## Model and implementation

Every particle has the same mass `m` and radius. When two centres are closer than `2 * radius`, their repulsive force has magnitude `k * (2 * radius - d)`, where `d` is the distance between them. The force acts in opposite directions on the two particles. Gravity adds `m * [0, -9.81]` to each particle's force.

Each step updates velocity by half a time step, moves the particles, handles the boundaries, recalculates forces and finishes the velocity update. Every pair is checked for contact, so the work grows as O(N²). The model does not include rotation, friction, damping or a faster neighbour search.

The original timestep rule is:

```text
dt = 0.01 * sqrt(m / k)
```

Use consistent units: the program does not check them. This time-step rule has not been shown to give stable results for every set of settings.

## Configuration and output

Edit a copy of `demo.json`, then run `python demo.py --config path/to/config.json --output outputs/custom-run`.

| Setting | Meaning |
| --- | --- |
| `num_particles` | Number of particles; must match the supplied positions |
| `k`, `m`, `radius` | Contact stiffness, common mass, common contact radius |
| `timesteps` | Number of integration steps; demo wrapper requires at least 5 |
| `boundary_min`, `boundary_max` | Shared lower/upper coordinate limits for both axes |
| `initial_positions` | Explicit `[x, y]` positions for the reproducible demo |
| `random_seed` | Fixes the original constructor's random initialisation before positions are replaced |

The result file has blocks headed `Timestep`, followed by positions and velocities. There is a small but important detail in the original output: a label `s` is written after that step's update, so its physical time is `(s + 1) * dt`. The 100-step demo writes labels 0, 20, 40, 60 and 80. It does not save the final state.

The original entry point is also available:

```bash
python PIE2023.py
```

This uses random positions, 50 particles, 1,000 steps and the original large radius. It overwrites `simulation_results.txt` in the current folder. Use the demo for a first run: the original defaults cause the overlaps described below and are not a useful physical benchmark.

## Known limitations

- The original position setup does not scale correctly to other box bounds. With the default box `[0, 1]`, some particles start above the box: their y coordinates range from 0.5 to just below 1.5. A default radius of 1 also produces large overlaps.
- Walls clip particle centres to the boundary and reverse velocity components. They do not account for radius or the exact collision time.
- Particle pairs closer than `1e-6` are skipped to avoid division by zero. Two particles at exactly the same position therefore receive no separating contact force.
- The original output loop fails with fewer than five steps, misses the final state and uses the step labels described above.
- Input checks are incomplete. There has been no convergence study, energy-conservation study, experimental calibration or performance benchmark.

The added tests check invalid settings, equal-and-opposite contact forces, a free-fall step, finite demo results and output labels. They help catch code changes that break these behaviours; they do not establish the physical accuracy of the model.

## Files

- [PIE2023.py](PIE2023.py) — original simulation code, unchanged.
- [demo.py](demo.py) and [demo.json](demo.json) — added runner and starting conditions for the small demo.
- [tests/test_simulation.py](tests/test_simulation.py) — added tests, including the short-run limitation.
- [requirements.txt](requirements.txt) — NumPy version used for the checks.

The original report and university teaching materials are not included. This folder does not grant an open-source licence; publication and reuse permissions need to be checked separately.

## What could be improved

The first changes should be better starting positions, correct output timestamps and wall handling that accounts for particle radius. After that, convergence and energy tests would help assess the numerical results. Keep revisions separate from the original submission, include a small example for each fix and run the tests above.
