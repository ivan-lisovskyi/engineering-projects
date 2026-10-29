# Model, data and run settings

[Back to the project](../README.md)

## Dataset

The plate is `0.10 × 0.05 m`, simulated over `0–300 s`. The CSV contains 6,001 time rows and 140 virtual-sensor locations. Four COMSOL metadata rows precede the header. Sensor coordinates are parsed from the temperature-column names; temperatures are in kelvin, coordinates in metres and time in seconds.

`sensors_T.csv.xz` is a lossless compressed copy of the complete source CSV. From the project folder:

```bash
python3 prepare_data.py --check-only
python3 prepare_data.py
```

The first command checks the checksum and table dimensions without writing a file. The second unpacks the CSV, checking it against the original checksum. Neither needs third-party packages.

## Model and validation

The network predicts normalised temperature from normalised `(x, y, t)`. Its physical residual is based on:

```text
dT/dt = alpha × (d²T/dx² + d²T/dy²)
```

The left boundary has prescribed ramped Gaussian heating. The other three sides use convection and radiation losses. The initial plate temperature is 293.15 K.

Sensor IDs are shuffled with seed 0: 112 sensors are used for training and 28 are held out. Validation RMSE is estimated from randomly sampled time/sensor pairs at those held-out locations. All locations belong to the same COMSOL simulation.

## Configuration

The first code cell of the original notebook contains `ALPHA_INIT`, loss weights, Fourier-feature settings, batch sizes, `SEED`, and `STAGE1` / `STAGE2`. The package keeps the same values as the fields of `pinn.config.Config` (`alpha_init`, `w_bc`, `n_ff`, `data_batch`, `seed`, `stage1`, `stage2`, ...). The known reference `ALPHA_TRUE = 1e-5 m²/s` (`Config.alpha_true`) is used to report errors and make plots, not as a target in the training loss.

The default schedule is 2,000 fixed-diffusivity epochs followed by 6,000 joint-training epochs. `python -m pinn train --epochs N` runs `N` epochs in total, split in the same 1:3 ratio. Hardware, dependency versions and random samples can affect both runtime and results. `requirements.txt` pins the versions used for the tests and the 50-epoch smoke run; the saved results were produced before the versions were recorded. No network connection is needed once the dependencies are installed.

## Files and outputs

| Path | Contents |
|---|---|
| [`notebooks/final_notebook.ipynb`](../notebooks/final_notebook.ipynb) | Original notebook, unchanged: model, training code, explanations and academic references |
| [`notebooks/pinn_walkthrough.ipynb`](../notebooks/pinn_walkthrough.ipynb) | Thin notebook that calls the package |
| [`src/pinn/`](../src/pinn/) | Package extracted from the notebook: model, physics, training, plots, results tables, command line |
| [`tests/`](../tests/) | Tests on exact fields and a tiny synthetic case, plus the package-versus-notebook equivalence check |
| [`sensors_T.csv.xz`](../sensors_T.csv.xz) | Complete compressed dataset |
| [`prepare_data.py`](../prepare_data.py) | Dataset checks and unpacking |
| [`requirements.txt`](../requirements.txt), [`requirements-dev.txt`](../requirements-dev.txt) | Pinned dependencies; the second adds pytest |
| [`results/selected-run/`](../results/selected-run/) | Saved metrics, history and plots |
| [`outputs_alpha_sensitivity/alpha_sensitivity_summary.csv`](../outputs_alpha_sensitivity/alpha_sensitivity_summary.csv) | Six saved starting-value experiments |
| [`outputs_comparison/comparison_table.csv`](../outputs_comparison/comparison_table.csv) | Twelve saved architecture-comparison runs |

A new notebook run writes history, metrics, a PyTorch checkpoint and plots to `outputs_submission/`; `python -m pinn train` writes the same kinds of files to `outputs_cli/` (its `metrics.txt` adds the epochs, seed and device). Both folders are ignored by Git, and the command line refuses to write into `results/`. Neither overwrites `results/selected-run/`.

Section 8 of the original notebook reads and plots the existing sensitivity and architecture CSVs, and `python -m pinn results` prints the same tables. Neither repeats those experiments. The notebook's contour plots clip temperature rise to `0–30 K`; inspect the numerical output as well as the pictures.
