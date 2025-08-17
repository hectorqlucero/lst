# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

## [0.1.5] - 2025-08-17
### Added
- GitHub Actions workflow to publish to Clojars on tag or manual dispatch
### Changed
- Move template coordinates to verified group `org.clojars.hector/lein-template.lst` and bump version to 0.1.5

## [0.1.4] - 2025-08-17
### Fixed
- Template `project.clj` now uses dotted namespaces (no stray spaces), allowing generated apps to parse and run tests
### Changed
- VS Code workspace settings to avoid format-on-save corruption of template files
- CI workflow cleans any leftover `ciapp/` before generating a fresh project

## [0.1.1] - 2019-11-08
### Changed
- Documentation on how to make the widgets.

### Removed
- `make-widget-sync` - we're all async, all the time.

### Fixed
- Fixed widget maker to keep working when daylight savings switches over.

## 0.1.0 - 2019-11-08
### Added
- Files from the new template.
- Widget maker public API - `make-widget-sync`.

[Unreleased]: https://github.com/hectorqlucero/lst/compare/v0.1.5...HEAD
[0.1.5]: https://github.com/hectorqlucero/lst/compare/v0.1.4...v0.1.5
[0.1.4]: https://github.com/hectorqlucero/lst/compare/v0.1.3...v0.1.4
[0.1.1]: https://github.com/your-name/ls/compare/0.1.0...0.1.1
