"""Plot helpers: numerical content of predict_delta_T and that every figure builds and saves."""
from pathlib import Path

import numpy as np
import pytest

from helpers import ExactMode
from pinn.plots import (
    closest_sensor,
    plot_centerline,
    plot_contours,
    plot_sensor_fit,
    plot_sensor_layout,
    plot_training_dashboard,
    predict_delta_T,
    save_figures,
)
from pinn.train import train

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"


@pytest.fixture(scope="module")
def trained():
    from conftest import tiny_config  # same tiny setup as the fixture, usable at module scope
    from helpers import make_synthetic_table

    cfg = tiny_config()
    table = make_synthetic_table(cfg)
    model, hist, data = train(cfg, data=table, verbose=False)
    return cfg, model, hist, data


def test_predict_delta_T_converts_u_to_kelvin_rise(tiny_cfg):
    exact = ExactMode(tiny_cfg, tiny_cfg.alpha_true)
    x = np.array([0.02, 0.05, 0.08], dtype=np.float32)
    y = np.array([0.01, 0.025, 0.04], dtype=np.float32)
    t = np.array([0.0, 100.0, 300.0], dtype=np.float32)
    lam = tiny_cfg.alpha_true * np.pi**2 * (1 / tiny_cfg.lx**2 + 1 / tiny_cfg.ly**2)
    expected = tiny_cfg.d_t * np.exp(-lam * t) * np.sin(np.pi * x / tiny_cfg.lx) * np.sin(np.pi * y / tiny_cfg.ly)
    got = predict_delta_T(exact, x, y, t, tiny_cfg)
    assert got.shape == x.shape and got.dtype == np.float32
    np.testing.assert_allclose(got, expected, rtol=1e-4, atol=1e-4)


def test_predict_delta_T_handles_more_points_than_one_batch(tiny_cfg):
    exact = ExactMode(tiny_cfg, tiny_cfg.alpha_true)
    n = 250_000  # larger than the 200 000-point batch
    x = np.full(n, 0.05, dtype=np.float32)
    got = predict_delta_T(exact, x, np.full(n, 0.025, np.float32), np.zeros(n, np.float32), tiny_cfg)
    assert got.shape == (n,)
    assert np.allclose(got, tiny_cfg.d_t, rtol=1e-5)


def test_closest_sensor():
    x = np.array([0.0, 0.05, 0.1], dtype=np.float32)
    y = np.array([0.0, 0.025, 0.05], dtype=np.float32)
    assert closest_sensor(x, y, 0.049, 0.02) == 1
    assert closest_sensor(x, y, 0.2, 0.2) == 2


def test_every_figure_builds(trained):
    cfg, model, hist, data = trained
    t, x, y, temps, train_s, val_s = data
    figs = [
        plot_sensor_layout(x, y, cfg, train_s, val_s),
        plot_training_dashboard(hist, cfg),
        plot_sensor_fit(model, (t, x, y, temps), cfg),
        plot_centerline(model, (t, x, y, temps), cfg),
        plot_contours(model, cfg, times=(5.0, 300.0)),
    ]
    assert [len(f.axes) for f in figs][:4] == [1, 4, 2, 1]
    assert len(figs[4].axes) >= 3  # two panels and a colour bar
    import matplotlib.pyplot as plt

    for fig in figs:
        plt.close(fig)


def test_save_figures_writes_pngs_named_like_the_saved_run(trained, tmp_path):
    cfg, model, hist, data = trained
    paths = save_figures(model, hist, data, cfg, tmp_path)
    assert sorted(Path(p).name for p in paths) == [
        "centerline_profile.png",
        "contour_t300.png",
        "sensor_fit.png",
        "training_dashboard.png",
    ]
    for p in paths:
        assert Path(p).read_bytes()[:8] == PNG_MAGIC
