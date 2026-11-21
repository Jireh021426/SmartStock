SmartStock — Shared Front-End Enhancements (safe integration)
============================================================

Contents:
  src/main/resources/static/css/enhancements.css   -- Full CSS
  src/main/resources/static/js/enhancements.js     -- Full JS
  reference/navigation-full.html                  -- The complete navigation fragment supplied by the user
  tools/install-shared-enhancements.sh             -- Safe and repeatable navigation installer

WHY NOT OVERWRITE NAVIGATION.HTML?
The deployed SmartStock navigation now contains newer Daily Closing, Historical
Reconciliation and Employees links. The installer keeps the user's actual current
navigation intact and adds exactly two asset tags. A full copy of the supplied
navigation file is included under reference/ but NOT applied blindly.

HOW TO INSTALL
From SmartStock project root (Git Bash):
  tar -xzf "$HOME/Downloads/smartstock-shared-enhancements-safe.tar.gz" -C .
  bash tools/install-shared-enhancements.sh
  git diff --check

WHAT IT DOES
- Theme-aware shared accessibility, styles, peso formatting, table numeric alignment.
- Client-side filter/pagination on large non-paginated tables (>10 rows).
- SKIPS existing server-side Employees and Inventory pagination and existing
  client-side Location Balances pagination; will not double-paginate them.
- POST form double-submit visual guard and dismissible notices.
- No changes to Java, controllers, database, inventory or financial totals.

LIMITATIONS
- Filtering a client-paginated table searches the rows already rendered in HTML,
  not the entire database. It deliberately does not filter server-paginated tables.
- Table enhancements are presentation only; nothing is saved to the database.
- Do not deploy without checking core forms and table pages in your local app.

PRODUCTION SAFETY
Test locally, then commit ONLY these three project files:
  src/main/resources/static/css/enhancements.css
  src/main/resources/static/js/enhancements.js
  src/main/resources/templates/fragments/navigation.html
Do not commit sample data, local backup, or private credentials.
