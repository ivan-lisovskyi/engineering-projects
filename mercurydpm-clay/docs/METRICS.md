# How the fabric metrics are computed

[Back to the project](../README.md)

Every formula below is read from the code and each one names the function and line numbers where it lives, so it can be checked. Line numbers refer to the files as committed. Where the code or README does not define something, this page says **not documented** instead of guessing.

Abbreviations: `cpp` is [`src/clay_anisotropy_updated.cpp`](../src/clay_anisotropy_updated.cpp); `plot` is [`scripts/plot_ph3_ph10_six_seed_clean.py`](../scripts/plot_ph3_ph10_six_seed_clean.py); `stack` is [`scripts/stacking_analysis.py`](../scripts/stacking_analysis.py); `overlap` is [`scripts/initial_overlap_check.py`](../scripts/initial_overlap_check.py); `template` is [`scripts/plot_charge_templates.py`](../scripts/plot_charge_templates.py).

## What is computed where

| Quantity | Computed in | Written to |
|---|---|---|
| Source values per surface site, zero-mean correction | `cpp` lines 485-521; mirrored in `template` `source_values` | pebble `Charge` field of the VTU files |
| Platelet normal, `S_z`, `S_tensor`, `lambda_max`, `Axx..Azz` | `cpp` `computeAnisotropySnapshot`, lines 593-641 | `AnisotropyLog.csv`, `clay_anisotropy_updated_normals_*.vtp` |
| Long-range energy `U_lr` | `cpp` `computeLRPotEnergy` (204-246) and line 807 | `LRPotential.txt` |
| Energy change `delta_U_lr`, kinetic energy | `plot` `read_run` (93-119), `read_kinetic` (73-90) | `final_values_six_seed_summary.csv` |
| Stack-like groups | `plot` `stack_components` (352-386), `stack` `group_platelets` (44-73) | `stacking_*_summary.csv`, `stacking_time_series_six_seed.csv` |
| Initial overlaps | `overlap` `overlap_stats` (62-87) | `initial_overlap_summary.csv` |
| Means and standard deviations over seeds | `plot` `write_csvs` (676-749) | `six_seed_means.txt` |

The simulation box is the unit cube (`f_min = 0`, `f_max = 1`, `cpp` line 64) with periodic boundaries (`PERIODIC = true`, line 57). All lengths below are in these model units; time is "model units" (the plot axes say so). Physical units are **not documented**.

## 1. Source template (the prescribed values per surface site)

Platelet geometry, from `setCustomClump` (`cpp` 392-413): two hexagonal layers with `N_side = 14` (lattice radius 13), `spacing = 0.1 * clumpScale = 0.01`, `pebble_rad = 0.08 * clumpScale = 0.008`, layers at z = +0.2 and -0.2 times the spacing from the centre. A layer has 3*14*13 + 1 = 547 sites (computed from the loops in `generate_hexagonal_lattice`, lines 250-277); the outer ring has 6*13 = 78 sites and counts as the edge (`append_surface_labels_for_hex_layer`, 286-303), leaving 469 interior sites per layer. So there are 469 + 469 + 156 = 1094 pebbles per platelet, matching `N_SILICA`, `N_ALUMINA`, `N_EDGE`, `N_TOTAL` in `template` lines 16-19 and `N_PEBBLES = 1094` in `overlap` line 27. The 50 platelets of the saved runs are 50 such clumps (`N_CLUMPS = 50`, `overlap` line 26).

With the pH input clamped to [3, 10] (`cpp` 486-487) and

    lambda3  = (10 - pH) / 7        lambda10 = (pH - 3) / 7        (cpp 489-490)

the raw value per site (`cpp` 492-499) is:

| Site label in `cl_surface` | `cpp` expression | Raw value, with t = (pH - 3) / 7 |
|---|---|---|
| +1 (top layer interior) | `-0.22` | -0.22 |
| -1 (bottom layer interior) | `lambda3*(+0.20) + lambda10*(-0.20)` | 0.20 - 0.40 t |
| 0 (outer ring, both layers) | `lambda3*(+0.06) + lambda10*(-0.06)` | 0.06 - 0.12 t |

`template` lines 30-35 give the same three expressions under the names "Silica-like face", "Alumina-like face" and "Edge". The C++ driver itself only says top / bottom / edge (a search for "silica" and "alumina" in `cpp` finds nothing), so the pairing top = silica-like and bottom = alumina-like rests on the identical constants, not on a statement in the driver.

For the periodic solver the mean is subtracted (`cpp` 518-521, `PERIODIC` only):

    mean = (1/1094) * sum over the pebbles of one clump of raw value
    used value = raw value - mean

`template` lines 36-41 compute the same mean as `(469*raw_silica + 469*raw_alumina + 156*raw_edge) / 1094`. Worked values from that formula: the mean is -1.83e-5 at pH 3 and -0.18861 at pH 10, so the corrected values sum to zero over a clump (this is asserted in `tests/test_scripts_smoke.py`). The driver's comment says the correction is a numerical periodic-boundary device, not a surface-chemistry prediction (`cpp` 512-519).

How the pH label relates to any real chemistry is **not documented**; the README states that the labels are simplified model inputs.

## 2. Orientation metrics

At each saved step, `computeAnisotropySnapshot` (`cpp` 593-641) loops over the clumps:

1. Platelet normal `n_i` = third principal-direction axis of the clump, `getPrincipalDirections_e3()`, divided by its length (lines 607-611). Clumps whose normal is not finite and non-zero are skipped; `N` = `validClumps` counts the rest. The principal directions are set to the identity at insertion (`setPrincipalDirections`, lines 535-538), and the two layers are separated along z, so each platelet starts with `n = (0, 0, 1)`. The plotting code relies on this (see below).
2. Orientation tensor, accumulated in lines 617-622 and divided by `N` in lines 629-636:

       Q = (1/N) * sum_i  n_i n_i^T          (Axx, Axy, Axz, Ayy, Ayz, Azz)

3. `lambda_max` = largest eigenvalue of `Q`, from `largestEigenvalueSymmetric3x3` (lines 111-134): power iteration from the start vector (1,1,1)/sqrt(3), exactly `EIGEN_POWER_ITERATIONS = 80` iterations, then the Rayleigh quotient. There is no convergence test; when the two largest eigenvalues of `Q` are close, 80 iterations may leave a small underestimate. That limit is a property of the method, not something measured in this repository.
4. Scalar order parameter (line 639):

       S_tensor = (3 * lambda_max - 1) / 2

   Because the eigenvalues of `Q` sum to 1, `lambda_max` lies in [1/3, 1] and `S_tensor` in [0, 1]. The plot script uses y-limits (1/3, 1.02) for `lambda_max` and (0, 1.02) for `S` (`plot` lines 255-257).
5. Vertical alignment (lines 612, 623, 636):

       S_z = (1/N) * sum_i 0.5 * (3 * n_i,z^2 - 1)

   range [-0.5, 1]; 0 for isotropic normals, 1 when all normals are along z. The per-platelet term is also written to the VTP file as `S_z_local`.

All three scalars are written per saved step to `AnisotropyLog.csv` (columns `time,step,n_clumps,valid_clumps,S_z,S_tensor,lambda_max,Axx,...,Azz`, lines 587-588 and 643-663). The `plot` script reads `S_tensor`, `S_z`, `lambda_max` (`read_run`, lines 116-118). "Final" values are the last row (`write_csvs`, lines 702-704), at time 0.999999 (see `final_values_six_seed_summary.csv`).

The `plot` script prepends a `t = 0` point of 1.0 for all three scalars (`T0_VALUES`, line 34, `with_t0`, 139-147). This value is assumed from the initial alignment of the normals, not read from a file.

## 3. Energy

- `U_lr` (`computeLRPotEnergy`, `cpp` 204-246): PVFMM Laplace *potential* kernel evaluated at all pebble positions with the same boundary type as the force solve (periodic if `PERIODIC`); each potential is multiplied by that pebble's source value (line 235) and the sum is halved (line 239):

      U_raw = 0.5 * sum_i  q_i * phi(r_i)

  The logged value is `LRMagnitude * U_raw` with `LRMagnitude = 0.01` (line 807, constant at line 72). The exact normalisation of PVFMM's Laplace kernel is **not documented** in this repository.
- `delta_U_lr` (`plot` line 109): `U_lr(t_k) - U_lr(t_0)`, where `t_0` is the first logged entry of `LRPotential.txt`. Entries line up one-to-one with the rows of `AnisotropyLog.csv` (checked at `plot` lines 99-100), whose first time in the committed time series is 0.001999 (`stacking_time_series_six_seed.csv` for t > 0), so "first saved state" is the first output after the start, not t = 0 itself. Absolute `U_lr` is not comparable between the two templates (the README gives the reason; final means are -151.141 at pH 3 and 40.5063 at pH 10 in `six_seed_means.txt`).
- Kinetic energy (`plot` `read_kinetic`, lines 73-90): `traKineticEnergy + rotKineticEnergy` from the MercuryDPM `.ene` file; a leading row with time 0 is dropped to match the log length (lines 101-103).
- Saving cadence: `outputStride = round(timeMax / (timeStep * 500))` (`computeOutputStride`, lines 342-346, with `TARGET_OUTPUT_FRAMES = 500`). The saved runs used duration 1 and `timeStep = 1e-6`, which gives a stride of 2000 steps (derived).

## 4. Stack-like groups

Two scripts implement the same rule independently: `group_platelets` in `stack` and `stack_components` in `plot`. Inputs are the platelet centres (the VTP `Points`) and unit normals (`platelet_normal`).

Platelets `i` and `j` are linked when both hold:

    min-image distance:   | r_i - r_j - round(r_i - r_j) |  <  0.08          (strict <)
    parallel normals:     | n_i . n_j |  >=  cos(20 degrees)                (unoriented)

- `stack` lines 63-67 use `delta -= np.round(delta)` and the Euclidean norm; `plot` `periodic_distance` (343-349) takes `min(d, 1 - d)` per axis. For the unit box these are the same minimum-image distance.
- `STACK_DISTANCE = 0.08` and `STACK_ANGLE_DEG = 20.0` (`stack` lines 23-24); `STACK_CUTOFF = 0.08`, `STACK_MAX_ANGLE_DEG = 20.0` (`plot` lines 35-36). The absolute value in the angle test means a platelet and its flipped copy count as parallel.
- Links are combined with union-find into connected components (`stack` 55-73, `plot` 359-386). A component is therefore a chain of links: two members of one group can differ by more than 20 degrees or be farther apart than 0.08 as long as intermediate platelets link them.
- A group counts as "stack-like" only if it has at least 2 platelets (`stack` line 107, `plot` line 449). Then, per run and time:

      n_stacks            = number of such groups
      platelets_in_stacks = sum of their sizes
      largest_stack       = size of the largest group (0 if there is none)

  (`stack` 115-117, `plot` 452-459, `component_metrics` 463-476).
- The code says explicitly that this is a geometric check and not a contact classifier (`stack` docstring, lines 2-8); the README says the same. For scale (derived from the geometry in section 1): the centre-to-corner distance of a platelet is 13 * 0.01 = 0.13, larger than the 0.08 cutoff.

**Internal order of a group** (`mean_stack_order` in `stacking_six_seed_summary.csv`, `stack_S_internal` in `stack`): for a group `g` of at least two platelets,

    Q_g = (1/|g|) * sum_{i in g} n_i n_i^T        S_g = (3 * lambda_max(Q_g) - 1) / 2

`plot` `stack_order` (389-416) uses its own power iteration (`largest_eigenvalue_3x3`, 419-433, 80 iterations); `stack` `group_order_parameter` (76-80) uses `numpy.linalg.eigvalsh`. `mean_stack_order` is the mean of `S_g` over a run's groups (`plot` line 458), and is **0.0 when the run has no groups**. In the committed CSV the four pH 10 seeds without groups therefore show 0.0: that is a placeholder meaning "no group", not a measured disorder. `six_seed_means.txt` does not use this column.

**Final state vs time series.** Final metrics use the VTP file with the highest trailing integer (`stack` `latest_vtp`, 83-87; `plot` `normal_vtp_files`, 288-292, last entry). The time series (`compute_stacking_time_series`, `plot` 479-517) applies the same rule to every saved VTP, plus a `t = 0` state read from the initial `Particle_0.vtu`: the clump centres are the particles with `|Charge| <= 1e-12` and **all normals are set to (0, 0, 1)** (`parse_initial_clump_state`, 335-340). The script also checks that matched pH 3 and pH 10 runs share the same `t = 0` centres to 1e-7 (lines 487-499). In the committed time series the last row of each run equals the final summary (checked for all 12 runs when writing this page).

**Threshold sensitivity** (`compute_stacking_sensitivity`, `plot` 520-548): distance cutoff in `[0.06, 0.07, 0.08, 0.09, 0.10]` at 20 degrees, and maximum angle in `[10, 20, 30]` degrees at 0.08 (lines 37-38), for all 12 final states. Results are in `stacking_sensitivity_six_seed_summary.csv`; see the [gallery](GALLERY.md) for the six-seed means.

## 5. Initial overlaps

`overlap` (`overlap_stats`, 62-87), for each of the six seeds on the pH 3 run (pH 3 and pH 10 start from the same positions, per the script docstring and the check at `plot` lines 487-499):

1. Read the t = 0 VTU. Particles with `|Charge| <= 1e-12` are the 50 clump centres, the rest are 50 x 1094 pebbles (lines 44-54).
2. `TOUCH = 2 * R_PEBBLE = 0.016` (line 29).
3. `circumradius` = largest centre-to-pebble distance over all clumps (line 69, no periodic wrapping applied there); `cutoff = 2 * circumradius + TOUCH` (line 74). Only clump pairs with minimum-image centre distance below the cutoff are examined.
4. For such a pair, `min_dist` = smallest minimum-image pebble-pebble distance. If `min_dist < TOUCH` the pair is counted as overlapping and `penetration = TOUCH - min_dist`.
5. Output per seed: the number of overlapping pairs, the largest penetration, and `100 * penetration / TOUCH` as a percentage of a pebble diameter (lines 96-103).

The script assumes that the VTU lists the pebbles clump by clump in the same order as the centres (it only checks the counts). That ordering is **not documented** in the script.

## 6. Aggregation across seeds

`six_seed_means.txt` (`plot` 739-749) holds, for each pH, the mean and the sample standard deviation (`statistics.stdev`, n - 1, over the six seeds) of the final value of `lr_total`, `lr_delta`, `kinetic`, `S_tensor`, `S_z`, `lambda_max`, and of `n_stacks`, `platelets_in_stacks`, `largest_stack`. The figures draw one thin line per seed and a thick line for the mean over seeds at each saved time (`plot_ph_lines`, 196-225).

## Not documented

- Physical units and any mapping of model time or length to real clay.
- The normalisation of PVFMM's Laplace kernel, and whether `U_lr` corresponds to a physical energy (the README says it should not be read as a free energy).
- Why 0.08 and 20 degrees were chosen as the grouping rule (the sensitivity table shows their effect, no rationale is given).
- Convergence of the 80-iteration eigenvalue routine, beyond the code itself.
- The ordering of pebbles in the VTU files assumed by `overlap`.
