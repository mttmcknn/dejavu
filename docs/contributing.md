# Contributing

DejaVu is a recomposition testing library. Intentionally inefficient fixtures are part of its
contract: tests compare measured counts with independent unkeyed `SideEffect` counters and
verify expected budget failures. Preserve those fixtures when contributing.

- [Development setup and test commands](https://github.com/mttmcknn/dejavu/blob/main/CONTRIBUTING.md)
- [Report a reproducible bug](https://github.com/mttmcknn/dejavu/issues/new?template=bug_report.md)
- [Propose a feature](https://github.com/mttmcknn/dejavu/issues/new?template=feature_request.md)
- [Release checklist](https://github.com/mttmcknn/dejavu/blob/main/RELEASING.md)
- [Code of conduct](https://github.com/mttmcknn/dejavu/blob/main/CODE_OF_CONDUCT.md)
- [Security policy](https://github.com/mttmcknn/dejavu/blob/main/SECURITY.md)
- [Apache 2.0 license](https://github.com/mttmcknn/dejavu/blob/main/LICENSE)

Include your DejaVu version, Compose BOM or Multiplatform version, Kotlin version, platform,
reproduction, and expected count when reporting a bug. Record test environments and actual
results in pull requests. Passing equivalent local checks can replace budget-constrained hosted
CI; reproduced product failures must still be fixed.
