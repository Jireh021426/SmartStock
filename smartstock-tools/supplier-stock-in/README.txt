SMARTSTOCK — Supplier-linked Stock In (2026-11-23)

PURPOSE
- Manual Stock In requires an active registered Supplier and delivery receipt/invoice number.
- Stock Movement History displays supplier and receipt to Admin, Manager and Auditor roles.
- Supplier ID, name and code are captured when delivery is recorded, preserving history if supplier is renamed.
- Existing movements remain untouched; supplier_id and new metadata default to NULL.
- Manual Stock Out, Sales, Transfers, Remittances, Stock balances, and weighted-average costing are unchanged.

INSTALLATION (LOCAL FIRST)
1. Back up your project or ensure changes are committed. Stop Spring Boot.
2. Extract the downloaded archive into your SmartStock project root.
3. From the SmartStock root run: bash smartstock-tools/supplier-stock-in/install.sh
   The installer validates 7 current files, refuses incompatible anchors, creates a timestamped
   SOURCE backup under ~/smartstock-private-backups, then changes 7 existing files and adds 3.
   It deliberately does NOT open or copy the H2 database and does NOT touch Railway.
4. Set JAVA_HOME/JDK/PATH and run: mvn clean test
5. Check git diff --check. Launch locally and test with one safely controlled demo transaction.

FILES CHANGED
src/main/java/com/smartstock/controller/InventoryController.java
src/main/java/com/smartstock/model/MovementForm.java
src/main/java/com/smartstock/model/StockMovement.java
src/main/java/com/smartstock/model/InventoryMovementView.java
src/main/java/com/smartstock/service/InventoryService.java
src/main/resources/templates/movement-form.html
src/main/resources/templates/inventory.html

FILES ADDED
src/main/resources/static/css/supplier-delivery.css
src/test/java/com/smartstock/SupplierStockInIntegrationTest.java
database/migrations/2026-11-23_supplier_stock_movements.sql

DEPLOYMENT CAUTION
- On Railway, production is PostgreSQL with Hibernate schema validation.
- First confirm the Railway PostgreSQL backup and apply the migration from a verified SQL session.
- Then deploy only if all Maven regression tests and your local workflow check pass.
- Do NOT post made-up supplier deliveries into production just for a professor demo.
- Historical entries containing 'Marcela Farms' in free-text reasons are NOT automatically linked:
  guessing their supplier ID or receipt would compromise the audit trail.

SAFETY
- If a source file differs too much, the dry-run aborts without changing files.
- Do not bypass these checks by copying files from 'full-updated-files-from-source-snapshot':
  those are reference copies from an older source snapshot, not replacements for your current UI.
- Check the live updated generated source directly in your src/... after successful install.

WINDOWS REPAIR (2026-11-23): fixed UTF-8 decoding and removed invalid form getters.
