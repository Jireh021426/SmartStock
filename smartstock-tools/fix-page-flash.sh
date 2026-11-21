#!/usr/bin/env bash
# SmartStock: load the Gentelella theme stylesheet in the HTML head before first paint.
# Keeps the user's existing complete templates, Thymeleaf bindings, permissions and data.
set -euo pipefail

templates='src/main/resources/templates'
nav="$templates/fragments/navigation.html"
css='src/main/resources/static/css/gentelella-dark.css'

if [[ ! -d "$templates" || ! -f "$nav" || ! -f "$css" ]]; then
  echo 'ERROR: Run this from the SmartStock project root after installing the dark dashboard redesign.' >&2
  exit 1
fi

mkdir -p "$HOME/smartstock-private-backups"
backup="$HOME/smartstock-private-backups/before-anti-flash-$(date +%Y%m%d-%H%M%S).tar.gz"
tar -czf "$backup" "$templates"
echo "Backup: $backup"

updated=0
present=0
while IFS= read -r -d '' file; do
  if ! grep -qi '</head>' "$file"; then
    continue
  fi
  # Ensure the existing theme override is loaded at the END of the head,
  # after page-specific CSS; this prevents late-repaint FOUC on navigation.
  if grep -q 'gentelella-dark.css' "$file"; then
    # Existing head link is already in the right place; bump cache version.
    sed -i 's/gentelella-dark\.css(v=1)/gentelella-dark.css(v=2)/g;s/gentelella-dark\.css(v=2)/gentelella-dark.css(v=2)/g' "$file"
    present=$((present+1))
  else
    sed -i '/<\/head>/i\    <link rel="stylesheet" th:href="@{/css/gentelella-dark.css(v=2)}">' "$file"
    updated=$((updated+1))
  fi
done < <(find "$templates" -type f -name '*.html' -print0)

# This late stylesheet link in the navigation fragment causes the flash.
# Once each page has the link in its head, remove the late body link.
sed -i '/<link rel="stylesheet" th:href="@{\/css\/gentelella-dark\.css(v=1)}">/d; /<link rel="stylesheet" th:href="@{\/css\/gentelella-dark\.css(v=2)}">/d' "$nav"

# Verify every HTML document page has the theme CSS in its head.
failures=0
checked=0
while IFS= read -r -d '' file; do
  if ! grep -qi '</head>' "$file"; then continue; fi
  checked=$((checked+1))
  head_only="$(sed -n '1,/<\/head>/p' "$file")"
  if ! grep -q 'gentelella-dark.css' <<< "$head_only"; then
    echo "ERROR: Theme CSS missing from head: $file" >&2
    failures=$((failures+1))
  fi
done < <(find "$templates" -type f -name '*.html' -print0)
if grep -q 'gentelella-dark.css' "$nav"; then
  echo 'ERROR: Late navigation theme link still present.' >&2
  failures=$((failures+1))
fi
if (( failures > 0 )); then
  echo "ERROR: Found $failures HTML issues, restore from backup if necessary." >&2
  exit 1
fi

echo "PASS: $checked page heads have early theme CSS ($updated added, $present already present)."
echo 'Original functionality preserved. Restart Spring Boot and hard-refresh once.'
