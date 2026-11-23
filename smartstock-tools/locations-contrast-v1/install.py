#!/usr/bin/env python3
"""Install a scoped, CSS-only Locations dark-theme contrast correction.

Checks all inputs before changes, makes a backup, and verifies SHA-256 after
writing. A failed write rolls back any files already changed.
"""
from datetime import datetime
import hashlib
import os
from pathlib import Path
import sys
import tempfile
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parents[2]
ASSET = Path(__file__).resolve().parent / "assets/locations-contrast-v1.css"
PAGE = "src/main/resources/templates/locations.html"
CSS = "src/main/resources/static/css/locations-contrast-v1.css"
TAG = '<link rel="stylesheet" th:href="@{/css/locations-contrast-v1.css(v=20261123)}">'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temp = tempfile.mkstemp(prefix=".locations-contrast-", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as handle:
            handle.write(data)
        os.replace(temp, path)
    finally:
        if os.path.exists(temp):
            os.unlink(temp)


def main():
    if not (ROOT / "pom.xml").is_file():
        raise RuntimeError("This installer must be inside the SmartStock project root")
    if not ASSET.is_file():
        raise RuntimeError("Missing packaged stylesheet; nothing changed")
    page_file = ROOT / PAGE
    css_file = ROOT / CSS
    if not page_file.is_file():
        raise RuntimeError(f"Missing {PAGE}; nothing changed")

    original_page = page_file.read_bytes()
    try:
        page = original_page.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise RuntimeError("Locations template is not UTF-8; nothing changed") from exc
    if page.count("</head>") != 1 or "/css/locations.css" not in page:
        raise RuntimeError("Unknown Locations template structure; nothing changed")
    if "locations-contrast-v1.css" in page and TAG not in page:
        raise RuntimeError("A different Locations V1 stylesheet tag exists; nothing changed")

    if TAG in page:
        updated_page = original_page
    else:
        newline = "\r\n" if "\r\n" in page else "\n"
        updated_page = page.replace("</head>", "    " + TAG + newline + "</head>").encode("utf-8")
    new_css = ASSET.read_bytes()
    original_css = css_file.read_bytes() if css_file.is_file() else None
    if original_css is not None and original_css != new_css:
        raise RuntimeError(f"Conflicting existing {CSS}; nothing changed")

    original = {PAGE: original_page, CSS: original_css}
    updated = {PAGE: updated_page, CSS: new_css}
    changed = [name for name in updated if original[name] != updated[name]]
    print("PASS: Locations template and stylesheet validated; no Java or DB changes")
    print(f"Files requiring changes: {len(changed)}")
    if "--dry-run" in sys.argv:
        print("DRY RUN PASS: no files changed")
        return
    if not changed:
        print("Already installed; nothing to change")
        return

    backup_folder = Path.home() / "smartstock-private-backups"
    backup_folder.mkdir(parents=True, exist_ok=True)
    backup = backup_folder / ("locations-contrast-v1-before-" + datetime.now().strftime("%Y%m%d-%H%M%S-%f") + ".zip")
    with ZipFile(backup, "w", ZIP_DEFLATED) as z:
        for name in changed:
            if original[name] is not None:
                z.writestr(name, original[name])
        z.writestr("FILES_ADDED.txt", "\n".join(name for name in changed if original[name] is None))

    written = []
    try:
        for name in changed:
            write_atomic(ROOT / name, updated[name])
            written.append(name)
        for name in changed:
            if digest((ROOT / name).read_bytes()) != digest(updated[name]):
                raise RuntimeError(f"Integrity mismatch: {name}")
    except Exception:
        for name in reversed(written):
            if original[name] is None:
                (ROOT / name).unlink(missing_ok=True)
            else:
                write_atomic(ROOT / name, original[name])
        raise
    print(f"SUCCESS: {len(changed)} files installed, SHA-256 verified")
    print("BACKUP:", backup)
    print("Restart SmartStock and hard-refresh /locations (Ctrl+Shift+R)")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print("ABORTED:", exc)
        raise SystemExit(1)
