SmartStock — Uniform Pagination
================================
Match the supplied pagination screenshot throughout the application:
Previous | 1 | 2 | 3 | 4 | … | 7 | Next

Included:
 - src/main/resources/static/css/pagination-unified.css
 - src/main/resources/static/js/pagination-unified.js
 - tools/install-unified-pagination.sh

Existing paginator families styled:
1. Employees server-side pagination
2. Stock History / Inventory server-side pagination
3. Locations balance pagination
4. table-pagination.js legacy/remittance pagination
5. enhancements.js shared client-side pagination

This CSS keeps both light/dark modes. The JS only condenses long server-side
page links into 1-4 + ellipsis + final page when at the beginning,
using the original page URL/query/size. It does not change backend queries,
store or modify data, or create any new pagination engine.

Install from SmartStock repository root:
 tar -xzf "$HOME/Downloads/smartstock-pagination-unified.tar.gz" -C .
 bash tools/install-unified-pagination.sh
 node --check src/main/resources/static/js/pagination-unified.js
 git diff --check

Stage ONLY:
 src/main/resources/templates/fragments/navigation.html
 src/main/resources/static/css/pagination-unified.css
 src/main/resources/static/js/pagination-unified.js

No database migration. Do not deploy until checked in browser. Archive includes
README and installer for local use; neither needs to be committed.
