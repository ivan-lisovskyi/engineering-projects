"""September 2026 smoke/regression checks, not physical validation."""

from contextlib import chdir, redirect_stdout
from io import StringIO
from pathlib import Path
import tempfile
import unittest

import numpy as np

from PIE2023 import ParticleSimulation, SimulationConfig
from demo import make_simulation


class SimulationSmokeTests(unittest.TestCase):
    def test_rejects_non_positive_configuration(self):
        for keyword in ("num_particles", "k", "m", "timesteps", "radius"):
            with self.subTest(parameter=keyword), self.assertRaises(ValueError):
                SimulationConfig(**{keyword: 0})

    def test_contact_pair_is_equal_and_opposite(self):
        config = SimulationConfig(num_particles=2, k=100.0, radius=0.1)
        simulation = ParticleSimulation(config)
        simulation.positions = np.array([[0.4, 0.5], [0.55, 0.5]])
        simulation.computeForces()
        np.testing.assert_allclose(simulation.forces[:, 0], [-5.0, 5.0])
        np.testing.assert_allclose(simulation.forces[:, 1], [-9.81, -9.81])

    def test_single_free_fall_step(self):
        config = SimulationConfig(num_particles=1, k=100.0, radius=0.04)
        simulation = ParticleSimulation(config)
        simulation.positions = np.array([[0.5, 0.8]])
        simulation.computeForces()
        simulation.velocityVerlet()
        np.testing.assert_allclose(simulation.positions,
                                   [[0.5, 0.8 - 0.5 * 9.81 * config.dt**2]])
        np.testing.assert_allclose(simulation.velocities, [[0.0, -9.81 * config.dt]])

    def test_demo_finite_output_and_original_snapshot_labels(self):
        fixture = Path(__file__).resolve().parents[1] / "demo.json"
        simulation = make_simulation(fixture)
        with tempfile.TemporaryDirectory() as directory, chdir(directory):
            with redirect_stdout(StringIO()):
                simulation.start_simulation()
            result = Path("simulation_results.txt").read_text(encoding="utf-8")
        labels = [line for line in result.splitlines() if line.startswith("Timestep")]
        self.assertEqual(labels, [f"Timestep {step}:" for step in [0, 20, 40, 60, 80]])
        self.assertTrue(np.isfinite(simulation.positions).all())
        self.assertTrue(np.isfinite(simulation.velocities).all())
        self.assertTrue((simulation.positions >= 0).all())
        self.assertTrue((simulation.positions <= 1).all())

    def test_explicit_demo_initial_conditions_are_reproducible(self):
        fixture = Path(__file__).resolve().parents[1] / "demo.json"
        first, second = make_simulation(fixture), make_simulation(fixture)
        for _ in range(10):
            first.velocityVerlet()
            second.velocityVerlet()
        np.testing.assert_array_equal(first.positions, second.positions)
        np.testing.assert_array_equal(first.velocities, second.velocities)

    def test_documents_original_fewer_than_five_steps_limitation(self):
        simulation = ParticleSimulation(SimulationConfig(num_particles=1, timesteps=4))
        with self.assertRaises(ZeroDivisionError):
            simulation.start_simulation()


if __name__ == "__main__":
    unittest.main()
