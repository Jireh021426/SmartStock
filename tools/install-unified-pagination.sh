#!/usr/bin/env bash
set -euo pipefail
NAV='src/main/resources/templates/fragments/navigation.html'
CSS='src/main/resources/static/css/pagination-unified.css'
JS='src/main/resources/static/js/pagination-unified.js'
for path in "$NAV" "$CSS" "$JS"; do
    if [ ! -s "$path" ]; then echo "Missing required file: $path" >&2; exit 1; fi
done
if command -v python >/dev/null 2>&1; then PYTHON=python
elif command -v python3 >/dev/null 2>&1; then PYTHON=python3
elif command -v py >/dev/null 2>&1; then PYTHON=py
else echo 'Python required to update existing navigation safely.' >&2; exit 1; fi
mkdir -p "$HOME/smartstock-private-backups"
cp "$NAV" "$HOME/smartstock-private-backups/navigation-before-pagination-$(date +%Y%m%d-%H%M%S).html"
"$PYTHON" - "$NAV" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1])
s=p.read_text(encoding='utf-8')
css='    <link rel="stylesheet" th:href="@{/css/pagination-unified.css}">'
js='    <script th:src="@{/js/pagination-unified.js}" defer></script>'
# Use the known shared-enhancements CSS/JS reference as insertion points.
css_anchor='    <link rel="stylesheet" th:href="@{/css/enhancements.css}">'
js_anchor='    <script th:src="@{/js/enhancements.js}" defer></script>'
if css_anchor not in s or js_anchor not in s:
    raise SystemExit('ERROR: shared enhancements references not found in navigation. No changes made.')
if css not in s: s=s.replace(css_anchor, css_anchor+'\n'+css, 1)
if js not in s: s=s.replace(js_anchor, js_anchor+'\n'+js, 1)
if s.count('pagination-unified.css')!=1 or s.count('pagination-unified.js')!=1:
    raise SystemExit('ERROR: Duplicate pagination references. No changes made.')
p.write_text(s, encoding='utf-8', newline='\n')
print('Unified pagination assets linked once; existing SmartStock navigation preserved.')
PY
