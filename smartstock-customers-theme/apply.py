from pathlib import Path
import shutil
root=Path(__file__).resolve().parent
project=Path.cwd()
files=['src/main/resources/templates/customers.html','src/main/resources/templates/customer-form.html','src/main/resources/static/css/customers.css']
if not (project/'pom.xml').exists(): raise SystemExit('Run from the SmartStock project root (pom.xml required).')
for rel in files:
 source=root/rel
 target=project/rel
 target.parent.mkdir(parents=True,exist_ok=True)
 if target.exists(): shutil.copy2(target,target.with_name(target.name+'.bak'))
 shutil.copy2(source,target)
 print('Installed:',rel)
 compiled=project/'target/classes'/Path(rel).relative_to('src/main/resources')
 if compiled.parent.exists():
  compiled.parent.mkdir(parents=True,exist_ok=True)
  shutil.copy2(source,compiled)
  print('Updated running resources:',compiled)
print('Done. Hard-refresh /customers and /customers/new.')
