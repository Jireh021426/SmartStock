SMARTSTOCK — PRODUCTS + FORMS + LOGIN LIGHT/DARK INTEGRATION

INCLUDES
  Complete replacement templates: products.html, payment-form.html,
  movement-form.html, login.html (same Thymeleaf fields and actions as supplied)
  Complete replacement page CSS: products.css, forms.css, login.css
  Safe installer: apply.py

IMPORTANT
  The supplied product-form.html was identical to products.html, not a product
  creation/editing form. The installer PRESERVES the existing real product-form.html
  and only adds missing forms.css, theme.css and theme.js references there.
  Do not overwrite your product form with the duplicate listing.

INSTRUCTIONS (Git Bash, from SmartStock project root):
  unzip -o ~/Downloads/SmartStock-Products-Forms-Login-Theme.zip -d .
  python smartstock-theme-integration/apply.py

This installer requires existing /css/theme.css and /js/theme.js.
It creates a timestamped backup folder OUTSIDE the SmartStock Git repository.
It never opens or changes the H2 databases, Java files or application settings.

TEST
  Open your already-running isolated in-memory demo on 127.0.0.1:8081.
  Visit /products, /products/new, /inventory/new, /login and the customer payment screen.
  Press Ctrl+Shift+R. Test both Light and Dark Mode.
  Do NOT start the application using plain mvn spring-boot:run.
