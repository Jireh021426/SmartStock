#!/usr/bin/env python3
"""UI-only SmartStock fix. Validate all changes first; backup, write, verify, rollback."""
from pathlib import Path
from datetime import datetime
import hashlib
import os
import sys
import tempfile
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[2]
ASSETS = Path(__file__).resolve().parent / 'assets'
EMP_PAGE = 'src/main/resources/templates/employees.html'
FORMS = {
    'src/main/resources/templates/sale-form.html': '/sales/save',
    'src/main/resources/templates/payment-form.html': '/payments/save',
    'src/main/resources/templates/independent-payment-form.html': '/independent-stalls/payments/save',
}
STATIC = 'src/main/resources/static/'
CSS_BASE = 'css/gcash-qr.css'
JS_BASE = 'js/gcash-qr.js'
QR = 'images/gcash-merchant-qr.png'
EMP_CONTRAST = 'css/employees-contrast-v2.css'
QR_DISPLAY = 'css/gcash-qr-display-v2.css'
EMP_TAG = '<link rel="stylesheet" th:href="@{/css/employees-contrast-v2.css(v=20261123b)}">'
GCASH_TAG = '<link rel="stylesheet" th:href="@{/css/gcash-qr.css(v=1)}">'
GCASH_JS = '<script defer th:src="@{/js/gcash-qr.js(v=1)}"></script>'
DISPLAY_TAG = '<link rel="stylesheet" th:href="@{/css/gcash-qr-display-v2.css(v=20261123b)}">'


def text(data, name):
    try:
        return data.decode('utf-8')
    except UnicodeDecodeError as exc:
        raise ValueError(f'{name}: invalid UTF-8: {exc}') from exc


def add_head(source, tag):
    if tag in source:
        return source
    nl = '\r\n' if '\r\n' in source else '\n'
    return source.replace('</head>', '    ' + tag + nl + '</head>')


def employee_layout_supported(source):
    common = ('employees-page', 'employee-role', 'employee-location')
    legacy = 'emp-icon-button' in source
    modern = all(key in source for key in (
        'emx-page', 'emx-icon-btn', 'emx-icon-btn--danger', 'emx-icon-btn--good'
    ))
    return all(key in source for key in common) and (legacy or modern)


def adjust_template(data, name):
    source = text(data, name)
    if source.count('</head>') != 1 or source.count('</body>') != 1:
        raise ValueError(f'{name}: cannot locate unique head/body; nothing changed')

    if name == EMP_PAGE:
        if not employee_layout_supported(source):
            raise ValueError('Employee page structure differs from supported legacy/modern layouts; nothing changed')
        source = add_head(source, EMP_TAG)
    else:
        if FORMS[name] not in source or 'GCASH' not in source.upper():
            raise ValueError(f'{name}: payment form structure differs; nothing changed')
        if '/css/gcash-qr.css' not in source:
            source = add_head(source, GCASH_TAG)
        source = add_head(source, DISPLAY_TAG)
        if '/js/gcash-qr.js' not in source:
            nl = '\r\n' if '\r\n' in source else '\n'
            source = source.replace('</body>', GCASH_JS + nl + '</body>')
    return source.encode('utf-8')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    if not (ROOT / 'pom.xml').is_file():
        raise RuntimeError('Extract archive inside SmartStock project root, then run install.py')

    before = {}
    after = {}
    for rel in [EMP_PAGE, *FORMS.keys()]:
        path = ROOT / rel
        if not path.is_file():
            raise RuntimeError(f'Missing required template: {rel}; no files changed')
        data = path.read_bytes()
        before[rel] = data
        after[rel] = adjust_template(data, rel)

    for name, asset in [
        (CSS_BASE, 'gcash-qr.css'),
        (JS_BASE, 'gcash-qr.js'),
        (QR, 'gcash-merchant-qr.png'),
        (EMP_CONTRAST, 'employees-contrast-v2.css'),
        (QR_DISPLAY, 'gcash-qr-display-v2.css'),
    ]:
        rel = STATIC + name
        src = (ASSETS / asset).read_bytes()
        dest = ROOT / rel
        prev = dest.read_bytes() if dest.is_file() else None
        # Preserve any existing official merchant QR and customized popup code.
        if name == QR and prev is not None and prev != src:
            raise RuntimeError(f'Official QR already exists but differs from uploaded image: {rel}; no files changed')
        if name in (CSS_BASE, JS_BASE) and prev is not None:
            src = prev
        if name in (EMP_CONTRAST, QR_DISPLAY) and prev is not None and prev != src:
            raise RuntimeError(f'Conflicting existing UI V2 asset: {rel}; no files changed')
        before[rel] = prev
        after[rel] = src

    changed = [path for path in after if before[path] != after[path]]
    print('PASS: checked employee template, 3 payment forms, 5 static assets; no Java or DB operations.')
    print(f'Found {len(changed)} new/changed files.')
    if '--dry-run' in sys.argv:
        print('DRY RUN PASS: no files changed.')
        return
    if not changed:
        print('Already installed; nothing to change.')
        return

    backup_home = Path.home() / 'smartstock-private-backups'
    backup_home.mkdir(parents=True, exist_ok=True)
    backup = backup_home / (
        'employee-gcash-v2-before-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f') + '.zip'
    )
    with ZipFile(backup, 'w', ZIP_DEFLATED) as archive:
        for name in changed:
            if before[name] is not None:
                archive.writestr(name, before[name])
        archive.writestr('FILES_ADDED.txt', '\n'.join(name for name in changed if before[name] is None))

    written = []
    try:
        for rel in changed:
            dest = ROOT / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            handle, temp = tempfile.mkstemp(prefix='.smartstock-ui-v2-', dir=str(dest.parent))
            try:
                with os.fdopen(handle, 'wb') as output:
                    output.write(after[rel])
                os.replace(temp, dest)
            finally:
                if os.path.exists(temp):
                    os.unlink(temp)
            written.append(rel)
        for rel in changed:
            if sha((ROOT / rel).read_bytes()) != sha(after[rel]):
                raise RuntimeError(f'Integrity check failed: {rel}')
    except Exception:
        for rel in reversed(written):
            dest = ROOT / rel
            if before[rel] is None:
                dest.unlink(missing_ok=True)
            else:
                dest.write_bytes(before[rel])
        raise

    print(f'SUCCESS: {len(changed)} files installed; SHA-256 integrity verified.')
    print('BACKUP:', backup)
    print('ORIGINAL QR IMAGE:', ROOT / (STATIC + QR))
    print('No Java, database, payment processing, or Railway deployment changes.')
    print('Run Maven tests and restart locally. Scan QR only after verifying its destination in GCash.')


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        print('ABORTED:', exc)
        sys.exit(1)
