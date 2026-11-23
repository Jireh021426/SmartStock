#!/usr/bin/env python3
"""Safely add audited admin-on-behalf-of-cashier sale entry to SmartStock.

No file is changed until all expected source anchors are found.
Files being edited are backed up in ~/smartstock-private-backups.
"""
from pathlib import Path
from datetime import datetime
import argparse, os, shutil, sys, tarfile, tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--root', default='.')
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
root = Path(args.root).resolve()
if not (root / 'pom.xml').is_file():
    raise SystemExit('Not a SmartStock Maven project: pom.xml missing. Run from project root.')
changed = {}

def edit(rel, operations):
    path = root / rel
    if not path.is_file():
        raise RuntimeError(f'MISSING SOURCE: {rel}')
    original = path.read_text(encoding='utf-8-sig')
    value = original
    for old, new in operations:
        if new in value and (old not in value or old in new):
            # Already applied; do not repeat an insertion containing its anchor.
            continue
        count = value.count(old)
        if count != 1:
            raise RuntimeError(f'SOURCE MISMATCH: {rel}: expected one anchor, found {count}: {old[:95]!r}')
        value = value.replace(old, new)
    if value != original:
        changed[rel] = value

J = 'src/main/java/com/smartstock/'
T = 'src/main/resources/templates/'

edit(J+'config/SecurityConfig.java', [
('''                // Sale creation is cashier-only
                .requestMatchers(
                        HttpMethod.GET,
                        "/sales/new"
                ).hasRole("CASHIER")

                .requestMatchers(
                        HttpMethod.POST,
                        "/sales/save"
                ).hasRole("CASHIER")''', '''                // Administrators can record sales on behalf of an active
                // stall cashier; remittance ownership stays with that cashier.
                .requestMatchers(
                        HttpMethod.GET,
                        "/sales/new"
                ).hasAnyRole("ADMIN", "CASHIER")

                .requestMatchers(
                        HttpMethod.POST,
                        "/sales/save"
                ).hasAnyRole("ADMIN", "CASHIER")''')
])

edit(J+'repository/EmployeeRepository.java', [
('''    boolean existsByUsername(String username);''','''    // Admin sale entry locks the responsible cashier, preserving
    // serialization with cashier remittances and normal cashier sales.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"assignedLocation"})
    @Query("SELECT e FROM Employee e WHERE e.id = :id")
    Optional<Employee> findLockedById(@Param("id") Long id);

    boolean existsByUsername(String username);'''),
('''    List<Employee> findByRole(EmployeeRole role);''', '''    @EntityGraph(attributePaths = {"assignedLocation"})
    List<Employee> findByRole(EmployeeRole role);''')
])

edit(J+'model/SaleForm.java', [
('''    private Long customerId;

    // ========================================
    // GETTERS AND SETTERS''', '''    private Long customerId;

    // An administrator must select the responsible cashier. Cashiers' own
    // sale forms ignore this field (cannot change remittance ownership).
    private Long cashierId;

    public Long getCashierId() { return cashierId; }
    public void setCashierId(Long cashierId) { this.cashierId = cashierId; }

    // ========================================
    // GETTERS AND SETTERS''')
])

edit(J+'model/Sale.java', [
('''    public Employee getCashier() { return cashier; }
    public void assignCashier(Employee employee) {''','''    // Authenticated person who actually entered the sale. Nullable for
    // legacy and independent-stall delivery records.
    @Column(name = "recorded_by", length = 100)
    private String recordedBy;

    public String getRecordedBy() { return recordedBy; }

    public void assignRecordedBy(String username) {
        if (recordedBy != null || username == null || username.isBlank()
                || username.trim().length() > 100) {
            throw new IllegalArgumentException("Valid sale recorder is required");
        }
        this.recordedBy = username.trim();
    }

    public Employee getCashier() { return cashier; }
    public void assignCashier(Employee employee) {''')
])

edit(J+'service/SalesService.java', [
('''import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;''','''import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;'''),
('''    @Transactional
    public Sale recordSale(String username, SaleForm form) {

        Employee cashier = requireCashier(username);
        requireOpenRemittanceDay(cashier);

        StockLocation assignedLocation = cashier.getAssignedLocation();

        // Validate form.''', '''    @Transactional
    public Sale recordSale(String username, SaleForm form) {
        Employee cashier = requireCashier(username);
        // This existing cashier-only entry point intentionally ignores any
        // cashierId supplied by the browser.
        return recordSaleForCashier(cashier, form, username);
    }

    @Transactional
    public Sale recordSaleAsAdmin(Authentication authentication, SaleForm form) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream().noneMatch(a ->
                        "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Administrator role required");
        }
        if (form == null || form.getCashierId() == null) {
            throw new IllegalArgumentException(
                    "Choose the cashier responsible for this sale and remittance");
        }
        Employee cashier = employees.findLockedById(form.getCashierId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Selected cashier account not found"));
        validateCashier(cashier);
        return recordSaleForCashier(cashier, form, authentication.getName());
    }

    private Sale recordSaleForCashier(
            Employee cashier, SaleForm form, String recordedBy) {
        requireOpenRemittanceDay(cashier);

        StockLocation assignedLocation = cashier.getAssignedLocation();

        // Validate form.'''),
('''        sale.assignCashier(cashier);

        // Update customer balance for credit sales.''','''        sale.assignCashier(cashier);
        sale.assignRecordedBy(recordedBy);

        // Update customer balance for credit sales.'''),
('''        if (!cashier.isActive() ||
                cashier.getRole() != EmployeeRole.CASHIER) {

            throw new IllegalArgumentException(
                    "Only active cashiers may record sales.");
        }

        StockLocation assigned = cashier.getAssignedLocation();''', '''        validateCashier(cashier);
        return cashier;
    }

    private void validateCashier(Employee cashier) {
        if (cashier == null || !cashier.isActive() ||
                cashier.getRole() != EmployeeRole.CASHIER) {
            throw new IllegalArgumentException(
                    "An active cashier is required for sale recording.");
        }

        StockLocation assigned = cashier.getAssignedLocation();'''),
('''                    "Cashier requires an active STALL assignment.");
        }

        return cashier;
    }

    // ========================================
    // CUSTOMER MANAGEMENT''', '''                    "Cashier requires an active STALL assignment.");
        }
    }

    // ========================================
    // CUSTOMER MANAGEMENT''')
])

edit(J+'controller/SalesController.java', [
('''            Sale sale = sales.recordSale(
                    authentication.getName(), form);''', '''            Sale sale = isAdmin(authentication)
                    ? sales.recordSaleAsAdmin(authentication, form)
                    : sales.recordSale(authentication.getName(), form);'''),
('''    private void fillSaleOptions(
            Model model,
            Authentication authentication) {

        Employee cashier = employees.findByUsername(''', '''    private static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(a ->
                        "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private void fillSaleOptions(
            Model model,
            Authentication authentication) {

        model.addAttribute("adminSale", isAdmin(authentication));
        model.addAttribute("products", products.findAll());
        model.addAttribute("customers", sales.retailCustomers());

        if (isAdmin(authentication)) {
            // Admin selects a real active cashier. The same cashier will own
            // the sale for close-of-day and remittance reconciliation.
            var cashiers = employees.findByRole(EmployeeRole.CASHIER)
                    .stream()
                    .filter(Employee::isActive)
                    .filter(e -> e.getAssignedLocation() != null
                            && e.getAssignedLocation().isActive()
                            && !"LEGACY".equalsIgnoreCase(
                                    e.getAssignedLocation().getCode())
                            && "STALL".equalsIgnoreCase(
                                    e.getAssignedLocation().getLocationType()))
                    .sorted(java.util.Comparator.comparing(Employee::getFullName))
                    .toList();
            model.addAttribute("cashiers", cashiers);
            model.addAttribute("locations", cashiers.stream()
                    .map(Employee::getAssignedLocation)
                    .distinct().toList());
            return;
        }

        Employee cashier = employees.findByUsername('''),
('''        model.addAttribute("products", products.findAll());

        model.addAttribute(
                "locations",
                java.util.List.of(cashier.getAssignedLocation()));

        model.addAttribute(
                "customers",
                sales.retailCustomers());''','''        model.addAttribute(
                "locations",
                java.util.List.of(cashier.getAssignedLocation()));''')
])

edit(T+'sale-form.html', [
('''                <!-- SELLING LOCATION -->''', '''                <!-- ADMIN-ONLY: sale is attributed to this cashier for remittances -->
                <label class="full-width" th:if="${adminSale}">
                    Responsible Cashier (for remittance)
                    <select id="cashierId" th:field="*{cashierId}" required>
                        <option value="">Select active cashier</option>
                        <option th:each="e : ${cashiers}" th:value="${e.id}"
                                th:text="${e.fullName + ' — ' + e.assignedLocation.name}"></option>
                    </select>
                    <span class="form-hint">Admin enters this sale; the selected cashier's stall and remittance will be used. Choose the matching selling location below.</span>
                    <span class="error-text" th:if="${#fields.hasErrors('cashierId')}" th:errors="*{cashierId}"></span>
                </label>
                <p class="form-hint full-width" th:if="${adminSale and #lists.isEmpty(cashiers)}">
                    No active cashier is assigned to a stall. Create and assign one under Employees before recording sales.
                </p>

                <!-- SELLING LOCATION -->'''),
('''<option value="">Assigned selling location</option>''','''<option value="" th:text="${adminSale} ? 'Choose the cashier assigned stall' : 'Assigned selling location'">Assigned selling location</option>'''),
('''<span class="form-hint" style="border: none; padding: 4px 0 0 0; background: transparent;">Only your assigned stall is available. Changing the request cannot bypass this restriction.</span>''','''<span class="form-hint" th:unless="${adminSale}" style="border: none; padding: 4px 0 0 0; background: transparent;">Only your assigned stall is available. Changing the request cannot bypass this restriction.</span>
                    <span class="form-hint" th:if="${adminSale}" style="border: none; padding: 4px 0 0 0; background: transparent;">Choose the location assigned to the responsible cashier; server validation enforces the match.</span>''')
])

edit(T+'sales.html', [
('''<a class="btn btn-primary" th:href="@{/sales/new}">+ New Sale</a>''', '''<a class="btn btn-primary" th:if="${isAdmin or isCashier}" th:href="@{/sales/new}">+ New Sale</a>''')
])

edit(T+'fragments/navigation.html', [
('''th:if="${nav.cashier}" th:href="@{/sales/new}"''','''th:if="${nav.admin or nav.cashier}" th:href="@{/sales/new}"'''),
('''<a th:if="${nav.cashier}"
           th:href="@{/sales/new}">''','''<a th:if="${nav.admin or nav.cashier}"
           th:href="@{/sales/new}">''')
])

edit(T+'receipt.html', [
('''            <div class="receipt-row">
                <span class="receipt-label">Product</span>''','''            <div class="receipt-row" th:if="${sale.recordedBy != null}">
                <span class="receipt-label">Recorded by</span>
                <span class="receipt-value" th:text="${sale.recordedBy}">admin</span>
            </div>
            <div class="receipt-row" th:if="${sale.cashier != null}">
                <span class="receipt-label">Responsible cashier</span>
                <span class="receipt-value" th:text="${sale.cashier.fullName}">Cashier</span>
            </div>
            <div class="receipt-row">
                <span class="receipt-label">Product</span>''')
])

edit('src/test/java/com/smartstock/HttpRoleAuthorizationTest.java', [
('''    // Auditors must not record sales''', '''    @Test
    void adminMayOpenSaleCreationForm() throws Exception {
        allowedGet("/sales/new", "ADMIN");
    }

    @Test
    void adminMaySubmitSaleFormForValidation() throws Exception {
        allowedPost("/sales/save", "ADMIN");
    }

    @Test
    void managerCannotCreateSales() throws Exception {
        deniedGet("/sales/new", "MANAGER");
        deniedPost("/sales/save", "MANAGER");
    }

    // Auditors must not record sales''')
])

# Include a new integration regression test and a production migration script.
def add_new(rel, bundled):
    path = root / rel
    content = (Path(__file__).resolve().parent / bundled).read_text(encoding='utf-8')
    if path.exists():
        if path.read_text(encoding='utf-8-sig') != content:
            raise RuntimeError(f'EXISTING FILE CONFLICT: {rel} (nothing changed)')
    else:
        changed[rel] = content

add_new('src/test/java/com/smartstock/AdminSaleFlowTest.java', 'AdminSaleFlowTest.java')
add_new('database/migrations/2026-11-23_admin_sales_recorded_by.sql', '2026-11-23_admin_sales_recorded_by.sql')

if not changed:
    print('Admin sale permissions already installed; no changes needed.')
    sys.exit(0)

print(f'Verified {len(changed)} file(s):')
for path in changed:
    print('  '+path)

if args.check:
    print('Dry run only. No files modified.')
    sys.exit(0)

backup_folder = Path.home() / 'smartstock-private-backups'
backup_folder.mkdir(parents=True, exist_ok=True)
backup = backup_folder / f'admin-sales-before-{datetime.now().strftime("%Y%m%d-%H%M%S")}.tar.gz'
with tarfile.open(backup, 'w:gz') as archive:
    for rel in changed:
        if (root / rel).is_file():
            archive.add(root / rel, arcname=rel)
    h2 = root / 'data/smartstock-chicken-phase3.mv.db'
    if h2.is_file():
        archive.add(h2, arcname='data/smartstock-chicken-phase3.mv.db')
print('Backup: ' + str(backup))

# Check all files first, then update using atomic writes.
for rel, content in changed.items():
    dest = root / rel
    handle, tmp = tempfile.mkstemp(prefix='.smartstock-sales-', dir=dest.parent)
    try:
        with os.fdopen(handle, 'w', encoding='utf-8', newline='\n') as out:
            out.write(content)
        os.replace(tmp, dest)
    finally:
        if os.path.exists(tmp):
            os.remove(tmp)
print('Admin sales permissions installed. Do NOT deploy before migration + tests.')
