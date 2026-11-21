package com.smartstock;

import com.smartstock.model.Employee;
import com.smartstock.model.EmployeeRole;
import com.smartstock.repository.EmployeeRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
class EmployeePaginationTest {

    @Autowired private MockMvc mvc;
    @Autowired private EmployeeRepository employeeRepository;

    @MockBean private LegacyInventoryMigration legacyInventoryMigration;
    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    private MvcResult request(String page, String size) throws Exception {
        var builder = get("/employees").with(user("admin").roles("ADMIN"));
        if (page != null) builder.param("page", page);
        if (size != null) builder.param("size", size);
        return mvc.perform(builder).andExpect(status().isOk()).andReturn();
    }

    private Object attr(MvcResult result, String key) {
        assertNotNull(result.getModelAndView());
        return result.getModelAndView().getModel().get(key);
    }

    @Test
    void defaultSizeAndAllowedSizeAreEnforced() throws Exception {
        assertEquals(10, attr(request(null, null), "pageSize"));
        assertEquals(5, attr(request("0", "5"), "pageSize"));
        assertEquals(20, attr(request("0", "20"), "pageSize"));
        assertEquals(50, attr(request("0", "50"), "pageSize"));
        assertEquals(10, attr(request("0", "99999"), "pageSize"));
        assertEquals(10, attr(request("0", "-2"), "pageSize"));
    }

    @Test
    void negativeAndOverlargePageNumbersAreClamped() throws Exception {
        assertEquals(0, attr(request("-7", "10"), "currentPage"));
        MvcResult result = request("999", "10");
        int current = (Integer) attr(result, "currentPage");
        int total = (Integer) attr(result, "totalPages");
        assertTrue(total == 0 || current < total);
    }

    @Test
    void employeePagesDoNotRepeatEmployees() throws Exception {
        String seed = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        for (int i = 0; i < 13; i++) {
            employeeRepository.save(new Employee(
                    String.format("Pagination Person %02d", i),
                    "page_" + seed + "_" + i,
                    "TestHashNotUsedForAuthentication",
                    EmployeeRole.ADMIN,
                    null));
        }
        employeeRepository.flush();

        Set<Long> seen = new HashSet<>();
        long total = employeeRepository.count();
        int pages = (int) ((total + 4) / 5);
        assertTrue(pages >= 3);
        for (int page = 0; page < pages; page++) {
            MvcResult result = request(Integer.toString(page), "5");
            @SuppressWarnings("unchecked")
            List<Employee> rows = (List<Employee>) attr(result, "employees");
            assertTrue(rows.size() <= 5);
            assertEquals(total, ((Number) attr(result, "totalEmployees")).longValue());
            assertTrue(((List<?>) attr(result, "pageNumbers")).size() <= 7);
            for (Employee employee : rows) {
                assertTrue(seen.add(employee.getId()), "Employee repeated across pages");
            }
        }
        assertEquals(total, seen.size());
    }

    @Test
    void nonAdminCannotListEmployees() throws Exception {
        mvc.perform(get("/employees").with(user("cashier").roles("CASHIER")))
                .andExpect(status().isForbidden());
    }
}
