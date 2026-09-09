#!/usr/bin/env python3
"""Verify every public DejaVu target artifact and its Maven coordinates."""

import json
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from xml.etree import ElementTree

PUBLICATIONS = {
    "dejavu": "jar",
    "dejavu-android": "aar",
    "dejavu-jvm": "jar",
    "dejavu-iosarm64": "klib",
    "dejavu-iossimulatorarm64": "klib",
    "dejavu-wasm-js": "klib",
}
GROUP = "me.mmckenna.dejavu"
BASE = "https://repo.maven.apache.org/maven2/me/mmckenna/dejavu"


def verify(version: str) -> bool:
    passed = True
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    for artifact, extension in PUBLICATIONS.items():
        prefix = f"{BASE}/{artifact}/{version}/{artifact}-{version}"
        try:
            with urlopen(Request(f"{prefix}.{extension}", method="HEAD"), timeout=30):
                pass
            with urlopen(f"{prefix}.pom", timeout=30) as response:
                pom = ElementTree.fromstring(response.read())
            coordinates = tuple(pom.findtext(f"m:{field}", namespaces=namespace)
                                for field in ("groupId", "artifactId", "version"))
            if coordinates != (GROUP, artifact, version):
                raise ValueError(f"Unexpected Maven coordinates: {coordinates}")
            with urlopen(f"{prefix}.module", timeout=30) as response:
                metadata = json.load(response)
            component = metadata["component"]
            module_coordinates = tuple(component.get(field) for field in ("group", "module", "version"))
            # KMP target metadata identifies the root component that owns its variants.
            if module_coordinates != (GROUP, "dejavu", version):
                raise ValueError(f"Unexpected Gradle module coordinates: {module_coordinates}")
            files = {entry["url"] for variant in metadata["variants"]
                     for entry in variant.get("files", [])}
            if f"{artifact}-{version}.{extension}" not in files:
                raise ValueError("Gradle metadata does not reference the target artifact")
            print(f"PASS {artifact}:{version} ({extension}, POM, and Gradle module metadata)")
        except (HTTPError, URLError, TimeoutError, KeyError, ValueError, ElementTree.ParseError) as error:
            print(f"FAIL {artifact}:{version}: {error}")
            passed = False
    return passed


if __name__ == "__main__":
    if len(sys.argv) != 2 or not re.fullmatch(r"\d+\.\d+\.\d+", sys.argv[1]):
        sys.exit("Usage: verify_maven_publication.py X.Y.Z")
    sys.exit(0 if verify(sys.argv[1]) else 1)
