package com.smartstock;

import com.smartstock.model.PaymentForm;
import com.smartstock.service.LegacyInventoryMigration;
import com.smartstock.service.PaymentRecoveryService;
import org.springframework.dao.DataIntegrityViolationException;

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
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class PaymentHttpConflictTest {

    @Autowired MockMvc mvc;
    @Autowired DataSource dataSource;

    @MockBean PaymentRecoveryService paymentRecovery;
    @MockBean LegacyInventoryMigration legacyInventoryMigration;
    @MockBean(name = "temporaryAdminService")
    UserDetailsService temporaryAdminService;

    @BeforeEach
    void verifyDatabaseIsolation() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTrue(
                connection.getMetaData().getURL()
                    .startsWith("jdbc:h2:mem:smartstock_test"),
                "Unsafe database connection"
            );
        }
    }


    @Test
    void unrelatedDatabaseConflictShowsNeutralError() throws Exception {
        doThrow(new DataIntegrityViolationException(
            "Simulated unrelated database constraint failure"
        )).when(paymentRecovery)
          .recordPayment(eq("testcashier"), any(PaymentForm.class));

        mvc.perform(
            post("/payments/save")
                .with(user("testcashier").roles("CASHIER"))
                .with(csrf())
                .param("customerId", "1")
                .param("amount", "500.00")
                .param("method", "CASH")
                .param("requestId", UUID.randomUUID().toString())
        )
        .andExpect(status().isOk())
        .andExpect(view().name("payment-form"))
        .andExpect(model().attribute(
            "error",
            "The payment could not be completed because of a database conflict. " +
            "Check the customer's payment history and contact an administrator " +
            "before attempting another payment."
        ));
    }

    @Test
    void duplicatePaymentShowsControlledError() throws Exception {
        String requestId = UUID.randomUUID().toString();

        doThrow(new IllegalArgumentException(
            "Payment request ID already used. " +
            "Check payment history before trying again."
        )).when(paymentRecovery)
          .recordPayment(eq("testcashier"), any(PaymentForm.class));

        mvc.perform(
            post("/payments/save")
                .with(user("testcashier").roles("CASHIER"))
                .with(csrf())
                .param("customerId", "1")
                .param("amount", "500.00")
                .param("method", "CASH")
                .param("requestId", requestId)
        )
        .andExpect(status().isOk())
        .andExpect(view().name("payment-form"))
        .andExpect(model().attributeExists("error"));
    }
}
