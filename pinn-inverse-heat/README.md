# Inverse heat transfer with a physics-informed neural network

Ivan Lisovskyi · Individual Application of Advanced Technology project · Python, PyTorch

In this project, I used temperature data to estimate how quickly heat spreads through a plate. The unknown quantity is thermal diffusivity, `alpha`. I trained a physics-informed neural network (PINN) to fit the temperatures while also following the heat equation and the plate's boundary conditions.

The most useful result was a warning about how to read the model's output: it could fit temperatures well and still give a poor estimate of diffusivity. I therefore compared different starting values of `alpha`, rather than judging the model only by its temperature error.

![Saved centreline comparison of PINN predictions and COMSOL temperatures](results/selected-run/centerline_profile.png)

## What I worked on

My work covered the PINN notebook, its training setup, validation and interpretation of the results. The model uses PyTorch, Fourier input features and a trainable `log_alpha`, with `alpha = exp(log_alpha)` keeping diffusivity positive. Automatic differentiation supplies the derivatives needed for the heat equation and boundary losses.

Training has two stages. First, the network fits the temperature field with diffusivity fixed. Then both the network and diffusivity are trained together. I also used held-out sensors and saved comparison runs to check the fit and its sensitivity to the starting value.

The input temperatures come from virtual sensors in a COMSOL simulation. They are not laboratory measurements that I collected. The original COMSOL `.mph` model is not included, and this repository does not claim authorship of that solver or model.

## Take a quick look

The saved [sensor fit](results/selected-run/sensor_fit.png), [centreline comparison](results/selected-run/centerline_profile.png), [temperature contour](results/selected-run/contour_t300.png) and [starting-value comparison](results/selected-run/notebook_alpha_sensitivity_summary.png) can be viewed without installing anything. The [notebook](final_notebook.ipynb) contains the model and training code.

To check and unpack the complete dataset, run these commands from this folder with Python 3.10 or later:

```bash
python3 prepare_data.py --check-only
python3 prepare_data.py
```

The first command checks the checksum and table dimensions without writing a file. The second unpacks `sensors_T.csv` (about 15.5 MB). Both use only the Python standard library. The helper will not overwrite a different CSV with the same name.

## Run the notebook

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
python prepare_data.py
jupyter lab final_notebook.ipynb
```

On Windows, activate with `.venv\Scripts\activate` instead of `source .venv/bin/activate`.

In Jupyter, select this environment's Python kernel and run the notebook from this project folder. Sections 1–5 set up the model and helper functions. **Section 6 starts training:** 2,000 epochs with fixed diffusivity, followed by 6,000 with trainable diffusivity. This can take time; it is not a short demonstration run.

The notebook selects Apple MPS when available and otherwise uses the CPU. It does not select CUDA automatically. To use the CPU explicitly, set `DEVICE = "cpu"` in the constants cell before running the later cells.

The notebook does not need network access once its dependencies are installed. Exact package versions were not recorded, so `requirements.txt` is not a locked environment. The original notebook records Python 3.10.19; dataset and packaging checks used Python 3.12.14. A clean installation and full training run have not been repeated for this copy.

## Inputs, model and outputs

The plate is `0.10 × 0.05 m`, simulated over `0–300 s`. The CSV contains 6,001 time rows and 140 virtual-sensor locations. Four COMSOL metadata rows precede the header; sensor coordinates are parsed from each temperature-column name. Values are in kelvin, coordinates in metres and time in seconds.

The model predicts normalised temperature from normalised `(x, y, t)`. The physical residual is based on:

```text
dT/dt = alpha × (d²T/dx² + d²T/dy²)
```

The left boundary has prescribed ramped Gaussian heating. The other three sides use convection and radiation losses; the initial plate temperature is 293.15 K. Sensor IDs are shuffled with seed 0: 112 sensors are used for training and 28 are held out. Validation RMSE is estimated on randomly sampled time/sensor pairs from those held-out locations, not on an independent physical experiment.

Configuration is in the first code cell, including `ALPHA_INIT`, loss weights, Fourier-feature settings, batch sizes, `SEED`, and `STAGE1` / `STAGE2`. The known reference `ALPHA_TRUE = 1e-5 m²/s` is used for reporting errors and plots, not as a target in the training loss.

A new run writes history, metrics, a PyTorch checkpoint and plots to `outputs_submission/`, which is ignored by Git. Saved results are kept separately in `results/selected-run/`, so a rerun does not overwrite the earlier results. The sensitivity and architecture CSVs also come from earlier experiments; Section 8 reads and plots them, but does not rerun those experiments.

## Results and what they mean

The selected run's saved [metrics file](results/selected-run/metrics.txt) reports:

| Quantity | Saved result |
|---|---:|
| Estimated diffusivity | `1.085367e-5 m²/s` |
| Relative diffusivity error | `8.537%` |
| Held-out-sensor temperature RMSE | `0.395640 K` |

That selected run looks encouraging on its own. The six saved starting-value experiments give a less simple picture: diffusivity errors range from about **12.3% to 50.5%**, even though temperature RMSE stays below 1 K. In this setup, matching temperature data is not enough to establish that the recovered material parameter is correct.

The results suggest that diffusivity is difficult to identify reliably under these conditions. This was not a formal test of whether the parameter can be identified reliably; model architecture and the randomness of training also matter. Some saved runs have different outcomes despite sharing the same nominal starting diffusivity.

These figures and tables come from the original saved experiments. They were not generated by a new training run when this folder was prepared for GitHub, and the current notebook is not guaranteed to reproduce every saved value exactly.

## Limits

- Validation uses held-out locations from one simulated dataset, not independent experiments or physical measurements.
- The full-field contours are visual checks. The plotting code clips temperature rise to `0–30 K`, so the pictures alone do not establish full-field accuracy.
- Changing hardware, software or random samples may change the results.

This is a student modelling project, not a validated engineering design tool.

## Files

| Path | Purpose |
|---|---|
| `final_notebook.ipynb` | Original code and explanations; copy without saved cell outputs |
| `sensors_T.csv.xz` | Lossless compressed copy of the complete source CSV |
| `prepare_data.py` | Checks and unpacks the dataset; no extra packages needed |
| `requirements.txt` | Notebook runtime dependencies; not a version lock |
| `results/selected-run/` | Small selection of original saved metrics, history and plots |
| `outputs_alpha_sensitivity/alpha_sensitivity_summary.csv` | Six saved initialisation experiments |
| `outputs_comparison/comparison_table.csv` | Twelve saved architecture-comparison runs |

The notebook's code and Markdown cell text are unchanged. Execution outputs, counts and cell metadata were cleared, and the kernel metadata now uses a generic Python 3 entry. The unpacked CSV is byte-for-byte identical to the source. Checkpoints, environments, reports, slides and course handouts are not included. Unused document-generation packages were left out of the dependency list.

## Next steps

A sensible next step would be controlled repeat runs in a recorded environment, followed by tests with different sensor positions, heating conditions and an independent synthetic dataset.

If you change the model, keep the existing results and save new runs separately. Record the environment, settings and seed, and report both temperature error and diffusivity error.

## References and permissions

Academic references are listed in the notebook. No new open-source licence has been added to this package. The creator and publication permission for the original COMSOL model and dataset still need to be confirmed; keep the repository private until those and any coursework publication conditions have been checked.
