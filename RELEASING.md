# Releasing

Dejavu follows the useful part of Coil's Compose release model: each library release pins one
Compose Multiplatform version, validates the complete test suite against it, records the Compose
upgrade in the changelog, and publishes that exact version. Dejavu additionally keeps an Android
Compose BOM compatibility window because recomposition tracking depends directly on runtime
behavior across those releases.

## Compose Policy

- DejaVu uses its own semantic versions, not Compose version numbers. Track stable Compose
  Multiplatform releases; do not publish merely to match every alpha/beta dependency update unless
  a compatibility problem requires it. This follows [Coil's policy](https://coil-kt.github.io/coil/faq/).
- `composeMultiplatform` is the KMP release baseline.
- `composeBom` is the Android release baseline and should be the latest validated stable BOM.
- `composeBomCompat*` entries are retained checkpoints: the minimum supported BOM, the latest BOM
  from each older supported Compose line, and the first BOM from newer supported lines.
- A BOM patch updates only `composeBom`; CI picks it up automatically.
- A new Compose minor adds a checkpoint, new-API tests in `compose-experimental`, and a Dejavu minor
  release when the compatibility contract or targets change.
- Moving the minimum supported checkpoint requires a documented Dejavu release; the previous
  Dejavu release remains the maintenance line for consumers on the older Compose line. Redundant
  intermediate patch checkpoints can be retired while retaining the minimum and latest checkpoint
  for each supported Compose line.

## Checklist

1. Update `composeMultiplatform`, `composeBom`, and any new `composeBomCompat*` checkpoint in
   `gradle/libs.versions.toml`.
2. Add exact, `SideEffect`-backed regressions for new runtime paths or composables in
   `compose-experimental`.
3. Set `dejavu/build.gradle.kts` to the release version and move all release notes under that
   version in `CHANGELOG.md`.
4. Update dependency snippets and the compatibility table in the README and docs.
5. Start a clean emulator, set `ANDROID_SERIAL` to it, and run the full local gate:

   ```bash
   ANDROID_SERIAL=emulator-5556 ./test.sh --all-boms
   ```

6. Record the local commands, environment, actual test counts, exclusions, and results in
   `docs/releases/X.Y.Z.md`. Run test tasks freshly; do not present cached historical reports as
   a new run. Document any difference between the local device coverage and the hosted CI matrix.
   Equivalent passing local checks are sufficient if hosted CI is unavailable or fails because of
   budget or infrastructure. Reproduced product failures must still be fixed.
7. Publish the candidate to Maven Local once using its release baseline, then run the
   [standalone consumer checks](validation/consumer/README.md) against those same artifacts on
   the retained Android BOMs and KMP baseline. Inspect AAR minimum compile SDK and resolved runtime
   versions. This verifies the packaged binary without recompiling it for each older Compose BOM.
8. Commit the verified changes and tag the merge commit as `vX.Y.Z`. Publishing is an explicit
   workflow dispatch so pushing a tag does not spend CI minutes repeating the full suite:

   ```bash
   gh workflow run publish.yml --ref vX.Y.Z \
     -f local_verification=true -f verified_sha="$(git rev-parse 'vX.Y.Z^{commit}')"
   ```

   The workflow requires a tag matching the Gradle version, the full verified commit SHA, and
   the checked-in validation record. Omit `local_verification` to run the hosted test suite first.
9. Verify that every target artifact is available from Maven Central, then create the GitHub
   release from the matching changelog entry. Record the release URL and artifact availability in
   the [shared release tracker](https://docs.google.com/document/d/1nfIzsfxz0ze_rTy5EKO0vG8TO_JyXDdricco7q5oyfY/edit).
   Keep release notes and social post drafts there; social copy must describe only verified changes.

   ```bash
   python3 validation/verify_maven_publication.py X.Y.Z
   ```

   This checks all six public target artifacts and their POM and Gradle module coordinates.
   A successful upload workflow can mean Central is still publishing; wait for public availability
   before announcing.
10. Set the next development version to `X.Y.Z-SNAPSHOT` after publication, as in
   [Coil's release checklist](https://github.com/coil-kt/coil/blob/main/RELEASING.md).
11. Verify the Docs workflow succeeded and inspect the live site's home, getting-started,
    compatibility, release, and generated API pages. Check `versions.json`: `latest` must point
    to the published release and `snapshot` to the next development version. Follow an old
    unversioned guide URL too; it must redirect to `latest`, not serve an archived guide.

## Documentation maintenance

Keep `extra.dejavu_release` in `mkdocs.yml`, README and guide dependency examples, bundled
onboarding instructions, and the compatibility matrix aligned with the published stable release.
When bundled skill instructions change, advance both `.claude-plugin` manifest versions together
so installed copies can receive the update; the plugin has its own version sequence.
Keep historical release notes unchanged except for corrections and verified publication evidence.
The site exposes release notes, migration guidance, validation records, and contribution policies
in its navigation. A strict MkDocs build and `validation/docs.py verify site` check local files,
anchors, API output, and stable dependency references before deployment.

Documentation corrections can be shipped without a new Maven release. When `main` still has
the released library source, API signatures, and dependency catalog, run:

```bash
gh workflow run docs.yml --ref main -f release_version=X.Y.Z
```

This refreshes the current stable site's guides and generates API docs labelled with the release
version and linked to its tag. A guard refuses to relabel changed library APIs or dependencies
as an existing release. If runtime development has moved on, apply the documentation correction
on the matching release source instead; do not bypass that guard.

To refresh development docs, run `gh workflow run docs.yml --ref main` without `release_version`.
Snapshot pages identify themselves as unreleased and continue to show the stable dependency in
install examples. The workflow also refreshes redirects from old unversioned guide URLs.
It does not publish library artifacts or run the UI matrix.
