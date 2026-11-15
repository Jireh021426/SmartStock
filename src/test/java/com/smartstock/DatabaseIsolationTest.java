
package com.smartstock;

import com.smartstock.service.LegacyInventoryMigration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@ActiveProfiles("test")
public class DatabaseIsolationTest {

    @Autowired
    private DataSource dataSource;

    // Prevent legacy migration from executing during tests.
    @MockBean
    private LegacyInventoryMigration legacyInventoryMigration;

    // Prevent test startup from requiring the production
    // temporary admin authentication configuration.
    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    @Test
    void testDatabaseMustBeInMemory() throws Exception {

        try (Connection connection = dataSource.getConnection()) {

            DatabaseMetaData metadata = connection.getMetaData();

            String url = metadata.getURL();

            assertNotNull(
                    url,
                    "Database URL must not be null"
            );

            assertTrue(
                    url.startsWith("jdbc:h2:mem:smartstock_test"),
                    "Expected isolated in-memory database, but got: " + url
            );

            assertFalse(
                    url.contains("smartstock-chicken-phase3"),
                    "Production database must never be used by tests"
            );

            System.out.println(
                    "PASS: Isolated test database confirmed: " + url
            );
        }
    }

    @Test
    void testDatabaseConnectionIsValid() throws Exception {

        try (Connection connection = dataSource.getConnection()) {

            assertTrue(
                    connection.isValid(5),
                    "Test database connection must be valid"
            );

            System.out.println(
                    "PASS: Test database connection is valid."
            );
        }
    }
}
