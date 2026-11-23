#!/usr/bin/env python3
"""Safely install GCash QR viewer and employee dark-mode contrast fixes, no backend changes."""
from __future__ import annotations
import hashlib
import os
from pathlib import Path
import sys
import tempfile
from datetime import datetime
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
MARKER_START = "/* BEGIN SMARTSTOCK EMPLOYEE DARK CONTRAST FIX 20261123 */"
MARKER_END = "/* END SMARTSTOCK EMPLOYEE DARK CONTRAST FIX 20261123 */"
TARGETS = [
    "src/main/resources/templates/sale-form.html",
    "src/main/resources/templates/payment-form.html",
    "src/main/resources/templates/independent-payment-form.html",
]
EMP_CSS = "src/main/resources/static/css/employees.css"
ASSETS = [
    "src/main/resources/static/css/gcash-qr.css",
    "src/main/resources/static/js/gcash-qr.js",
]
CSS_TAG = '<link rel="stylesheet" th:href="@{/css/gcash-qr.css(v=1)}">'
JS_TAG = '<script defer th:src="@{/js/gcash-qr.js(v=1)}"></script>'

def encode(s: str) -> bytes:
    return s.encode("utf-8")

def decode(b: bytes, path: str) -> str:
    try:
        return b.decode("utf-8")
    except UnicodeDecodeError as e:
        raise RuntimeError(f"{path} is not UTF-8: {e}") from e

def change_template(source: bytes, path: str) -> bytes:
    s = decode(source, path)
    if s.count("</head>") != 1 or s.count("</body>") != 1:
        raise RuntimeError(f"Unexpected HTML structure in {path}; no files modified")
    if "/sales/save" not in s and path.endswith("sale-form.html"):
        raise RuntimeError(f"Sales form changed unexpectedly: {path}")
    if "/payments/save" not in s and path.endswith("payment-form.html"):
        raise RuntimeError(f"Payment form changed unexpectedly: {path}")
    if "/independent-stalls/payments/save" not in s and path.endswith("independent-payment-form.html"):
        raise RuntimeError(f"Independent collection form changed unexpectedly: {path}")
    if "GCASH" not in s:
        raise RuntimeError(f"No GCash option found in {path}; no files modified")
    nl = "\r\n" if "\r\n" in s else "\n"
    if CSS_TAG not in s:
        s = s.replace("</head>", f"    {CSS_TAG}{nl}</head>")
    if JS_TAG not in s:
        s = s.replace("</body>", f"{JS_TAG}{nl}</body>")
    return encode(s)

def patch_css(source: bytes) -> bytes:
    s = decode(source, EMP_CSS)
    css = (HERE / "employee-dark-override.css").read_text(encoding="utf-8")
    if MARKER_START in s and MARKER_END in s:
        return source
    if MARKER_START in s or MARKER_END in s:
        raise RuntimeError("Employees CSS has an incomplete previous patch; no files modified")
    if "employees-page" not in s:
        raise RuntimeError("Employees CSS structure does not match expectations; no files modified")
    nl = "\r\n" if "\r\n" in s else "\n"
    addition = nl + MARKER_START + nl + css.strip().replace("\n", nl) + nl + MARKER_END + nl
    return encode(s + addition)

def main():
    if not (ROOT / "pom.xml").is_file():
        raise RuntimeError("Run the installer extracted inside your SmartStock project root")
    before: dict[str, bytes | None] = {}
    after: dict[str, bytes] = {}
    for rel in TARGETS + [EMP_CSS]:
        file = ROOT / rel
        if not file.is_file():
            raise RuntimeError(f"Required current source file missing: {rel}; no files modified")
        before[rel] = file.read_bytes()
    for rel in TARGETS:
        after[rel] = change_template(before[rel], rel)
    after[EMP_CSS] = patch_css(before[EMP_CSS])
    for rel in ASSETS:
        new = (HERE / "assets" / Path(rel).name).read_bytes()
        old = (ROOT / rel).read_bytes() if (ROOT / rel).exists() else None
        if old is not None and old != new:
            raise RuntimeError(f"Existing custom asset must not be overwritten: {rel}; no files modified")
        before[rel] = old
        after[rel] = new
    changed = [rel for rel in after if before[rel] != after[rel]]
    print(f"PASS: validated {len(TARGETS)} live form templates, Employees CSS, and 2 new assets.")
    if not changed:
        print("Already installed; no files changed.")
        return
    if "--dry-run" in sys.argv:
        print(f"DRY RUN: {len(changed)} files would be created/updated. No files modified.")
        return
    backup_dir = Path.home() / "smartstock-private-backups"
    backup_dir.mkdir(parents=True, exist_ok=True)
    name = "gcash-employee-before-" + datetime.now().strftime("%Y%m%d-%H%M%S-%f") + ".zip"
    backup = backup_dir / name
    with ZipFile(backup, "w", compression=ZIP_DEFLATED) as zipfile:
        for rel in changed:
            if before[rel] is not None:
                zipfile.writestr(rel, before[rel])
        zipfile.writestr("MANIFEST.txt", "Original files before GCash/Employee UI update. New asset files had no previous version.\n")
    written = []
    try:
        for rel in changed:
            dest = ROOT / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            fd, tmp = tempfile.mkstemp(prefix="smartstock-ui-", dir=dest.parent)
            try:
                with os.fdopen(fd, "wb") as out:
                    out.write(after[rel])
                os.replace(tmp, dest)
            finally:
                if os.path.exists(tmp):
                    os.unlink(tmp)
            written.append(rel)
        for rel in changed:
            if hashlib.sha256((ROOT / rel).read_bytes()).digest() != hashlib.sha256(after[rel]).digest():
                raise RuntimeError("Hash check failed for " + rel)
    except Exception:
        for rel in reversed(written):
            dest = ROOT / rel
            if before[rel] is None:
                dest.unlink(missing_ok=True)
            else:
                dest.write_bytes(before[rel])
        raise
    print("Backup:", backup)
    print(f"SUCCESS: Installed UI improvements in {len(changed)} files; SHA-256 verified.")
    print("Merchant QR image NOT included. Add your official QR to src/main/resources/static/images/gcash-merchant-qr.png")
    print("No database, Java, migrations, payment verification, or Railway changes.")
    print("Next: mvn clean test; restart; inspect Employees and GCash selection locally.")

if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("ABORTED:", exc)
        sys.exit(1)
