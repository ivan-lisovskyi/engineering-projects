"""The package must behave like the original notebook it was extracted from.

The model/training cells of notebooks/final_notebook.ipynb are executed unchanged in a scratch
namespace (on CPU, with the sensor table replaced by a small synthetic one) and compared with
pinn.train.train for the same seed: same loss history, same trained weights.
"""
import json
from dataclasses import replace
from pathlib import Path

import numpy as np
import torch

from pinn.train import train

NOTEBOOK = Path(__file__).resolve().parents[1] / "notebooks" / "final_notebook.ipynb"
# constants, data helpers, network, physics, sampling helpers, train()
CODE_CELLS = [2, 4, 8, 10, 13, 15]
SMALL = dict(data_batch=128, pde_batch=128, bc_batch=64, val_batch=200)


def load_notebook_namespace(table, monkeypatch, tmp_path):
    monkeypatch.chdir(tmp_path)  # the constants cell creates ./outputs_submission
    cells = json.loads(NOTEBOOK.read_text())["cells"]
    ns = {"__name__": "notebook"}
    for index in CODE_CELLS:
        source = "".join(cells[index]["source"])
        # drop the two lines that read the real CSV at import time; train() reloads it anyway
        source = "\n".join(
            line
            for line in source.splitlines()
            if not line.startswith("t_np, x_np, y_np, T_np = load_comsol_sensor_data()")
            and not line.startswith('print(f"timesteps=')
        )
        exec(compile(source, f"{NOTEBOOK.name}[cell {index}]", "exec"), ns)
    ns["DEVICE"] = "cpu"
    ns["load_comsol_sensor_data"] = lambda *a, **k: table
    for name, value in {
        "DATA_BATCH": SMALL["data_batch"],
        "PDE_BATCH": SMALL["pde_batch"],
        "BC_BATCH": SMALL["bc_batch"],
        "VAL_BATCH": SMALL["val_batch"],
        "LOG_EVERY": 3,
    }.items():
        ns[name] = value
    return ns


def test_package_reproduces_the_notebook_training_run(tiny_cfg, synthetic_table, monkeypatch, tmp_path):
    ns = load_notebook_namespace(synthetic_table, monkeypatch, tmp_path)
    nb_model, nb_hist, nb_data = ns["train"](stage1=3, stage2=3, verbose=False)

    # default-size network (width 64, depth 4), only the batches are shrunk
    cfg = replace(tiny_cfg, width=64, depth=4, n_ff=32, stage1=3, stage2=3, log_every=3, **SMALL)
    pk_model, pk_hist, pk_data = train(cfg, data=synthetic_table, verbose=False)

    assert nb_hist["epoch"] == pk_hist["epoch"] == [1, 3, 6]
    for key in nb_hist:
        assert nb_hist[key] == pk_hist[key], key
    nb_state, pk_state = nb_model.state_dict(), pk_model.state_dict()
    assert nb_state.keys() == pk_state.keys()
    assert all(torch.equal(nb_state[k], pk_state[k]) for k in nb_state)
    np.testing.assert_array_equal(nb_data[4], pk_data[4])  # train sensors
    np.testing.assert_array_equal(nb_data[5], pk_data[5])  # held-out sensors
