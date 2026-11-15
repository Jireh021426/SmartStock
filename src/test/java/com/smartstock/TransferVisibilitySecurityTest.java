
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
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferVisibilitySecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ProductRepository products;

    @Autowired
    private StockLocationRepository locations;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private StockTransferRepository transfers;

    @Autowired
    private TransferResolutionRepository resolutions;

    @MockBean
    private LegacyInventoryMigration legacyInventoryMigration;

    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    private String senderUsername;
    private String receiverUsername;

    private Long visibleTransferId;
    private Long hiddenTransferId;

    private String visibleProductName;
    private String hiddenProductName;

    private String investigationExplanation;

    private static final String SECRET_LOSS = "987654.32";

    @BeforeEach
    void createTransferFixtures() throws Exception {

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

        StockLocation source = locations.saveAndFlush(
            new StockLocation(
                "SRC_" + unique,
                "Source " + unique,
                "WAREHOUSE"
            )
        );

        StockLocation destination = locations.saveAndFlush(
            new StockLocation(
                "DST_" + unique,
                "Destination " + unique,
                "STALL"
            )
        );

        StockLocation otherSource = locations.saveAndFlush(
            new StockLocation(
                "OS_" + unique,
                "Other Source " + unique,
                "WAREHOUSE"
            )
        );

        StockLocation otherDestination = locations.saveAndFlush(
            new StockLocation(
                "OD_" + unique,
                "Other Destination " + unique,
                "STALL"
            )
        );

        senderUsername = "sender_" + unique;
        receiverUsername = "receiver_" + unique;

        Employee sender = employees.saveAndFlush(
            new Employee(
                "Sender " + unique,
                senderUsername,
                "TEST_PASSWORD_HASH",
                EmployeeRole.SENDER,
                source
            )
        );

        Employee receiver = employees.saveAndFlush(
            new Employee(
                "Receiver " + unique,
                receiverUsername,
                "TEST_PASSWORD_HASH",
                EmployeeRole.RECEIVER,
                destination
            )
        );

        Employee manager = employees.saveAndFlush(
            new Employee(
                "Manager " + unique,
                "manager_" + unique,
                "TEST_PASSWORD_HASH",
                EmployeeRole.MANAGER,
                null
            )
        );

        Employee otherSender = employees.saveAndFlush(
            new Employee(
                "Other Sender " + unique,
                "other_sender_" + unique,
                "TEST_PASSWORD_HASH",
                EmployeeRole.SENDER,
                otherSource
            )
        );

        visibleProductName = "Visible Transfer " + unique;
        hiddenProductName = "Hidden Transfer " + unique;

        WholeChicken visibleProduct = products.saveAndFlush(
            new WholeChicken(
                "TV_" + unique,
                visibleProductName,
                new BigDecimal("500.00"),
                new BigDecimal("10.000")
            )
        );

        WholeChicken hiddenProduct = products.saveAndFlush(
            new WholeChicken(
                "TH_" + unique,
                hiddenProductName,
                new BigDecimal("600.00"),
                new BigDecimal("10.000")
            )
        );

        StockTransfer visibleTransfer = new StockTransfer(
            visibleProduct,
            source,
            destination,
            sender,
            new BigDecimal("10.000"),
            "Transfer visibility test"
        );

        visibleTransfer.confirmReceipt(
            receiver,
            new BigDecimal("8.000"),
            "Two kilograms missing"
        );

        visibleTransfer = transfers.saveAndFlush(
            visibleTransfer
        );

        visibleTransferId = visibleTransfer.getId();

        StockTransfer hiddenTransfer = new StockTransfer(
            hiddenProduct,
            otherSource,
            otherDestination,
            otherSender,
            new BigDecimal("5.000"),
            "Unrelated location"
        );

        hiddenTransfer = transfers.saveAndFlush(
            hiddenTransfer
        );

        hiddenTransferId = hiddenTransfer.getId();

        investigationExplanation = "INVESTIGATION_" + unique;

        TransferResolution resolution = new TransferResolution(
            visibleTransfer,
            manager,
            new BigDecimal("1.000"),
            new BigDecimal("1.000"),
            new BigDecimal(SECRET_LOSS),
            investigationExplanation
        );

        resolutions.saveAndFlush(resolution);
    }

    private MvcResult requestAs(
            String username,
            String role) throws Exception {

        return mvc.perform(
                get("/transfers")
                    .with(user(username).roles(role))
            )
            .andExpect(status().isOk())
            .andReturn();
    }

    private Map<String, Object> model(MvcResult result) {
        assertNotNull(result.getModelAndView());
        return result.getModelAndView().getModel();
    }

    private List<StockTransferView> transferViews(
            MvcResult result) {

        Object value = model(result).get("transfers");
        assertInstanceOf(List.class, value);

        List<?> items = (List<?>) value;

        for (Object item : items) {
            assertInstanceOf(StockTransferView.class, item);
        }

        return items.stream()
                .map(StockTransferView.class::cast)
                .toList();
    }

    private List<TransferResolutionView> resolutionViews(
            MvcResult result) {

        Object value = model(result).get("resolutionHistory");
        assertInstanceOf(List.class, value);

        List<?> items = (List<?>) value;

        for (Object item : items) {
            assertInstanceOf(TransferResolutionView.class, item);
        }

        return items.stream()
                .map(TransferResolutionView.class::cast)
                .toList();
    }

    private void assertOperationalVisibility(
            String username,
            String role) throws Exception {

        MvcResult result = requestAs(username, role);
        String html = result.getResponse().getContentAsString();

        List<StockTransferView> history = transferViews(result);

        assertTrue(
            history.stream().anyMatch(t ->
                visibleTransferId.equals(t.id())
            )
        );

        assertFalse(
            history.stream().anyMatch(t ->
                hiddenTransferId.equals(t.id())
            )
        );

        assertTrue(
            history.stream().anyMatch(t ->
                visibleProductName.equals(t.productName())
            )
        );

        assertFalse(
            history.stream().anyMatch(t ->
                hiddenProductName.equals(t.productName())
            )
        );

        List<TransferResolutionView> investigations =
                resolutionViews(result);

        TransferResolutionView resolution =
                investigations.stream()
                    .filter(r ->
                        visibleTransferId.equals(r.transferId())
                    )
                    .findFirst()
                    .orElseThrow();

        assertFalse(
            investigations.stream().anyMatch(r ->
                hiddenTransferId.equals(r.transferId())
            )
        );

        assertEquals(
            0,
            new BigDecimal("1.000")
                .compareTo(resolution.recoveredKg())
        );

        assertEquals(
            0,
            new BigDecimal("1.000")
                .compareTo(resolution.confirmedLossKg())
        );

        assertEquals(
            investigationExplanation,
            resolution.explanation()
        );

        assertNull(resolution.lossValue());

        assertTrue(html.contains(investigationExplanation));
        assertTrue(html.contains("1.000 kg"));

        assertFalse(html.contains(SECRET_LOSS));
        assertFalse(html.contains("<th>Loss Value</th>"));

        assertEquals(
            Boolean.FALSE,
            model(result).get("canSeeTransferFinancials")
        );
    }

    private void assertPrivilegedVisibility(
            String role) throws Exception {

        MvcResult result = requestAs(
            role.toLowerCase(),
            role
        );

        String html = result.getResponse().getContentAsString();

        List<StockTransferView> history = transferViews(result);

        assertTrue(
            history.stream().anyMatch(t ->
                visibleTransferId.equals(t.id())
            )
        );

        assertTrue(
            history.stream().anyMatch(t ->
                hiddenTransferId.equals(t.id())
            )
        );

        assertTrue(html.contains(visibleProductName));
        assertTrue(html.contains(hiddenProductName));

        TransferResolutionView resolution =
                resolutionViews(result)
                    .stream()
                    .filter(r ->
                        visibleTransferId.equals(r.transferId())
                    )
                    .findFirst()
                    .orElseThrow();

        assertNotNull(resolution.lossValue());

        assertEquals(
            0,
            new BigDecimal(SECRET_LOSS)
                .compareTo(resolution.lossValue())
        );

        assertTrue(html.contains(SECRET_LOSS));
        assertTrue(html.contains("Loss Value"));

        assertEquals(
            Boolean.TRUE,
            model(result).get("canSeeTransferFinancials")
        );
    }

    @Test
    void senderSeesOnlyRelatedTransfers()
            throws Exception {

        assertOperationalVisibility(
            senderUsername,
            "SENDER"
        );
    }

    @Test
    void receiverSeesOnlyRelatedTransfers()
            throws Exception {

        assertOperationalVisibility(
            receiverUsername,
            "RECEIVER"
        );
    }

    @Test
    void adminSeesAllTransfersAndLossValues()
            throws Exception {

        assertPrivilegedVisibility("ADMIN");
    }

    @Test
    void managerSeesAllTransfersAndLossValues()
            throws Exception {

        assertPrivilegedVisibility("MANAGER");
    }

    @Test
    void auditorSeesAllTransfersAndLossValues()
            throws Exception {

        assertPrivilegedVisibility("AUDITOR");
    }

    @Test
    void cashierCannotAccessTransfers()
            throws Exception {

        mvc.perform(
                get("/transfers")
                    .with(user("cashier").roles("CASHIER"))
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotAccessTransfers()
            throws Exception {

        MvcResult result = mvc.perform(
                get("/transfers")
            )
            .andReturn();

        int status = result.getResponse().getStatus();

        assertTrue(
            status == 302 || status == 401 || status == 403
        );

        assertFalse(
            result.getResponse()
                .getContentAsString()
                .contains(visibleProductName)
        );
    }

    @Test
    void receiverCannotConfirmUnrelatedTransfer()
            throws Exception {

        MvcResult result = mvc.perform(
                post("/transfers/receive")
                    .with(csrf())
                    .with(
                        user(receiverUsername)
                            .roles("RECEIVER")
                    )
                    .param(
                        "transferId",
                        hiddenTransferId.toString()
                    )
                    .param("actualKg", "5.000")
            )
            .andExpect(status().is3xxRedirection())
            .andReturn();

        assertEquals(
            "/transfers",
            result.getResponse().getRedirectedUrl()
        );

        StockTransfer unchanged = transfers.findById(
            hiddenTransferId
        ).orElseThrow();

        assertEquals(
            StockTransferStatus.DISPATCHED,
            unchanged.getStatus()
        );

        assertNull(unchanged.getReceiver());
        assertNull(unchanged.getReceivedKg());
    }

    @Test
    void senderCannotAccessReceiverEndpoint()
            throws Exception {

        mvc.perform(
                post("/transfers/receive")
                    .with(csrf())
                    .with(
                        user(senderUsername)
                            .roles("SENDER")
                    )
                    .param(
                        "transferId",
                        visibleTransferId.toString()
                    )
                    .param("actualKg", "8.000")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void receiverCannotAccessDispatchEndpoint()
            throws Exception {

        mvc.perform(
                post("/transfers/dispatch")
                    .with(csrf())
                    .with(
                        user(receiverUsername)
                            .roles("RECEIVER")
                    )
                    .param("productId", "1")
                    .param("destinationId", "1")
                    .param("weightKg", "1.000")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void senderCannotResolveInvestigations()
            throws Exception {

        mvc.perform(
                post("/transfers/resolve")
                    .with(csrf())
                    .with(
                        user(senderUsername)
                            .roles("SENDER")
                    )
                    .param(
                        "transferId",
                        visibleTransferId.toString()
                    )
                    .param("recoveredKg", "1.000")
                    .param("confirmedLossKg", "1.000")
                    .param("explanation", "Unauthorized")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void receiverCannotResolveInvestigations()
            throws Exception {

        mvc.perform(
                post("/transfers/resolve")
                    .with(csrf())
                    .with(
                        user(receiverUsername)
                            .roles("RECEIVER")
                    )
                    .param(
                        "transferId",
                        visibleTransferId.toString()
                    )
                    .param("recoveredKg", "1.000")
                    .param("confirmedLossKg", "1.000")
                    .param("explanation", "Unauthorized")
            )
            .andExpect(status().isForbidden());
    }
}
