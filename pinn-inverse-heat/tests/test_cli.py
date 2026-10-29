"""The command line: python -m pinn train --epochs N."""
import os
import subprocess
import sys
from pathlib import Path

import pytest

from helpers import write_comsol_csv
from pinn.cli import main, split_epochs

PROJECT = Path(__file__).resolve().parents[1]
EXPECTED_FILES = {
    "history.csv",
    "metrics.txt",
    "checkpoint.pt",
    "centerline_profile.png",
    "contour_t300.png",
    "sensor_fit.png",
    "training_dashboard.png",
}


def test_split_epochs_keeps_the_notebooks_one_to_three_ratio(tiny_cfg):
    from pinn.config import Config

    assert split_epochs(8000, Config()) == (2000, 6000)
    assert split_epochs(50, Config()) == (12, 38)
    assert split_epochs(1, Config()) == (0, 1)
    with pytest.raises(ValueError):
        split_epochs(0, Config())


def test_train_command_writes_history_metrics_checkpoint_and_figures(tmp_path, synthetic_table, tiny_cfg, capsys):
    data = tmp_path / "sensors.csv"
    write_comsol_csv(data, synthetic_table)
    out = tmp_path / "out"
    code = main(["train", "--epochs", "9", "--data", str(data), "--out", str(out), "--device", "cpu"], base_cfg=tiny_cfg)
    assert code == 0
    assert {p.name for p in out.iterdir()} == EXPECTED_FILES
    metrics = (out / "metrics.txt").read_text()
    assert "epochs = 3+6 (fixed alpha + joint)" in metrics
    printed = capsys.readouterr().out
    assert "short run" in printed  # 9 < 8000 epochs: must not look like the study result


def test_train_command_refuses_to_write_into_the_saved_results(tmp_path, synthetic_table, tiny_cfg):
    data = tmp_path / "sensors.csv"
    write_comsol_csv(data, synthetic_table)
    saved = PROJECT / "results" / "selected-run"
    with pytest.raises(SystemExit):
        main(["train", "--epochs", "3", "--data", str(data), "--out", str(saved), "--device", "cpu"], base_cfg=tiny_cfg)


def test_module_entry_point_runs_end_to_end(tmp_path, synthetic_table):
    """`python -m pinn train --epochs N` as a user runs it (default network size, CPU)."""
    data = tmp_path / "sensors.csv"
    write_comsol_csv(data, synthetic_table)
    out = tmp_path / "out"
    env = dict(os.environ, PYTHONPATH=str(PROJECT / "src"), MPLBACKEND="Agg")
    result = subprocess.run(
        [sys.executable, "-m", "pinn", "train", "--epochs", "6", "--data", str(data), "--out", str(out), "--device", "cpu"],
        capture_output=True,
        text=True,
        env=env,
        cwd=tmp_path,
        timeout=120,
    )
    assert result.returncode == 0, result.stderr
    assert {p.name for p in out.iterdir()} == EXPECTED_FILES
