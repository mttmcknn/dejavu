# Adapted for DejaVu from chrisbanes/skills; see evals/upstream/README.md.
from __future__ import annotations
from dataclasses import dataclass

ROUTER_SKILL = ""  # This bundle has no router entrypoint.
PUBLIC_SKILLS = ("dejavu-onboarding", "dejavu-test-writer", "dejavu-error-triage", "dejavu-perf-loop")
COMPOSE_SKILLS = PUBLIC_SKILLS
COMPOSE_TOPICS = ()

@dataclass(frozen=True)
class SuitePolicy:
    id: str
    title: str
    skills: tuple[str, ...]
    benchmark_cases: int
    routing_cases: int
    calibration_cases: int = 0
    topic_triads: tuple[tuple[str, str], ...] = ()
    require_skill_triads: bool = False
    historical_minimum: int = 0

SUITES = {"dejavu": SuitePolicy("dejavu", "DejaVu", PUBLIC_SKILLS, 15, 3, require_skill_triads=True)}

def suite_for_skills(skills: tuple[str, ...]) -> SuitePolicy:
    if not skills or not set(skills) <= set(PUBLIC_SKILLS):
        raise ValueError(f"unknown DejaVu skills: {skills}")
    return SUITES["dejavu"]
