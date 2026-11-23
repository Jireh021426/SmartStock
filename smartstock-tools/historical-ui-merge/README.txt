SMARTSTOCK — HISTORICAL RECONCILIATION REDESIGN (SAFE MERGE)
============================================================
Source baseline: exact current historical-reconciliation.html and CSS from uploaded
smartstock-historical-current-sources.tar.gz (November 23, 2026).

Changed files (complete replacements):
  src/main/resources/templates/historical-reconciliation.html
  src/main/resources/static/css/historical-reconciliation.css

What stays the same:
- Existing Thymeleaf form bindings, routes, role restrictions (canEdit)
- All existing saved-days table bindings and status logic
- Shared Gentelella stylesheet, theme script and navigation
- No migrations, Java/controller, business logic, data, or Railway changes

Installation (from repository root, with Spring Boot stopped):
  tar -xzf "$HOME/Downloads/smartstock-historical-reconciliation-safe-merge.tar.gz" -C .
  PYTHONUTF8=1 python smartstock-tools/historical-ui-merge/install.py

The installer checks SHA-256 against the exact uploaded original, validates
Thymeleaf binding preservation, backs up the original 2 files to
$HOME/smartstock-private-backups, and refuses to overwrite newer local edits.

After installation:
  git diff --check
  mvn clean test
  mvn spring-boot:run

Verify the page at http://localhost:8080/historical-reconciliation in
both dark and light modes. Do not deploy until local tests pass.
