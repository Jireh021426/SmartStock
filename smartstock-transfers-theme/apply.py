from pathlib import Path
import shutil
project=Path.cwd()
root=Path(__file__).resolve().parent
for rel in ['src/main/resources/templates/transfers.html','src/main/resources/static/css/transfers.css']:
    dest=project/rel
    if not dest.parent.exists(): raise SystemExit('Run this from the SmartStock project directory')
    if dest.exists(): shutil.copy2(dest,Path(str(dest)+'.bak'))
    shutil.copy2(root/rel,dest)
    print('Installed:',rel)
# Copy to already-running Spring Boot compiled resources without restarting demo DB.
for rel in ['templates/transfers.html','static/css/transfers.css']:
    src=project/'src/main/resources'/rel
    dst=project/'target/classes'/rel
    if dst.parent.exists():
        shutil.copy2(src,dst)
        print('Updated running resource:',rel)
print('Hard-refresh http://127.0.0.1:8081/transfers')
