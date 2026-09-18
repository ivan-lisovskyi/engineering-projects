# Building the driver and running the analysis

[Back to the project](../README.md)

## C++ dependencies and build

[`src/clay_anisotropy_updated.cpp`](../src/clay_anisotropy_updated.cpp) is not a standalone program. It needs a compatible MercuryDPM source checkout with the Clump headers, PVFMM integration, MPI and an OpenMP-capable compiler. Those dependencies are not included here.

Copy the driver into `Drivers/LongRange/Clay/` in that checkout. Preserve any existing file with the same name first. The inspected upstream CMake setup creates a target for each `.cpp` filename in that directory, links `Chute` and `pvfmmStatic`, and adds the PVFMM include paths. The expected target is `clay_anisotropy_updated`; reconfigure after adding the driver.

From the upstream source root, the configuration outline is:

```bash
cmake -S . -B build \
  -DMercuryDPM_PVFMM=ON \
  -DMercuryDPM_USE_MPI=ON \
  -DMercuryDPM_USE_OpenMP=ON
cmake --build build --target clay_anisotropy_updated
```

Compiler selection and dependency paths depend on the machine. The original macOS setup used Homebrew GCC 15, not Apple Clang. The driver assumes periodic PVFMM boundaries and is incompatible with `PVFMM_EXTENDED_BC`. Exact dependency revisions were not recorded, so this is a build outline, not a tested clean-install recipe.

## Simulation arguments and output

The executable accepts positional arguments:

```text
clay_anisotropy_updated <pH> <insertion-attempt count> <simulation time> <seed>
```

Defaults are pH 8, 50 insertion attempts, simulation time 0.2 and seed 1. The saved study used pH 3/10 and duration 1. Check the output for the number of clumps actually inserted. The `1e-6` timestep, long-range scaling and output cadence are set in the driver.

**Use a new, empty output directory for every simulation.** The driver calls `removeOldFiles()` before solving and can remove or replace previous files. The original 50-clump runs produced hundreds of VTU/VTP files and several gigabytes per run.

The six matched seeds were `73019`, `11234`, `101098`, `333`, `112233` and `1337212`, giving 12 runs across the two templates.

## Raw data layout

Three of the four Python scripts read raw runs under `Path.home() / "MercuryRuns"`:

```text
MercuryRuns/
  ph3_anisotropy_updated_clean_N50_1s_seed73019/
  ph10_anisotropy_updated_clean_N50_1s_seed73019/
  ...same two cases for the other five seeds...
```

Inputs include `LRPotential.txt`, `AnisotropyLog.csv`, `clay_anisotropy_updated.ene`, `clay_anisotropy_updated_normals_*.vtp` and particle `.vtu` files. These large raw files are not included; the summary CSVs cannot be used in their place.

For another data location, edit `run_folder()` or `RUNS_BASE` / `RUN_BASE` in a working copy of the relevant script. There is no raw-data-path command-line option.

## Analysis scripts

Install the packages listed in [`scripts/requirements.txt`](../scripts/requirements.txt). Run commands from the project folder.

| Script | Inputs and output |
|---|---|
| [`plot_charge_templates.py`](../scripts/plot_charge_templates.py) | No raw runs needed; use `--out-dir generated` |
| [`plot_ph3_ph10_six_seed_clean.py`](../scripts/plot_ph3_ph10_six_seed_clean.py) | Reads all 12 runs; use `--out-dir generated/six_seed` for new figures and tables |
| [`stacking_analysis.py`](../scripts/stacking_analysis.py) | Examines final geometric groups and writes `figures/stacking_summary.csv` |
| [`initial_overlap_check.py`](../scripts/initial_overlap_check.py) | Checks six initial particle files and writes `figures/six_seed_clean/initial_overlap_summary.csv` |

The overlap script's default output **overwrites the summary included here**. Change `OUT_CSV` before running it, or use a disposable copy. Keep new analysis output separate from the saved thesis results.
