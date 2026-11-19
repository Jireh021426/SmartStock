"""Install supplied SmartStock Products/Forms/Login frontend integration.
Run from the existing SmartStock Maven project root: python smartstock-theme-integration/apply.py
This script NEVER accesses databases, Java files, application settings or dependency files.
"""
from __future__ import annotations
from datetime import datetime
from pathlib import Path
import shutil
import sys

root = Path.cwd().resolve()
bundle = Path(__file__).resolve().parent
resources = root / 'src' / 'main' / 'resources'
templates = resources / 'templates'
css_dir = resources / 'static' / 'css'
js_dir = resources / 'static' / 'js'

required = [root / 'pom.xml', templates, css_dir / 'style.css', css_dir / 'theme.css', js_dir / 'theme.js', templates / 'fragments' / 'navigation.html']
missing = [str(p) for p in required if not p.exists()]
if missing:
    print('STOP: Could not verify SmartStock project/theme prerequisites:')
    print('\n'.join(missing))
    sys.exit(1)

files_to_copy = {
    templates / 'products.html': bundle / 'assets/templates/products.html',
    templates / 'payment-form.html': bundle / 'assets/templates/payment-form.html',
    templates / 'movement-form.html': bundle / 'assets/templates/movement-form.html',
    templates / 'login.html': bundle / 'assets/templates/login.html',
    css_dir / 'products.css': bundle / 'assets/css/products.css',
    css_dir / 'forms.css': bundle / 'assets/css/forms.css',
    css_dir / 'login.css': bundle / 'assets/css/login.css',
}

product_form = templates / 'product-form.html'
if not product_form.exists():
    print('STOP: Existing product-form.html is missing. Nothing changed.')
    sys.exit(1)
if any(not source.is_file() for source in files_to_copy.values()):
    print('STOP: Integration ZIP is incomplete. Nothing changed.')
    sys.exit(1)

# Preserve the real product form. The supplied "product-form.html" pasted text
# was a duplicate of the products listing and MUST NOT replace a working form.
original_form = product_form.read_text(encoding='utf-8')
form = original_form
if form.count('</head>') != 1:
    print('STOP: Unable to locate the product form head safely. Nothing changed.')
    sys.exit(1)
refs = []
if '@{/css/forms.css}' not in form:
    refs.append('    <link rel="stylesheet" th:href="@{/css/forms.css}">')
if '@{/css/theme.css}' not in form:
    refs.append('    <link rel="stylesheet" th:href="@{/css/theme.css}">')
if '@{/js/theme.js}' not in form:
    refs.append('    <script th:src="@{/js/theme.js}" defer></script>')
if refs:
    form = form.replace('</head>', '\n'.join(refs) + '\n</head>', 1)

# Backups are outside Git repository. The source database files are untouched.
backup_dir = root.parent / ('SmartStock-Theme-Backup-' + datetime.now().strftime('%Y%m%d-%H%M%S-%f'))
backup_dir.mkdir(parents=True, exist_ok=False)
for destination in [*files_to_copy, product_form]:
    if destination.is_file():
        archived = backup_dir / destination.relative_to(root)
        archived.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(destination, archived)

for destination, source in files_to_copy.items():
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)
if form != original_form:
    product_form.write_text(form, encoding='utf-8')

print('SUCCESS: Products, Payment, Stock Movement, Login and shared form styles integrated.')
print('Preserved existing product-form.html; added missing theme/form references only.')
print('Backup:', backup_dir)
print('No Java, H2 database, application properties or migration files touched.')
if 'th:object' not in form and 'th:field' not in form:
    print('WARNING: product-form.html has no visible bound form fields; check whether it was accidentally replaced by the products list.')
print('Verify in isolated demo: /products, /products/new, /inventory/new, /login, and a customer payment page.')
