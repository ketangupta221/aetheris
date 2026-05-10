# Contributing to Aetheris Planner

Thanks for your interest. This project is source-available under the
PolyForm Noncommercial 1.0.0 licence — you can use, modify, and study
the code for personal, educational, research, or non-profit purposes.
Commercial use by third parties is prohibited.

## How to contribute

1. Fork the repo.
2. Create a feature branch (`git checkout -b my-feature`).
3. Make your changes, keeping the same module layout and style as the
   surrounding code.
4. Run `./gradlew check` to ensure lint, detekt, unit tests, and the
   manifest invariant all pass.
5. Open a pull request with a clear description of what changed and why.

## Ground rules

- Keep the runtime zero-network invariant intact. The only module that
  may open sockets is `:distribution:model-downloader`, and only inside
  `withConsentedDownload { ... }`. Both the `:checkManifests` task and
  the `NetworkDenyingSocketFactory` enforce this — don't work around
  them.
- Every new correctness property from design §6 should ship with a
  jqwik property test (500 iterations by default).
- Commit-message style: `<component>: short imperative summary`
  (for example, `core:data: encrypt task tags column`).
- Do not introduce analytics, telemetry, crash reporting, or advertising
  SDKs. Ever. See Requirement 5.7 in `requirements.md`.

## Code of conduct

By participating you agree to follow [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md).

## Licence of contributions

By submitting a pull request you agree that your contribution is
licensed under PolyForm Noncommercial 1.0.0 alongside the rest of
the project.
