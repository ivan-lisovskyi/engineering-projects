"""The results tables are built only from the CSV/metrics files committed with the repository."""
import csv
import statistics
from pathlib import Path

import pytest

from pinn.results import (
    alpha_sensitivity_table,
    architecture_summary,
    load_saved_results,
    markdown_table,
    read_metrics,
)

PROJECT = Path(__file__).resolve().parents[1]


def read_rows(path):
    with open(path, newline="") as handle:
        return list(csv.DictReader(handle))


def test_read_metrics_parses_the_selected_run():
    m = read_metrics(PROJECT / "results" / "selected-run" / "metrics.txt")
    assert m["alpha"] == pytest.approx(1.085367e-05)
    assert m["alpha_error_pct"] == pytest.approx(8.537)
    assert m["val_rmse_K"] == pytest.approx(0.395640)


def test_alpha_sensitivity_table_matches_the_csv_and_the_readme_range():
    saved = load_saved_results(PROJECT)
    table = alpha_sensitivity_table(saved["alpha_sensitivity"])
    rows = read_rows(PROJECT / "outputs_alpha_sensitivity" / "alpha_sensitivity_summary.csv")
    assert len(table) == len(rows) == 6
    errors = [float(r["alpha_error_pct"]) for r in rows]
    assert min(errors) == pytest.approx(12.269)
    assert max(errors) == pytest.approx(50.458)
    assert max(float(r["val_rmse_K"]) for r in rows) < 1.0  # "temperature RMSE stays below 1 K"
    assert list(table["alpha_init"]) == sorted(float(r["ALPHA_INIT"]) for r in rows)


def test_architecture_summary_matches_an_independent_calculation():
    saved = load_saved_results(PROJECT)
    summary = architecture_summary(saved["comparison"]).set_index("variant")
    rows = read_rows(PROJECT / "outputs_comparison" / "comparison_table.csv")
    assert set(summary.index) == {"baseline (plain MLP)", "bigger MLP", "Fourier features"}
    for variant in summary.index:
        mine = [r for r in rows if r["variant"] == variant]
        assert summary.loc[variant, "runs"] == len(mine) == 4
        rmse = [float(r["val_rmse_K"]) for r in mine]
        err = [float(r["alpha_error_pct"]) for r in mine]
        assert summary.loc[variant, "mean_rmse_K"] == pytest.approx(statistics.mean(rmse))
        assert summary.loc[variant, "std_rmse_K"] == pytest.approx(statistics.stdev(rmse))
        assert summary.loc[variant, "mean_alpha_error_pct"] == pytest.approx(statistics.mean(err))
        assert summary.loc[variant, "best_alpha_error_pct"] == pytest.approx(min(err))
        assert summary.loc[variant, "min_alpha_x1e5"] == pytest.approx(1e5 * min(float(r["alpha"]) for r in mine))
        assert summary.loc[variant, "max_alpha_x1e5"] == pytest.approx(1e5 * max(float(r["alpha"]) for r in mine))


def test_markdown_table_formats_columns():
    saved = load_saved_results(PROJECT)
    text = markdown_table(architecture_summary(saved["comparison"]), {"mean_rmse_K": "{:.3f}"})
    lines = text.splitlines()
    assert lines[0].startswith("| variant |") and lines[1].startswith("|---")
    assert len(lines) == 2 + 3
    assert all(line.count("|") == lines[0].count("|") for line in lines)


def _short_exp(value):
    mantissa, exponent = f"{value:.3e}".split("e")
    return f"{mantissa}e{int(exponent)}"


def test_readme_tables_agree_with_the_saved_files():
    """Every number in the README's results tables is the committed value, rounded."""
    readme = (PROJECT / "README.md").read_text().splitlines()
    saved = load_saved_results(PROJECT)

    for row in saved["alpha_sensitivity"].itertuples():
        lines = [ln for ln in readme if ln.startswith("|") and f"| {_short_exp(row.alpha)} |" in ln]
        assert len(lines) == 1, row
        assert f"{row.alpha_error_pct:.1f}%" in lines[0] and f"{row.val_rmse_K:.3f}" in lines[0]

    labels = {"baseline (plain MLP)": "plain MLP", "bigger MLP": "bigger MLP", "Fourier features": "Fourier features"}
    for row in architecture_summary(saved["comparison"]).itertuples():
        lines = [ln for ln in readme if ln.startswith(f"| {labels[row.variant]} |")]
        assert len(lines) == 1, row
        expected = [
            f"{row.mean_rmse_K:.3f}",
            f"{row.std_rmse_K:.3f}",
            f"{row.mean_alpha_error_pct:.1f}%",
            f"{row.best_alpha_error_pct:.1f}%",
            f"{row.min_alpha_x1e5:.2f} – {row.max_alpha_x1e5:.2f}",
        ]
        assert all(text in lines[0] for text in expected), (row, lines[0])

    selected = saved["selected_run"]
    text = "\n".join(readme)
    assert f"`{selected['alpha_error_pct']:.3f}%`" in text
    assert f"`{selected['val_rmse_K']:.6f} K`" in text
