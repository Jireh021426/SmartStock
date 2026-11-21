SmartStock Daily Closing UI integration (visual-only)

Replaces:
  src/main/resources/templates/daily-closing.html
Adds:
  src/main/resources/static/css/daily-closing.css

Scoped CSS protects shared navbar, other pages, and light/dark theme.
Preserves existing Thymeleaf bindings, review approval conditions, routes and CSRF handling.
No migrations, Java files, or database changes. Test locally before pushing.
