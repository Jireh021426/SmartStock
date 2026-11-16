package com.smartstock;

import com.smartstock.model.InventoryMovementView;
import com.smartstock.model.MovementType;
import com.smartstock.model.StockMovement;
import com.smartstock.model.WholeChicken;
import com.smartstock.repository.ProductRepository;
import com.smartstock.repository.StockMovementRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.UUID;
import com.smartstock.service.LegacyInventoryMigration;
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
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "smartstock.migration.enabled=false"
    }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryPaginationTest {

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

    private MvcResult request(String page, String size) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(
                "jdbc:h2:mem:smartstock_test",
                connection.getMetaData().getURL()
            );
        }

        var request = get("/inventory")
            .with(user("admin").roles("ADMIN"));

        if (page != null) {
            request.param("page", page);
        }

        if (size != null) {
            request.param("size", size);
        }

        return mvc.perform(request)
            .andExpect(status().isOk())
            .andReturn();
    }

    private Object model(MvcResult result, String name) {
        assertNotNull(result.getModelAndView());
        return result.getModelAndView().getModel().get(name);
    }

    @Test
    void defaultPageSizeIsTen() throws Exception {
        var result = request(null, null);
        assertEquals(10, model(result, "pageSize"));
        assertEquals(0, model(result, "currentPage"));
    }

    @Test
    void supportsTwentyFiveRows() throws Exception {
        var result = request(null, "25");
        assertEquals(25, model(result, "pageSize"));
    }

    @Test
    void supportsFiftyRows() throws Exception {
        var result = request(null, "50");
        assertEquals(50, model(result, "pageSize"));
    }

    @Test
    void rejectsUnsupportedPageSize() throws Exception {
        var result = request(null, "100000");
        assertEquals(10, model(result, "pageSize"));
    }

    @Test
    void negativePageFallsBackToZero() throws Exception {
        var result = request("-5", "10");
        assertEquals(0, model(result, "currentPage"));
    }

    @Test
    void pageBeyondLastPageIsHandled() throws Exception {
        var result = request("999", "10");
        int current = (Integer) model(result, "currentPage");
        int total = (Integer) model(result, "totalPages");

        assertTrue(current >= 0);
        assertTrue(total == 0 || current < total);
    }

    @Test
    void pageWindowContainsAtMostSevenPages() throws Exception {
        var result = request(null, null);
        Object numbers = model(result, "pageNumbers");

        assertInstanceOf(List.class, numbers);
        assertTrue(((List<?>) numbers).size() <= 7);
    }

    @Test
    void twentyFiveMovementsArePaginatedWithoutDuplicates()
            throws Exception {

        String unique = UUID.randomUUID()
                .toString().replace("-", "").substring(0, 12);

        WholeChicken chicken = new WholeChicken(
                "PAGE_" + unique,
                "Pagination Chicken " + unique,
                new BigDecimal("200.00"),
                new BigDecimal("5.000")
        );

        chicken = products.saveAndFlush(chicken);

        var insertedIds = new HashSet<Long>();

        for (int i = 1; i <= 25; i++) {
            StockMovement movement = new StockMovement(
                    chicken,
                    MovementType.STOCK_IN,
                    new BigDecimal("1.000"),
                    "PAGINATION_" + unique + "_" + i
            );

            movement = movements.saveAndFlush(movement);
            insertedIds.add(movement.getId());
        }

        var allSeen = new HashSet<Long>();
        long previousId = Long.MAX_VALUE;

        for (int page = 0; page < 3; page++) {
            MvcResult result = request(
                    String.valueOf(page), "10");

            @SuppressWarnings("unchecked")
            List<InventoryMovementView> rows =
                    (List<InventoryMovementView>)
                            model(result, "movements");

            assertEquals(
                    page < 2 ? 10 : 5,
                    rows.size(),
                    "Incorrect number of rows on page " + page
            );

            assertEquals(25L,
                    ((Number) model(result, "totalMovements")).longValue());

            assertEquals(page * 10 + 1,
                    model(result, "showingStart"));

            assertEquals(Math.min((page + 1) * 10, 25),
                    ((Number) model(result, "showingEnd")).intValue());

            int matching = 0;

            for (InventoryMovementView row : rows) {
                if (!insertedIds.contains(row.id())) {
                    continue;
                }

                matching++;

                assertTrue(allSeen.add(row.id()),
                        "Duplicate movement across pages");

                assertTrue(row.id() < previousId,
                        "Movement order is not newest first");

                previousId = row.id();
            }

            assertEquals(rows.size(), matching,
                    "Unexpected movement on page " + page);
        }

        assertEquals(25, allSeen.size(),
                "Not all 25 movements were retrieved");
    }

}
