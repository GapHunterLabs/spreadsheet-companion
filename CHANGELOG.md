<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Spreadsheet Companion Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/kendjm/spreadsheet-companion/compare/0.1.1...HEAD
[0.1.1]: https://github.com/kendjm/spreadsheet-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/kendjm/spreadsheet-companion/commits/0.1.0
