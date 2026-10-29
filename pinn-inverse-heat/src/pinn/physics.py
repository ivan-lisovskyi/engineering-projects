"""Heat-equation residual and boundary conditions (notebook section 4)."""
import torch

from .model import to_hat


def derivs(model, h):
    """Return u and the derivatives needed for the heat equation.

    h contains normalised coordinates [xhat, yhat, that]. The returned
    derivatives are also with respect to these normalised coordinates.
    """
    h = h.requires_grad_(True)  # autograd must track h
    u = model(h)  # predicted normalised temperature

    # First derivatives du/dxhat, du/dyhat, du/dthat
    grad_outputs = torch.ones_like(u)
    grad_u = torch.autograd.grad(u, h, grad_outputs, create_graph=True)[0]
    u_xh, u_yh, u_th = grad_u[:, 0:1], grad_u[:, 1:2], grad_u[:, 2:3]

    # Second derivatives for diffusion d2u/dxhat2 and d2u/dyhat2
    u_xx = torch.autograd.grad(u_xh, h, torch.ones_like(u_xh), create_graph=True)[0][:, 0:1]
    u_yy = torch.autograd.grad(u_yh, h, torch.ones_like(u_yh), create_graph=True)[0][:, 1:2]
    return u, u_xh, u_yh, u_th, u_xx, u_yy


def pde_residual(model, alpha, h, cfg):
    """Heat-equation residual at interior points h."""
    # Chain-rule factors Lx, Ly and T_MAX convert derivatives back to physical units
    _, _, _, u_th, u_xx, u_yy = derivs(model, h)
    diffusion_term = u_xx / cfg.lx**2 + u_yy / cfg.ly**2
    return u_th.squeeze() - alpha * cfg.t_max * diffusion_term.squeeze()


def left_bc_u(y, t, cfg):
    """Known normalised temperature on the heated left boundary x=0."""
    # Time ramp: (1-exp(-t/tau)); spatial Gaussian: strongest at y=Ly/2
    return (1.0 - torch.exp(-t / cfg.tau)) * torch.exp(-(((y - cfg.ly / 2) / cfg.heat_sigma) ** 2))


def robin_bc_residual(model, alpha, side, coord, t, cfg):
    """Robin boundary residual on the right, top, or bottom side.

    side chooses the boundary. coord is y for the right side, and x for
    top/bottom. t is the time vector for those boundary points.
    """
    # Build physical coordinates for the selected boundary
    if side == "right":
        x, y = torch.full_like(coord, cfg.lx), coord
        sign, length = +1.0, cfg.lx
    elif side == "top":
        x, y = coord, torch.full_like(coord, cfg.ly)
        sign, length = +1.0, cfg.ly
    elif side == "bottom":
        x, y = coord, torch.zeros_like(coord)
        sign, length = -1.0, cfg.ly
    else:
        raise ValueError("side must be right/top/bottom")

    # Get temperature and boundary-normal derivative from the PINN
    u, u_xh, u_yh, _, _, _ = derivs(model, to_hat(x, y, t, cfg))
    dudn_hat = u_xh.squeeze() if side == "right" else u_yh.squeeze()
    dTdn = cfg.d_t * sign * dudn_hat / length  # convert du/dnhat to dT/dn
    temperature_K = cfg.t0 + cfg.d_t * u.squeeze()

    # Energy balance: conductive heat leaving the plate should match heat lost to air/radiation
    k = alpha * cfg.rho * cfg.cp
    conductive_flux_out = -k * dTdn
    heat_loss_out = cfg.h_conv * (temperature_K - cfg.t0) + cfg.eps * cfg.sigma_sb * (
        temperature_K**4 - cfg.t0**4
    )

    # Normalise the flux residual so it has a similar numerical size to other losses
    flux_scale = (
        torch.abs(k.detach()) * cfg.d_t / length
        + cfg.h_conv * cfg.d_t
        + 4 * cfg.eps * cfg.sigma_sb * (cfg.t0 + cfg.d_t) ** 3 * cfg.d_t
        + 1e-12
    )
    return (conductive_flux_out - heat_loss_out) / flux_scale
