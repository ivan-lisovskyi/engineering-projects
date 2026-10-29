"""Results tables built only from the files committed with the repository.

Inputs: results/selected-run/metrics.txt, outputs_alpha_sensitivity/alpha_sensitivity_summary.csv
and outputs_comparison/comparison_table.csv. Nothing is trained or simulated here
(notebook section 8 does the same aggregation).
"""
from pathlib import Path

import pandas as pd

from .data import PROJECT_DIR

SELECTED_METRICS = Path("results/selected-run/metrics.txt")
ALPHA_SENSITIVITY_CSV = Path("outputs_alpha_sensitivity/alpha_sensitivity_summary.csv")
COMPARISON_CSV = Path("outputs_comparison/comparison_table.csv")


def read_metrics(path):
    """Parse a metrics.txt written by the notebook (``key = value [unit]`` per line)."""
    metrics = {}
    for line in Path(path).read_text().splitlines():
        if " = " not in line:
            continue
        key, value = line.split(" = ", 1)
        try:
            metrics[key.strip()] = float(value.split()[0])
        except ValueError:
            metrics[key.strip()] = value.strip()
    return metrics


def load_saved_results(project_dir=PROJECT_DIR):
    """Read the three committed result files; raise a clear error if one is missing."""
    project_dir = Path(project_dir)
    for relative in (SELECTED_METRICS, ALPHA_SENSITIVITY_CSV, COMPARISON_CSV):
        if not (project_dir / relative).is_file():
            raise FileNotFoundError(f"Missing saved result file: {project_dir / relative}")
    return {
        "selected_run": read_metrics(project_dir / SELECTED_METRICS),
        "alpha_sensitivity": pd.read_csv(project_dir / ALPHA_SENSITIVITY_CSV),
        "comparison": pd.read_csv(project_dir / COMPARISON_CSV),
    }


def alpha_sensitivity_table(frame):
    """One row per saved starting value of alpha (Fourier-feature model), sorted by start."""
    table = frame[["ALPHA_INIT", "alpha", "alpha_error_pct", "val_rmse_K"]].copy()
    table = table.rename(columns={"ALPHA_INIT": "alpha_init"})
    return table.sort_values("alpha_init").reset_index(drop=True)


def architecture_summary(frame):
    """One row per architecture: temperature fit and the spread of recovered alpha."""
    summary = frame.groupby("variant", sort=False).agg(
        runs=("alpha", "count"),
        mean_rmse_K=("val_rmse_K", "mean"),
        std_rmse_K=("val_rmse_K", "std"),
        mean_alpha_error_pct=("alpha_error_pct", "mean"),
        best_alpha_error_pct=("alpha_error_pct", "min"),
        min_alpha=("alpha", "min"),
        max_alpha=("alpha", "max"),
    )
    summary["min_alpha_x1e5"] = summary.pop("min_alpha") * 1e5
    summary["max_alpha_x1e5"] = summary.pop("max_alpha") * 1e5
    return summary.reset_index()


def markdown_table(frame, formats=None):
    """Render a DataFrame as a GitHub-flavoured Markdown table (no extra dependency)."""
    formats = formats or {}

    def cell(column, value):
        if column in formats:
            return formats[column].format(value)
        return f"{value:.4g}" if isinstance(value, float) else str(value)

    columns = list(frame.columns)
    lines = ["| " + " | ".join(columns) + " |", "|" + "|".join("---" for _ in columns) + "|"]
    for _, row in frame.iterrows():
        lines.append("| " + " | ".join(cell(c, row[c]) for c in columns) + " |")
    return "\n".join(lines)


def render_all(project_dir=PROJECT_DIR):
    """Return the three tables as Markdown text, ready for a README."""
    saved = load_saved_results(project_dir)
    sel = saved["selected_run"]
    selected = pd.DataFrame(
        [{"alpha": sel["alpha"], "alpha_error_pct": sel["alpha_error_pct"], "val_rmse_K": sel["val_rmse_K"]}]
    )
    parts = [
        "Selected run (results/selected-run/metrics.txt)",
        markdown_table(selected, {"alpha": "{:.6e}", "alpha_error_pct": "{:.3f}", "val_rmse_K": "{:.6f}"}),
        "Six saved starting-value experiments (outputs_alpha_sensitivity/alpha_sensitivity_summary.csv)",
        markdown_table(
            alpha_sensitivity_table(saved["alpha_sensitivity"]),
            {"alpha_init": "{:.1e}", "alpha": "{:.3e}", "alpha_error_pct": "{:.1f}", "val_rmse_K": "{:.3f}"},
        ),
        "Twelve saved architecture-comparison runs, summarised per variant (outputs_comparison/comparison_table.csv)",
        markdown_table(
            architecture_summary(saved["comparison"]),
            {
                "mean_rmse_K": "{:.3f}",
                "std_rmse_K": "{:.3f}",
                "mean_alpha_error_pct": "{:.1f}",
                "best_alpha_error_pct": "{:.1f}",
                "min_alpha_x1e5": "{:.2f}",
                "max_alpha_x1e5": "{:.2f}",
            },
        ),
    ]
    return "\n\n".join(parts)
