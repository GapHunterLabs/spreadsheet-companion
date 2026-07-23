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
- **XLSX stays read-only in 0.1.x on purpose.** Writing XLSX while
  preserving formatting is exactly where the incumbent corrupts files.
  Editing ships when it can be done safely, not before.

## Usage

- Open any `.csv`/`.tsv` file → click the **Table** tab at the top of
  the editor. Edit cells in place; use the toolbar to add/delete rows
  and columns. Changes go straight into the document (undo works as
  usual).
- Open any `.xlsx` file → the workbook view opens with one tab per
  sheet.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
