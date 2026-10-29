"""Sampling, validation and the two-stage training loop (notebook sections 5 and 6)."""
import os
import random
import time

import numpy as np
import pandas as pd
import torch

from .config import resolve_device
from .data import find_data_file, load_comsol_sensor_data
from .model import PINN, to_hat
from .physics import left_bc_u, pde_residual, robin_bc_residual

HISTORY_KEYS = ["epoch", "loss", "L_data", "L_pde", "L_bc", "alpha", "rmse_val"]
BOUNDARY_MODEL = "left Dirichlet heating + right/top/bottom convection+radiation"


def set_seed(seed):
    """Make random choices repeatable for this run."""
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)


def alpha_error_pct(alpha, cfg):
    """Relative error of alpha against the reference value, in percent."""
    return abs(alpha - cfg.alpha_true) / cfg.alpha_true * 100


def sample_times(n, dev, lo, hi, cfg):
    """Return n random physical times between lo and hi.

    n = number of samples, dev = CPU/GPU device, lo/hi = min/max value.

    Some samples are intentionally placed near lo. In this problem, the
    early heating transient is important, so the model should see it often.
    """
    n_early = int(n * cfg.early_frac)  # early-time samples
    n_uniform = n - n_early  # normal uniform samples

    uniform_times = lo + (hi - lo) * torch.rand(n_uniform, device=dev)
    early_times = lo + (hi - lo) * (torch.rand(n_early, device=dev) ** cfg.early_pow)

    return torch.cat([uniform_times, early_times])


def sample_time_indices(n, Nt, dev, cfg):
    """Return n random row numbers for the COMSOL time table.

    Nt means number of time rows in the COMSOL table.

    This is the same idea as sample_times(), but here we need integer indices
    because sensor measurements are stored in rows of the CSV file.
    """
    n_early = int(n * cfg.early_frac)
    n_uniform = n - n_early

    uniform_idx = torch.randint(0, Nt, (n_uniform,), device=dev)
    early_idx = (torch.rand(n_early, device=dev) ** cfg.early_pow * (Nt - 1)).long()

    return torch.cat([uniform_idx, early_idx])


def validation_rmse_K(model, time_values, sensor_x, sensor_y, u_data, val_sensor_idx, dev, cfg):
    """Estimate validation RMSE in Kelvin on sensors not used for training.

    RMSE means root mean squared error. idx means index/id in the data table.
    val_sensor_idx contains the held-out sensor ids used only for validation.

    The function picks random validation sensor/time pairs, predicts the
    temperature there, and compares it with the COMSOL value.
    """
    time_idx = torch.randint(0, time_values.numel(), (cfg.val_batch,), device=dev)
    val_positions = torch.randint(0, val_sensor_idx.numel(), (cfg.val_batch,), device=dev)
    sensor_idx = val_sensor_idx[val_positions]

    h_val = to_hat(sensor_x[sensor_idx], sensor_y[sensor_idx], time_values[time_idx], cfg)
    u_pred = model(h_val).squeeze()

    error_K = (u_pred - u_data[time_idx, sensor_idx]) * cfg.d_t
    return float(torch.sqrt(torch.mean(error_K**2)))


def train(cfg, data=None, verbose=True):
    """Train the PINN and return the model, history, and data split.

    cfg.stage1: epochs with alpha frozen, mainly fitting the temperature field.
    cfg.stage2: epochs with alpha trainable, identifying the material parameter.
    data: optional (time, x, y, T) tuple as returned by load_comsol_sensor_data();
    by default the COMSOL table is read from disk.
    """
    device = resolve_device(cfg.device)
    stage1, stage2 = cfg.stage1, cfg.stage2
    total = stage1 + stage2
    set_seed(cfg.seed)
    if data is None:
        data = load_comsol_sensor_data(find_data_file())
    t_np, x_np, y_np, T_np = data
    Nt, Ns = T_np.shape  # Nt=time rows, Ns=sensor columns

    # Sensor numbers are 0, 1, ..., Ns-1. Shuffle once, then hold out 20%
    sensor_indices = np.arange(Ns)
    rng = np.random.default_rng(cfg.seed)
    rng.shuffle(sensor_indices)
    num_val = Ns // 5
    val_s = sensor_indices[:num_val]  # validation sensor ids
    train_s = sensor_indices[num_val:]  # training sensor ids

    # Move the data to the same device as the model
    time_values = torch.tensor(t_np, device=device)
    sensor_x = torch.tensor(x_np, device=device)
    sensor_y = torch.tensor(y_np, device=device)
    u_data = torch.tensor((T_np - cfg.t0) / cfg.d_t, device=device)  # normalised COMSOL temperatures
    train_sensor_idx = torch.tensor(train_s, device=device)
    val_sensor_idx = torch.tensor(val_s, device=device)

    def sample_uniform(n, lo, hi):
        """Uniform random physical coordinates between lo and hi."""
        return lo + (hi - lo) * torch.rand(n, device=device)

    model = PINN(cfg).to(device)
    model.log_alpha.requires_grad_(False)  # alpha is frozen during stage 1

    # updating theta (network weights) and beta (log_alpha)
    opt = torch.optim.Adam(
        [
            {"params": model.net.parameters(), "lr": cfg.lr},
            {"params": [model.log_alpha], "lr": cfg.alpha_lr},
        ]
    )

    # History is only logged every log_every epochs (and at the first and last epoch)
    hist = {k: [] for k in HISTORY_KEYS}
    t0 = time.time()

    for ep in range(1, total + 1):
        if ep == stage1 + 1:
            model.log_alpha.requires_grad_(True)
            if verbose:
                print(f"--- stage 2 (identify alpha) begins at epoch {ep} ---")
        opt.zero_grad()

        # DATA LOSS: random mini-batch of measured COMSOL sensor/time points
        time_idx = sample_time_indices(cfg.data_batch, Nt, device, cfg)
        train_positions = torch.randint(0, len(train_s), (cfg.data_batch,), device=device)
        sensor_idx = train_sensor_idx[train_positions]
        h_data = to_hat(sensor_x[sensor_idx], sensor_y[sensor_idx], time_values[time_idx], cfg)
        u_pred = model(h_data).squeeze()
        u_true = u_data[time_idx, sensor_idx]
        L_data = torch.mean((u_pred - u_true) ** 2)

        # INITIAL CONDITION LOSS: at t=0 the whole plate starts at T0, so u=0
        xi, yi = sample_uniform(cfg.bc_batch, 0, cfg.lx), sample_uniform(cfg.bc_batch, 0, cfg.ly)
        t_zero = torch.zeros(cfg.bc_batch, device=device)
        L_ic = torch.mean(model(to_hat(xi, yi, t_zero, cfg)) ** 2)

        # LEFT BOUNDARY LOSS: at x=0, match the known Gaussian heating function
        y_left = sample_uniform(cfg.bc_batch, 0, cfg.ly)
        t_left = sample_times(cfg.bc_batch, device, 0.0, cfg.t_max, cfg)
        x_left = torch.zeros(cfg.bc_batch, device=device)
        u_left_pred = model(to_hat(x_left, y_left, t_left, cfg)).squeeze()
        u_left_true = left_bc_u(y_left, t_left, cfg)
        L_left = torch.mean((u_left_pred - u_left_true) ** 2)

        # PDE LOSS
        x_pde = sample_uniform(cfg.pde_batch, 0, cfg.lx)
        y_pde = sample_uniform(cfg.pde_batch, 0, cfg.ly)
        t_pde = sample_times(cfg.pde_batch, device, cfg.t_pde_min, cfg.t_max, cfg)
        L_pde = torch.mean(pde_residual(model, model.alpha(), to_hat(x_pde, y_pde, t_pde, cfg), cfg) ** 2)

        # ROBIN BOUNDARY LOSS: random points on right/top/bottom boundaries
        y_right = sample_uniform(cfg.bc_batch, 0, cfg.ly)  # right side uses y as free coordinate
        t_right = sample_times(cfg.bc_batch, device, 0.0, cfg.t_max, cfg)
        x_top = sample_uniform(cfg.bc_batch, 0, cfg.lx)  # top side uses x as free coordinate
        t_top = sample_times(cfg.bc_batch, device, 0.0, cfg.t_max, cfg)
        x_bottom = sample_uniform(cfg.bc_batch, 0, cfg.lx)  # bottom side also uses x
        t_bottom = sample_times(cfg.bc_batch, device, 0.0, cfg.t_max, cfg)

        bc = torch.cat(
            [
                robin_bc_residual(model, model.alpha(), "right", y_right, t_right, cfg),
                robin_bc_residual(model, model.alpha(), "top", x_top, t_top, cfg),
                robin_bc_residual(model, model.alpha(), "bottom", x_bottom, t_bottom, cfg),
            ]
        )
        L_bc = torch.mean(bc**2)

        # One scalar objective for backpropagation
        loss = cfg.w_data * L_data + L_ic + L_left + cfg.w_pde * L_pde + cfg.w_bc * L_bc
        loss.backward()
        opt.step()

        # The notebook logs at epoch 1 and every LOG_EVERY epochs; the last epoch is also
        # logged here so a short run always ends on its final state (no change when the total
        # is a multiple of log_every, e.g. the default 8000 / 200).
        if ep % cfg.log_every == 0 or ep == 1 or ep == total:
            with torch.no_grad():
                rmse = validation_rmse_K(model, time_values, sensor_x, sensor_y, u_data, val_sensor_idx, device, cfg)
            a = float(model.alpha().detach())
            hist["epoch"].append(ep)
            hist["loss"].append(float(loss.detach()))
            hist["L_data"].append(float(L_data.detach()))
            hist["L_pde"].append(float(L_pde.detach()))
            hist["L_bc"].append(float(L_bc.detach()))
            hist["alpha"].append(a)
            hist["rmse_val"].append(rmse)
            if verbose and (ep % cfg.print_every == 0 or ep == 1 or ep == stage1 + 200 or ep == total):
                tag = "fit " if ep <= stage1 else "idfy"
                print(
                    f"[{tag}] ep {ep:5d} | loss {float(loss.detach()):.2e} | alpha {a:.3e} "
                    f"({alpha_error_pct(a, cfg):4.1f}%) | val RMSE {rmse:.3f} K"
                )

    a = float(model.alpha().detach())
    if verbose:
        print(
            f"\nDONE in {time.time() - t0:.0f}s | alpha = {a:.4e} "
            f"({alpha_error_pct(a, cfg):.1f}% error) | val RMSE {hist['rmse_val'][-1]:.3f} K"
        )
    return model, hist, (t_np, x_np, y_np, T_np, train_s, val_s)


def save_run(model, hist, cfg, out_dir):
    """Write history.csv, metrics.txt and checkpoint.pt (notebook section 6 formats).

    metrics.txt keeps the notebook's four lines and adds the epochs, seed and device so a
    short run cannot be mistaken for the full 2000 + 6000 epoch study run.
    """
    os.makedirs(out_dir, exist_ok=True)
    pd.DataFrame(hist).to_csv(os.path.join(out_dir, "history.csv"), index=False)

    a = float(model.alpha().detach())
    with open(os.path.join(out_dir, "metrics.txt"), "w") as f:
        f.write(f"alpha = {a:.6e} m^2/s\n")
        f.write(f"alpha_error_pct = {alpha_error_pct(a, cfg):.3f}\n")
        f.write(f"val_rmse_K = {hist['rmse_val'][-1]:.6f}\n")
        f.write(f"boundary_model = {BOUNDARY_MODEL}\n")
        f.write(f"epochs = {cfg.stage1}+{cfg.stage2} (fixed alpha + joint)\n")
        f.write(f"seed = {cfg.seed}\n")
        f.write(f"device = {resolve_device(cfg.device)}\n")

    # checkpoint.pt stores model weights
    torch.save({"model_state": model.state_dict(), "alpha": a}, os.path.join(out_dir, "checkpoint.pt"))
