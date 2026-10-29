"""Checks for the frame-recording logic of make_gif.py (no image library needed)."""

from pathlib import Path
import unittest

import numpy as np

from demo import make_simulation
from make_gif import record_frames, to_pixels

DEMO_CONFIG = Path(__file__).resolve().parents[1] / "demo.json"


class RecordFramesTests(unittest.TestCase):
    def test_frames_start_at_initial_state_and_follow_the_solver(self):
        simulation = make_simulation(DEMO_CONFIG)
        initial = simulation.positions.copy()
        frames = record_frames(simulation, steps=20, every=5)
        self.assertEqual([step for step, _ in frames], [0, 5, 10, 15, 20])
        np.testing.assert_array_equal(frames[0][1], initial)

        reference = make_simulation(DEMO_CONFIG)
        for _ in range(20):
            reference.velocityVerlet()
        np.testing.assert_array_equal(frames[-1][1], reference.positions)

    def test_frames_are_copies_not_views(self):
        simulation = make_simulation(DEMO_CONFIG)
        frames = record_frames(simulation, steps=10, every=5)
        self.assertFalse(np.array_equal(frames[0][1], frames[-1][1]))

    def test_rejects_bad_step_arguments(self):
        simulation = make_simulation(DEMO_CONFIG)
        for steps, every in ((0, 1), (10, 0), (10, 11)):
            with self.subTest(steps=steps, every=every), self.assertRaises(ValueError):
                record_frames(simulation, steps=steps, every=every)


class ToPixelsTests(unittest.TestCase):
    def test_box_corners_map_to_image_corners_with_y_up(self):
        points = np.array([[0.0, 0.0], [1.0, 1.0], [0.5, 0.5]])
        pixels = to_pixels(points, box_min=0.0, box_max=1.0, size=400, margin=20)
        np.testing.assert_allclose(pixels, [[20, 380], [380, 20], [200, 200]])


if __name__ == "__main__":
    unittest.main()
