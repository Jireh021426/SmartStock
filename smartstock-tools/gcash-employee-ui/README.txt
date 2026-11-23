SmartStock — GCash QR popup and Employee dark-mode visibility fix

1. Stop Spring Boot.
2. Extract archive into the root of your current SmartStock project.
3. Run:
   PYTHONUTF8=1 python smartstock-tools/gcash-employee-ui/install.py --dry-run
   PYTHONUTF8=1 python smartstock-tools/gcash-employee-ui/install.py
4. Run mvn clean test, restart SmartStock.
5. For the GCash modal to show a real, scannable merchant QR, copy the OFFICIAL
   QR image furnished by your business GCash merchant account to:
   src/main/resources/static/images/gcash-merchant-qr.png
   Restart the server, and test selecting GCash in:
   - New Sale
   - Record Utang Repayment
   - Collect Independent-Stall Payment

SECURITY: This does not initiate, receive, or verify GCash payments. The merchant QR
must not be invented. Verify the actual GCash transaction separately before
recording it as paid. If the image is absent, the popup warns it is not configured.

Styling is scoped to the Employees page. The installer backs up existing templates
and CSS, then adds one CSS file, one JS file and two includes to each payment form.
No Java, database, migrations, or Railway changes.
