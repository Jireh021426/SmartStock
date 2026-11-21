#!/usr/bin/env bash
set -euo pipefail
NAV='src/main/resources/templates/fragments/navigation.html'
CSS='src/main/resources/static/css/enhancements.css'
JS='src/main/resources/static/js/enhancements.js'
if [ ! -f "$NAV" ] || [ ! -s "$CSS" ] || [ ! -s "$JS" ]; then
  echo 'ERROR: Run from SmartStock project root after extracting this package.' >&2
  exit 1
fi
if command -v python >/dev/null 2>&1; then
  PYTHON=python
elif command -v python3 >/dev/null 2>&1; then
  PYTHON=python3
elif command -v py >/dev/null 2>&1; then
  PYTHON=py
else
  echo 'ERROR: Python is needed to update navigation.html safely.' >&2
  exit 1
fi
mkdir -p "$HOME/smartstock-private-backups"
cp "$NAV" "$HOME/smartstock-private-backups/navigation-before-enhancements-$(date +%Y%m%d-%H%M%S).html"
"$PYTHON" - "$NAV" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1]); text = path.read_text(encoding='utf-8')
css = '    <link rel="stylesheet" th:href="@{/css/enhancements.css}">'
js = '    <script th:src="@{/js/enhancements.js}" defer></script>'
anchor = '    <script th:src="@{/js/navigation-search.js}" defer></script>'
if anchor not in text and (css not in text or js not in text):
    raise SystemExit('ERROR: Expected navigation-search script anchor not found. No change made.')
if css not in text:
    text = text.replace(anchor, anchor + '\n' + css, 1)
if js not in text:
    target = css if css in text else anchor
    text = text.replace(target, target + '\n' + js, 1)
if text.count('enhancements.css') != 1 or text.count('enhancements.js') != 1:
    raise SystemExit('ERROR: Duplicate enhancement asset references; no change made.')
path.write_text(text, encoding='utf-8', newline='\n')
print('Navigation references installed once; existing navigation preserved.')
PY
printf '%s\n' 'Shared enhancements installed locally (not deployed).'
