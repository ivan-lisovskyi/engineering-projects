"""Shared fixtures: a tiny CPU-only configuration and a tiny synthetic sensor table."""
import os

os.environ.setdefault("MPLBACKEND", "Agg")  # no display needed

import numpy as np
import pytest

from pinn.config import Config


@pytest.fixture
def tiny_cfg():
    """Small network and small batches so a whole training run takes a second or two."""
    return Config(
        width=16,
        depth=2,
        n_ff=8,
        stage1=30,
        stage2=60,
        log_every=30,
        print_every=1000,
        data_batch=128,
        pde_batch=128,
        bc_batch=64,
        val_batch=200,
        device="cpu",
    )


def make_synthetic_table(cfg, n_t=21, nx=6, ny=4, alpha=None):
    """Synthetic sensor data from an exact solution of the heat equation.

    u = exp(-lambda t) sin(pi x/Lx) sin(pi y/Ly) with lambda = alpha*pi^2*(1/Lx^2 + 1/Ly^2).
    This is NOT the COMSOL set-up (the plate starts hot and the sides stay at ambient);
    it is only a tiny, fully known table to exercise the code.
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


@pytest.fixture
def synthetic_table(tiny_cfg):
    return make_synthetic_table(tiny_cfg)
