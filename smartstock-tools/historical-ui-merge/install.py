#!/usr/bin/env python3
"""Safe Historical Reconciliation UI merge for the user's Nov 23 source snapshot.

Usage from SmartStock project root:
  PYTHONUTF8=1 python smartstock-tools/historical-ui-merge/install.py

Exactly two complete files are installed (template and page-specific CSS). No DB or Java updates.
"""
from __future__ import annotations

from collections import Counter
from datetime import datetime
from hashlib import sha256
from html.parser import HTMLParser
from pathlib import Path
import sys
import zipfile

ROOT = Path.cwd()
TOOL = ROOT / 'smartstock-tools' / 'historical-ui-merge'
REL_HTML = Path('src/main/resources/templates/historical-reconciliation.html')
REL_CSS = Path('src/main/resources/static/css/historical-reconciliation.css')
REL = (REL_HTML, REL_CSS)

# Original SHA-256 values from the exact source archive provided on Nov 23, 2026.
BASELINE = {
    REL_HTML: 'aa745b4a18f1075ead60316823065be002f31b1386d522eb30a1682abad1ae39',
    REL_CSS: '8161a5d740f27b0db8f119b68433e0911f20c001c07741f2439d84f549b245b1',
}


def stop(message: str) -> None:
    print('ABORTED: ' + message, file=sys.stderr)
    raise SystemExit(2)


def digest(data: bytes) -> str:
    return sha256(data).hexdigest()


class TemplateBindings(HTMLParser):
    def __init__(self):
        super().__init__()
        self.values = Counter()

    def handle_starttag(self, tag, attrs):
        for key, value in attrs:
            if key.startswith('th:'):
                # The page-specific CSS link intentionally gets a cache-busting version bump.
                if key == 'th:href' and 'historical-reconciliation.css' in (value or ''):
                    continue
                self.values[(key, value)] += 1


def bindings(html: str) -> Counter:
    parser = TemplateBindings()
    parser.feed(html)
    return parser.values


def main():
    if not (ROOT / 'pom.xml').is_file():
        stop('Open Git Bash in the SmartStock project root (folder containing pom.xml).')
    source = {}
    installed = {}
    for rel in REL:
        existing = ROOT / rel
        staged = TOOL / 'payload' / rel
        if not existing.is_file():
            stop('Current source file missing: ' + rel.as_posix())
        if not staged.is_file():
            stop('Package payload missing: ' + rel.as_posix())
        if existing.is_symlink():
            stop('Refusing to overwrite a symbolic link: ' + rel.as_posix())
        source[rel] = existing.read_bytes()
        installed[rel] = staged.read_bytes()
        if not source[rel] or not installed[rel]:
            stop('Empty source/payload file: ' + rel.as_posix())

    # Idempotent if the package has already been installed.
    if all(source[rel] == installed[rel] for rel in REL):
        print('ALREADY INSTALLED — current historical UI matches this package.')
        return

    # The package is specifically merged with the supplied original, not arbitrary code revisions.
    # Refuse instead of overwriting a more recent edited source.
    for rel in REL:
        if digest(source[rel]) != BASELINE[rel]:
            stop('Live source differs from uploaded snapshot: ' + rel.as_posix()
                 + '. No files modified. Export the latest template/CSS before retrying.')

    try:
        original_html = source[REL_HTML].decode('utf-8')
        revised_html = installed[REL_HTML].decode('utf-8')
        installed[REL_CSS].decode('utf-8')
    except UnicodeError:
        stop('Source/payload contains non-UTF-8 data; nothing changed.')

    missing = bindings(original_html) - bindings(revised_html)
    if missing:
        stop('Safety check: missing Thymeleaf bindings: ' + repr(list(missing.elements())))

    invariants = (
        'th:replace="~{fragments/navigation :: siteHeader}"',
        'th:if="${canEdit}"',
        'th:action="@{/historical-reconciliation}"',
        'th:object="${form}"',
        '*{locationId}', '*{businessDate}', '*{collections}', '*{sourceFile}', '*{notes}',
        'th:each="day : ${days}"',
        '@{/historical-reconciliation/{id}(id=${day.id})}',
        '@{/css/gentelella-dark.css(v=2)}',
    )
    for fragment in invariants:
        if fragment not in revised_html:
            stop('Missing required binding/function: ' + fragment)
    print('PASS: The original Thymeleaf bindings, navigation and form routes are preserved.')

    backup_folder = Path.home() / 'smartstock-private-backups'
    backup_folder.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    backup = backup_folder / ('historical-ui-merge-before-' + stamp + '.zip')
    with zipfile.ZipFile(backup, 'w', compression=zipfile.ZIP_DEFLATED) as z:
        for rel in REL:
            z.writestr(rel.as_posix(), source[rel])
    print('Backed up current template and stylesheet:', backup)

    updated = []
    try:
        for rel in REL:
            target = ROOT / rel
            temp = target.with_name(target.name + '.ui-merge-incomplete')
            temp.write_bytes(installed[rel])
            temp.replace(target)
            updated.append(rel)
        for rel in REL:
            if digest((ROOT / rel).read_bytes()) != digest(installed[rel]):
                raise RuntimeError('SHA-256 post-install verification failed for ' + rel.as_posix())
    except Exception as exc:
        for rel in updated:
            (ROOT / rel).write_bytes(source[rel])
        stop('Install failed; updated files rolled back. Backup: ' + str(backup) + '. Cause: ' + str(exc))

    print('SUCCESS: Historical Reconciliation redesigned (2 complete files, SHA-256 verified).')
    print('No Java, business logic, migrations, database, or Railway changes.')
    print('Next: git diff --check; run mvn clean test; restart SmartStock; inspect light and dark themes.')


if __name__ == '__main__':
    main()
