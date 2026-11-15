
package com.smartstock;

import com.smartstock.model.Employee;
import com.smartstock.model.EmployeeRole;
import com.smartstock.repository.EmployeeRepository;
import com.smartstock.service.LegacyInventoryMigration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@ActiveProfiles("test")
public class AuthenticationSecurityTest {

    private static final String PASSWORD =
            "SmartStock-Test-Password-2026!";

    private static final String ADMIN_PASSWORD =
            "SmartStock-Test-Admin-2026!";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @MockBean
    private LegacyInventoryMigration legacyInventoryMigration;

    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    private String cashierUsername;
    private String managerUsername;
    private String adminUsername;
    private String disabledUsername;

    @BeforeEach
    void setUp() throws Exception {

        try (Connection connection = dataSource.getConnection()) {

            String url = connection.getMetaData().getURL();

            assertNotNull(url);

            assertTrue(
                    url.startsWith("jdbc:h2:mem:smartstock_test"),
                    "Unsafe database URL: " + url
            );
        }

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        cashierUsername = "auth_cashier_" + suffix;
        managerUsername = "auth_manager_" + suffix;
        adminUsername = "auth_admin_" + suffix;
        disabledUsername = "auth_disabled_" + suffix;

        saveEmployee(
                cashierUsername,
                EmployeeRole.CASHIER
        );

        saveEmployee(
                managerUsername,
                EmployeeRole.MANAGER
        );

        saveEmployee(
                adminUsername,
                EmployeeRole.ADMIN
        );

        Employee disabled = new Employee(
                "Disabled Test Employee",
                disabledUsername,
                encoder.encode(PASSWORD),
                EmployeeRole.CASHIER,
                null
        );

        disabled.deactivate();
        employees.saveAndFlush(disabled);

        UserDetails temporaryAdmin = User.builder()
                .username("admin")
                .password(encoder.encode(ADMIN_PASSWORD))
                .roles("ADMIN")
                .build();

        // Every unknown username must throw the correct
        // exception rather than return Mockito's default null.
        when(temporaryAdminService.loadUserByUsername(anyString()))
                .thenAnswer(invocation -> {

                    String username = invocation.getArgument(0);

                    if ("admin".equals(username)) {
                        return temporaryAdmin;
                    }

                    throw new UsernameNotFoundException(
                            "Invalid username or password."
                    );
                });
    }

    @Test
    void correctCashierPasswordMustAuthenticate() {

        Authentication result = authenticate(
                cashierUsername,
                PASSWORD
        );

        assertTrue(result.isAuthenticated());
        assertEquals(cashierUsername, result.getName());
        assertRole(result, "ROLE_CASHIER");
    }

    @Test
    void incorrectPasswordMustBeRejected() {

        assertThrows(
                BadCredentialsException.class,
                () -> authenticate(
                        cashierUsername,
                        "Incorrect-Password"
                )
        );
    }

    @Test
    void unknownEmployeeMustBeRejected() {

        assertThrows(
                BadCredentialsException.class,
                () -> authenticate(
                        "nonexistent_" + UUID.randomUUID(),
                        PASSWORD
                )
        );
    }

    @Test
    void disabledEmployeeMustBeRejected() {

        assertThrows(
                DisabledException.class,
                () -> authenticate(
                        disabledUsername,
                        PASSWORD
                )
        );
    }

    @Test
    void managerMustReceiveManagerRole() {

        Authentication result = authenticate(
                managerUsername,
                PASSWORD
        );

        assertRole(result, "ROLE_MANAGER");
    }

    @Test
    void databaseAdministratorMustReceiveAdminRole() {

        Authentication result = authenticate(
                adminUsername,
                PASSWORD
        );

        assertRole(result, "ROLE_ADMIN");
    }

    @Test
    void uppercaseLoginUsernameMustBeNormalized() {

        Authentication result = authenticate(
                cashierUsername.toUpperCase(),
                PASSWORD
        );

        assertTrue(result.isAuthenticated());
        assertRole(result, "ROLE_CASHIER");
    }

    @Test
    void temporaryAdministratorMustAuthenticate() {

        Authentication result = authenticate(
                "admin",
                ADMIN_PASSWORD
        );

        assertTrue(result.isAuthenticated());
        assertRole(result, "ROLE_ADMIN");
    }

    private Employee saveEmployee(
            String username,
            EmployeeRole role) {

        Employee employee = new Employee(
                "Authentication Test Employee",
                username,
                encoder.encode(PASSWORD),
                role,
                null
        );

        return employees.saveAndFlush(employee);
    }

    private Authentication authenticate(
            String username,
            String password) {

        return authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        username,
                        password
                )
        );
    }

    private void assertRole(
            Authentication authentication,
            String expectedRole) {

        assertTrue(
                authentication.getAuthorities().stream()
                        .anyMatch(authority ->
                                expectedRole.equals(
                                        authority.getAuthority()
                                )
                        ),
                "Missing expected role: " + expectedRole
        );
    }
}
