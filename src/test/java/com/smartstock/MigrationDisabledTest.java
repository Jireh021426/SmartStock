package com.smartstock;

import com.smartstock.service.LegacyInventoryMigration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "smartstock.migration.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:smartstock_migration_disabled;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@ActiveProfiles("test")
class MigrationDisabledTest {

    @Autowired
    private ApplicationContext context;

    @MockBean(name = "temporaryAdminService")
    private UserDetailsService temporaryAdminService;

    @Test
    void migrationBeanMustBeAbsentWhenDisabled() {
        assertEquals(
                0,
                context.getBeansOfType(LegacyInventoryMigration.class).size(),
                "Legacy migration must not be registered when disabled"
        );
    }
}
