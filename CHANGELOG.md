# Changelog

All notable changes to the llms.txt manager module are documented in this file.

## [Unreleased]

### Fixed
- The module now builds on a current JDK 17 toolchain: pinned `jahia.plugin.version` to 6.10 (parent 8.1.5.0 defaulted to 6.7, whose `prepare-package-dependencies` goal failed with `ClassNotFoundException: ParsingContext`), and added `resolutions` for `strip-ansi`/`string-width`/`wrap-ansi` so `lint:scss` (stylelint) no longer crashes with `ERR_REQUIRE_ESM`.
- `Llms` editor no longer passes `undefined` to the controlled `<textarea>` when a site has no `j:llms` value yet (React controlled/uncontrolled warning); it now defaults to an empty string.
- Replaced the incorrect `refetchQueries: refetch` mutation option with an explicit `refetch()` after a successful save.
- Hardcoded English "Loading…" / error strings are now translated via i18n.

### Accessibility
- The editor `<textarea>` now has a programmatic accessible name; the editor width is responsive (`max-width`) for reflow (WCAG 1.4.10).

### Added
- First Jest test suite (`@testing-library/react`, 5 tests) for the editor component (loading, error, populated/empty value, save-enablement and mutation call). JS coverage (lcov) wired for SonarQube.
