#!/usr/bin/env python3
"""Install a minimal scoped CSS repair, preserving the user's live stylesheet."""
from pathlib import Path
from datetime import datetime
import os
import shutil
import sys

ROOT = Path.cwd()
TARGET = ROOT / 'src/main/resources/static/css/remittances.css'
HERE = Path(__file__).resolve().parent
PATCH = (HERE / 'theme-override.css').read_bytes()
MARKER = b'SMARTSTOCK_REMITTANCE_HISTORY_THEME_FIX_V1'

if not TARGET.is_file():
    sys.exit('ABORTED: SmartStock remittances.css not found. Run from project root.')
original = TARGET.read_bytes()
if MARKER in original:
    print('ALREADY INSTALLED: Remittance History dark-mode fix is present. No change made.')
    sys.exit(0)
if b'.remittances-page' not in original or b'.rc-foot' not in original:
    sys.exit('ABORTED: unexpected stylesheet structure. No changes made.')

backups = Path.home() / 'smartstock-private-backups'
backups.mkdir(parents=True, exist_ok=True)
when = datetime.now().strftime('%Y%m%d-%H%M%S')
backup = backups / f'remittances-css-before-theme-fix-{when}.css'
if backup.exists():
    sys.exit('ABORTED: backup filename collision. No changes made.')
shutil.copy2(TARGET, backup)

newline = b'\r\n' if b'\r\n' in original else b'\n'
patch = PATCH.replace(b'\n', newline)
separator = newline if original.endswith((b'\n', b'\r')) else newline*2
updated = original + separator + patch
staged = TARGET.with_name(TARGET.name + '.smartstock-theme-tmp')
try:
    with staged.open('xb') as f:
        f.write(updated)
    os.replace(staged, TARGET)
except Exception:
    staged.unlink(missing_ok=True)
    raise
assert MARKER in TARGET.read_bytes()
print('PASS: Remittance History theme fix installed (CSS only).')
print(f'Backup: {backup}')
print('No Java, database, sales, or remittance records modified.')
