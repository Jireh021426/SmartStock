package com.smartstock;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import com.smartstock.service.InventoryService;
import com.smartstock.service.LegacyInventoryMigration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:smartstock_supplier_test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.sql.init.mode=never",
    "smartstock.migration.enabled=false"
})
@ActiveProfiles("test")
@Transactional
class SupplierStockInIntegrationTest {

    @Autowired private InventoryService inventory;
    @Autowired private SupplierRepository suppliers;
    @Autowired private ProductRepository products;
    @Autowired private StockLocationRepository locations;
    @Autowired private StockMovementRepository movements;

    @MockBean private LegacyInventoryMigration legacyInventoryMigration;
    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    private String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private MovementForm sampleMovement(Long supplierId) {
        String u = unique();
        WholeChicken product = products.save(new WholeChicken(
                "ST_SUP_" + u, "Supplier Test Chicken " + u,
                new BigDecimal("195.00"), new BigDecimal("5.000")));
        StockLocation location = locations.save(new StockLocation(
                "ST_SUP_" + u, "Supplier Test Storage " + u, "STORAGE"));
        MovementForm form = new MovementForm();
        form.setProductId(product.getId());
        form.setLocationId(location.getId());
        form.setType(MovementType.STOCK_IN);
        form.setWeightKg(new BigDecimal("2.000"));
        form.setPurchaseCostPerKg(new BigDecimal("180.00"));
        form.setBusinessDate(LocalDate.now(java.time.ZoneId.of("Asia/Manila")));
        form.setReason("Test supplier delivery");
        form.setSupplierId(supplierId);
        form.setDeliveryReceiptNumber("DR-TEST-001");
        return form;
    }

    @Test
    void stockInPersistsSupplierAndReceiptWithoutChangingAccounting() {
        String code = unique();
        Supplier supplier = suppliers.save(new Supplier(
                "SUP_" + code, "Marcela Test " + code, null, null));
        MovementForm form = sampleMovement(supplier.getId());

        inventory.record(form);

        StockMovement entry = movements.findAllByOrderByRecordedAtDescIdDesc().stream()
                .filter(m -> m.getProduct().getId().equals(form.getProductId()))
                .findFirst().orElseThrow();
        assertEquals(supplier.getId(), entry.getSupplier().getId());
        assertEquals("DR-TEST-001", entry.getDeliveryReceiptNumber());
        assertEquals(supplier.getName(), entry.getSupplierNameAtDelivery());
        assertEquals(supplier.getCode(), entry.getSupplierCodeAtDelivery());
        assertEquals(0, entry.getWeightKg().compareTo(new BigDecimal("2.000")));
        assertEquals(0, entry.getTotalPurchaseCost().compareTo(new BigDecimal("360.00")));
        assertEquals(0, products.findById(form.getProductId()).orElseThrow()
                .getStockKg().compareTo(new BigDecimal("2.000")));
    }

    @Test
    void stockInRejectsMissingSupplierWithoutChangingInventory() {
        MovementForm form = sampleMovement(null);
        assertThrows(IllegalArgumentException.class, () -> inventory.record(form));
        assertEquals(0, products.findById(form.getProductId()).orElseThrow()
                .getStockKg().compareTo(new BigDecimal("0.000")));
    }

    @Test
    void stockInRejectsInactiveSupplierAndMissingReceipt() {
        String code = unique();
        Supplier supplier = suppliers.save(new Supplier(
                "SUP_" + code, "Inactive Test " + code, null, null));
        MovementForm form = sampleMovement(supplier.getId());
        form.setDeliveryReceiptNumber(" ");
        assertThrows(IllegalArgumentException.class, () -> inventory.record(form));
        supplier.deactivate();
        suppliers.save(supplier);
        form.setDeliveryReceiptNumber("DR-TEST-001");
        assertThrows(IllegalArgumentException.class, () -> inventory.record(form));
    }

    @Test
    void legacyStockMovementRetainsNullableSupplierFields() {
        String u = unique();
        WholeChicken product = products.save(new WholeChicken(
                "ST_OLD_" + u, "Legacy Test " + u,
                new BigDecimal("190.00"), new BigDecimal("5.000")));
        StockMovement legacy = movements.save(new StockMovement(
                product, MovementType.STOCK_IN, new BigDecimal("1.000"), "Legacy movement"));
        assertNull(legacy.getSupplier());
        assertNull(legacy.getDeliveryReceiptNumber());
    }
}
