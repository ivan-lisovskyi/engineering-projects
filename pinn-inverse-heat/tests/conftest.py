"""Shared fixtures: a tiny CPU-only configuration and a tiny synthetic sensor table."""
import os

os.environ.setdefault("MPLBACKEND", "Agg")  # no display needed

import pytest

from helpers import make_synthetic_table
from pinn.config import Config


def tiny_config():
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


@pytest.fixture
def tiny_cfg():
    return tiny_config()


@pytest.fixture
def synthetic_table(tiny_cfg):
    return make_synthetic_table(tiny_cfg)
