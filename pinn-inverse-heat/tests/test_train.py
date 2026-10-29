"""Sampling, validation RMSE and the two-stage training loop on a tiny synthetic table."""
from dataclasses import replace

import numpy as np
import pytest
import torch

from helpers import ExactMode
from pinn.model import PINN
from pinn.train import (
    alpha_error_pct,
    sample_time_indices,
    sample_times,
    save_run,
    train,
    validation_rmse_K,
)


def run(cfg, table):
    return train(cfg, data=table, verbose=False)


def test_sample_times_stay_in_range_and_are_biased_early(tiny_cfg):
    torch.manual_seed(0)
    s = sample_times(10_000, "cpu", 0.5, 300.0, tiny_cfg)
    assert s.shape == (10_000,)
    assert float(s.min()) >= 0.5 and float(s.max()) <= 300.0
    # 60 % uniform (mean 150) + 40 % cubed-uniform (mean 75) -> clearly below the uniform mean
    assert float(s.mean()) < 150.0


def test_sample_time_indices_are_valid_row_numbers(tiny_cfg):
    torch.manual_seed(0)
    idx = sample_time_indices(5_000, 21, "cpu", tiny_cfg)
    assert idx.dtype == torch.int64
    assert int(idx.min()) >= 0 and int(idx.max()) <= 20


def test_validation_rmse_is_zero_for_the_exact_field_and_equals_a_known_offset(tiny_cfg, synthetic_table):
    t, x, y, temps = synthetic_table
    u_data = torch.tensor((temps - tiny_cfg.t0) / tiny_cfg.d_t)
    args = (torch.tensor(t), torch.tensor(x), torch.tensor(y), u_data, torch.arange(6))
    exact = ExactMode(tiny_cfg, tiny_cfg.alpha_true)
    torch.manual_seed(0)
    assert validation_rmse_K(exact, *args, "cpu", tiny_cfg) < 1e-3

    class Shifted(torch.nn.Module):  # exact field plus 0.1 in u = 3 K everywhere
        def forward(self, h):
            return exact(h) + 0.1

    torch.manual_seed(0)
    assert validation_rmse_K(Shifted(), *args, "cpu", tiny_cfg) == pytest.approx(3.0, abs=1e-3)


def test_history_schema_and_logged_epochs(tiny_cfg, synthetic_table):
    _, hist, _ = run(tiny_cfg, synthetic_table)
    assert list(hist) == ["epoch", "loss", "L_data", "L_pde", "L_bc", "alpha", "rmse_val"]
    assert hist["epoch"] == [1, 30, 60, 90]
    assert all(len(v) == 4 for v in hist.values())
    assert all(np.isfinite(v).all() for v in hist.values())


def test_final_epoch_is_always_logged(tiny_cfg, synthetic_table):
    cfg = replace(tiny_cfg, stage1=3, stage2=4, log_every=5)
    _, hist, _ = run(cfg, synthetic_table)
    assert hist["epoch"] == [1, 5, 7]


def test_loss_goes_down(tiny_cfg, synthetic_table):
    _, hist, _ = run(tiny_cfg, synthetic_table)
    assert hist["loss"][-1] < 0.5 * hist["loss"][0]


def test_alpha_is_frozen_in_stage_one_and_moves_in_stage_two(tiny_cfg, synthetic_table):
    model, hist, _ = run(tiny_cfg, synthetic_table)
    epochs, alpha = hist["epoch"], hist["alpha"]
    stage1_alpha = [a for e, a in zip(epochs, alpha) if e <= tiny_cfg.stage1]
    assert stage1_alpha == [stage1_alpha[0]] * len(stage1_alpha)
    assert stage1_alpha[0] == pytest.approx(tiny_cfg.alpha_init, rel=1e-5)
    assert alpha[-1] != alpha[epochs.index(tiny_cfg.stage1)]
    assert float(model.alpha().detach()) > 0.0


def test_sensor_split_is_80_20_and_disjoint(tiny_cfg, synthetic_table):
    _, _, (_, _, _, temps, train_s, val_s) = run(tiny_cfg, synthetic_table)
    n_sensors = temps.shape[1]
    assert len(val_s) == n_sensors // 5 == 4
    assert len(train_s) == n_sensors - 4
    assert set(train_s).isdisjoint(val_s)
    assert sorted(np.concatenate([train_s, val_s])) == list(range(n_sensors))


def test_same_seed_gives_identical_history_and_weights(tiny_cfg, synthetic_table):
    m1, h1, _ = run(tiny_cfg, synthetic_table)
    m2, h2, _ = run(tiny_cfg, synthetic_table)
    assert h1 == h2
    assert all(torch.equal(a, b) for a, b in zip(m1.state_dict().values(), m2.state_dict().values()))


def test_different_seed_changes_the_run(tiny_cfg, synthetic_table):
    _, h1, _ = run(tiny_cfg, synthetic_table)
    _, h2, _ = run(replace(tiny_cfg, seed=1), synthetic_table)
    assert h1["loss"] != h2["loss"]


def test_alpha_error_pct():
    from pinn.config import Config

    assert alpha_error_pct(1.5e-5, Config()) == pytest.approx(50.0)
    assert alpha_error_pct(0.9e-5, Config()) == pytest.approx(10.0)


def test_save_run_writes_notebook_format_files(tmp_path, tiny_cfg, synthetic_table):
    model, hist, _ = run(tiny_cfg, synthetic_table)
    save_run(model, hist, tiny_cfg, tmp_path)
    lines = (tmp_path / "metrics.txt").read_text().splitlines()
    assert lines[0].startswith("alpha = ") and lines[0].endswith(" m^2/s")
    assert lines[1].startswith("alpha_error_pct = ")
    assert lines[2].startswith("val_rmse_K = ")
    assert lines[3] == "boundary_model = left Dirichlet heating + right/top/bottom convection+radiation"
    assert "epochs = 30+60 (fixed alpha + joint)" in lines
    history_header = (tmp_path / "history.csv").read_text().splitlines()[0]
    assert history_header == "epoch,loss,L_data,L_pde,L_bc,alpha,rmse_val"
    checkpoint = torch.load(tmp_path / "checkpoint.pt")
    assert set(checkpoint) == {"model_state", "alpha"}
    fresh = PINN(tiny_cfg)
    fresh.load_state_dict(checkpoint["model_state"])
    assert float(fresh.alpha().detach()) == pytest.approx(checkpoint["alpha"], rel=1e-6)
