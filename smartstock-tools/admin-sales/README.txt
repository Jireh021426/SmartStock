SMARTSTOCK — ADMIN + CASHIER SALES
================================

Scope:
- GET /sales/new and POST /sales/save allowed for ADMIN and CASHIER only.
- Admin selects an existing active Cashier assigned to an active physical STALL.
- The chosen cashier owns this sale for daily remittance accounting.
- The sale's recorded_by column stores who ACTUALLY entered the transaction.
- Cashiers continue using only their own assigned stall; hidden cashierId
  cannot change their remittance owner.
- A submitted cashier remittance still blocks further sales on the same day.
- Every sale still deducts location inventory, company inventory and records
  stock movements atomically; credit sales still post customer debt once.
- Other role rules (Manager/Auditor/Sender/Receiver) are unchanged.
- Customer creation and repayments remain cashier-only.

Safe install:
1. Stop local Spring Boot (Ctrl+C) before any database backup.
2. Extract tar.gz into SmartStock project directory.
3. Run: bash smartstock-tools/admin-sales/install.sh
4. Run full Maven regression tests (mvn test), before restarting/deploying.
5. Start Spring Boot with the existing local env variables. H2 ddl-auto=update
   will add nullable SALE.RECORDED_BY column to your LOCAL database.
6. In Employees, verify at least one active cashier is assigned to an active
   physical stall. Admin can then choose cashier and matching stall for sale.
7. Record a SMALL DEMO sale, verify receipt's Recorded By / Responsible
   Cashier fields, stock count and cashier's remittance totals.

Production deployment:
- Do NOT deploy before tests pass and a verified PostgreSQL backup exists.
- Since application-cloud.properties uses ddl-auto=validate, run the
  idempotent migration against Railway's PostgreSQL BEFORE the new code:
  database/migrations/2026-11-23_admin_sales_recorded_by.sql
- No migration is automatically run by this installer. Local application
  databases are not changed until you start Spring Boot.
- Do not assign transactions to another cashier unless the physical cash/GCash
  is actually remitted to that cashier. A sale entered by admin will appear
  in the selected cashier's expected remittance.

The 'full-updated-files' folder contains COMPLETE source files for reference.
The installer modifies your real current Java and HTML source by checked
anchors, preserving other CSS and UI changes. If an anchor doesn't match,
it stops without editing anything. It makes a private backup of original
source files and local H2 database prior to writing.

Created from the most recent available Java source snapshot (Nov 20, 2026).
All structural checks were run locally. Maven integration tests must still
be executed on your Windows machine; no Maven runtime is available here.
