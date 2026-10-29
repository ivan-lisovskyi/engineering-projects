"""What genuinely runs without MercuryDPM, PVFMM or the raw runs.

The C++ driver and the three raw-run analysis scripts cannot be exercised here
(no simulation build, no ~/MercuryRuns data). These checks cover the rest:
every script compiles, and plot_charge_templates.py runs end to end.
"""

import importlib.util
import py_compile
import subprocess
import sys

import pytest

SCRIPTS = [
    "plot_charge_templates.py",
    "plot_ph3_ph10_six_seed_clean.py",
    "stacking_analysis.py",
    "initial_overlap_check.py",
]


@pytest.mark.parametrize("name", SCRIPTS)
def test_script_compiles(project, tmp_path, name):
    py_compile.compile(
        str(project / "scripts" / name),
        cfile=str(tmp_path / (name + "c")),
        doraise=True,
    )


def test_charge_template_plot_writes_files(project, tmp_path):
    out_dir = tmp_path / "plots"
    result = subprocess.run(
        [sys.executable, str(project / "scripts" / "plot_charge_templates.py"),
         "--out-dir", str(out_dir)],
        capture_output=True,
        text=True,
        timeout=120,
    )
    assert result.returncode == 0, result.stderr
    stem = "charge_template_raw_and_corrected_vs_ph"
    produced = {p.name for p in out_dir.iterdir()}
    assert produced == {f"{stem}.png", f"{stem}.pdf", f"{stem}.svg"}
    assert (out_dir / f"{stem}.png").read_bytes().startswith(b"\x89PNG\r\n\x1a\n")
    assert (out_dir / f"{stem}.pdf").read_bytes().startswith(b"%PDF")
    assert b"<svg" in (out_dir / f"{stem}.svg").read_bytes()
    for path in out_dir.iterdir():
        assert path.stat().st_size > 1000


def _load_template_module(project):
    spec = importlib.util.spec_from_file_location(
        "plot_charge_templates", project / "scripts" / "plot_charge_templates.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_template_source_values_follow_documented_formulas(project):
    mod = _load_template_module(project)
    assert (mod.N_SILICA, mod.N_ALUMINA, mod.N_EDGE, mod.N_TOTAL) == (469, 469, 156, 1094)

    raw3, solver3, mean3 = mod.source_values(3.0)
    raw10, solver10, mean10 = mod.source_values(10.0)
    assert raw3 == pytest.approx(
        {"Silica-like face": -0.22, "Alumina-like face": 0.20, "Edge": 0.06})
    assert raw10 == pytest.approx(
        {"Silica-like face": -0.22, "Alumina-like face": -0.20, "Edge": -0.06})

    for raw, solver, weighted_mean in ((raw3, solver3, mean3), (raw10, solver10, mean10)):
        # corrected values = raw values minus the site-weighted mean ...
        for name in raw:
            assert solver[name] == pytest.approx(raw[name] - weighted_mean)
        # ... so the corrected template has zero total source over the 1094 sites
        total = (mod.N_SILICA * solver["Silica-like face"]
                 + mod.N_ALUMINA * solver["Alumina-like face"]
                 + mod.N_EDGE * solver["Edge"])
        assert total == pytest.approx(0.0, abs=1e-9)
