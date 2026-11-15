
package com.smartstock;

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
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class HttpRoleAuthorizationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource dataSource;

    @MockBean
    private LegacyInventoryMigration legacyInventoryMigration;

    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    @BeforeEach
    void verifyDatabaseIsolation() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();

            assertNotNull(url);
            assertTrue(
                    url.startsWith("jdbc:h2:mem:smartstock_test"),
                    "Unsafe database URL: " + url
            );
        }
    }

    // Checks that security does not reject the request.
    // Controller validation errors are separate from authorization.
    private void allowedGet(String path, String role)
            throws Exception {

        int statusCode = mvc.perform(
                get(path).with(user("testuser").roles(role))
        ).andReturn().getResponse().getStatus();

        assertNotEquals(401, statusCode);
        assertNotEquals(403, statusCode);
        assertNotEquals(302, statusCode);
    }

    private void deniedGet(String path, String role)
            throws Exception {

        mvc.perform(
                get(path).with(user("testuser").roles(role))
        ).andExpect(status().isForbidden());
    }

    private void allowedPost(String path, String role)
            throws Exception {

        int statusCode = mvc.perform(
                post(path)
                        .with(user("testuser").roles(role))
                        .with(csrf())
        ).andReturn().getResponse().getStatus();

        assertNotEquals(401, statusCode);
        assertNotEquals(403, statusCode);
    }

    private void deniedPost(String path, String role)
            throws Exception {

        mvc.perform(
                post(path)
                        .with(user("testuser").roles(role))
                        .with(csrf())
        ).andExpect(status().isForbidden());
    }

    @Test
    void anonymousEmployeeAccessRedirectsToLogin()
            throws Exception {

        mvc.perform(get("/employees"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void cashierCannotManageEmployees() throws Exception {
        deniedGet("/employees", "CASHIER");
    }

    @Test
    void managerCannotManageEmployees() throws Exception {
        deniedGet("/employees", "MANAGER");
    }

    @Test
    void adminCanAccessEmployeeAdministration()
            throws Exception {
        allowedGet("/employees", "ADMIN");
    }

    @Test
    void cashierCanSubmitRemittance() throws Exception {
        allowedPost("/remittances/submit", "CASHIER");
    }

    @Test
    void cashierCannotVerifyRemittance() throws Exception {
        deniedPost("/remittances/verify", "CASHIER");
    }

    @Test
    void managerCanVerifyRemittance() throws Exception {
        allowedPost("/remittances/verify", "MANAGER");
    }

    @Test
    void adminCanVerifyRemittance() throws Exception {
        allowedPost("/remittances/verify", "ADMIN");
    }

    @Test
    void cashierCannotDispatchStock() throws Exception {
        deniedPost("/transfers/dispatch", "CASHIER");
    }

    @Test
    void senderCanDispatchStock() throws Exception {
        allowedPost("/transfers/dispatch", "SENDER");
    }

    @Test
    void missingCsrfTokenIsRejected() throws Exception {

        mvc.perform(
                post("/remittances/submit")
                        .with(user("cashier").roles("CASHIER"))
        ).andExpect(status().isForbidden());
    }

    // Dashboard security

    @Test
    void cashierCannotViewFinancialDashboard() throws Exception {
        deniedGet("/dashboard", "CASHIER");
    }

    @Test
    void auditorCannotViewFinancialDashboard() throws Exception {
        deniedGet("/dashboard", "AUDITOR");
    }

    @Test
    void receiverCannotViewFinancialDashboard() throws Exception {
        deniedGet("/dashboard", "RECEIVER");
    }

    @Test
    void managerCanViewFinancialDashboard() throws Exception {
        allowedGet("/dashboard", "MANAGER");
    }

    @Test
    void adminCanViewFinancialDashboard() throws Exception {
        allowedGet("/dashboard", "ADMIN");
    }

    // Sales visibility

    @Test
    void cashierCanViewSalesFromAllStalls() throws Exception {
        allowedGet("/sales", "CASHIER");
    }

    @Test
    void auditorCanViewSalesReadOnly() throws Exception {
        allowedGet("/sales", "AUDITOR");
    }

    @Test
    void senderCannotViewSales() throws Exception {
        deniedGet("/sales", "SENDER");
    }

    @Test
    void receiverCannotViewSales() throws Exception {
        deniedGet("/sales", "RECEIVER");
    }

    @Test
    void auditorReceives404ForMissingReceipt() throws Exception {

        mvc.perform(
                get("/sales/999999")
                        .with(user("auditor").roles("AUDITOR"))
        ).andExpect(status().isNotFound());
    }

    @Test
    void senderCannotViewSalesReceipts() throws Exception {
        deniedGet("/sales/999999", "SENDER");
    }

    // Auditors must not record sales

    @Test
    void auditorCannotOpenSaleCreationForm() throws Exception {
        deniedGet("/sales/new", "AUDITOR");
    }

    @Test
    void auditorCannotCreateSale() throws Exception {
        deniedPost("/sales/save", "AUDITOR");
    }

    @Test
    void auditorCannotCreateCustomer() throws Exception {
        deniedPost("/customers/save", "AUDITOR");
    }

    @Test
    void auditorCannotRecordCustomerPayment() throws Exception {
        deniedPost("/payments/save", "AUDITOR");
    }

    // Customer information

    @Test
    void cashierCanViewCustomers() throws Exception {
        allowedGet("/customers", "CASHIER");
    }

    @Test
    void auditorCanViewCustomersReadOnly() throws Exception {
        allowedGet("/customers", "AUDITOR");
    }

    @Test
    void senderCannotViewCustomers() throws Exception {
        deniedGet("/customers", "SENDER");
    }

    @Test
    void receiverCannotViewCustomers() throws Exception {
        deniedGet("/customers", "RECEIVER");
    }

    // Inventory visibility

    @Test
    void auditorCanViewInventoryHistory() throws Exception {
        allowedGet("/inventory", "AUDITOR");
    }

    @Test
    void senderCanViewInventoryHistory() throws Exception {
        allowedGet("/inventory", "SENDER");
    }

    @Test
    void receiverCanViewInventoryHistory() throws Exception {
        allowedGet("/inventory", "RECEIVER");
    }

    @Test
    void cashierCannotViewInventoryHistory() throws Exception {
        deniedGet("/inventory", "CASHIER");
    }

    // Default deny behavior

    @Test
    void unknownAuthenticatedEndpointIsDenied()
            throws Exception {
        deniedGet("/unapproved-security-test-route", "ADMIN");
    }

    @Test
    void cashierCanAccessSharedProductCatalog()
            throws Exception {
        allowedGet("/products", "CASHIER");
    }

    @Test
    void receiverCanAccessSharedProductCatalog()
            throws Exception {
        allowedGet("/products", "RECEIVER");
    }
}
