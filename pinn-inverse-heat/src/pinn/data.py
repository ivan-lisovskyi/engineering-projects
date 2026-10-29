"""Load the exported COMSOL sensor table (notebook section 2)."""
from pathlib import Path

import numpy as np
import pandas as pd

PROJECT_DIR = Path(__file__).resolve().parents[2]


def find_data_file(explicit=None):
    """Return the sensor table to read.

    An explicit path must exist. Otherwise the first of these that exists is used:
    ./sensors_T.csv, ./sensors_T.csv.xz, then the same two names next to the package's project
    folder. pandas reads the .xz file directly, so ``prepare_data.py`` is optional.
    """
    if explicit is not None:
        path = Path(explicit)
        if not path.is_file():
            raise FileNotFoundError(f"Sensor table not found: {path}")
        return path.resolve()
    for folder in (Path.cwd(), PROJECT_DIR):
        for name in ("sensors_T.csv", "sensors_T.csv.xz"):
            if (folder / name).is_file():
                return (folder / name).resolve()
    raise FileNotFoundError(
        "sensors_T.csv(.xz) not found in the current folder or the project folder; "
        "run from the project folder or pass --data."
    )


def parse_sensor_column(column_name):
    """Read one COMSOL sensor header and return its (x, y) position."""
    # Example header: "Temperature (K), Point: (0.0115, 0.005)".
    # Only the text after "Point:" is needed here.
    coordinate_text = column_name.split("Point:", 1)[1].strip()
    coordinate_text = coordinate_text.strip("()")
    x_text, y_text = coordinate_text.split(",")
    return float(x_text), float(y_text)


def load_comsol_sensor_data(csv_path=None):
    """Load the exported COMSOL sensor table.

    Returns:
    - time_values: shape [Nt]
    - sensor_x_positions, sensor_y_positions: shape [Ns]
    - temperature_values: shape [Nt, Ns], in Kelvin
    """
    if csv_path is None:
        csv_path = find_data_file()
    # COMSOL puts four metadata rows before the actual table header
    sensor_table = pd.read_csv(csv_path, skiprows=4)

    # First column: simulation time in seconds
    time_values = sensor_table.iloc[:, 0].to_numpy(dtype=np.float32)

    # Remaining columns: one temperature trace per sensor point
    sensor_x_positions = []
    sensor_y_positions = []
    for column_name in sensor_table.columns[1:]:
        x_position, y_position = parse_sensor_column(column_name)
        sensor_x_positions.append(x_position)
        sensor_y_positions.append(y_position)

    # Matrix layout: rows are time steps, columns are sensor locations.
    temperature_values = sensor_table.iloc[:, 1:].to_numpy(dtype=np.float32)

    return (
        time_values,
        np.array(sensor_x_positions, dtype=np.float32),
        np.array(sensor_y_positions, dtype=np.float32),
        temperature_values,
    )
