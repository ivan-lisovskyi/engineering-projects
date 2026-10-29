"""All constants of the notebook in one place.

Every field keeps the value of the matching constant in ``notebooks/final_notebook.ipynb``
(section 1). The notebook name is given in a comment where it differs.
"""
from dataclasses import dataclass

import torch


@dataclass(frozen=True)
class Config:
    # Plate size [m], final time [s]
    lx: float = 0.1  # Lx
    ly: float = 0.05  # Ly
    t_max: float = 300.0  # T_MAX

    # Temperature scale: the network predicts u = (T - T0) / dT
    t0: float = 293.15  # T0, ambient temperature [K]
    d_t: float = 30.0  # dT, heating amplitude [K]

    # Left boundary: fast time ramp and Gaussian shape along y
    tau: float = 0.5  # TAU, heating ramp time constant [s]

    # Material and boundary parameters
    rho: float = 7850.0  # RHO [kg/m^3]
    cp: float = 470.0  # CP [J/(kg K)]
    h_conv: float = 10.0  # H_CONV
    eps: float = 0.8  # EPS, emissivity
    sigma_sb: float = 5.670374419e-8  # SIGMA_SB, Stefan-Boltzmann constant

    # Reference alpha (only used for error reporting) and the starting guess
    alpha_true: float = 1.0e-5  # ALPHA_TRUE
    alpha_init: float = 1.5e-5  # ALPHA_INIT

    # Sampling and network choices
    t_pde_min: float = 0.5  # T_PDE_MIN
    n_ff: int = 32  # NFF, Fourier features per group
    sigma_xy: float = 3.0  # SIGMA_XY
    sigma_t: float = 2.5  # SIGMA_T
    early_frac: float = 0.4  # EARLY_FRAC
    early_pow: float = 3.0  # EARLY_POW
    width: int = 64  # PINN() default
    depth: int = 4  # PINN() default
    lr: float = 1e-3  # network learning rate (hard-coded in the notebook's train())

    # Two-stage training
    stage1: int = 2000  # STAGE1, epochs with alpha frozen
    stage2: int = 6000  # STAGE2, epochs with alpha trainable
    seed: int = 0  # SEED

    # Loss weights and alpha learning rate
    w_data: float = 1.0  # W_DATA
    w_pde: float = 1.0  # W_PDE
    w_bc: float = 0.05  # W_BC
    alpha_lr: float = 3e-4  # ALPHA_LR

    # Mini-batch sizes
    data_batch: int = 2048  # DATA_BATCH
    pde_batch: int = 2048  # PDE_BATCH
    bc_batch: int = 512  # BC_BATCH
    val_batch: int = 8000  # VAL_BATCH
    log_every: int = 200  # LOG_EVERY, history is recorded every N epochs
    print_every: int = 1000  # progress line every N epochs (notebook: hard-coded 1000)

    # "auto" = Apple MPS when available, otherwise CPU (the notebook's rule; never CUDA)
    device: str = "auto"

    @property
    def heat_sigma(self):
        """SIGMA in the notebook: width of the heated spot on the left edge [m]."""
        return self.ly / 3.0


def resolve_device(name="auto"):
    """Return a torch device string. "auto" follows the notebook: MPS if available, else CPU."""
    if name == "auto":
        return "mps" if torch.backends.mps.is_available() else "cpu"
    return name
