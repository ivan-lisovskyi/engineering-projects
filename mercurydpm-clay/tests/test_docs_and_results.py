"""The documentation pages must link to real files and quote the committed numbers."""

import csv
import re
import statistics

import pytest

PAGES = ["docs/GALLERY.md", "docs/METRICS.md", "docs/SCRIPTS.md"]
RESULTS = "figures/six_seed_clean"


def _text(project, rel):
    return (project / rel).read_text(encoding="utf-8")


def _rows(project, name):
    with (project / RESULTS / name).open(newline="") as f:
        return list(csv.DictReader(f))


@pytest.mark.parametrize("page", PAGES)
def test_page_exists_and_relative_links_resolve(project, page):
    path = project / page
    assert path.is_file(), f"{page} is missing"
    text = path.read_text(encoding="utf-8")
    links = re.findall(r"\]\(([^)\s]+)\)", text)
    assert links, f"{page} has no links"
    for link in links:
        if link.startswith(("http://", "https://", "#", "mailto:")):
            continue
        target = (path.parent / link.split("#")[0]).resolve()
        assert target.exists(), f"{page}: broken link {link}"


def test_gallery_embeds_every_committed_png(project):
    gallery = _text(project, "docs/GALLERY.md")
    for png in sorted((project / RESULTS).glob("*.png")):
        assert png.name in gallery, f"{png.name} is not in the gallery"


def test_gallery_quotes_numbers_from_committed_files(project):
    gallery = _text(project, "docs/GALLERY.md")
    final = _rows(project, "final_values_six_seed_summary.csv")
    stacks = _rows(project, "stacking_six_seed_summary.csv")
    expected = []
    for ph in ("3", "10"):
        for key, fmt in (("S", "{:.3f}"), ("S_z", "{:.3f}"), ("delta_U_lr", "{:.2f}")):
            mean = statistics.mean(float(r[key]) for r in final if r["pH"] == ph)
            expected.append(fmt.format(mean))
        mean = statistics.mean(float(r["platelets_in_stacks"]) for r in stacks if r["pH"] == ph)
        expected.append(f"{mean:.1f}" if ph == "3" else f"{mean:.2f}")
    overlaps = _rows(project, "initial_overlap_summary.csv")
    worst = max(overlaps, key=lambda r: float(r["max_pebble_penetration"]))
    expected += [worst["max_pebble_penetration"], f"{worst['penetration_pct_of_diameter']}%"]
    for token in expected:
        assert token in gallery, f"gallery does not quote {token}"


def test_means_txt_agrees_with_csv(project):
    """six_seed_means.txt is quoted by the README; make sure it matches the CSV."""
    text = _text(project, f"{RESULTS}/six_seed_means.txt")
    final = _rows(project, "final_values_six_seed_summary.csv")
    for ph in ("3", "10"):
        block = text.split(f"pH {ph}\n")[1].split("\n\n")[0]
        mean_s = float(re.search(r"S_tensor: mean=([-\d.e]+)", block).group(1))
        csv_s = statistics.mean(float(r["S"]) for r in final if r["pH"] == ph)
        assert mean_s == pytest.approx(csv_s, rel=1e-4)


def test_metrics_page_cites_code_locations(project):
    metrics = _text(project, "docs/METRICS.md")
    for needle in (
        "stacking_analysis.py", "plot_ph3_ph10_six_seed_clean.py",
        "initial_overlap_check.py", "clay_anisotropy_updated.cpp",
        "stack_components", "group_platelets", "stack_order",
        "computeAnisotropySnapshot", "largestEigenvalueSymmetric3x3",
        "overlap_stats", "not documented",
    ):
        assert needle in metrics, f"METRICS.md does not mention {needle}"


def test_licence_and_credit_text_untouched(project):
    readme = _text(project, "README.md")
    assert "[upstream licence](LICENSE-MercuryDPM.txt)" in readme
    assert "The driver's MercuryDPM copyright notice" in readme
    assert (project / "LICENSE-MercuryDPM.txt").is_file()
