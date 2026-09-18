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

- **MercuryDPM:** the driver retains the [upstream licence](../mercurydpm-clay/LICENSE-MercuryDPM.txt). This does not give new rights to all thesis material or dependencies. Relevant thesis/publication permissions still need checking.
- **PINN:** the input is a COMSOL-exported dataset. The creator of the underlying model and permission to publish its data have not been confirmed. The `.mph` model is not included.
- **Skip-Bo:** this is shared team code. Teammate permission and any course restrictions need checking. It is not affiliated with Mattel and contains no Mattel artwork.
- **Python particle simulation:** no open-source licence has been added. Any course publication conditions still apply.
- **Snake:** this was a two-person project adapted from an external example. Teammate permission and the terms for the adapted code need checking. The original source links and recorded assistance are listed in its README.

For these reasons the repository remains private. Making it public, or sharing it with an employer, should follow the relevant permission checks.
