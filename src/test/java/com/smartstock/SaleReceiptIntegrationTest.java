
package com.smartstock;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import com.smartstock.service.LegacyInventoryMigration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SaleReceiptIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ProductRepository products;

    @Autowired
    private StockLocationRepository locations;

    @Autowired
    private SaleRepository sales;

    @MockBean
    private LegacyInventoryMigration legacyInventoryMigration;

    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    private Long receiptId;

    private long originalSaleCount;

    @BeforeEach
    void createReceipt() throws Exception {

        try (Connection connection = dataSource.getConnection()) {
            assertEquals(
                "jdbc:h2:mem:smartstock_test",
                connection.getMetaData().getURL()
            );
        }

        String unique = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        StockLocation stallOne = locations.save(
            new StockLocation(
                "R1_" + unique,
                "Receipt Test Stall One",
                "STALL"
            )
        );

        locations.save(
            new StockLocation(
                "R2_" + unique,
                "Receipt Test Stall Two",
                "STALL"
            )
        );

        WholeChicken chicken = new WholeChicken(
            "RC_" + unique,
            "Receipt Test Chicken",
            new BigDecimal("195.00"),
            new BigDecimal("5.000")
        );

        chicken.setCostPerKg(new BigDecimal("175.00"));

        chicken = products.save(chicken);

        Sale sale = new Sale(
            chicken,
            null,
            stallOne,
            new BigDecimal("2.500"),
            PaymentMethod.CASH
        );

        sale = sales.saveAndFlush(sale);

        receiptId = sale.getId();

        originalSaleCount = sales.count();

        assertEquals(
            0,
            new BigDecimal("487.50")
                .compareTo(sale.getTotal())
        );
    }

    @Test
    void auditorCanViewExistingReceipt() throws Exception {

        mvc.perform(
            get("/sales/{id}", receiptId)
                .with(user("auditor").roles("AUDITOR"))
        )
        .andExpect(status().isOk())
        .andExpect(view().name("receipt"))
        .andExpect(model().attributeExists("sale"));
    }

    @Test
    void cashierCanViewReceiptFromAnotherStall() throws Exception {

        mvc.perform(
            get("/sales/{id}", receiptId)
                .with(user("cashier_stall_two").roles("CASHIER"))
        )
        .andExpect(status().isOk())
        .andExpect(view().name("receipt"));
    }

    @Test
    void senderCannotViewReceipt() throws Exception {

        mvc.perform(
            get("/sales/{id}", receiptId)
                .with(user("sender").roles("SENDER"))
        )
        .andExpect(status().isForbidden());
    }

    @Test
    void receiptContainsCorrectFinancialValues() throws Exception {

        mvc.perform(
            get("/sales/{id}", receiptId)
                .with(user("auditor").roles("AUDITOR"))
        )
        .andExpect(status().isOk())
        .andExpect(
            content().string(
                org.hamcrest.Matchers.containsString(
                    "Receipt Test Chicken"
                )
            )
        )
        .andExpect(
            content().string(
                org.hamcrest.Matchers.containsString(
                    "2.500 kg"
                )
            )
        )
        .andExpect(
            content().string(
                org.hamcrest.Matchers.containsString(
                    "195.00"
                )
            )
        )
        .andExpect(
            content().string(
                org.hamcrest.Matchers.containsString(
                    "487.50"
                )
            )
        );
    }

    @Test
    void viewingReceiptDoesNotCreateOrDeleteSales() throws Exception {

        mvc.perform(
            get("/sales/{id}", receiptId)
                .with(user("auditor").roles("AUDITOR"))
        )
        .andExpect(status().isOk());

        mvc.perform(
            get("/sales/{id}", receiptId)
                .with(user("cashier").roles("CASHIER"))
        )
        .andExpect(status().isOk());

        assertEquals(originalSaleCount, sales.count());

        Sale unchanged = sales.findById(receiptId)
            .orElseThrow();

        assertEquals(
            0,
            new BigDecimal("487.50")
                .compareTo(unchanged.getTotal())
        );

        assertEquals(
            PaymentMethod.CASH,
            unchanged.getPaymentMethod()
        );
    }

    @Test
    void nonexistentReceiptReturns404() throws Exception {

        mvc.perform(
            get("/sales/{id}", Long.MAX_VALUE)
                .with(user("auditor").roles("AUDITOR"))
        )
        .andExpect(status().isNotFound());
    }
}
