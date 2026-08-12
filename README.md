# Spreadsheet Companion

IntelliJ-family plugin. Edit CSV/TSV files in a real table and view XLSX
workbooks — without leaving the IDE, and without the plugin taking over
your files.

## Why it exists

Born from real evidence in JetBrains Marketplace reviews, not
assumptions: the leading paid spreadsheet plugin in this space
($39/year, ~307K downloads) has 50% of its reviews at 3 stars or fewer,
with paying users reporting that they cannot add or delete rows, cannot
copy/paste cells, that the plugin forcibly takes over CSV files with no
way to opt out, that its license prompt freezes the whole IDE, and that
it corrupted XLSX files on save.

## Why built this way

- **The table view is an extra tab, never a replacement.** CSV files
  keep opening in the text editor exactly as before
  (`FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR`); the table is one
  click away when you want it. That is the direct fix for the
  "I can't stop it touching my CSV files" complaint.
- **XLSX parsing is JDK-only and off the UI thread.** An .xlsx file is
  a ZIP of XML, so the reader uses `java.util.zip` plus the JDK's SAX
  parser — no Apache POI (the dependency would be many times larger
  than the plugin) and no work on the EDT, so a big workbook never
  freezes the IDE.
- **XLSX writing is surgical, never a full regeneration.** Editing a
  cell re-parses and rewrites only the one `sheetN.xml` ZIP entry that
  changed; every other entry (styles, theme, `sharedStrings.xml`,
  merged-cell ranges) is copied byte for byte, untouched. That is the
  direct fix for "it corrupted my file on save" — the usual way that
  bug happens is regenerating the whole workbook.
- **Formula cells are read-only, not silently overwritten.** `.xlsx`
  formulas aren't evaluated by this plugin; a cell that holds one
  stays non-editable in the table so a manual edit can never drop the
  formula and leave a stale cached value behind.
- **Adding/deleting rows or columns in XLSX is out of scope for now.**
  That requires re-flowing formula references and merged-cell ranges
  correctly — a materially bigger, riskier feature than editing a
  value in place. CSV/TSV already supports it via the toolbar; XLSX
  editing is cell-value-only until that's designed properly.

## Usage

- Open any `.csv`/`.tsv` file → click the **Table** tab at the top of
  the editor. Edit cells in place; use the toolbar to add/delete rows
  and columns. Changes go straight into the document (undo works as
  usual).
- Open any `.xlsx` file → the workbook view opens with one tab per
  sheet. Edit any cell that isn't a formula directly in the table —
  changes save straight to the `.xlsx` file, no separate save step.

## Enterprise / Team Licensing

Need enterprise features, custom spreadsheet workflows, or team
licensing? Contact us at **gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
