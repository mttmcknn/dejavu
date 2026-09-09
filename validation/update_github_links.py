#!/usr/bin/env python3
"""Keep GitHub links current across the entire published documentation archive."""
import argparse
import re
from pathlib import Path

OLD_OWNER = re.compile(rb"\bhimattm\b", re.IGNORECASE)
NEW_OWNER = b"mttmcknn"
TEXT_SUFFIXES = {".html", ".json", ".xml", ".txt", ".md", ".js", ".css", ".svg"}


def update(root, check=False):
    changed = []
    occurrences = 0
    for path in sorted(Path(root).rglob("*")):
        if path.is_symlink() or not path.is_file() or path.suffix not in TEXT_SUFFIXES:
            continue
        original = path.read_bytes()
        updated, count = OLD_OWNER.subn(NEW_OWNER, original)
        if count:
            changed.append(path.relative_to(root))
            occurrences += count
            if not check:
                path.write_bytes(updated)
    if check and changed:
        raise ValueError(f"Old GitHub account references in {len(changed)} published files: "
                         + ", ".join(map(str, changed)))
    print(f"{'Checked' if check else 'Updated'} published GitHub links: "
          f"{len(changed)} files, {occurrences} old-account references.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if not args.root.is_dir():
        parser.error("root must be an existing documentation directory")
    try:
        update(args.root, args.check)
    except ValueError as error:
        parser.exit(1, f"{error}\n")
