SmartStock anti-flash fix (FOUC)

Cause: gentelella-dark.css was included at the bottom of navigation.html's fragment,
which is inserted within the <body>. This can cause the page to paint with older
styles, then repaint in the dark theme while browsing from page to page.

What this installer does:
  - Backs up the current complete templates in ~/smartstock-private-backups.
  - Adds a blocking dark-theme stylesheet link near the end of each page's <head>.
  - Removes the late duplicate stylesheet link from the navigation fragment.
  - Leaves all existing HTML content, forms, bindings, permissions, CSS and JS intact.
  - Uses the actual files in YOUR project, rather than replacing them with older copies.

Run from SmartStock project root in Git Bash:
  bash smartstock-tools/fix-page-flash.sh

Then run: git diff --check
Restart Spring Boot and hard-refresh once (Ctrl+Shift+R).
Do not commit/deploy until the app and tests are verified.
