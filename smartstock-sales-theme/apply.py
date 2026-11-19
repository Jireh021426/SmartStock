from pathlib import Path
import shutil
root=Path(__file__).resolve().parent
project=Path.cwd()
paths=['src/main/resources/templates/sales.html','src/main/resources/templates/sale-form.html','src/main/resources/static/css/sales.css']
for rel in paths:
    dest=project/rel
    if not dest.parent.is_dir(): raise SystemExit('Run this installer from the SmartStock project root.')
for rel in paths:
    src=root/rel
    dest=project/rel
    if dest.exists(): shutil.copy2(dest,Path(str(dest)+'.bak'))
    shutil.copy2(src,dest)
    print('Installed:',rel)
    compiled=project/'target/classes'/Path(rel).relative_to('src/main/resources')
    if compiled.parent.is_dir():
        shutil.copy2(src,compiled)
        print('Updated running classpath:',compiled)
print('Done. Hard-refresh /sales and /sales/new.')
