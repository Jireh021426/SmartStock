#!/usr/bin/env python3
"""SmartStock supplier-delivery integration installer. Local source only; never connects to a database."""
from pathlib import Path
import re, shutil, tarfile, tempfile, datetime, os, sys

ROOT = Path.cwd().resolve()
THIS = Path(__file__).resolve().parent
FILES = ['src/main/java/com/smartstock/model/StockMovement.java', 'src/main/java/com/smartstock/model/MovementForm.java', 'src/main/java/com/smartstock/service/InventoryService.java', 'src/main/java/com/smartstock/model/InventoryMovementView.java', 'src/main/java/com/smartstock/controller/InventoryController.java', 'src/main/resources/templates/inventory.html', 'src/main/resources/templates/movement-form.html']
NEW_FILES = ['src/main/resources/static/css/supplier-delivery.css', 'database/migrations/2026-11-23_supplier_stock_movements.sql', 'src/test/java/com/smartstock/SupplierStockInIntegrationTest.java']

def verify_root():
    if not (ROOT / 'pom.xml').is_file():
        raise RuntimeError('Run this installer in the SmartStock project root.')
    for name in FILES:
        if not (ROOT / name).is_file():
            raise RuntimeError('Missing expected file: ' + name)
    for name in NEW_FILES:
        if (ROOT / name).exists():
            raise RuntimeError('File already exists; refusing to overwrite: ' + name)


def prepare(O):
    for name in FILES:
        dest=O/name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(ROOT/name, dest)

O = None

def build(stage):
    global O
    O = stage
    def edit(path,fn):
     p=O/path; old=p.read_text(encoding='utf-8-sig'); new=fn(old)
     if old==new: raise RuntimeError(f'No change: {path}')
     p.write_text(new, encoding='utf-8', newline='\n')
     return new

    def replace(text,old,new,label):
     n=text.count(old)
     if n!=1: raise RuntimeError(f'{label}: expected one anchor; got {n}')
     return text.replace(old,new,1)

    entity='src/main/java/com/smartstock/model/StockMovement.java'
    def mod_entity(t):
     t=replace(t,'    // ========================================\n    // PURCHASE ACCOUNTING', '''    // Supplier identity is recorded at delivery time; legacy rows remain nullable.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "delivery_receipt_number", length = 80)
    private String deliveryReceiptNumber;

    // Immutable supplier identity at delivery time, even if the supplier is renamed later.
    @Column(name = "supplier_name_at_delivery", length = 150)
    private String supplierNameAtDelivery;

    @Column(name = "supplier_code_at_delivery", length = 40)
    private String supplierCodeAtDelivery;

    public void assignSupplierDelivery(Supplier supplier, String receiptNumber) {
        if (type != MovementType.STOCK_IN) {
            throw new IllegalStateException("Supplier details are only valid for stock-in");
        }
        if (supplier == null || !supplier.isActive()) {
            throw new IllegalArgumentException("Select an active supplier");
        }
        if (receiptNumber == null || receiptNumber.isBlank()
                || receiptNumber.trim().length() > 80) {
            throw new IllegalArgumentException("Delivery receipt number must contain 1-80 characters");
        }
        this.supplier = supplier;
        this.supplierNameAtDelivery = supplier.getName();
        this.supplierCodeAtDelivery = supplier.getCode();
        this.deliveryReceiptNumber = receiptNumber.trim();
    }

    // ========================================
    // PURCHASE ACCOUNTING''', 'stock entity accounting anchor')
     t=replace(t,'    public BigDecimal getInventoryLossValue() {', '''    public Supplier getSupplier() {
        return supplier;
    }

    public String getSupplierNameAtDelivery() {
        return supplierNameAtDelivery;
    }

    public String getSupplierCodeAtDelivery() {
        return supplierCodeAtDelivery;
    }

    public String getDeliveryReceiptNumber() {
        return deliveryReceiptNumber;
    }

    public BigDecimal getInventoryLossValue() {''','stock movement getter anchor')
     return t
    edit(entity,mod_entity)

    form='src/main/java/com/smartstock/model/MovementForm.java'
    def mod_form(t):
     t=replace(t,'    // Required for manual STOCK_OUT.', '''    // Required for new supplier deliveries; not applicable to STOCK_OUT.
    private Long supplierId;

    @Size(max = 80, message = "Delivery receipt must be at most 80 characters")
    private String deliveryReceiptNumber;

    // Required for manual STOCK_OUT.''','movement form fields')
     t=replace(t,'    public Long getProductId() {','''    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public String getDeliveryReceiptNumber() {
        return deliveryReceiptNumber;
    }

    public void setDeliveryReceiptNumber(String deliveryReceiptNumber) {
        this.deliveryReceiptNumber = deliveryReceiptNumber;
    }

    public Long getProductId() {''','movement form getter')
     return t
    edit(form,mod_form)

    service='src/main/java/com/smartstock/service/InventoryService.java'
    def mod_service(t):
     t=replace(t,'    private final ProductRepository products;', '''    private final SupplierRepository suppliers;
    private final ProductRepository products;''','inventory service field')
     t=replace(t,'    public InventoryService(\n            ProductRepository products,', '''    public InventoryService(
            SupplierRepository suppliers,
            ProductRepository products,''','inventory service constructor')
     t=replace(t,'        this.products = products;', '''        this.suppliers = suppliers;
        this.products = products;''','inventory service assignment')
     t=replace(t,'        if (form.getType() == MovementType.STOCK_IN) {\n\n            BigDecimal incomingCost =', '''        if (form.getType() == MovementType.STOCK_IN) {

            // Validate supplier and receipt BEFORE adjusting stock balances.
            if (form.getSupplierId() == null) {
                throw new IllegalArgumentException("Select a supplier for stock-in");
            }
            Supplier supplier = suppliers.findById(form.getSupplierId())
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found"));
            if (!supplier.isActive()) {
                throw new IllegalArgumentException("Supplier is inactive");
            }
            String receiptNumber = form.getDeliveryReceiptNumber();
            if (receiptNumber == null || receiptNumber.isBlank()
                    || receiptNumber.trim().length() > 80) {
                throw new IllegalArgumentException("Delivery receipt number must contain 1-80 characters");
            }

            BigDecimal incomingCost =''','stockin branch')
     t=replace(t,'            movement.assignLocation(location);\n            movement.assignCategory(MovementCategory.PURCHASE);', '''            movement.assignLocation(location);
            movement.assignSupplierDelivery(supplier, receiptNumber);
            movement.assignCategory(MovementCategory.PURCHASE);''','record supplier on movement')
     return t
    edit(service,mod_service)

    view='src/main/java/com/smartstock/model/InventoryMovementView.java'
    def mod_view(t):
     t=replace(t,'        LocalDate businessDate\n) {', '''        LocalDate businessDate,
        String supplierName,
        String deliveryReceiptNumber
) {''','view record constructor')
     return t
    edit(view,mod_view)

    controller='src/main/java/com/smartstock/controller/InventoryController.java'
    def mod_controller(t):
     t=replace(t,'import com.smartstock.service.ProductService;', 'import com.smartstock.service.ProductService;\nimport com.smartstock.service.SupplierService;', 'controller import')
     t=replace(t,'    private final InventoryService inventory;', '    private final SupplierService suppliers;\n    private final InventoryService inventory;', 'controller field')
     t=replace(t,'            InventoryService inventory,\n            ProductService products,', '            InventoryService inventory,\n            SupplierService suppliers,\n            ProductService products,','controller constructor')
     t=replace(t,'        this.inventory = inventory;', '        this.inventory = inventory;\n        this.suppliers = suppliers;', 'controller assign')
     t=replace(t,'                movement.getBusinessDate()\n        );', '''                movement.getBusinessDate(),
                canSeeFinancials ? movement.getSupplierNameAtDelivery() : null,
                canSeeFinancials ? movement.getDeliveryReceiptNumber() : null
        );''','view toView')
     t=replace(t,'    private void loadFormOptions(Model model) {\n', '''    private void loadFormOptions(Model model) {
        model.addAttribute("suppliers", suppliers.activeSuppliers());
''', 'load active suppliers')
     t=replace(t,'        if (form.getType() == MovementType.STOCK_IN &&\n                form.getPurchaseCostPerKg() == null) {', '''        if (form.getType() == MovementType.STOCK_IN) {
            if (form.getSupplierId() == null) {
                result.rejectValue("supplierId", "required", "Select an active supplier");
            }
            if (form.getDeliveryReceiptNumber() == null
                    || form.getDeliveryReceiptNumber().isBlank()) {
                result.rejectValue("deliveryReceiptNumber", "required", "Enter a delivery receipt number");
            }
        }

        if (form.getType() == MovementType.STOCK_IN &&
                form.getPurchaseCostPerKg() == null) {''','stockin checks')
     return t
    edit(controller,mod_controller)

    h='src/main/resources/templates/inventory.html'
    def mod_history(t):
     t=replace(t,'                        <th>Reason / Reference</th>', '''                        <th th:if="${canSeeInventoryFinancials}">Supplier</th>
                        <th th:if="${canSeeInventoryFinancials}">Delivery receipt</th>
                        <th>Reason / Reference</th>''','inventory history head')
     t=replace(t,'                        <td th:text="${m.reason}"></td>', '''                        <td th:if="${canSeeInventoryFinancials}"
                            th:text="${m.supplierName != null ? m.supplierName : '—'}"></td>
                        <td th:if="${canSeeInventoryFinancials}"
                            th:text="${m.deliveryReceiptNumber != null ? m.deliveryReceiptNumber : '—'}"></td>
                        <td th:text="${m.reason}"></td>''','inventory history row')
     t=replace(t,'th:colspan="${canSeeInventoryFinancials ? 10 : 6}"','th:colspan="${canSeeInventoryFinancials ? 12 : 6}"','inventory empty colspan')
     return t
    edit(h,mod_history)

    movement='src/main/resources/templates/movement-form.html'
    # Insert minimal div just below opening form tag so it survives current redesign
    def mod_movement(t):
     pat=r'(<form\b(?=[^>]*th:object="\$\{movementForm\}")(?=[^>]*method="post")[^>]*>)'
     if len(re.findall(pat,t,re.S|re.I))!=1: raise RuntimeError('form tag anchor missing')
     addon='''
            <!-- Supplier-linked receipt applies only to new stock-in deliveries. -->
            <div id="supplierDeliveryFields" class="supplier-delivery-fields">
                <label>
                    Supplier (required for Stock In)
                    <select th:field="*{supplierId}" id="supplierId">
                        <option value="">Select a registered supplier</option>
                        <option th:each="s : ${suppliers}" th:value="${s.id}"
                                th:text="${s.name + ' (' + s.code + ')'}"></option>
                    </select>
                    <small class="error-text" th:if="${#fields.hasErrors('supplierId')}"
                           th:errors="*{supplierId}"></small>
                </label>
                <label>
                    Delivery Receipt / Invoice No. (required for Stock In)
                    <input type="text" id="deliveryReceiptNumber"
                           th:field="*{deliveryReceiptNumber}" maxlength="80"
                           placeholder="e.g. DR-001">
                    <small class="error-text" th:if="${#fields.hasErrors('deliveryReceiptNumber')}"
                           th:errors="*{deliveryReceiptNumber}"></small>
                </label>
            </div>
'''
     t=re.sub(pat,lambda m:m.group(1)+addon,t, count=1, flags=re.S|re.I)
     if 'supplierDeliveryFields' not in t: raise RuntimeError('form insertion failed')
     js='''
<script>
(function () {
    function toggleSupplierDelivery() {
        var type = document.getElementById('movementType') || document.querySelector('select[name="type"]');
        var fields = document.getElementById('supplierDeliveryFields');
        if (!type || !fields) return;
        var incoming = type.value === 'STOCK_IN';
        fields.hidden = !incoming;
        for (var id of ['supplierId', 'deliveryReceiptNumber']) {
            var input = document.getElementById(id);
            if (input) {
                input.required = incoming;
                input.disabled = !incoming;
            }
        }
    }
    document.addEventListener('DOMContentLoaded', function () {
        var type = document.getElementById('movementType') || document.querySelector('select[name="type"]');
        if (type) type.addEventListener('change', toggleSupplierDelivery);
        toggleSupplierDelivery();
    });
})();
</script>
'''
     t=replace(t,'</body>',js+'</body>','movement body js')
     t=replace(t,'</head>', '''    <link rel="stylesheet" th:href="@{/css/supplier-delivery.css}">
</head>''', 'movement head')
     return t
    edit(movement,mod_movement)


    for name in NEW_FILES:
        source = THIS / 'new-files' / name
        if not source.is_file():
            raise RuntimeError('Package is missing: ' + name)
        dest=O/name
        dest.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(source,dest)

def run():
    verify_root()
    with tempfile.TemporaryDirectory(prefix='smartstock-supplier-') as td:
        stage=Path(td)
        prepare(stage)
        build(stage)
        # Lightweight structural sanity checks; Maven tests still required.
        assert 'assignSupplierDelivery(supplier, receiptNumber)' in (stage/'src/main/java/com/smartstock/service/InventoryService.java').read_text(encoding='utf-8')
        assert 'supplier_name_at_delivery' in (stage/'src/main/java/com/smartstock/model/StockMovement.java').read_text(encoding='utf-8')
        assert 'th:field="*{supplierId}"' in (stage/'src/main/resources/templates/movement-form.html').read_text(encoding='utf-8')
        if '--apply' not in sys.argv:
            print('PASS: dry-run validated',len(FILES),'existing files and',len(NEW_FILES),'new files. No files modified.')
            return
        backupdir = Path.home()/'smartstock-private-backups'
        backupdir.mkdir(parents=True,exist_ok=True)
        archive=backupdir/('supplier-link-before-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'.tar.gz')
        with tarfile.open(archive, mode='w:gz') as tf:
            for name in FILES:
                tf.add(ROOT/name,arcname=name)
        print('Backup:',archive)
        for name in FILES+NEW_FILES:
            dest=ROOT/name
            dest.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(stage/name,dest)
        print('Installed supplier-linked Stock In and Stock History changes.')
        print('NO database modifications performed. DO NOT deploy without migration and passing Maven tests.')

if __name__ == '__main__':
    try: run()
    except Exception as e:
        print('ABORTED:', e, file=sys.stderr)
        sys.exit(1)
