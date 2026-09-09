#!/usr/bin/env python3
"""External source-scope checks. No semantic or runtime correctness claim."""
import hashlib
import json
import subprocess
import sys
from pathlib import Path


def validate(case_id, workspace):
    case_dir = Path(__file__).resolve().parents[1] / 'cases' / case_id
    expected = json.loads((case_dir / 'expectations.json').read_text())
    failures = []
    for relative, digest in expected['protected'].items():
        path = workspace / relative
        if path.is_symlink() or not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            failures.append(f'Protected input changed: {relative}')
    for relative in expected['changed']:
        path = workspace / relative
        if path.is_symlink() or not path.is_file() or not path.read_text().strip():
            failures.append(f'Required result missing: {relative}')
            continue
        initial = subprocess.run(['git', 'show', f'HEAD:{relative}'], cwd=workspace, capture_output=True)
        if initial.returncode == 0 and initial.stdout == path.read_bytes():
            failures.append(f'Requested edit was not made: {relative}')
    return failures


if __name__ == '__main__':
    if len(sys.argv) != 2 or '/' in sys.argv[1] or '..' in sys.argv[1]:
        raise SystemExit('Usage: source_contract.py <case-id>')
    errors = validate(sys.argv[1], Path.cwd())
    print('\n'.join(errors) if errors else 'Source scope checks passed; semantic correctness requires rubric review; compile/UI checks were not run.')
    raise SystemExit(bool(errors))
