"""Command line: ``python -m pinn train --epochs 50`` and ``python -m pinn results``."""
import argparse
from dataclasses import replace
from pathlib import Path

from .config import Config, resolve_device
from .data import PROJECT_DIR, find_data_file

# Folders holding the saved study results; the CLI never writes into them.
PROTECTED = ("results", "outputs_alpha_sensitivity", "outputs_comparison")


def split_epochs(total, cfg):
    """Split a total epoch count between the two stages in the notebook's ratio (2000 : 6000)."""
    if total < 1:
        raise ValueError("--epochs must be at least 1")
    stage1 = int(total * cfg.stage1 / (cfg.stage1 + cfg.stage2))
    return stage1, total - stage1


def _check_output_dir(out):
    resolved = Path(out).resolve()
    for name in PROTECTED:
        protected = (PROJECT_DIR / name).resolve()
        if resolved == protected or protected in resolved.parents:
            raise SystemExit(f"Refusing to write into the saved results folder {protected}; choose another --out.")


def build_parser():
    parser = argparse.ArgumentParser(prog="python -m pinn", description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    train = sub.add_parser("train", help="train the PINN and write history, metrics, checkpoint and figures")
    train.add_argument("--epochs", type=int, default=None,
                       help="total epochs, split 1:3 between fixed-alpha and joint training "
                            "(default: the notebook's 2000 + 6000)")
    train.add_argument("--data", default=None, help="sensor table (default: sensors_T.csv or sensors_T.csv.xz)")
    train.add_argument("--out", default="outputs_cli", help="output folder (default: outputs_cli, git-ignored)")
    train.add_argument("--device", default=None, help="auto (MPS if available, else CPU), cpu or mps")
    train.add_argument("--seed", type=int, default=None, help="random seed (default: the notebook's 0)")

    sub.add_parser("results", help="print the saved-results tables (built from the committed CSV files)")
    return parser


def _train(args, base_cfg):
    import matplotlib

    matplotlib.use("Agg")  # figures are written to files, no window
    from .plots import save_figures
    from .train import alpha_error_pct, save_run, train

    cfg = base_cfg
    full_length = cfg.stage1 + cfg.stage2
    if args.epochs is not None:
        stage1, stage2 = split_epochs(args.epochs, cfg)
        log_every = max(1, min(cfg.log_every, args.epochs // 10))
        cfg = replace(cfg, stage1=stage1, stage2=stage2, log_every=log_every, print_every=5 * log_every)
    if args.device is not None:
        cfg = replace(cfg, device=args.device)
    if args.seed is not None:
        cfg = replace(cfg, seed=args.seed)

    _check_output_dir(args.out)
    data_path = find_data_file(args.data)
    print(f"device: {resolve_device(cfg.device)} | data: {data_path}")
    print(f"epochs: {cfg.stage1} (alpha fixed) + {cfg.stage2} (alpha trainable) = {cfg.stage1 + cfg.stage2}")

    from .data import load_comsol_sensor_data

    model, hist, data = train(cfg, data=load_comsol_sensor_data(data_path))
    save_run(model, hist, cfg, args.out)
    figures = save_figures(model, hist, data, cfg, args.out)

    alpha = float(model.alpha().detach())
    print(f"\nwrote history.csv, metrics.txt, checkpoint.pt and {len(figures)} figures to {args.out}/")
    print(f"alpha = {alpha:.6e} m^2/s | alpha error {alpha_error_pct(alpha, cfg):.3f}% "
          f"| val RMSE {hist['rmse_val'][-1]:.6f} K")
    if cfg.stage1 + cfg.stage2 < full_length:
        print(f"NOTE: short run ({cfg.stage1 + cfg.stage2} of {full_length} epochs): "
              "a reproducibility check, not the saved study result.")
    return 0


def main(argv=None, base_cfg=None):
    args = build_parser().parse_args(argv)
    if args.command == "train":
        return _train(args, base_cfg or Config())
    if args.command == "results":
        from .results import render_all

        print(render_all())
        return 0
    return 2
