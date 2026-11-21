SmartStock Daily Closing v2 UI (visual-only update)

Replaces complete files:
- src/main/resources/templates/daily-closing.html
- src/main/resources/static/css/daily-closing.css

Preserves Thymeleaf routes and manager approval conditions; intentionally does not change app-wide theme/sidebar, Java controllers, migrations, or production data.

Run from SmartStock project root after downloading archive into Downloads:
tar -xzf "$HOME/Downloads/smartstock-daily-closing-v2.tar.gz" -C .

Test:
JAVA_HOME='C:\Program Files\Java\jdk-21.0.12.1' PATH="/c/Program Files/Java/jdk-21.0.12.1/bin:$PATH" mvn -Dtest=DailyStockClosingTest test
