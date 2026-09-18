# Clay platelet simulation and structural analysis

Ivan Lisovskyi · BSc thesis project · C++, MercuryDPM, PVFMM, Python

For my bachelor's thesis, I studied how small clay-like platelets arrange themselves in a simulation. I extended an existing MercuryDPM driver and compared two prescribed interaction patterns labelled pH 3 and pH 10. I then used Python to look at energy changes, platelet orientation and local groups of platelets across repeated runs.

The pH labels describe simplified model inputs, not a calibrated model of clay chemistry in water.

![Saved comparison of local stack-like groups across six seeds per template](figures/six_seed_clean/04_stacking_summary_six_seeds.png)

## My work

I started from the upstream `Clay.cpp` driver. MercuryDPM, its Clump extension and PVFMM provide the simulation engine, contact handling, time integration, rigid clumps and long-range solver. My work was to extend that setup and analyse its output:

- Added source values for the silica-like face, alumina-like face and platelet edge, with pH 3 and pH 10 endpoints and interpolation between them.
- Subtracted the mean source value for the periodic calculation and used the corrected values in both the solver and visualisation output.
- Corrected the potential-energy calculation to use the same periodic or free-space boundary choice as the force calculation.
- Added platelet normals and orientation measures: the orientation tensor, its largest eigenvalue and a scalar order parameter.
- Compared matched random seeds, checked grouping thresholds and measured initial particle overlaps using Python.
- Worked with MPI and OpenMP in a macOS build and inspected the 3-D output in ParaView.

## What the runs showed

The study compared 50-clump simulations using six matched seeds for each template: 12 runs in total. The tables and figures here are saved thesis results.

| Measure, averaged across six seeds | pH 3 template | pH 10 template |
|---|---:|---:|
| Global orientation order, `S_tensor` | 0.694 | 0.678 |
| Platelets in stack-like groups | 24.5 | 1.33 |

Overall orientation varied substantially between seeds and did not show a clear, consistent separation. Local grouping showed a larger difference. Looking at both measures helped distinguish overall alignment from local structure.

The grouping rule connects platelets when their minimum-image centre distance is `< 0.08` and the angle between their unoriented normals is `≤ 20°`. Connected platelets form a group. This counts nearby, similarly oriented platelets, **not confirmed face-to-face contacts**. The [threshold-sensitivity table](figures/six_seed_clean/stacking_sensitivity_six_seed_summary.csv) shows how changing the rule affects the count.

See the [orientation plot](figures/six_seed_clean/03_orientation_metrics_six_seeds.png), [energy-change plot](figures/six_seed_clean/01_energy_change_six_seeds.png), [per-run values](figures/six_seed_clean/final_values_six_seed_summary.csv) and [means](figures/six_seed_clean/six_seed_means.txt).

### Model limits

Contact response is frictionless and normal-only. Initial overlaps were allowed and checked in the [overlap summary](figures/six_seed_clean/initial_overlap_summary.csv).

Long-range energy includes a within-clump baseline that differs between templates. I compared changes from each run's first saved state; absolute totals should not be read as thermodynamic free energies. These results describe the simulated cases, not a general finding about real clay.

## Try the source-template plot

This example needs Python 3.10 or later, but no simulation build or large raw data files. From this folder:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r scripts/requirements.txt
python scripts/plot_charge_templates.py --out-dir generated
```

On Windows, activate the environment with `.venv\Scripts\activate` instead.

The command creates PNG, PDF and SVG files named `charge_template_raw_and_corrected_vs_ph` in `generated/`. They show the prescribed source values before and after subtracting their mean for the periodic solver.

The plot command was checked with Python 3.12 and Matplotlib 3.10.9. The full C++ build and simulations were not rerun when preparing this repository.

## Build and full analysis

The C++ driver needs a compatible MercuryDPM/Clump/PVFMM checkout, MPI and OpenMP. The full Python analysis needs the original raw runs, which are not included here. See the [build and analysis guide](docs/RUNNING.md) for dependencies, arguments and data paths.

Run simulations only in a new, empty output directory: the driver can remove earlier output files. The overlap-analysis script can also overwrite an included summary; the guide explains where to change its output path.

Further work would include recording a complete build environment, testing the geometry calculations, adding seeds and comparing against physical data.

## Credits

The driver's MercuryDPM copyright notice and [upstream licence](LICENSE-MercuryDPM.txt) are preserved. See the [project notes](../docs/PROJECT_NOTES.md) for publication permissions and the scope of the included material.

The thesis declared help from ChatGPT, Claude, Grammarly and DeepL with setup, explanations, literature searches, code and report review, data-processing scripts and language. I reviewed and edited the final work and am responsible for its interpretation.
