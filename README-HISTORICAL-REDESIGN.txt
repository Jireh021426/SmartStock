SmartStock — Historical Reconciliation overview redesign

This package contains exactly two complete application files:
  src/main/resources/templates/historical-reconciliation.html
  src/main/resources/static/css/historical-reconciliation.css

It does NOT overwrite navigation, shared style/theme, controllers, entities, or the detail page.
The new CSS is scoped to the history overview and supports light/dark themes.

Preserved functionality:
 - GET /historical-reconciliation with canEdit, days, form, locations
 - POST /historical-reconciliation with th:object and all five original field names
 - Server-side validation, Spring Security/CSRF, data types
 - Listing each day, collections, debt/stock completion, review state, details link
 - View-only access for auditors
 - No changes to database, business logic, or history detail/locking rules

Important: Source File / Reference is a TEXT filename/reference, not an upload.
The screenshot mockup showed example records: this release never inserts sample records.

Install from project root (Git Bash):
  tar -xzf "$HOME/Downloads/smartstock-historical-clean-redesign.tar.gz" -C .

Then test from project root (existing JDK/Maven):
  JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1' PATH="/c/Program Files/Java/jdk-21.0.12.1/bin:$PATH" mvn test

Run git diff --check; review actual page in local browser; commit only these two files after tests pass.
