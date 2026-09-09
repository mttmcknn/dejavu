"""Select an immutable skill revision without changing the corpus or harness."""
from pathlib import Path
import subprocess

_selected: Path | None = None


def skill_root(repo_root: Path) -> Path:
    return _selected if _selected is not None else repo_root / 'skills'


def select_skill_revision(repo_root: Path, revision: str | None) -> None:
    global _selected
    _selected = None
    if revision is None:
        return
    sha = subprocess.check_output(['git', 'rev-parse', '--verify', '--end-of-options', revision + '^{commit}'], cwd=repo_root, text=True).strip()
    prefix = '.claude/skills/'
    files = subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', sha, '--', prefix], cwd=repo_root, text=True).splitlines()
    if not files:
        raise ValueError(f'No bundled skills at {sha}')
    destination = repo_root / '.scratch/skill-evals/skill-sources' / sha
    expected_paths = {Path(name.removeprefix(prefix)) for name in files}
    if destination.is_symlink() or any(p.is_symlink() for p in destination.rglob('*')):
        raise ValueError('Archived skill cache contains symlinks')
    actual_paths = {p.relative_to(destination) for p in destination.rglob('*') if p.is_file()}
    if actual_paths - expected_paths:
        raise ValueError('Archived skill cache contains unexpected files')
    for filename in files:
        relative = Path(filename.removeprefix(prefix))
        if '..' in relative.parts or relative.is_absolute():
            raise ValueError('Invalid archived skill path')
        content = subprocess.check_output(['git', 'show', f'{sha}:{filename}'], cwd=repo_root)
        path = destination / relative
        if path.is_symlink() or (path.exists() and path.read_bytes() != content):
            raise ValueError(f'Archived skill cache changed: {path}')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
    _selected = destination
