# Engineering projects — Ivan Lisovskyi

Selected academic projects from my BSc in Advanced Technology at the University of Twente. My interests sit at the intersection of physical systems, numerical modelling, software and practical technical problem-solving.

This collection brings together research-oriented C++ and Python work, a networked Java team project, and earlier programming exercises. Each folder explains the problem, implementation, contribution, setup and limitations.

## Explore the projects

| Project | Main technologies | What to look for |
| --- | --- | --- |
| [Clay platelet modelling — bachelor thesis](mercurydpm-clay/) | C++, MercuryDPM, PVFMM, Python, ParaView | Extending a scientific driver, periodic force/energy consistency, orientation diagnostics and multi-seed analysis |
| [Inverse heat transfer with a PINN](pinn-inverse-heat/) | Python, PyTorch, Jupyter | Combining sensor data and a differential equation; separating prediction accuracy from parameter identifiability |
| [Networked Skip-Bo](skipbo-java/) | Java, TCP sockets, JUnit | Client/server organisation, game rules, text UI, rule-based bots and tests; academic team project |
| [2D particle simulation](python-particle-simulation/) | Python, NumPy | Individual educational DEM implementation, contact forces and velocity-Verlet integration |
| [Terminal Snake](snake-cpp/) | C++, POSIX terminal APIs | Early paired programming exercise: game state, terminal input and collision logic |

For a modelling or engineering role, start with the thesis and PINN. For a software-oriented discussion, start with Skip-Bo and the Python particle simulation. Snake is included as an earlier learning project, not as production-quality software.

## Run and review

There is no single build command for this collection. Open a project folder and follow its README. The lightweight Python demo can be run locally; the larger research projects have separate environment and data requirements.

During portfolio preparation in September 2026:

- Java sources compiled and 132 non-network tests passed; socket tests were not rerun.
- Six Python particle-simulation checks and the small demo passed.
- Snake compiled on macOS; its historical runtime limitations remain documented.
- PINN data integrity and notebook syntax were checked; full neural-network training was not repeated.
- MercuryDPM analysis helpers passed small checks and the standalone source-template plot ran; the full C++ simulation was not rebuilt or rerun.

The individual READMEs distinguish newly checked behaviour from archived numerical results. Compiling a program or passing a smoke test is not scientific validation.

## Scope, authorship and reuse

This is a curated portfolio copy, not a claim that every dependency or team contribution was written by me. Teamwork, upstream foundations and recorded external/AI assistance are described in the relevant folders. Historical algorithms were preserved; new packaging helpers and tests are explicitly identified.

Original copyright notices remain in place. The MercuryDPM driver carries its upstream terms. No blanket open-source licence is applied to the collection. Coursework, team-contributor and dataset permissions must be confirmed before public release; until then this repository is intended for private review.

Personal documents, grades, university handouts, third-party research PDFs, credentials, compiled binaries and large raw simulation outputs are not included.

## Future improvements

Changes should keep the archived work traceable: describe the problem, add a reproducible example or test, and identify new results separately from old ones. Project-specific improvement ideas are listed in each README.
