"""The thin notebook stays thin, and the original notebook stays byte-identical."""
import hashlib
import json
from pathlib import Path

NOTEBOOKS = Path(__file__).resolve().parents[1] / "notebooks"
# SHA-256 of final_notebook.ipynb as committed before the package was extracted
ORIGINAL_SHA256 = "ab613b79d9acf9abe17d237048b752b87ba91c45cf40bd8e513f4c73005c26cb"


def code_cells(name):
    cells = json.loads((NOTEBOOKS / name).read_text())["cells"]
    return [c for c in cells if c["cell_type"] == "code"]


def test_original_notebook_is_unchanged():
    assert hashlib.sha256((NOTEBOOKS / "final_notebook.ipynb").read_bytes()).hexdigest() == ORIGINAL_SHA256


def test_thin_notebook_only_calls_the_package():
    cells = code_cells("pinn_walkthrough.ipynb")
    source = "\n".join("".join(c["source"]) for c in cells)
    assert "from pinn." in source
    assert "def " not in source and "class " not in source
    assert len(source.splitlines()) < 60
    assert all(c["outputs"] == [] and c["execution_count"] is None for c in cells)  # committed without outputs
