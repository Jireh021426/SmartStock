package com.smartstock;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import com.smartstock.service.DailyStockClosingService;
import com.smartstock.service.LegacyInventoryMigration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "smartstock.migration.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DailyStockClosingTest {
    @Autowired DailyStockClosingService closingService;
    @Autowired DailyStockClosingRepository closings;
    @Autowired ProductRepository products;
    @Autowired StockLocationRepository locations;
    @Autowired LocationInventoryRepository inventories;
    @Autowired SaleRepository sales;
    @Autowired MockMvc mvc;
    @Autowired EmployeeRepository employeeRepository;

    @MockBean LegacyInventoryMigration migration;
    @MockBean(name = "temporaryAdminService") UserDetailsService temporaryAdminService;

    private Long productId;
    private Long locationId;
    private LocalDate today;

    private Authentication account(String name, String role) {
        return new UsernamePasswordAuthenticationToken(name, "unused",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @BeforeEach
    void setup() {
        String key = UUID.randomUUID().toString().substring(0, 10);
        Product p = new WholeChicken("CL" + key, "Closing Chicken", new BigDecimal("180.00"),
                new BigDecimal("5.000"));
        p.setCostPerKg(new BigDecimal("160.00"));
        p.changeStock(new BigDecimal("200.000"));
        p = products.save(p);
        StockLocation l = locations.save(new StockLocation("CL" + key, "Closing Stall", "STALL"));
        LocationInventory balance = new LocationInventory(l, p);
        balance.changeStock(new BigDecimal("200.000"));
        inventories.save(balance);
        productId = p.getId();
        locationId = l.getId();
        today = LocalDate.now(ZoneId.of("Asia/Manila"));
    }

    private DailyClosingForm form(String qty) {
        DailyClosingForm form = new DailyClosingForm();
        form.setBusinessDate(today);
        form.setLocationId(locationId);
        form.setProductId(productId);
        form.setCountedKg(new BigDecimal(qty));
        form.setNote("Scale and witness verified");
        return form;
    }

    @Test
    void balancedCountCanBeApprovedWithoutStockOrSalesMutation() {
        Long id = closingService.submit(account("auditor1", "AUDITOR"), form("200.000"));
        closingService.review(account("manager1", "MANAGER"), id, true, "Matches physical count");
        assertEquals(DailyStockClosing.Status.APPROVED, closings.findById(id).orElseThrow().getStatus());
        assertEquals(0, products.findById(productId).orElseThrow().getStockKg().compareTo(new BigDecimal("200.000")));
        assertFalse(sales.existsByProductId(productId));
    }

    @Test
    void discrepancyIsNotAutomaticallyBookedAsSale() {
        Long id = closingService.submit(account("auditor2", "AUDITOR"), form("195.000"));
        DailyStockClosing count = closings.findById(id).orElseThrow();
        assertEquals(0, count.getPotentialUnrecordedKg().compareTo(new BigDecimal("5.000")));
        assertEquals(0, count.getPotentialUnrecordedRevenue().compareTo(new BigDecimal("900.00")));
        assertThrows(IllegalStateException.class, () ->
                closingService.review(account("manager2", "MANAGER"), id, true, "Assume it's a sale"));
        assertFalse(sales.existsByProductId(productId));
        assertEquals(0, products.findById(productId).orElseThrow().getStockKg().compareTo(new BigDecimal("200.000")));
        closingService.review(account("manager2", "MANAGER"), id, false, "Investigate discrepancy");
        assertEquals(DailyStockClosing.Status.REJECTED, closings.findById(id).orElseThrow().getStatus());
    }

    @Test
    void rejectedCountCanBeReplacedButPendingCannotBeDuplicated() {
        Long id = closingService.submit(account("auditor3", "AUDITOR"), form("190.000"));
        assertThrows(IllegalStateException.class, () ->
                closingService.submit(account("auditor3", "AUDITOR"), form("190.000")));
        closingService.review(account("manager3", "MANAGER"), id, false, "Recount required");
        Long revised = closingService.submit(account("auditor3", "AUDITOR"), form("200.000"));
        assertNotEquals(id, revised);
    }

    @Test
    void mustUseCompanyStallAndApprovedReviewerMustDiffer() {
        Long id = closingService.submit(account("admin", "ADMIN"), form("200.000"));
        assertThrows(IllegalArgumentException.class, () ->
                closingService.review(account("admin", "ADMIN"), id, true, "Self approve"));
        StockLocation storage = locations.save(new StockLocation("ST" + UUID.randomUUID().toString().substring(0,10),
                "Main Storage", "STORAGE"));
        DailyClosingForm invalid = form("0.000");
        invalid.setLocationId(storage.getId());
        assertThrows(IllegalArgumentException.class, () ->
                closingService.submit(account("auditor", "AUDITOR"), invalid));
    }

    @Test
    void pastDatedCountIsRejectedRatherThanUsingCurrentInventory() {
        DailyClosingForm old = form("200.000");
        old.setBusinessDate(today.minusDays(1));
        assertThrows(IllegalArgumentException.class, () ->
                closingService.submit(account("auditor", "AUDITOR"), old));
    }

    @Test
    void recordedCashierSalesCountOnceButIndependentDeliveriesDoNot() {
        Product product = products.findById(productId).orElseThrow();
        StockLocation location = locations.findById(locationId).orElseThrow();
        Employee cashier = new Employee("Cashier", "c" + UUID.randomUUID().toString().substring(0,8),
                "hash", EmployeeRole.CASHIER, location);
        // A cashier can only sell when assigned a company stall.
        cashier = employeeRepository.save(cashier);
        Sale sale = new Sale(product, null, location, new BigDecimal("10.000"), PaymentMethod.CASH);
        sale.assignCashier(cashier);
        sales.save(sale);
        var summary = closingService.summary(today);
        assertEquals(0, summary.recordedKg().compareTo(new BigDecimal("10.000")));
        assertEquals(0, summary.recordedRevenue().compareTo(new BigDecimal("1800.00")));
    }

    @Test
    void managerCannotApproveAfterInventoryChangesFollowingCount() {
        Long id = closingService.submit(account("auditor5", "AUDITOR"), form("200.000"));
        Product product = products.findById(productId).orElseThrow();
        LocationInventory balance = inventories.findByLocationIdAndProductId(locationId, productId)
                .orElseThrow();
        product.changeStock(new BigDecimal("-1.000"));
        balance.changeStock(new BigDecimal("-1.000"));
        products.save(product);
        inventories.save(balance);
        assertThrows(IllegalStateException.class, () ->
                closingService.review(account("manager5", "MANAGER"), id, true, "No changes assumed"));
    }

    @Test
    void cashiersCannotAccessClosingModule() throws Exception {
        mvc.perform(get("/daily-closing").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/daily-closing").with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk());
    }
}
