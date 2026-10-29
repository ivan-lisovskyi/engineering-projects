"""Physics checks on exactly known fields (no training involved)."""
import math

import pytest
import torch
import torch.nn as nn

from pinn.config import Config
from pinn.model import PINN, to_hat
from pinn.physics import left_bc_u, pde_residual, robin_bc_residual

DT64 = torch.float64


class ExactMode(nn.Module):
    """u(xhat, yhat, that) for the exact solution exp(-lam t) sin(pi x/Lx) sin(pi y/Ly)."""

    def __init__(self, cfg, alpha):
        super().__init__()
        self.t_max = cfg.t_max
        self.lam = alpha * math.pi**2 * (1 / cfg.lx**2 + 1 / cfg.ly**2)

    def forward(self, h):
        xh, yh, th = h[:, 0:1], h[:, 1:2], h[:, 2:3]
        return torch.exp(-self.lam * self.t_max * th) * torch.sin(math.pi * xh) * torch.sin(math.pi * yh)


class Linear(nn.Module):
    """u = c * coordinate of one hat axis (to check the Robin flux bookkeeping by hand)."""

    def __init__(self, axis, c):
        super().__init__()
        self.axis, self.c = axis, c

    def forward(self, h):
        a = h[:, self.axis : self.axis + 1]
        # The 0 * a**2 term keeps a second-derivative graph alive (a purely linear u has none);
        # it does not change u or du/da.
        return self.c * a + 0.0 * a**2


def grid_points(cfg, n=7):
    xs = torch.linspace(0.1, 0.9, n, dtype=DT64) * cfg.lx
    ys = torch.linspace(0.1, 0.9, n, dtype=DT64) * cfg.ly
    ts = torch.linspace(0.05, 1.0, n, dtype=DT64) * cfg.t_max
    x, y, t = torch.meshgrid(xs, ys, ts, indexing="ij")
    return to_hat(x.ravel(), y.ravel(), t.ravel(), cfg)


def test_pde_residual_vanishes_for_exact_solution_with_true_alpha():
    cfg = Config()
    field = ExactMode(cfg, cfg.alpha_true)
    res = pde_residual(field, torch.tensor(cfg.alpha_true, dtype=DT64), grid_points(cfg), cfg)
    assert res.abs().max() < 1e-9


def test_pde_residual_is_large_for_wrong_alpha():
    cfg = Config()
    field = ExactMode(cfg, cfg.alpha_true)
    res = pde_residual(field, torch.tensor(2 * cfg.alpha_true, dtype=DT64), grid_points(cfg), cfg)
    assert res.abs().max() > 1.0


def test_alpha_is_recovered_from_an_exact_field_through_log_alpha():
    """Tiny inverse problem: known field, unknown alpha = exp(log_alpha), start 50 % too high."""
    cfg = Config()
    field = ExactMode(cfg, cfg.alpha_true)
    h = grid_points(cfg)
    log_alpha = nn.Parameter(torch.tensor(math.log(cfg.alpha_init), dtype=DT64))
    opt = torch.optim.Adam([log_alpha], lr=0.05)
    for _ in range(300):
        opt.zero_grad()
        loss = torch.mean(pde_residual(field, torch.exp(log_alpha), h.clone(), cfg) ** 2)
        loss.backward()
        opt.step()
    recovered = float(torch.exp(log_alpha).detach())
    assert abs(recovered - cfg.alpha_true) / cfg.alpha_true < 0.01


def test_left_boundary_ramp_and_gaussian():
    cfg = Config()
    y_mid = torch.tensor([cfg.ly / 2], dtype=DT64)
    zero, late = torch.tensor([0.0], dtype=DT64), torch.tensor([100 * cfg.tau], dtype=DT64)
    assert float(left_bc_u(y_mid, zero, cfg)) == 0.0
    assert float(left_bc_u(y_mid, late, cfg)) == pytest.approx(1.0, abs=1e-12)
    off_centre = torch.tensor([cfg.ly / 2 + cfg.heat_sigma], dtype=DT64)
    assert float(left_bc_u(off_centre, late, cfg)) == pytest.approx(math.exp(-1.0), abs=1e-12)


@pytest.mark.parametrize("side", ["right", "top", "bottom"])
def test_robin_residual_is_zero_for_a_plate_at_ambient(side):
    cfg = Config()
    coord = torch.tensor([0.2, 0.5, 0.8], dtype=DT64) * (cfg.ly if side == "right" else cfg.lx)
    t = torch.tensor([10.0, 100.0, 250.0], dtype=DT64)
    res = robin_bc_residual(Linear(0, 0.0), torch.tensor(cfg.alpha_true, dtype=DT64), side, coord, t, cfg)
    assert torch.allclose(res, torch.zeros_like(res), atol=1e-12)


@pytest.mark.parametrize(
    "side, axis, hat_value, sign, length",
    [("right", 0, 1.0, +1.0, 0.1), ("top", 1, 1.0, +1.0, 0.05), ("bottom", 1, 0.0, -1.0, 0.05)],
)
def test_robin_residual_matches_a_hand_computed_energy_balance(side, axis, hat_value, sign, length):
    cfg = Config()
    c, alpha = 0.3, cfg.alpha_true
    u_boundary = c * hat_value
    temperature = cfg.t0 + cfg.d_t * u_boundary
    k = alpha * cfg.rho * cfg.cp
    d_t_dn = cfg.d_t * sign * c / length  # outward normal derivative of T
    conductive_out = -k * d_t_dn
    loss_out = cfg.h_conv * (temperature - cfg.t0) + cfg.eps * cfg.sigma_sb * (temperature**4 - cfg.t0**4)
    scale = (
        k * cfg.d_t / length
        + cfg.h_conv * cfg.d_t
        + 4 * cfg.eps * cfg.sigma_sb * (cfg.t0 + cfg.d_t) ** 3 * cfg.d_t
        + 1e-12
    )
    expected = (conductive_out - loss_out) / scale

    coord = torch.tensor([0.4 * (cfg.ly if side == "right" else cfg.lx)], dtype=DT64)
    t = torch.tensor([50.0], dtype=DT64)
    res = robin_bc_residual(Linear(axis, c), torch.tensor(alpha, dtype=DT64), side, coord, t, cfg)
    assert float(res) == pytest.approx(expected, rel=1e-9)


def test_robin_residual_rejects_unknown_side():
    cfg = Config()
    with pytest.raises(ValueError):
        robin_bc_residual(Linear(0, 0.0), torch.tensor(1e-5), "left", torch.zeros(1), torch.zeros(1), cfg)


def test_model_shapes_and_positive_alpha(tiny_cfg):
    torch.manual_seed(0)
    model = PINN(tiny_cfg)
    h = to_hat(torch.rand(5) * tiny_cfg.lx, torch.rand(5) * tiny_cfg.ly, torch.rand(5) * tiny_cfg.t_max, tiny_cfg)
    assert h.shape == (5, 3)
    assert float(h.max()) <= 1.0 and float(h.min()) >= 0.0
    assert model(h).shape == (5, 1)
    assert float(model.alpha()) == pytest.approx(tiny_cfg.alpha_init, rel=1e-5)
    model.log_alpha.data.fill_(-50.0)
    assert float(model.alpha()) > 0.0


def test_fourier_matrices_are_buffers_not_parameters(tiny_cfg):
    model = PINN(tiny_cfg)
    names = {n for n, _ in model.named_parameters()}
    assert "log_alpha" in names
    assert not any(n.startswith("B_") for n in names)
    assert {"B_xy", "B_t"} <= set(model.state_dict())
