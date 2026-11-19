from pathlib import Path
import shutil
base=Path(__file__).resolve().parent
project=Path.cwd()
if not (project/'pom.xml').exists():
    raise SystemExit('Run from SmartStock project root (pom.xml not found).')
files=[('templates/audits.html','src/main/resources/templates/audits.html'),('static/css/audits.css','src/main/resources/static/css/audits.css')]
for source,target in files:
    dest=project/target
    dest.parent.mkdir(parents=True,exist_ok=True)
    if dest.exists(): shutil.copy2(dest,dest.with_suffix(dest.suffix+'.bak'))
    shutil.copy2(base/source,dest)
    compiled=project/'target/classes'/target.removeprefix('src/main/resources/')
    if (project/'target/classes').exists():
        compiled.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(base/source,compiled)
    print('Installed:',target)
print('Done. Hard refresh /audits in your logged-in browser.')
