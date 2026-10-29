"""Shared paths for the mercurydpm-clay checks."""

from pathlib import Path

import pytest

PROJECT = Path(__file__).resolve().parents[1]


@pytest.fixture(scope="session")
def project() -> Path:
    return PROJECT
