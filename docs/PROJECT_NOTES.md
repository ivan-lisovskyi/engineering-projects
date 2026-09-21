# Notes about this collection

[Back to the projects](../README.md)

## Original work and later additions

These folders were prepared for GitHub in September 2026. The original algorithms were kept; the changes listed below concern setup, documentation and small checks.

| Project | What was added or changed for this copy |
| --- | --- |
| MercuryDPM | README and ignore rules; selected existing scripts, tables and plots. The C++ driver, four Python scripts and upstream licence are unchanged. |
| PINN | Data-checking/unpacking helper and ignore rules. The complete CSV is stored with lossless XZ compression. Notebook outputs and machine-specific metadata were cleared; cell text and code are unchanged. Unused document-generation packages were left out of the dependency list. |
| Skip-Bo | Maven folder layout, `pom.xml` and ignore rules. All Java source and tests are unchanged. |
| Python particle simulation | A reproducible demo, its settings, six tests, a dependency version and ignore rules. `PIE2023.py` is unchanged. The submitted report is dated July 2024 despite the source filename. |
| Snake | README and ignore rules. `snake.cpp` is unchanged. The original submission spells Ivan's surname “Lisovskiy”. |

README wording and organisation were revised on 18 September 2026. This did not change the programs or rerun the original research experiments. Each project lists its own build or test checks.

Personal documents, student records, course handouts, third-party research papers, compiled programs and large raw MercuryDPM output are not included. The PINN dataset and small saved result tables are included where described.

## Credit and permissions

This collection has no single open-source licence. Existing copyright notices and source credits remain in place.

- **MercuryDPM:** the driver retains the [upstream licence](../mercurydpm-clay/LICENSE-MercuryDPM.txt). Other thesis material and dependencies retain their own rights and terms.
- **PINN:** the input is a COMSOL-exported dataset. The `.mph` model is not included. The dataset is provided as input for this coursework example, not as an independently validated experimental dataset.
- **Skip-Bo:** this is shared team code. It is not affiliated with Mattel and contains no Mattel artwork.
- **Python particle simulation:** this is my individual coursework. No open-source licence has been added.
- **Snake:** this was a two-person project adapted from an external example. The original source links and recorded assistance are listed in its README. Public access does not change the terms that apply to that example.

I confirmed permission to publish the included coursework, shared code and data on 21 September 2026. This collection is available for portfolio review. It does not grant a blanket licence to reuse third-party code, data or project material.
