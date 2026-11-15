
package com.smartstock;

import com.smartstock.model.InventoryMovementView;
import com.smartstock.model.MovementType;
import com.smartstock.model.StockMovement;
import com.smartstock.model.WholeChicken;

import com.smartstock.repository.ProductRepository;
import com.smartstock.repository.StockMovementRepository;

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
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryVisibilitySecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ProductRepository products;

    @Autowired
    private StockMovementRepository movements;

    @MockBean
    private LegacyInventoryMigration legacyInventoryMigration;

    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    private String productName;
    private String movementReason;
    private Long movementId;

    private static final String PURCHASE_COST = "8765.43";
    private static final String TOTAL_PURCHASE = "43827.15";
    private static final String AVERAGE_COST = "8765.43";

    @BeforeEach
    void createInventoryMovement() throws Exception {

        // Never write to the production database.
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

        productName = "Inventory Visibility " + unique;
        movementReason = "SECURITY_TEST_" + unique;

        WholeChicken chicken = new WholeChicken(
            "INV_" + unique,
            productName,
            new BigDecimal("9876.54"),
            new BigDecimal("5.000")
        );

        chicken.setCostPerKg(
            new BigDecimal(PURCHASE_COST)
        );

        chicken = products.saveAndFlush(chicken);

        StockMovement movement = new StockMovement(
            chicken,
            MovementType.STOCK_IN,
            new BigDecimal("5.000"),
            movementReason
        );

        movement.setPurchaseDetails(
            new BigDecimal(PURCHASE_COST),
            new BigDecimal(AVERAGE_COST)
        );

        movement = movements.saveAndFlush(movement);
        movementId = movement.getId();
    }

    private MvcResult requestAs(String role) throws Exception {

        return mvc.perform(
                get("/inventory")
                    .with(user(role.toLowerCase()).roles(role))
            )
            .andExpect(status().isOk())
            .andReturn();
    }

    private String htmlAs(String role) throws Exception {

        return requestAs(role)
                .getResponse()
                .getContentAsString();
    }

    private InventoryMovementView findTestMovement(
            MvcResult result) {

        assertNotNull(result.getModelAndView());

        Object value = result.getModelAndView()
                .getModel()
                .get("movements");

        assertInstanceOf(List.class, value);

        List<?> history = (List<?>) value;

        assertFalse(history.isEmpty());

        for (Object item : history) {

            assertInstanceOf(
                InventoryMovementView.class,
                item
            );

            InventoryMovementView movement =
                    (InventoryMovementView) item;

            if (movementId.equals(movement.id())) {
                return movement;
            }
        }

        fail("Test movement missing from inventory history");

        return null;
    }

    private void assertRestrictedHtml(String html) {

        assertTrue(html.contains(productName));
        assertTrue(html.contains(movementReason));

        assertFalse(html.contains("Purchase Cost / kg"));
        assertFalse(html.contains("Total Purchase Cost"));
        assertFalse(html.contains("Average Cost After"));
        assertFalse(html.contains("Inventory Loss"));

        assertFalse(html.contains(PURCHASE_COST));
        assertFalse(html.contains(TOTAL_PURCHASE));

        assertFalse(html.contains("+ Record Movement"));
        assertFalse(html.contains("href=\"/inventory/new\""));

        assertFalse(html.contains("Inventory Accounting"));
    }

    private void assertAuthorizedHtml(String html) {

        assertTrue(html.contains(productName));
        assertTrue(html.contains(movementReason));

        assertTrue(html.contains("Purchase Cost / kg"));
        assertTrue(html.contains("Total Purchase Cost"));
        assertTrue(html.contains("Average Cost After"));
        assertTrue(html.contains("Inventory Loss"));

        assertTrue(html.contains(PURCHASE_COST));
        assertTrue(html.contains(TOTAL_PURCHASE));
    }

    private void assertRestrictedModel(String role)
            throws Exception {

        MvcResult result = requestAs(role);

        InventoryMovementView movement =
                findTestMovement(result);

        assertEquals(productName, movement.productName());
        assertEquals(movementReason, movement.reason());

        assertNull(movement.purchaseCostPerKg());
        assertNull(movement.totalPurchaseCost());
        assertNull(movement.averageCostAfter());
        assertNull(movement.inventoryLossValue());

        assertEquals(
            Boolean.FALSE,
            result.getModelAndView()
                  .getModel()
                  .get("canSeeInventoryFinancials")
        );

        assertEquals(
            Boolean.FALSE,
            result.getModelAndView()
                  .getModel()
                  .get("canRecordMovement")
        );
    }

    private void assertAuthorizedModel(String role)
            throws Exception {

        MvcResult result = requestAs(role);

        InventoryMovementView movement =
                findTestMovement(result);

        assertEquals(
            0,
            new BigDecimal(PURCHASE_COST)
                .compareTo(movement.purchaseCostPerKg())
        );

        assertEquals(
            0,
            new BigDecimal(TOTAL_PURCHASE)
                .compareTo(movement.totalPurchaseCost())
        );

        assertEquals(
            0,
            new BigDecimal(AVERAGE_COST)
                .compareTo(movement.averageCostAfter())
        );

        assertNull(movement.inventoryLossValue());

        assertEquals(
            Boolean.TRUE,
            result.getModelAndView()
                  .getModel()
                  .get("canSeeInventoryFinancials")
        );
    }

    // ========================================
    // SENDER
    // ========================================

    @Test
    void senderCannotSeeFinancialHtml() throws Exception {

        assertRestrictedHtml(htmlAs("SENDER"));
    }

    @Test
    void senderModelDoesNotContainFinancialValues()
            throws Exception {

        assertRestrictedModel("SENDER");
    }

    // ========================================
    // RECEIVER
    // ========================================

    @Test
    void receiverCannotSeeFinancialHtml() throws Exception {

        assertRestrictedHtml(htmlAs("RECEIVER"));
    }

    @Test
    void receiverModelDoesNotContainFinancialValues()
            throws Exception {

        assertRestrictedModel("RECEIVER");
    }

    // ========================================
    // ADMIN
    // ========================================

    @Test
    void adminCanSeeFinancialHtml() throws Exception {

        String html = htmlAs("ADMIN");

        assertAuthorizedHtml(html);

        assertTrue(html.contains("+ Record Movement"));
        assertTrue(html.contains("href=\"/inventory/new\""));
    }

    @Test
    void adminModelContainsFinancialValues()
            throws Exception {

        assertAuthorizedModel("ADMIN");

        MvcResult result = requestAs("ADMIN");

        assertEquals(
            Boolean.TRUE,
            result.getModelAndView()
                  .getModel()
                  .get("canRecordMovement")
        );
    }

    // ========================================
    // MANAGER
    // ========================================

    @Test
    void managerCanSeeFinancialHtml() throws Exception {

        String html = htmlAs("MANAGER");

        assertAuthorizedHtml(html);

        assertFalse(html.contains("+ Record Movement"));
        assertFalse(html.contains("href=\"/inventory/new\""));
    }

    @Test
    void managerModelContainsFinancialValues()
            throws Exception {

        assertAuthorizedModel("MANAGER");
    }

    // ========================================
    // AUDITOR
    // ========================================

    @Test
    void auditorCanSeeFinancialHtml() throws Exception {

        String html = htmlAs("AUDITOR");

        assertAuthorizedHtml(html);

        assertFalse(html.contains("+ Record Movement"));
        assertFalse(html.contains("href=\"/inventory/new\""));
    }

    @Test
    void auditorModelContainsFinancialValues()
            throws Exception {

        assertAuthorizedModel("AUDITOR");
    }

    // ========================================
    // CASHIER
    // ========================================

    @Test
    void cashierCannotAccessInventory() throws Exception {

        mvc.perform(
                get("/inventory")
                    .with(user("cashier").roles("CASHIER"))
            )
            .andExpect(status().isForbidden());
    }

    // ========================================
    // UNAUTHENTICATED ACCESS
    // ========================================

    @Test
    void unauthenticatedUserCannotAccessInventory()
            throws Exception {

        MvcResult result = mvc.perform(
                get("/inventory")
            )
            .andReturn();

        int status = result.getResponse().getStatus();

        assertTrue(
            status == 302 || status == 401 || status == 403,
            "Unauthenticated inventory access must be blocked"
        );

        assertFalse(
            result.getResponse()
                  .getContentAsString()
                  .contains(productName)
        );
    }
}
