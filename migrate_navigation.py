from pathlib import Path
import re

root = Path("src/main/resources/templates")
fragment = '<header th:replace="~{fragments/navigation :: siteHeader}"></header>'

for path in sorted(root.glob("*.html")):
    content = path.read_text(encoding="utf-8")

    if fragment in content:
        print(f"SKIP: {path.name} (already migrated)")
        continue

    updated, count = re.subn(
        r"<header\b[^>]*>.*?</header\s*>",
        fragment,
        content,
        count=1,
        flags=re.DOTALL | re.IGNORECASE
    )

    if count == 1:
        path.write_text(updated, encoding="utf-8")
        print(f"UPDATED: {path.name}")
    else:
        print(f"REVIEW: {path.name} (no header found)")
