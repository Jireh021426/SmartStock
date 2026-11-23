#!/usr/bin/env python3
"""Install the Historical Reconciliation visual redesign with preflight + backup.

Run from the SmartStock project root: PYTHONUTF8=1 python smartstock-tools/historical-ui-redesign/install.py
Only changes one Thymeleaf overview template and one scoped CSS stylesheet.
"""
from __future__ import annotations
import hashlib
import html.parser
from collections import Counter
from datetime import datetime
from pathlib import Path
import sys
import zipfile

ROOT = Path.cwd()
TOOL = ROOT / 'smartstock-tools' / 'historical-ui-redesign'
RELPATHS = [
    Path('src/main/resources/templates/historical-reconciliation.html'),
    Path('src/main/resources/static/css/historical-reconciliation.css'),
]
BASELINE = '''<!DOCTYPE html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>'''

class ThymeleafParser(html.parser.HTMLParser):
    def __init__(self):
        super().__init__(); self.tags = Counter()
    def handle_starttag(self, tag, attrs):
        for k, v in attrs:
            if k.startswith('th:'):
                if k == 'th:href' and 'historical-reconciliation.css' in (v or ''):
                    continue
                self.tags[(k, v)] += 1

def binding_set(t: str) -> Counter:
    p = ThymeleafParser(); p.feed(t); return p.tags

def abort(reason: str) -> None:
    print('ABORTED: ' + reason, file=sys.stderr)
    sys.exit(2)

def sha(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()

def main():
    if not (ROOT / 'pom.xml').is_file() or not (ROOT / 'src/main/resources/templates').is_dir():
        abort('Run from the SmartStock project root (folder containing pom.xml).')
    source = []
    target = []
    for rel in RELPATHS:
        dest = ROOT / rel
        staged = TOOL / 'payload' / rel
        if not dest.is_file() or not staged.is_file():
            abort(f'Missing file: {rel}. Existing project source and package payload are required.')
        target.append(dest.read_bytes()); source.append(staged.read_bytes())
    try:
        live_html = target[0].decode('utf-8')
        new_html = source[0].decode('utf-8')
        live_css = target[1].decode('utf-8')
        new_css = source[1].decode('utf-8')
    except UnicodeDecodeError:
        abort('Non-UTF-8 project file. Nothing changed; inspect or share current files.')
    if any(not s.strip() for s in [live_html, live_css, new_html, new_css]):
        abort('One of the source files is empty.')
    if 'class="hr-body"' not in live_html or 'class="hr-page"' not in live_html or '.hr-page' not in live_css:
        abort('Unexpected current page layout. Nothing changed; provide current HTML/CSS for safe merge.')
    allowed_new = binding_set(new_html)
    current = binding_set(live_html)
    if current - allowed_new:
        abort('Current page has additional/changed Thymeleaf bindings not in this redesign. No files modified. Send the current template so it can be merged safely.')
    if not all(x in new_html for x in ['*{locationId}', '*{businessDate}', '*{collections}', '*{sourceFile}', '*{notes}', 'th:each="day : ${days}"']):
        abort('Package payload missing required historical fields.')
    if target == source:
        print('ALREADY INSTALLED: Historical Reconciliation redesign is up to date.')
        return
    backup_dir = Path.home() / 'smartstock-private-backups'
    backup_dir.mkdir(exist_ok=True, parents=True)
    stamp = datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    backup = backup_dir / f'historical-design-before-{stamp}.zip'
    with zipfile.ZipFile(backup, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        for rel, data in zip(RELPATHS, target):
            z.writestr(rel.as_posix(), data)
    applied = []
    try:
        for rel, data in zip(RELPATHS, source):
            dest = ROOT / rel
            tmp = dest.with_name(dest.name + '.hr-v2-pending')
            tmp.write_bytes(data)
            tmp.replace(dest)
            applied.append(dest)
    except Exception as exc:
        for dest, original in zip(applied, target):
            dest.write_bytes(original)
        abort(f'Installation failed and installed files were rolled back. Backup: {backup}. Error: {exc}')
    for rel, data in zip(RELPATHS, source):
        if sha((ROOT / rel).read_bytes()) != sha(data):
            abort(f'Post-install verification failed for {rel}; restore from {backup}.')
    print('PASS: Two source files installed and SHA-256 verified.')
    print(f'Backup: {backup}')
    print('No database changes; no Java/controller changes; no Railway deployment.')
    print('Next: git diff --check, mvn clean test, restart locally and inspect both themes.')

if __name__ == '__main__':
    main()
