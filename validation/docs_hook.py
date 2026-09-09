"""Identify development guides without rewriting stable dependency snippets."""
import os


def on_page_markdown(markdown, page, config, files):
    version = os.environ.get('DEJAVU_DOCS_VERSION', '')
    if version.endswith('-SNAPSHOT'):
        return (f'!!! warning "Development documentation: {version}"\n'
                '    These guides may describe unreleased APIs. '
                '[Read the latest stable documentation](https://dejavu.mmckenna.me/latest/). '
                'Dependency examples remain on the published stable release.\n\n' + markdown)
    return markdown
