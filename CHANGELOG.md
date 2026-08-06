<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Spreadsheet Companion Changelog

## [Unreleased]

## [0.1.3]

### Added

- XLSX cell editing: surgical single-sheet-entry rewrite — re-parses and
  rewrites only the one worksheet ZIP entry that changed, while every
  other entry (styles, theme, sharedStrings, merged ranges) is copied
  byte for byte. Formula cells (`<f>`) are flagged by the reader and
  stay read-only, so editing never leaves a cell in an ambiguous
  formula/value state.

[Unreleased]: https://github.com/GapHunterLabs/spreadsheet-companion/compare/0.1.3...HEAD
[0.1.3]: https://github.com/GapHunterLabs/spreadsheet-companion/compare/0.1.2...0.1.3

## [0.1.2]

### Changed

- Added a strict local `verifyPlugin` gate (catches
  `@ApiStatus.OverrideOnly`/`Internal`/`Experimental` API usage and
  compatibility problems before Marketplace's own verifier would) — no
  user-visible change, confirmed passing clean against all 6 target IDEs.

## [0.1.1]

### Added

- Gap Hunter Labs brand icon (`pluginIcon.svg` / `pluginIcon_dark.svg`).

## [0.1.0]

### Added

- CSV/TSV table editor as an extra editor tab (the text editor stays the
  default — the plugin never takes over how files open): edit cells, add
  and delete rows and columns, with RFC 4180 quoting and automatic
  delimiter detection (comma, semicolon, tab, pipe).
- Read-only XLSX workbook viewer with one tab per sheet, built on a
  lightweight JDK-only parser (no office libraries bundled) that runs off
  the UI thread so large workbooks never freeze the IDE.

[Unreleased]: https://github.com/GapHunterLabs/spreadsheet-companion/compare/0.1.2...HEAD
[0.1.2]: https://github.com/GapHunterLabs/spreadsheet-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/spreadsheet-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/spreadsheet-companion/commits/0.1.0
