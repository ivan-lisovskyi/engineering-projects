# Engineering projects — Ivan Lisovskyi

I'm Ivan, a recent Advanced Technology graduate from the University of Twente. These are five projects I worked on during my degree. I like using code to understand a physical problem, check a calculation or make something work. I'm interested in technical roles where programming is one part of the job.

My thesis and heat-transfer project are the main examples of my modelling work. The games show a different side: working with other students, keeping track of game state and handling user input. I also included an earlier Python particle simulation because it shows the basic mechanics behind the larger simulation tools I used later.

Each project has its own README with a short explanation, setup instructions and notes about what still needs work. Some were individual projects and some were team assignments; I have kept that distinction in the descriptions.

## Explore the projects

| Project | Main technologies | What to look for |
| --- | --- | --- |
| [Clay platelet modelling — bachelor thesis](mercurydpm-clay/) | C++, MercuryDPM, PVFMM, Python, ParaView | Changing an existing simulation, checking its energy calculation and comparing how clay platelets form groups |
| [Inverse heat transfer with a PINN](pinn-inverse-heat/) | Python, PyTorch, Jupyter | Estimating a material property from temperature data and checking how much the answer depends on the starting guess |
| [Networked Skip-Bo](skipbo-java/) | Java, TCP sockets, JUnit | A team-built card game with a server, terminal clients, rule-based bots and tests |
| [2D particle simulation](python-particle-simulation/) | Python, NumPy | A small program I wrote to calculate particle motion under gravity and contact forces |
| [Terminal Snake](snake-cpp/) | C++, POSIX terminal APIs | An early two-person project with keyboard input, a game loop and collision checks |

If you only have a few minutes, start with the thesis or the heat-transfer project. For a closer look at Java and testing, open Skip-Bo. Snake is an earlier learning project, so it is further down the list.

## Run and review

The projects run separately. Open a folder and follow its README. The Python particle demo is a small first example to try. The research projects need more setup; you can also read their saved results without running a simulation or training a model.

During portfolio preparation in September 2026:

- Java sources compiled and 132 non-network tests passed; socket tests were not rerun.
- Six Python particle-simulation checks and the small demo passed.
- Snake compiled on macOS; known gameplay issues are listed in its README.
- PINN data integrity and notebook syntax were checked; full neural-network training was not repeated.
- Small checks of the MercuryDPM analysis code passed, and the charge-template plotting example ran; the full C++ simulation was not rebuilt or rerun.

These are checks of the files in this repository. They do not mean that every feature was tested or that the physical models were validated. Each README says which results came from the original project and what was checked later.

## Scope, authorship and reuse

I kept the original project code and added setup files, documentation and a few small checks where useful. Those additions are labelled in the project READMEs. The descriptions also name team work, existing code I built on, and recorded use of outside examples or AI tools.

The original copyright notices are still included. The MercuryDPM driver has its own licence; there is no single open-source licence for this whole collection. This repository is kept private while permissions for team code, coursework and data are checked.

I left out personal documents, grades, course handouts, research papers, compiled programs and large raw simulation files.

## Future improvements

The next improvements are listed in each project. If I revise the code, I will keep the changes and new results separate from the original submission, so it stays clear what was done during the degree and what was added later.
