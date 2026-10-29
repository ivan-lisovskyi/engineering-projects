"""Render an animated GIF from the real solver output (added September 2026).

Runs the unchanged ParticleSimulation from PIE2023.py, starting from the same
fixture as demo.py (demo.json), and records the particle positions after every
`--every` integration steps. Nothing is drawn by hand: every frame is a state
the solver produced. Needs Pillow (see requirements-gif.txt).
"""

import argparse
import json
from pathlib import Path
import tempfile

import numpy as np

from demo import make_simulation


def record_frames(simulation, steps, every):
    """Advance `simulation` by `steps` velocity-Verlet steps.

    Returns [(step, positions copy), ...] for step 0 (the initial state) and
    every `every` steps after that.
    """
    if steps < 1 or every < 1 or every > steps:
        raise ValueError("Need steps >= 1 and 1 <= every <= steps.")
    frames = [(0, simulation.positions.copy())]
    for step in range(1, steps + 1):
        simulation.velocityVerlet()
        if step % every == 0:
            frames.append((step, simulation.positions.copy()))
    return frames


def to_pixels(points, box_min, box_max, size, margin):
    """Map simulation coordinates to image pixels (y axis points up)."""
    scale = (size - 2 * margin) / (box_max - box_min)
    pixels = np.empty_like(points, dtype=float)
    pixels[:, 0] = margin + (points[:, 0] - box_min) * scale
    pixels[:, 1] = size - margin - (points[:, 1] - box_min) * scale
    return pixels


def render_gif(frames, config, output, size=400, margin=20, frame_ms=40):
    from PIL import Image, ImageDraw, ImageFont  # imported here: tests do not need Pillow

    font = ImageFont.load_default(size=14)
    radius_px = config.radius * (size - 2 * margin) / (config.boundary_max - config.boundary_min)
    images = []
    for step, positions in frames:
        image = Image.new("RGB", (size, size), "white")
        draw = ImageDraw.Draw(image)
        draw.rectangle([margin, margin, size - margin, size - margin], outline="black", width=2)
        for x, y in to_pixels(positions, config.boundary_min, config.boundary_max, size, margin):
            draw.ellipse([x - radius_px, y - radius_px, x + radius_px, y + radius_px],
                         fill=(70, 130, 200), outline=(20, 50, 100))
        draw.text((margin, 2), f"step {step}, t = {step * config.dt:.3f} s", fill="black", font=font)
        images.append(image.quantize(colors=16))
    images[0].save(output, save_all=True, append_images=images[1:],
                   duration=frame_ms, loop=0, optimize=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=Path(__file__).with_name("demo.json"))
    parser.add_argument("--steps", type=int, default=1000,
                        help="integration steps to run (overrides 'timesteps' in the config)")
    parser.add_argument("--every", type=int, default=10, help="record one frame per this many steps")
    parser.add_argument("--output", type=Path, default=Path("demo.gif"))
    args = parser.parse_args()

    fixture = json.loads(args.config.read_text(encoding="utf-8"))
    fixture["parameters"]["timesteps"] = args.steps
    with tempfile.TemporaryDirectory() as folder:
        patched = Path(folder) / "config.json"
        patched.write_text(json.dumps(fixture), encoding="utf-8")
        simulation = make_simulation(patched)
    frames = record_frames(simulation, args.steps, args.every)
    render_gif(frames, simulation.config, args.output)
    print(f"Wrote {args.output} ({len(frames)} frames, {args.output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
