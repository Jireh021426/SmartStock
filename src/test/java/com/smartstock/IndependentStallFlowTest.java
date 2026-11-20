package com.smartstock;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import com.smartstock.service.IndependentStallService;
import com.smartstock.service.LegacyInventoryMigration;
import com.smartstock.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class IndependentStallFlowTest {
    @Autowired IndependentStallService service;
    @Autowired ProductRepository products;
    @Autowired StockLocationRepository locations;
    @Autowired LocationInventoryRepository inventories;
    @Autowired CustomerRepository customers;
    @Autowired CreditPaymentRepository payments;
    @Autowired SaleRepository sales;
    @Autowired ReportService reports;
    @Autowired MockMvc mvc;

    @MockBean LegacyInventoryMigration legacyInventoryMigration;
    @MockBean(name = "temporaryAdminService") UserDetailsService temporaryAdminService;

    Long customerId;
    Long productId;
    Long locationId;
    LocalDate businessDate;

    @BeforeEach
    void setup() {
        String key = UUID.randomUUID().toString().substring(0, 12);
        WholeChicken product = new WholeChicken("WC_" + key, "Test chicken", new BigDecimal("190"), new BigDecimal("5"));
        product.setCostPerKg(new BigDecimal("160.00"));
        product.changeStock(new BigDecimal("200.000"));
        product = products.save(product);
        StockLocation location = locations.save(new StockLocation("ST_" + key, "Cold storage", "STORAGE"));
        LocationInventory balance = new LocationInventory(location, product);
        balance.changeStock(new BigDecimal("200.000"));
        inventories.save(balance);
        Customer customer = customers.save(new Customer("Independent Stall " + key, "PH_" + key, CustomerType.INDEPENDENT_STALL));
        customerId = customer.getId();
        productId = product.getId();
        locationId = location.getId();
        businessDate = LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(4);
    }

    private IndependentDeliveryForm delivery(String reference, String weight) {
        IndependentDeliveryForm form = new IndependentDeliveryForm();
        form.setCustomerId(customerId);
        form.setProductId(productId);
        form.setLocationId(locationId);
        form.setWeightKg(new BigDecimal(weight));
        form.setPricePerKg(new BigDecimal("180.00"));
        form.setBusinessDate(businessDate);
        form.setDeliveryReference(reference);
        return form;
    }

    @Test
    void fullDeliveryBecomesDebtAndDeductsCompanyStockOnce() {
        BigDecimal previousRevenue = (BigDecimal) reports.daily(businessDate).get("revenue");
        Sale sale = service.recordDelivery(delivery(UUID.randomUUID().toString(), "100.000"));
        assertEquals(0, sale.getTotal().compareTo(new BigDecimal("18000.00")));
        assertEquals(businessDate, sale.getBusinessDate());
        assertEquals(0, customers.findById(customerId).orElseThrow().getBalance().compareTo(new BigDecimal("18000.00")));
        assertEquals(0, products.findById(productId).orElseThrow().getStockKg().compareTo(new BigDecimal("100.000")));
        assertEquals(0, inventories.findByLocationIdAndProductId(locationId, productId).orElseThrow().getStockKg().compareTo(new BigDecimal("100.000")));
        assertNull(sale.getCashier());
        assertEquals(0, ((BigDecimal) reports.daily(businessDate).get("revenue"))
                .subtract(previousRevenue).compareTo(new BigDecimal("18000.00")));
    }

    @Test
    void partialPaymentReducesDebtAndCountsOnBusinessDateNotRecordedDay() {
        BigDecimal previousCollections = (BigDecimal) reports.daily(businessDate).get("collected");
        service.recordDelivery(delivery(UUID.randomUUID().toString(), "100.000"));
        IndependentPaymentForm payment = new IndependentPaymentForm();
        payment.setCustomerId(customerId);
        payment.setAmount(new BigDecimal("12000.00"));
        payment.setMethod(PaymentMethod.CASH);
        payment.setBusinessDate(businessDate);
        service.recordPayment(payment);
        assertEquals(0, customers.findById(customerId).orElseThrow().getBalance().compareTo(new BigDecimal("6000.00")));
        CreditPayment saved = payments.findByRequestId(payment.getRequestId()).orElseThrow();
        assertNull(saved.getCashier());
        assertEquals(businessDate, saved.getBusinessDate());
        assertEquals(0, ((BigDecimal) reports.daily(businessDate).get("collected"))
                .subtract(previousCollections).compareTo(new BigDecimal("12000.00")));
        assertThrows(IllegalArgumentException.class, () -> service.recordPayment(payment));
        assertEquals(0, customers.findById(customerId).orElseThrow().getBalance().compareTo(new BigDecimal("6000.00")));
    }

    @Test
    void deliveryReferenceCannotBeReused() {
        String ref = UUID.randomUUID().toString();
        service.recordDelivery(delivery(ref, "100.000"));
        assertThrows(IllegalArgumentException.class, () -> service.recordDelivery(delivery(ref, "50.000")));
        assertEquals(0, products.findById(productId).orElseThrow().getStockKg().compareTo(new BigDecimal("100.000")));
    }

    @Test
    void deliveryCannotExceedCompanyStock() {
        assertThrows(IllegalArgumentException.class,
                () -> service.recordDelivery(delivery(UUID.randomUUID().toString(), "201.000")));
        assertEquals(0, customers.findById(customerId).orElseThrow().getBalance().compareTo(BigDecimal.ZERO));
    }

    @Test
    void cashierCannotAccessIndependentStallManagement() throws Exception {
        mvc.perform(get("/independent-stalls").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanAccessIndependentStallManagement() throws Exception {
        mvc.perform(get("/independent-stalls").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
