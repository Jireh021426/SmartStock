package com.smartstock;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import com.smartstock.service.LegacyInventoryMigration;
import com.smartstock.service.RemittanceService;
import com.smartstock.service.SalesService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSaleFlowTest {

    @Autowired DataSource dataSource;
    @Autowired MockMvc mvc;
    @Autowired SalesService service;
    @Autowired RemittanceService remittanceService;
    @Autowired ProductRepository products;
    @Autowired EmployeeRepository employees;
    @Autowired StockLocationRepository locations;
    @Autowired LocationInventoryRepository balances;
    @Autowired SaleRepository sales;

    @MockBean LegacyInventoryMigration legacyInventoryMigration;
    @MockBean(name = "temporaryAdminService") UserDetailsService temporaryAdminService;

    private Long cashierId;
    private Long stallId;
    private Long productId;
    private String cashierUsername;

    @BeforeEach
    void prepare() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:smartstock_test"),
                    "Test must never modify a persistent database");
        }
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 11);
        StockLocation stall = locations.save(new StockLocation("A" + key, "Admin Sale Test Stall", "STALL"));
        WholeChicken product = new WholeChicken("P" + key, "Admin Sale Chicken", new BigDecimal("190.00"), new BigDecimal("5.000"));
        product.setCostPerKg(new BigDecimal("180.00"));
        product.changeStock(new BigDecimal("10.000"));
        product = products.save(product);
        LocationInventory balance = new LocationInventory(stall, product);
        balance.changeStock(new BigDecimal("10.000"));
        balances.save(balance);
        cashierUsername = "ac" + key;
        Employee cashier = employees.save(new Employee("Admin Test Cashier", cashierUsername,
                "not-a-real-password-hash", EmployeeRole.CASHIER, stall));
        cashierId = cashier.getId();
        stallId = stall.getId();
        productId = product.getId();
    }

    private SaleForm saleForm() {
        SaleForm form = new SaleForm();
        form.setProductId(productId);
        form.setLocationId(stallId);
        form.setWeightKg(new BigDecimal("1.000"));
        form.setPaymentMethod(PaymentMethod.CASH);
        form.setCashierId(cashierId);
        return form;
    }

    private UsernamePasswordAuthenticationToken adminIdentity() {
        return new UsernamePasswordAuthenticationToken("admin", "irrelevant",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void adminCreatesSaleAndItBelongsToCashiersRemittance() throws Exception {
        Sale sale = service.recordSaleAsAdmin(adminIdentity(), saleForm());
        assertEquals("admin", sale.getRecordedBy());
        assertNotNull(sale.getCashier());
        assertEquals(cashierId, sale.getCashier().getId());
        assertEquals(0, new BigDecimal("190.00").compareTo(sale.getTotal()));
        assertEquals(0, new BigDecimal("9.000").compareTo(
                balances.findByLocationIdAndProductId(stallId, productId).orElseThrow().getStockKg()));
        assertEquals(0, new BigDecimal("9.000").compareTo(products.findById(productId).orElseThrow().getStockKg()));
        var expected = remittanceService.expected(employees.findById(cashierId).orElseThrow(), LocalDate.now());
        assertEquals(0, new BigDecimal("190.00").compareTo(expected.cash()));
        assertEquals(0, BigDecimal.ZERO.compareTo(expected.gcash()));
    }

    @Test
    void adminCanReachSaleFormAndPost() throws Exception {
        mvc.perform(get("/sales/new").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("sale-form"));
        mvc.perform(post("/sales/save").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("productId", productId.toString())
                        .param("locationId", stallId.toString())
                        .param("cashierId", cashierId.toString())
                        .param("weightKg", "1.000")
                        .param("paymentMethod", "CASH"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/sales/*"));
    }

    @Test
    void adminCannotAssignSaleToNonexistentCashier() {
        var form = saleForm();
        form.setCashierId(Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSaleAsAdmin(adminIdentity(), form));
        assertEquals(0, new BigDecimal("10.000").compareTo(products.findById(productId).orElseThrow().getStockKg()));
    }

    @Test
    void adminCannotPostToWrongStall() {
        var other = locations.save(new StockLocation("X" + UUID.randomUUID().toString().substring(0, 11),
                "Other Sale Stall", "STALL"));
        var form = saleForm();
        form.setLocationId(other.getId());
        assertThrows(IllegalArgumentException.class,
                () -> service.recordSaleAsAdmin(adminIdentity(), form));
        assertEquals(0, new BigDecimal("10.000").compareTo(products.findById(productId).orElseThrow().getStockKg()));
    }

    @Test
    void aCashierCannotClaimAdminPrivilege() {
        var form = saleForm();
        var actor = new UsernamePasswordAuthenticationToken(cashierUsername, "irrelevant",
                List.of(new SimpleGrantedAuthority("ROLE_CASHIER")));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.recordSaleAsAdmin(actor, form));
    }

    @Test
    void cashierCannotChangeRemittanceOwnerByTamperingWithForm() {
        Employee other = employees.save(new Employee("Other Cashier", "b" + UUID.randomUUID().toString().substring(0, 11),
                "not-a-real-password-hash", EmployeeRole.CASHIER,
                locations.findById(stallId).orElseThrow()));
        var form = saleForm();
        form.setCashierId(other.getId());
        Sale sale = service.recordSale(cashierUsername, form);
        assertEquals(cashierId, sale.getCashier().getId());
        assertEquals(cashierUsername, sale.getRecordedBy());
    }
}
