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

The first code cell contains `ALPHA_INIT`, loss weights, Fourier-feature settings, batch sizes, `SEED`, and `STAGE1` / `STAGE2`. The known reference `ALPHA_TRUE = 1e-5 m²/s` is used to report errors and make plots, not as a target in the training loss.

The default schedule is 2,000 fixed-diffusivity epochs followed by 6,000 joint-training epochs. Hardware, dependency versions and random samples can affect both runtime and results. Dependencies are listed but not version-locked. No network connection is needed once they are installed.

## Files and outputs

| Path | Contents |
|---|---|
| [`final_notebook.ipynb`](../final_notebook.ipynb) | Model, training code, explanations and academic references |
| [`sensors_T.csv.xz`](../sensors_T.csv.xz) | Complete compressed dataset |
| [`prepare_data.py`](../prepare_data.py) | Dataset checks and unpacking |
| [`requirements.txt`](../requirements.txt) | Notebook dependencies |
| [`results/selected-run/`](../results/selected-run/) | Saved metrics, history and plots |
| [`outputs_alpha_sensitivity/alpha_sensitivity_summary.csv`](../outputs_alpha_sensitivity/alpha_sensitivity_summary.csv) | Six saved starting-value experiments |
| [`outputs_comparison/comparison_table.csv`](../outputs_comparison/comparison_table.csv) | Twelve saved architecture-comparison runs |

A new run writes history, metrics, a PyTorch checkpoint and plots to `outputs_submission/`, which is ignored by Git. It does not overwrite `results/selected-run/`.

Section 8 reads and plots the existing sensitivity and architecture CSVs. Running that section does not repeat those experiments. The notebook's contour plots clip temperature rise to `0–30 K`; inspect the numerical output as well as the pictures.
