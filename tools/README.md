# Coach exercise catalog

`import_exercises.py` reads the coach's workbook and generates
`frontend/js/exercise-data.js`. Python 3 is sufficient; no packages are required.

From the project root:

```powershell
python tools/import_exercises.py "C:\path\to\KILO_Exercise_Database_261023.xlsx"
```

The workbook is never modified. The generated file preserves all 14 columns,
Excel row numbers, a source checksum and the Overzicht cells (including formula
text and cached values). It contains data, not executable Excel formulas.
The importer checks column headings and CAP's exercise-name length limit.

The picker groups identical rows for display; all original rows remain in the
generated file. Empty levels, series and media fields stay empty. Exercise names
are not translated or rewritten. Existing workouts keep their saved names.

When the coach sends an updated workbook, run the import again, refresh CAP,
and check library search, paging and workout selection before committing the new catalog.
