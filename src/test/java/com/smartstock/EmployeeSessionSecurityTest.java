package com.smartstock;

import com.smartstock.model.Employee;
import com.smartstock.model.EmployeeRole;
import com.smartstock.repository.EmployeeRepository;
import com.smartstock.service.LegacyInventoryMigration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class EmployeeSessionSecurityTest {

    private static final String PASSWORD = "SecurePassword123!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private PasswordEncoder encoder;

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

    private Employee createEmployee(EmployeeRole role) {
        String username = "session_" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        return employees.saveAndFlush(
                new Employee(
                        "Session Security Test",
                        username,
                        encoder.encode(PASSWORD),
                        role,
                        null
                )
        );
    }

    private MockHttpSession login(Employee employee) throws Exception {
        var result = mvc.perform(
                formLogin()
                        .user(employee.getUsername())
                        .password(PASSWORD)
        )
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/products"))
        .andReturn();

        MockHttpSession session =
                (MockHttpSession) result.getRequest().getSession(false);

        assertNotNull(session, "Login did not create a session.");

        return session;
    }


    @Test
    void temporaryAdministratorSessionRemainsValid() throws Exception {
        org.springframework.security.core.userdetails.UserDetails admin =
                org.springframework.security.core.userdetails.User
                        .withUsername("admin")
                        .password(encoder.encode(PASSWORD))
                        .roles("ADMIN")
                        .build();

        org.mockito.Mockito.when(
                temporaryAdminService.loadUserByUsername("admin")
        ).thenReturn(admin);

        var result = mvc.perform(
                formLogin()
                        .user("admin")
                        .password(PASSWORD)
        )
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/products"))
        .andReturn();

        MockHttpSession session =
                (MockHttpSession) result.getRequest().getSession(false);

        assertNotNull(session);

        mvc.perform(get("/employees").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void activeEmployeeCanUseExistingSession() throws Exception {
        Employee employee = createEmployee(EmployeeRole.MANAGER);
        MockHttpSession session = login(employee);

        mvc.perform(get("/products").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void deactivatedEmployeeLosesExistingSession() throws Exception {
        Employee employee = createEmployee(EmployeeRole.MANAGER);
        MockHttpSession session = login(employee);

        employee.deactivate();
        employees.saveAndFlush(employee);

        mvc.perform(get("/products").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?sessionExpired"));

        assertTrue(session.isInvalid());
    }

    @Test
    void roleChangeRevokesOldSession() throws Exception {
        Employee employee = createEmployee(EmployeeRole.MANAGER);
        MockHttpSession session = login(employee);

        mvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk());

        employee.changeRole(EmployeeRole.CASHIER);
        employees.saveAndFlush(employee);

        mvc.perform(get("/dashboard").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?sessionExpired"));

        assertTrue(session.isInvalid());
    }

    @Test
    void passwordChangeRevokesExistingSession() throws Exception {
        Employee employee = createEmployee(EmployeeRole.MANAGER);
        MockHttpSession session = login(employee);

        mvc.perform(get("/products").session(session))
                .andExpect(status().isOk());

        employee.changePassword(
                encoder.encode("NewSecurePassword456!")
        );
        employees.saveAndFlush(employee);

        mvc.perform(get("/products").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?sessionExpired"));

        assertTrue(session.isInvalid());
    }

    @Test
    void deletedEmployeeLosesExistingSession() throws Exception {
        Employee employee = createEmployee(EmployeeRole.MANAGER);
        MockHttpSession session = login(employee);

        employees.delete(employee);
        employees.flush();

        mvc.perform(get("/products").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?sessionExpired"));

        assertTrue(session.isInvalid());
    }
}
