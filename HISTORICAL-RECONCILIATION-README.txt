SmartStock: Historical Daily Reconciliation (safe evidence-only first release)

PURPOSE
Record historical daily collections, complete per-customer debt snapshots, and
per-product available/closing quantities from original Excel workbooks.
Do not create stock-in, sell stock, remit cash or change customer balances here.

NOVEMBER 2026 REFERENCE WORKBOOKS

1) In /historical-reconciliation, create ALJUN for 2026-11-15:
   Collections: 80650.00
   Source: November 15, 2026.xlsx
   Debts: DEPED 30000.00, PASIL 12780.00, CORDOVA 12771.00,
          RAMOS 2270.00, NERISA 2497.00, LISA 1354.00.
   Total 61672.00. Mark the debt list complete ONLY after all names are entered.
   As the baseline, its estimated total sales is unavailable without Nov 14.

2) Create ALJUN for 2026-11-16:
   Collections: 79564.00
   Source: November 16, 2026.xlsx
   Debts: DEPED 30000.00, PASIL 12780.00, CORDOVA 11286.00,
          RAMOS 3080.00, NERISA 2197.00, LISA 1643.00.
   Total 60986.00. Mark debt list complete.
   Estimated total sales: 79564 + 60986 - 61672 = 78878.00.

   Stock (Aljun; Whole Chicken): Available 219.850 KG,
   physical closing 0.000 KG, known losses 0.000 KG unless documented,
   historical price 175.00 PHP/KG from Stalls sheet.
   Potential whole-chicken retail value: 38473.75 PHP.
   Leave STOCK LIST INCOMPLETE until every fresh and frozen product is entered.

SOURCE CAVEATS
- The November 16 workbook's Distribution! Aljun 219.850 KG includes carryover
  52.200 KG from Nov 15 plus new allocations, not just Marcela's 103.250 KG.
- Stock depletion is NOT proof of sale; unexplained loss must be investigated.
- The 78878.00 figure assumes the 79564 collected belongs to the same stall,
  all six customer debt balances are complete, and no unrecorded write-offs,
  cross-stall settlements, discounts or unrelated receipts distort the equation.
- Another supplier and frozen goods mean whole-chicken retail value is only a
  part of all-product stock estimates.
- The cash float is reported separately in the original spreadsheet.
  Determine its treatment before final reconciliation.
- Past sales are NOT posted to the live Sales module, and past physical counts
  are NOT posted to the existing current-day Daily Closing module.
- Supplier and independent-stall distributions are NOT automatically imported
  into historical tables in this first version. Existing operations unchanged.

DEPLOYMENT
1. Extract this archive in the ROOT SmartStock directory containing pom.xml.
2. Test with local H2 test profile (provided integration tests).
3. Stop any old local app; apply database/migrations/2026-11-21_historical_reconciliation.sql
   to a local copy of the PostgreSQL database and check startup with cloud profile.
4. Run FULL mvn test. Verify current workflows (Sales, Transfers, Daily Closing).
5. Verify a new Railway production backup, apply the migration to production,
   THEN commit and push to frontend-professor-demo.
6. Rotate previously exposed Railway production Postgres credential.

LOCAL TEST DATA HELPER
An OPTIONAL script at database/examples/2026-11-aljun-historical-LOCAL-ONLY.sql
inserts Nov 15/16 Aljun reference records into new evidence tables only.
It is idempotent and NEVER to be applied to production as a migration.
Requires ALJ location to exist. Run only on a local test database, AFTER the
historical migration. If you are already entering those dates manually, skip it.

This is a RECORDING interface, not an XLSX file importer. The original XLSX
workbooks stay unchanged. Automated extraction / mapping can be added later.
