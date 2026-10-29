"""Figures from notebook sections 2 and 7. Each ``plot_*`` function returns a matplotlib Figure."""
import math
import os

import matplotlib.pyplot as plt
import numpy as np
import torch

from .model import to_hat

SNAPSHOT_TIMES = (5.0, 20.0, 100.0, 300.0)


@torch.no_grad()
def predict_delta_T(model, x, y, t, cfg):
    """Predict temperature rise Delta T [K] at physical coordinates.

    x, y, t can be NumPy arrays of the same shape. The function returns a
    NumPy array with the same shape, in Kelvin above T0.
    """
    device = next(model.parameters(), torch.zeros(0)).device  # parameter-free test models run on CPU
    x = np.asarray(x, dtype=np.float32)
    y = np.asarray(y, dtype=np.float32)
    t = np.asarray(t, dtype=np.float32)

    delta_T = np.empty(x.shape, dtype=np.float32)
    batch_size = 200_000  # avoids memory spikes on large grids

    for start in range(0, x.size, batch_size):
        end = start + batch_size
        x_batch = torch.tensor(x[start:end], device=device, dtype=torch.float32)
        y_batch = torch.tensor(y[start:end], device=device, dtype=torch.float32)
        t_batch = torch.tensor(t[start:end], device=device, dtype=torch.float32)
        u_batch = model(to_hat(x_batch, y_batch, t_batch, cfg)).squeeze()  # normalised PINN prediction
        delta_T[start:end] = cfg.d_t * u_batch.cpu().numpy()
    return delta_T


def closest_sensor(x_np, y_np, x_target, y_target):
    """Return the index of the exported sensor closest to a requested point."""
    distance_squared = (x_np - x_target) ** 2 + (y_np - y_target) ** 2
    return int(np.argmin(distance_squared))


def format_plate_axis(axis, cfg):
    """Apply the same plate outline, labels, and grid to sensor plots."""
    axis.set_xlim(-0.004, cfg.lx + 0.004)
    axis.set_ylim(-0.004, cfg.ly + 0.004)
    axis.set_aspect("equal")
    axis.set_xlabel("x [m]")
    axis.set_ylabel("y [m]")
    axis.set_xticks([0.00, 0.05, 0.10])
    axis.set_yticks([0.00, 0.025, 0.05])
    axis.add_patch(plt.Rectangle((0, 0), cfg.lx, cfg.ly, fill=False, lw=1.3, edgecolor="black"))
    axis.plot([0, 0], [0, cfg.ly], color="#d73027", lw=3.0)  # heated left edge
    axis.grid(alpha=0.18, lw=0.6)


def plot_sensor_layout(x_np, y_np, cfg, train_s=None, val_s=None):
    """Sensor positions on the plate; training and held-out sensors are coloured differently."""
    fig, ax = plt.subplots(figsize=(9, 4.8))
    format_plate_axis(ax, cfg)
    if train_s is None or val_s is None:
        ax.scatter(x_np, y_np, s=42, color="#2563eb", edgecolor="white", linewidth=0.6, zorder=3)
    else:
        ax.scatter(x_np[train_s], y_np[train_s], s=42, facecolor="#334155", edgecolor="white",
                   linewidth=0.7, label=f"training sensors ({len(train_s)})", zorder=3)
        ax.scatter(x_np[val_s], y_np[val_s], s=58, facecolor="#f59e0b", edgecolor="black",
                   linewidth=0.6, label=f"validation sensors ({len(val_s)})", zorder=4)
        ax.legend(loc="upper right")
    ax.set_title("COMSOL virtual sensor locations")
    fig.tight_layout()
    return fig


def plot_training_dashboard(hist, cfg):
    """Alpha estimate, alpha error, losses and validation RMSE against the epoch."""
    epochs = np.asarray(hist["epoch"])
    alpha = np.asarray(hist["alpha"])
    fig, ax = plt.subplots(2, 2, figsize=(12, 8))

    # Alpha is plotted in units of 1e-5 so the numbers are easy to read.
    ax[0, 0].plot(epochs, alpha * 1e5, color="tab:blue", linewidth=2)
    ax[0, 0].axhline(cfg.alpha_true * 1e5, color="black", linestyle="--")
    ax[0, 0].set_title("(a) alpha estimate")
    ax[0, 0].set_xlabel("epoch")
    ax[0, 0].set_ylabel(r"alpha [$10^{-5}$ m$^2$/s]")
    ax[0, 0].grid(alpha=0.3)

    alpha_error = np.abs(alpha - cfg.alpha_true) / cfg.alpha_true * 100
    ax[0, 1].semilogy(epochs, alpha_error, color="tab:blue", linewidth=2)
    ax[0, 1].axhline(5, color="green", linestyle="--", label="5%")
    ax[0, 1].axhline(10, color="orange", linestyle="--", label="10%")
    ax[0, 1].set_title("(b) alpha error")
    ax[0, 1].set_xlabel("epoch")
    ax[0, 1].set_ylabel("error [%]")
    ax[0, 1].legend()
    ax[0, 1].grid(alpha=0.3)

    # semilogy is useful because the loss terms can differ by orders of magnitude.
    for loss_name in ["loss", "L_data", "L_pde", "L_bc"]:
        ax[1, 0].semilogy(epochs, hist[loss_name], label=loss_name)
    ax[1, 0].set_title("(c) training losses")
    ax[1, 0].set_xlabel("epoch")
    ax[1, 0].set_ylabel("loss")
    ax[1, 0].legend()
    ax[1, 0].grid(alpha=0.3)

    # Validation RMSE is measured on held-out sensors and converted back to Kelvin.
    ax[1, 1].semilogy(epochs, hist["rmse_val"], color="tab:purple", linewidth=2)
    ax[1, 1].set_title("(d) validation RMSE")
    ax[1, 1].set_xlabel("epoch")
    ax[1, 1].set_ylabel("RMSE [K]")
    ax[1, 1].grid(alpha=0.3)

    fig.suptitle(f"Training diagnostics | alpha = {alpha[-1]:.3e}")
    fig.tight_layout()
    return fig


def plot_sensor_fit(model, data, cfg):
    """COMSOL sensor time traces (solid) against PINN predictions (dashed) at four sensors."""
    t_np, x_np, y_np, T_np = data[:4]
    # Four representative sensors: near the heated side, middle, far side, and upper-right area.
    sensor_targets = [(0.0115, 0.023), (0.0505, 0.023), (0.0700, 0.023), (0.0895, 0.0455)]
    sensor_ids = [closest_sensor(x_np, y_np, x0, y0) for x0, y0 in sensor_targets]
    colors = plt.cm.viridis(np.linspace(0, 0.85, len(sensor_ids)))

    fig, (ax_full, ax_zoom) = plt.subplots(1, 2, figsize=(13, 5))
    for sensor_id, color in zip(sensor_ids, colors):
        # Build one time trace at a fixed sensor position.
        x_sensor = np.full(t_np.shape, x_np[sensor_id], dtype=np.float32)
        y_sensor = np.full(t_np.shape, y_np[sensor_id], dtype=np.float32)
        pinn_delta_T = predict_delta_T(model, x_sensor, y_sensor, t_np, cfg)
        comsol_delta_T = T_np[:, sensor_id] - cfg.t0
        label = f"({x_np[sensor_id]:.3f}, {y_np[sensor_id]:.3f})"

        ax_full.plot(t_np, comsol_delta_T, color=color, linewidth=2.2, label=label)
        ax_full.plot(t_np, pinn_delta_T, "--", color=color, linewidth=1.5)
        ax_zoom.plot(t_np, comsol_delta_T, color=color, linewidth=2.2)
        ax_zoom.plot(t_np, pinn_delta_T, "--", color=color, linewidth=1.5)

    ax_full.set_xlim(0, cfg.t_max)
    ax_full.set_title("full 0-300 s")
    ax_full.legend(title="sensor (x,y) [m]", fontsize=8)
    ax_zoom.set_xlim(0, 30)
    ax_zoom.set_title("early-time zoom (0-30 s)")
    for axis in (ax_full, ax_zoom):
        axis.set_xlabel("t [s]")
        axis.set_ylabel(r"$\Delta T$ [K]")
        axis.grid(alpha=0.3)

    fig.suptitle("Sensor temperatures: COMSOL (solid) vs PINN (dashed)")
    fig.tight_layout()
    return fig


def plot_centerline(model, data, cfg):
    """Heat penetration along y = Ly/2: PINN line against COMSOL sensor dots at four times."""
    t_np, x_np, y_np, T_np = data[:4]
    # Centreline means y approximately equal to Ly/2, across the plate from left to right.
    y_mid = cfg.ly / 2.0
    centerline_sensors = np.where(np.abs(y_np - y_mid) < 0.004)[0]
    centerline_sensors = centerline_sensors[np.argsort(x_np[centerline_sensors])]
    x_line = np.linspace(0, cfg.lx, 200, dtype=np.float32)  # smooth PINN line
    colors = plt.cm.plasma(np.linspace(0, 0.85, len(SNAPSHOT_TIMES)))

    fig, ax = plt.subplots(figsize=(8, 5))
    for plot_time, color in zip(SNAPSHOT_TIMES, colors):
        time_id = int(np.argmin(np.abs(t_np - plot_time)))
        time_value = float(t_np[time_id])
        t_line = np.full(x_line.shape, time_value, dtype=np.float32)
        y_line = np.full(x_line.shape, y_mid, dtype=np.float32)
        pinn_delta_T = predict_delta_T(model, x_line, y_line, t_line, cfg)
        comsol_delta_T = T_np[time_id, centerline_sensors] - cfg.t0

        ax.plot(x_line, pinn_delta_T, color=color, linewidth=2, label=f"t = {time_value:g} s")
        ax.scatter(x_np[centerline_sensors], comsol_delta_T, color=color, s=28,
                   edgecolors="black", linewidths=0.3, zorder=5)

    ax.set_xlabel("x [m]  (0 = heated left edge)")
    ax.set_ylabel(r"$\Delta T$ [K]")
    ax.set_title("Centreline profile at y = Ly/2: PINN (line) vs COMSOL sensors (dots)")
    ax.legend()
    ax.grid(alpha=0.3)
    fig.tight_layout()
    return fig


def contour_grid_at_time(model, cfg, time_value, nx=180, ny=90):
    """Evaluate the trained PINN on the full x-y grid at one fixed time."""
    x_grid = np.linspace(0, cfg.lx, nx, dtype=np.float32)  # regular x grid for plotting
    y_grid = np.linspace(0, cfg.ly, ny, dtype=np.float32)  # regular y grid for plotting
    X, Y = np.meshgrid(x_grid, y_grid)  # 2D plate grid
    t_grid = np.full(X.size, time_value, dtype=np.float32)
    delta_T_grid = predict_delta_T(model, X.ravel(), Y.ravel(), t_grid, cfg)
    # Clip only for visualisation: the plotted quantity is temperature rise.
    return X, Y, np.clip(delta_T_grid.reshape(X.shape), 0.0, cfg.d_t)


def plot_contours(model, cfg, times=SNAPSHOT_TIMES):
    """Predicted temperature rise over the plate at several times (shared 0...dT colour scale)."""
    model.eval()
    levels = np.linspace(0.0, cfg.d_t, 41)  # fixed shared scale: 0...30 K
    rows, cols = math.ceil(len(times) / 2), min(2, len(times))
    fig, axes = plt.subplots(rows, cols, figsize=(12, 3 * rows), sharex=True, sharey=True,
                             constrained_layout=True, squeeze=False)
    axes = axes.ravel()
    cf = None
    for axis, plot_time in zip(axes, times):
        X, Y, Z = contour_grid_at_time(model, cfg, plot_time)
        cf = axis.contourf(X, Y, Z, levels=levels, cmap="viridis")
        axis.set_title(f"t = {plot_time:g} s")
        axis.set_aspect("equal")
        axis.set_xlabel("x [m]")
        axis.set_ylabel("y [m]")
    cbar = fig.colorbar(cf, ax=axes.tolist(), shrink=0.92)
    cbar.set_label(r"$\Delta T$ [K]")
    fig.suptitle("PINN temperature contours over time")
    return fig


def plot_contour(model, cfg, plot_time):
    """One full-plate contour image, like the notebook's contour_t<time>.png."""
    model.eval()
    levels = np.linspace(0.0, cfg.d_t, 41)
    X, Y, Z = contour_grid_at_time(model, cfg, plot_time)
    fig, axis = plt.subplots(figsize=(10, 4), constrained_layout=True)
    cf = axis.contourf(X, Y, Z, levels=levels, cmap="viridis")
    fig.colorbar(cf, ax=axis, label=r"$\Delta T$ [K]")
    axis.set_title(f"PINN temperature rise at t = {plot_time:g} s")
    axis.set_xlabel("x [m]")
    axis.set_ylabel("y [m]")
    axis.set_aspect("equal")
    return fig


def save_figures(model, hist, data, cfg, out_dir):
    """Save the four figures that are also kept in results/selected-run/; return their paths."""
    os.makedirs(out_dir, exist_ok=True)
    jobs = [
        ("training_dashboard.png", plot_training_dashboard(hist, cfg), 150, {}),
        ("sensor_fit.png", plot_sensor_fit(model, data, cfg), 150, {}),
        ("centerline_profile.png", plot_centerline(model, data, cfg), 150, {}),
        ("contour_t300.png", plot_contour(model, cfg, 300.0), 200, {}),
    ]
    paths = []
    for name, fig, dpi, kwargs in jobs:
        path = os.path.join(out_dir, name)
        fig.savefig(path, dpi=dpi, **kwargs)
        plt.close(fig)
        paths.append(path)
    return paths
