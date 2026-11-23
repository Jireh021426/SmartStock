#!/usr/bin/env bash
set -euo pipefail
if [[ ! -f pom.xml ]] || [[ ! -f src/main/java/com/smartstock/config/SecurityConfig.java ]]; then
  echo 'ERROR: Start in SmartStock project root.' >&2
  exit 1
fi
if command -v python3 >/dev/null 2>&1; then
  PY=python3
elif command -v python >/dev/null 2>&1; then
  PY=python
elif command -v py >/dev/null 2>&1; then
  PY='py -3'
else
  echo 'ERROR: Python 3 is required. No changes made.' >&2
  exit 1
fi
$PY smartstock-tools/admin-sales/apply_admin_sales.py --check
$PY smartstock-tools/admin-sales/apply_admin_sales.py
git diff --check -- \
 src/main/java/com/smartstock/config/SecurityConfig.java \
 src/main/java/com/smartstock/repository/EmployeeRepository.java \
 src/main/java/com/smartstock/model/SaleForm.java \
 src/main/java/com/smartstock/model/Sale.java \
 src/main/java/com/smartstock/service/SalesService.java \
 src/main/java/com/smartstock/controller/SalesController.java \
 src/main/resources/templates/sale-form.html \
 src/main/resources/templates/sales.html \
 src/main/resources/templates/fragments/navigation.html \
 src/main/resources/templates/receipt.html \
 src/test/java/com/smartstock/HttpRoleAuthorizationTest.java
printf '\nAdmin + Cashier sales patch installed. Run Maven tests before deployment.\n'
