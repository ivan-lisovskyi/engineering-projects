"""Fourier-feature MLP u_theta(x, y, t) with a trainable, positive diffusivity (notebook section 3)."""
import math

import torch
import torch.nn as nn


def to_hat(x, y, t, cfg):
    """Convert physical coordinates to the normalised network input h=[xhat,yhat,that].

    h is just a short name for the input tensor passed into the neural network.
    """
    # x, y, t are torch tensors with the same length N.
    # Output shape is [N, 3], which is what the neural network expects.
    return torch.stack([x / cfg.lx, y / cfg.ly, t / cfg.t_max], dim=1)


class PINN(nn.Module):
    def __init__(self, cfg):
        """Fourier-feature MLP used as u_theta(x,y,t).

        n_ff means number of Fourier features. width/depth set the MLP size.
        """
        super().__init__()
        n_ff, width, depth = cfg.n_ff, cfg.width, cfg.depth

        # B_xy and B_t are fixed random Fourier matrices.
        # They are buffers: saved with the model and moved to GPU/CPU, but not trained.
        self.register_buffer("B_xy", torch.randn(2, n_ff) * cfg.sigma_xy)  # maps [xhat,yhat]
        self.register_buffer("B_t", torch.randn(1, n_ff) * cfg.sigma_t)  # maps [that]

        # Four groups enter the MLP: sin(xy), cos(xy), sin(t), cos(t).
        in_dim = 4 * n_ff
        layers = [nn.Linear(in_dim, width), nn.Tanh()]
        for _ in range(depth - 1):
            layers += [nn.Linear(width, width), nn.Tanh()]
        layers += [nn.Linear(width, 1)]  # one output: normalised u
        self.net = nn.Sequential(*layers)

        # Store log(alpha), not alpha itself, so exp(log_alpha) is always positive.
        self.log_alpha = nn.Parameter(torch.tensor(math.log(cfg.alpha_init)))

    def features(self, h):
        """Build Fourier features from h with shape [N,3]."""
        xy = h[:, 0:2]  # first two columns: xhat,yhat
        t = h[:, 2:3]  # third column: that
        pxy = xy @ self.B_xy  # projected x-y coordinates
        pt = t @ self.B_t  # projected time coordinate
        return torch.cat([torch.sin(pxy), torch.cos(pxy), torch.sin(pt), torch.cos(pt)], dim=1)

    def forward(self, h):
        """Predict normalised temperature u from normalised input h."""
        return self.net(self.features(h))

    def alpha(self):
        """Return the physical diffusivity alpha in m^2/s."""
        return torch.exp(self.log_alpha)
