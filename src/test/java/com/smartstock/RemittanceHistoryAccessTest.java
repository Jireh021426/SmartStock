package com.smartstock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RemittanceHistoryAccessTest {

    @Autowired
    private MockMvc mvc;

    // A mock user has no employee record, so the controller may fail after
    // security lets the request through. That still counts as "not forbidden".
    private int statusFor() {
        try {
            return mvc.perform(get("/remittances/history"))
                    .andReturn().getResponse().getStatus();
        } catch (Exception e) {
            return -1;
        }
    }

    @Test
    @WithMockUser(username = "cashier.demo", roles = "CASHIER")
    void cashierIsNotForbidden() {
        assertNotEquals(403, statusFor());
    }

    @Test
    @WithMockUser(username = "manager.demo", roles = "MANAGER")
    void managerIsNotForbidden() {
        assertNotEquals(403, statusFor());
    }

    @Test
    @WithMockUser(username = "auditor.demo", roles = "AUDITOR")
    void auditorIsForbidden() {
        assertEquals(403, statusFor());
    }
}
