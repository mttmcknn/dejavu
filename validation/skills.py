#!/usr/bin/env python3
"""Offline bundle validation; behavioral quality is measured separately in evals/."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def validate(root=ROOT):
    errors = []
    skills = sorted((root / '.claude/skills').glob('*/SKILL.md'))
    if len(skills) != 4:
        errors.append('Expected four canonical skills')
    for path in skills:
        text = path.read_text()
        if not text.startswith('---\n') or '\n---\n' not in text[4:]:
            errors.append(f'{path}: missing frontmatter')
            continue
        header = text.split('---\n', 2)[1]
        name = re.search(r'^name: (.+)$', header, re.M)
        description = re.search(r'^description: (.+)$', header, re.M)
        if not name or name[1] != path.parent.name or not re.fullmatch('[a-z0-9-]{1,64}', name[1]):
            errors.append(f'{path}: invalid name')
        if not description or not 1 <= len(description[1]) <= 1024:
            errors.append(f'{path}: invalid description')
        for alias in ['skills', '.agents/skills']:
            link = root / alias / path.parent.name
            if not link.is_symlink() or link.resolve() != path.parent.resolve():
                errors.append(f'{link}: must link to canonical skill')
        for reference in path.parent.rglob('*.md'):
            for target in re.findall(r'\]\(([^)#]+)(?:#[^)]*)?\)', reference.read_text()):
                if '://' in target:
                    continue
                resolved = (reference.parent / target).resolve()
                if not resolved.is_relative_to(path.parent.resolve()) or not resolved.is_file():
                    errors.append(f'{reference}: reference is not bundled: {target}')
    plugin = json.loads((root / '.claude-plugin/plugin.json').read_text())
    market = json.loads((root / '.claude-plugin/marketplace.json').read_text())
    if plugin['version'] != market['plugins'][0]['version']:
        errors.append('Plugin and marketplace versions differ')
    return errors


if __name__ == '__main__':
    errors = validate()
    print('\n'.join(errors) if errors else 'Validated four skills, bundled references, discovery links and plugin versions')
    raise SystemExit(bool(errors))
