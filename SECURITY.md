# Security Policy

## Scope

Dejavu is a library for Jetpack Compose recomposition tracking, designed for test-time use. Consumers scope it to the appropriate build configuration (e.g., `debugImplementation` or `testImplementation`).

Dejavu does not provide networking or authentication. Its diagnostics can include inspected UI state
and parameter values, so remove sensitive values before sharing test logs or reproductions.

## Reporting

If you discover a security concern related to Dejavu, please open a [GitHub issue](https://github.com/mttmcknn/dejavu/issues). Since this library is designed for test-time use, standard issue reporting is appropriate.
