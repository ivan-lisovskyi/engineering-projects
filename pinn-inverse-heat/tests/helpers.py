"""Small helpers shared by the tests (importable because tests/ is on sys.path)."""
import csv
import io
import lzma
import math

import numpy as np
import torch
import torch.nn as nn


class ExactMode(nn.Module):
    """u(xhat, yhat, that) for the exact solution exp(-lam t) sin(pi x/Lx) sin(pi y/Ly)."""

    def __init__(self, cfg, alpha):
        super().__init__()
        self.t_max = cfg.t_max
        self.lam = alpha * math.pi**2 * (1 / cfg.lx**2 + 1 / cfg.ly**2)

    def forward(self, h):
        xh, yh, th = h[:, 0:1], h[:, 1:2], h[:, 2:3]
        return torch.exp(-self.lam * self.t_max * th) * torch.sin(math.pi * xh) * torch.sin(math.pi * yh)


def make_synthetic_table(cfg, n_t=21, nx=6, ny=4, alpha=None):
    """Synthetic sensor data from an exact solution of the heat equation.

    This is NOT the COMSOL set-up (the plate starts hot and the sides stay at ambient);
    it is only a tiny, fully known table (n_t time rows, nx*ny sensors) to exercise the code.
    Returns (t, x, y, T) with T in kelvin, shape [n_t, nx*ny].
    """
    alpha = cfg.alpha_true if alpha is None else alpha
    lam = alpha * np.pi**2 * (1 / cfg.lx**2 + 1 / cfg.ly**2)
    t = np.linspace(0.0, cfg.t_max, n_t, dtype=np.float32)
    xs = np.linspace(0.1 * cfg.lx, 0.9 * cfg.lx, nx, dtype=np.float32)
    ys = np.linspace(0.1 * cfg.ly, 0.9 * cfg.ly, ny, dtype=np.float32)
    x, y = np.meshgrid(xs, ys)
    x, y = x.ravel(), y.ravel()
    u = np.exp(-lam * t)[:, None] * (np.sin(np.pi * x / cfg.lx) * np.sin(np.pi * y / cfg.ly))[None, :]
    return t, x, y, (cfg.t0 + cfg.d_t * u).astype(np.float32)


def write_comsol_csv(path, table):
    """Write (t, x, y, T) in the COMSOL export layout: 4 metadata rows, then the header, then data.

    A path ending in .xz is written lzma-compressed, like sensors_T.csv.xz.
    """
    t, x, y, temps = table
    lines = ["% Model,synthetic.mph", "% Version,test", "% Date,today", "% Table,Table 1 - Point Evaluation 1"]
    header = ["% Time (s)"] + [f"Temperature (K), Point: ({xi:g}, {yi:g})" for xi, yi in zip(x, y)]
    buffer = io.StringIO()
    writer = csv.writer(buffer, lineterminator="\n")
    writer.writerow(header)
    for row, tv in enumerate(t):
        writer.writerow([f"{tv:g}"] + [f"{v:.9g}" for v in temps[row]])
    text = "\n".join(lines) + "\n" + buffer.getvalue()
    path = str(path)
    if path.endswith(".xz"):
        with lzma.open(path, "wt") as handle:
            handle.write(text)
    else:
        with open(path, "w") as handle:
            handle.write(text)
