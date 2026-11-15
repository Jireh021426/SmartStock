
package com.smartstock;

import com.smartstock.model.WholeChicken;
import com.smartstock.repository.ProductRepository;
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

import com.smartstock.model.ProductCatalogView;
import org.springframework.test.web.servlet.MvcResult;
import java.util.List;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductVisibilitySecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ProductRepository products;

    @MockBean
    private LegacyInventoryMigration legacyInventoryMigration;

    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    private String productName;

    private static final String SELLING_PRICE = "9876.54";
    private static final String PURCHASE_COST = "8765.43";

    @BeforeEach
    void createProduct() throws Exception {

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

        productName = "Visibility Test " + unique;

        WholeChicken chicken = new WholeChicken(
            "VIS_" + unique,
            productName,
            new BigDecimal(SELLING_PRICE),
            new BigDecimal("5.000")
        );

        chicken.setCostPerKg(
            new BigDecimal(PURCHASE_COST)
        );

        products.saveAndFlush(chicken);
    }

    private String pageFor(String role) throws Exception {

        return mvc.perform(
                get("/products")
                    .with(user(role.toLowerCase()).roles(role))
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private void assertFinancialDataHidden(String html) {

        assertTrue(html.contains(productName));

        assertFalse(html.contains(SELLING_PRICE));
        assertFalse(html.contains(PURCHASE_COST));

        assertFalse(html.contains(
            "Estimated stock value (selling price)"
        ));

        assertFalse(html.contains("Price / kg"));
        assertFalse(html.contains("Cost / kg"));
    }

    @Test
    void senderCannotSeeFinancialData() throws Exception {
        assertFinancialDataHidden(pageFor("SENDER"));
    }

    @Test
    void receiverCannotSeeFinancialData() throws Exception {
        assertFinancialDataHidden(pageFor("RECEIVER"));
    }

    @Test
    void cashierSeesSellingPriceButNotPurchaseCost() throws Exception {

        String html = pageFor("CASHIER");

        assertTrue(html.contains(productName));
        assertTrue(html.contains(SELLING_PRICE));

        assertFalse(html.contains(PURCHASE_COST));

        assertFalse(html.contains(
            "Estimated stock value (selling price)"
        ));

        assertTrue(html.contains("Price / kg"));
        assertFalse(html.contains("Cost / kg"));
    }

    @Test
    void auditorCanSeeAllFinancialData() throws Exception {

        assertFullFinancialVisibility(pageFor("AUDITOR"));
    }

    @Test
    void managerCanSeeAllFinancialData() throws Exception {

        assertFullFinancialVisibility(pageFor("MANAGER"));
    }

    @Test
    void adminCanSeeAllFinancialData() throws Exception {

        assertFullFinancialVisibility(pageFor("ADMIN"));
    }

    private void assertFullFinancialVisibility(String html) {

        assertTrue(html.contains(productName));
        assertTrue(html.contains(SELLING_PRICE));
        assertTrue(html.contains(PURCHASE_COST));

        assertTrue(html.contains(
            "Estimated stock value (selling price)"
        ));

        assertTrue(html.contains("Price / kg"));
        assertTrue(html.contains("Cost / kg"));
    }

    @Test
    void senderCannotSeeFinancialNavigationLinks() throws Exception {

        String html = pageFor("SENDER");

        assertFalse(html.contains("href=\"/sales\""));
        assertFalse(html.contains("href=\"/customers\""));
        assertFalse(html.contains("href=\"/dashboard\""));
    }

    @Test
    void receiverCannotSeeFinancialNavigationLinks() throws Exception {

        String html = pageFor("RECEIVER");

        assertFalse(html.contains("href=\"/sales\""));
        assertFalse(html.contains("href=\"/customers\""));
        assertFalse(html.contains("href=\"/dashboard\""));
    }

    @Test
    void cashierCannotSeeDashboardLink() throws Exception {

        String html = pageFor("CASHIER");

        assertFalse(html.contains("href=\"/dashboard\""));

        assertTrue(html.contains("href=\"/sales\""));
        assertTrue(html.contains("href=\"/customers\""));
    }


@Test
void senderModelDoesNotContainFinancialData() throws Exception {

    MvcResult result = mvc.perform(
            get("/products")
                .with(user("sender").roles("SENDER"))
        )
        .andExpect(status().isOk())
        .andReturn();

    List<?> catalog = (List<?>)
            result.getModelAndView()
                  .getModel()
                  .get("products");

    assertNotNull(catalog);
    assertFalse(catalog.isEmpty());

    for (Object item : catalog) {
        assertInstanceOf(ProductCatalogView.class, item);

        ProductCatalogView product =
                (ProductCatalogView) item;

        assertNull(product.price());
        assertNull(product.costPerKg());
    }

    assertFalse(
        result.getModelAndView()
              .getModel()
              .containsKey("totalValue")
    );
}

@Test
void cashierModelContainsSellingPriceOnly() throws Exception {

    MvcResult result = mvc.perform(
            get("/products")
                .with(user("cashier").roles("CASHIER"))
        )
        .andExpect(status().isOk())
        .andReturn();

    List<?> catalog = (List<?>)
            result.getModelAndView()
                  .getModel()
                  .get("products");

    assertNotNull(catalog);
    assertFalse(catalog.isEmpty());

    for (Object item : catalog) {
        assertInstanceOf(ProductCatalogView.class, item);

        ProductCatalogView product =
                (ProductCatalogView) item;

        assertNotNull(product.price());
        assertNull(product.costPerKg());
    }

    assertFalse(
        result.getModelAndView()
              .getModel()
              .containsKey("totalValue")
    );
}

@Test
void auditorModelContainsAuthorizedFinancialData() throws Exception {

    MvcResult result = mvc.perform(
            get("/products")
                .with(user("auditor").roles("AUDITOR"))
        )
        .andExpect(status().isOk())
        .andReturn();

    List<?> catalog = (List<?>)
            result.getModelAndView()
                  .getModel()
                  .get("products");

    assertNotNull(catalog);
    assertFalse(catalog.isEmpty());

    for (Object item : catalog) {
        assertInstanceOf(ProductCatalogView.class, item);

        ProductCatalogView product =
                (ProductCatalogView) item;

        assertNotNull(product.price());
        assertNotNull(product.costPerKg());
    }

    assertTrue(
        result.getModelAndView()
              .getModel()
              .containsKey("totalValue")
    );
}

}
