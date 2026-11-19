from pathlib import Path
import shutil
root=Path(__file__).resolve().parent
project=Path.cwd()
for rel in ['src/main/resources/templates/locations.html','src/main/resources/templates/location-form.html','src/main/resources/static/css/locations.css']:
    dest=project/rel
    if not dest.parent.is_dir(): raise SystemExit('Run from SmartStock project root')
    if dest.exists(): shutil.copy2(dest, dest.with_suffix(dest.suffix+'.bak'))
    shutil.copy2(root/rel,dest)
    print('Installed:',rel)
