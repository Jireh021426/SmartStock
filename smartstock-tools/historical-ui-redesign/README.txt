SMARTSTOCK — HISTORICAL RECONCILIATION VISUAL REDESIGN v2

Complete updated application files (in payload/):
- src/main/resources/templates/historical-reconciliation.html
- src/main/resources/static/css/historical-reconciliation.css

Both files are a drop-in visual update to the history overview page.
Preserves POST/GET routes and Thymeleaf bindings; no database, model,
service, navigation, or historical-detail changes. Does not create fake data.

INSTALL (GIT BASH, STOP SPRING BOOT FIRST)

cd ~/Downloads/SmartStock-Phase4-Dashboard/SmartStock && tar -xzf "$HOME/Downloads/smartstock-historical-reconciliation-design-v2.tar.gz" -C . && PYTHONUTF8=1 python smartstock-tools/historical-ui-redesign/install.py && git diff --check

TEST

JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1' PATH="/c/Program Files/Java/jdk-21.0.12.1/bin:$PATH" mvn clean test

The installer checks the existing page's Thymeleaf bindings and prevents
clobbering unrecognized dynamic bindings; it backs up both entire original
files to ~/smartstock-private-backups/ before replacing them.

Make sure the local page loads for the MANAGER account, and inspect both themes.
Do not push to Railway before tests and separate production database migrations.
