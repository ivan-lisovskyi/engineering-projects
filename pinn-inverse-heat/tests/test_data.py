"""Reading the COMSOL sensor table."""
from pathlib import Path

import numpy as np
import pytest

from helpers import write_comsol_csv
from pinn.data import find_data_file, load_comsol_sensor_data, parse_sensor_column

PROJECT = Path(__file__).resolve().parents[1]


def test_parse_sensor_column():
    assert parse_sensor_column("Temperature (K), Point: (0.0115, 0.005)") == (0.0115, 0.005)


@pytest.mark.parametrize("name", ["table.csv", "table.csv.xz"])
def test_loader_round_trips_a_comsol_style_table(tmp_path, synthetic_table, name):
    path = tmp_path / name
    write_comsol_csv(path, synthetic_table)
    t, x, y, temps = load_comsol_sensor_data(path)
    t0, x0, y0, temps0 = synthetic_table
    assert temps.shape == temps0.shape == (21, 24)
    assert t.dtype == x.dtype == y.dtype == temps.dtype == np.float32
    np.testing.assert_allclose(t, t0, rtol=1e-6)
    np.testing.assert_allclose(x, x0, rtol=1e-5)
    np.testing.assert_allclose(y, y0, rtol=1e-5)
    np.testing.assert_allclose(temps, temps0, rtol=1e-6)


def test_find_data_file_prefers_csv_then_xz(tmp_path, monkeypatch, synthetic_table):
    monkeypatch.chdir(tmp_path)
    xz = tmp_path / "sensors_T.csv.xz"
    write_comsol_csv(xz, synthetic_table)
    assert find_data_file() == xz.resolve()
    csv_path = tmp_path / "sensors_T.csv"
    write_comsol_csv(csv_path, synthetic_table)
    assert find_data_file() == csv_path.resolve()


def test_find_data_file_uses_an_explicit_path_and_reports_a_missing_one(tmp_path):
    with pytest.raises(FileNotFoundError, match="missing.csv"):
        find_data_file(tmp_path / "missing.csv")


def test_the_committed_dataset_has_the_documented_shape():
    t, x, y, temps = load_comsol_sensor_data(PROJECT / "sensors_T.csv.xz")
    assert temps.shape == (6001, 140)
    assert x.shape == y.shape == (140,)
    assert (float(t[0]), float(t[-1])) == (0.0, 300.0)
