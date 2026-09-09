#!/usr/bin/env python3
"""Validate documentation releases, local links, and legacy website redirects."""
import argparse
import html
import re
import subprocess
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
VERSION = re.compile(r'^version = "([^"]+)"', re.M)


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def documentation_version(ref, release):
    current = VERSION.search((ROOT / 'dejavu/build.gradle.kts').read_text()).group(1)
    if release:
        if ref != 'refs/heads/main':
            raise ValueError('Release documentation corrections must run from main.')
        if not re.fullmatch(r'\d+\.\d+\.\d+', release):
            raise ValueError('release_version must be a stable X.Y.Z version.')
        stable = re.search(r'^  dejavu_release: (\S+)', (ROOT / 'mkdocs.yml').read_text(), re.M).group(1)
        if release != stable:
            raise ValueError('Only the current stable documentation may be refreshed from main.')
        tag = f'v{release}'
        tagged = VERSION.search(git('show', f'{tag}:dejavu/build.gradle.kts')).group(1)
        if tagged != release:
            raise ValueError('Release tag and published Gradle version differ.')
        # Do not label new runtime APIs or dependencies as an already released version.
        changed = git('diff', '--name-only', tag, '--', 'dejavu/src', 'dejavu/api', 'gradle/libs.versions.toml')
        if changed:
            raise ValueError(f'Release API or dependencies changed; generate docs from the release source instead:\n{changed}')
        return release
    if ref.startswith('refs/tags/v'):
        if ref.removeprefix('refs/tags/v') != current or current.endswith('-SNAPSHOT'):
            raise ValueError('Documentation tag must match the Gradle release version.')
    elif ref != 'refs/heads/main' or not current.endswith('-SNAPSHOT'):
        raise ValueError('Development documentation requires main and a SNAPSHOT version.')
    return current


class Page(HTMLParser):
    def __init__(self, text):
        super().__init__()
        self.links = []
        self.ids = set()
        self.feed(text)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if 'id' in attrs:
            self.ids.add(attrs['id'])
        if tag == 'a' and 'href' in attrs:
            self.links.append(attrs['href'])


def verify(site):
    site = Path(site).resolve()
    pages = {p: Page(p.read_text()) for p in site.rglob('*.html')}
    if site / 'api/index.html' not in pages:
        raise ValueError('Generate the API documentation before building the site.')
    failures = []
    for path, page in pages.items():
        for href in page.links:
            url = urlsplit(href)
            if url.scheme or url.netloc or url.path.startswith('/'):
                continue
            target = (path.parent / unquote(url.path)).resolve() if url.path else path
            if target.is_dir():
                target /= 'index.html'
            if not target.is_relative_to(site):
                failures.append(f'{path.relative_to(site)}: outside site: {href}')
            elif not target.exists():
                failures.append(f'{path.relative_to(site)}: missing file: {href}')
            elif (url.fragment and target in pages
                  and url.fragment not in pages[target].ids
                  and unquote(url.fragment) not in pages[target].ids):
                failures.append(f'{path.relative_to(site)}: missing anchor: {href}')
    stable = re.search(r'^  dejavu_release: (\S+)', (ROOT / 'mkdocs.yml').read_text(), re.M).group(1)
    current_docs = [ROOT / 'README.md', *ROOT.glob('docs/*.md'), ROOT / '.claude/skills/dejavu-onboarding/SKILL.md']
    for path in current_docs:
        for version in re.findall(r'me\.mmckenna\.dejavu:dejavu:([\d.]+(?:-SNAPSHOT)?)', path.read_text()):
            if version != stable:
                failures.append(f'{path.relative_to(ROOT)}: dependency {version} differs from stable {stable}')
    if failures:
        raise ValueError('\n'.join(sorted(set(failures))))
    print(f'PASS: {len(pages)} HTML pages, local links and anchors, API output, and stable dependency examples.')


def redirect(path, target):
    path.parent.mkdir(parents=True, exist_ok=True)
    escaped = html.escape(target, quote=True)
    path.write_text(f'<!doctype html><html lang="en"><head><meta charset="utf-8">'
                    f'<title>DejaVu documentation</title><link rel="canonical" href="{escaped}">'
                    f'<meta http-equiv="refresh" content="0; url={escaped}"></head>'
                    f'<body><a href="{escaped}">Continue to the latest DejaVu documentation</a>'
                    f'<script>location.replace({target!r}+location.hash)</script></body></html>\n')


def repair_api(destination):
    # Dokka 2.2 merges inherited overloads into one row but can retain links to
    # individual anchors it never emits. Point those links at their containing row.
    repaired = 0
    for path in Path(destination).rglob('*.html'):
        original = path.read_text()
        ids = Page(original).ids
        def row(match):
            nonlocal repaired
            anchor, content = match.group(1), match.group(0)
            if anchor not in ids:
                return content
            def link(found):
                nonlocal repaired
                target = found.group(1)
                if target in ids or unquote(target) in ids:
                    return found.group(0)
                repaired += 1
                return f'href="index.html#{anchor}"'
            return re.sub(r'href="index.html#([^"]+)"', link, content)
        updated = re.sub(r'<a data-name="([^"]+)".*?(?=<a data-name="|\Z)', row, original, flags=re.S)
        if updated != original:
            path.write_text(updated)
    print(f'Repaired {repaired} Dokka links to merged overload rows.')


def redirects(destination):
    destination = Path(destination)
    routes = ['getting-started', 'examples', 'use-cases', 'how-it-works', 'error-messages', 'causality-analysis']
    for route in routes:
        redirect(destination / route / 'index.html', f'https://dejavu.mmckenna.me/latest/{route}/')
    for route in ['api-reference', 'api']:
        redirect(destination / route / 'index.html', 'https://dejavu.mmckenna.me/latest/api/index.html')
    print(f'Updated {len(routes) + 2} legacy documentation redirects.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    version = commands.add_parser('version')
    version.add_argument('--ref', default='refs/heads/main')
    version.add_argument('--release', default='')
    commands.add_parser('verify').add_argument('site')
    commands.add_parser('repair-api').add_argument('destination')
    commands.add_parser('redirects').add_argument('destination')
    args = parser.parse_args()
    try:
        if args.command == 'version':
            print(documentation_version(args.ref, args.release))
        elif args.command == 'verify':
            verify(args.site)
        elif args.command == 'repair-api':
            repair_api(args.destination)
        else:
            redirects(args.destination)
    except (ValueError, subprocess.CalledProcessError) as error:
        parser.exit(1, f'{error}\n')
