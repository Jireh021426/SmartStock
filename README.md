# SmartStock Phase 4 — Fresh Chicken Business

Java 21+, Maven, Spring Boot 3.3.13, Thymeleaf, H2.

## Run
Open the inner `SmartStock` folder containing `pom.xml`. In that folder run `mvn spring-boot:run`. Open http://localhost:8080/dashboard.

Phase 4 uses its own H2 file at `data/smartstock-chicken-phase4.mv.db`; Phase 3 data is NOT automatically migrated. Do not delete the Phase 3 database.

## Modules
Products: price/kg, cost/kg, stock kg; stock movements; cash/GCash/credit sales; customers/utang repayments; printable receipts; dashboard with date filtering, cost-of-goods snapshots, expense entry, collection breakdown and estimated stock loss.

## Reporting limitations
Gross profit = sales - cost at sale. Estimated result = gross profit - expenses - estimated non-sale stock-out cost. Non-sale stock-out costs use CURRENT product cost, not historical batch purchase cost. Stock-in purchase invoices, payment verification, expense payment methods, cash drawer counts, taxes, payroll accruals, multi-user roles, and true reconciliation are not implemented. Existing Phase 3 sales are not imported. Changing purchase cost only affects future sales. Use test data only.

## Admin Account
Username: admin

## Cashier Account
Username: cashier.demo

