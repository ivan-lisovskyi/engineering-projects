# Python scripts: inputs, outputs, how to run

[Back to the project](../README.md) · [Build and data layout](RUNNING.md) · [Metric formulas](METRICS.md)

All four scripts live in [`scripts/`](../scripts/) and are unchanged from the thesis work. Install the pinned packages first (`python -m pip install -r scripts/requirements.txt`; Matplotlib 3.10.9 and NumPy 2.2.6 are the versions the checks were run with) and run from the project folder.

| Script | Needs raw runs? | Command-line option | Default output location |
|---|---|---|---|
| `plot_charge_templates.py` | No | `--out-dir` | `figures/` |
| `plot_ph3_ph10_six_seed_clean.py` | Yes, all 12 runs | `--out-dir` | `figures/six_seed_clean/` (**overwrites the saved results**) |
| `stacking_analysis.py` | Yes, all 12 runs | none | `figures/stacking_summary.csv` |
| `initial_overlap_check.py` | Yes, the six pH 3 runs | none | `figures/six_seed_clean/initial_overlap_summary.csv` (**overwrites the saved file**) |

"Raw runs" means the MercuryDPM output folders described in [RUNNING.md](RUNNING.md#raw-data-layout), expected under `~/MercuryRuns/ph{3,10}_anisotropy_updated_clean_N50_1s_seed{seed}`. They are not in this repository, so only the first script can be run from a fresh clone. The CI workflow runs it and byte-compiles the other three.

## `plot_charge_templates.py`

- **Purpose:** plot the prescribed source value of the silica-like face, alumina-like face and edge against the pH input, raw (left) and after subtracting the weighted mean (right), plus the raw weighted mean. Formulas: [METRICS.md, section 1](METRICS.md#1-source-template-the-prescribed-values-per-surface-site).
- **Input:** none (constants in the script: 469 / 469 / 156 sites, pH 3 to 10 in 281 steps).
- **Output:** `charge_template_raw_and_corrected_vs_ph.png`, `.pdf` and `.svg` in the `--out-dir` folder (created if missing).
- **Run:** `python scripts/plot_charge_templates.py --out-dir generated`
- **Needs:** Matplotlib only.

## `plot_ph3_ph10_six_seed_clean.py`

- **Purpose:** rebuild the six-seed pH 3 / pH 10 figures and the tables behind them, including the stack-like check at every saved frame and the threshold sensitivity.
- **Input, per run folder:** `AnisotropyLog.csv` (every column is read as a float; needs `time`, `S_tensor`, `S_z`, `lambda_max`), `LRPotential.txt` (one value per line, same count as the log rows), `clay_anisotropy_updated.ene` (header with `time`, `traKineticEnergy`, `rotKineticEnergy`), `*normals_*.vtp` (platelet centres and `platelet_normal`) and the single `*Particle_0.vtu` (initial clump centres, `Charge` array).
- **Output in `--out-dir`** (default `figures/six_seed_clean/`):
  - Figures, each as `.png`, `.pdf` and `.svg`: `01_energy_change_six_seeds`, `02_residual_kinetic_energy_log_six_seeds`, `03_orientation_metrics_six_seeds`, `04_stacking_evolution_six_seeds`, `04_stacking_summary_six_seeds`, `05_final_values_scatter_six_seeds`.
  - Tables: `final_values_six_seed_summary.csv`, `stacking_six_seed_summary.csv`, `stacking_time_series_six_seed.csv`, `stacking_sensitivity_six_seed_summary.csv`, and `six_seed_means.txt` (mean and standard deviation over seeds).
  - Only three of the PNGs and the CSV/TXT tables are committed; see the [gallery](GALLERY.md).
- **Run:** `python scripts/plot_ph3_ph10_six_seed_clean.py --out-dir generated/six_seed`
- **Notes:** the run folder location is fixed in `run_folder()` (`Path.home() / "MercuryRuns"`); there is no option to change it. The script stops with an error if a run folder is missing, if the energy and log lengths differ, or if the matched pH 3 and pH 10 runs do not share the same initial centres.

## `stacking_analysis.py`

- **Purpose:** count stack-like groups in the final saved frame of each of the 12 runs and give each group's internal order parameter. Rule and formulas: [METRICS.md, section 4](METRICS.md#4-stack-like-groups).
- **Input:** the `clay_anisotropy_updated_normals_*.vtp` file with the highest frame number in each run folder.
- **Output:** `figures/stacking_summary.csv` (relative to the project folder, not the working directory) with columns `pH, seed, row_type, stacks, platelets_in_stacks, largest_stack, stack_S_internal`: one `summary` row per run followed by one `stack_<k>` row per group. One progress line per run is printed. This file is not part of the saved results; the committed table is `figures/six_seed_clean/stacking_six_seed_summary.csv`, written by the plotting script.
- **Run:** `python scripts/stacking_analysis.py`

## `initial_overlap_check.py`

- **Purpose:** measure how much the 50 clumps overlap at t = 0 (the driver allowed initial overlaps). Method: [METRICS.md, section 5](METRICS.md#5-initial-overlaps).
- **Input:** `*Particle_0.vtu` of the pH 3 run for each of the six seeds (pH 10 starts from the same positions).
- **Output:** `figures/six_seed_clean/initial_overlap_summary.csv` with `seed, overlapping_clump_pairs, max_pebble_penetration, penetration_pct_of_diameter`, plus a printed per-seed line and a range line.
- **Run:** change `OUT_CSV` in a working copy first, otherwise the committed summary is overwritten: `python scripts/initial_overlap_check.py`
- **Notes:** the layout is hard-coded: 50 clumps, 1094 pebbles each, pebble radius 0.008. The script raises an error if the file does not match.
