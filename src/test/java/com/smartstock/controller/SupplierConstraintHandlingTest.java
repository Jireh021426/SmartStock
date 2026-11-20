
package com.smartstock.controller;

import com.smartstock.service.SupplierService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierConstraintHandlingTest {

    @Mock
    private SupplierService suppliers;

    @Mock
    private Model model;

    @Mock
    private RedirectAttributes redirect;

    @InjectMocks
    private SupplierController controller;

    private Authentication admin() {
        return new UsernamePasswordAuthenticationToken(
                "admin",
                "test",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                )
        );
    }

    @Test
    void createHandlesDatabaseConstraintViolation() {

        when(suppliers.createSupplier(
                "SUP-001",
                "Test Supplier",
                "",
                ""
        )).thenThrow(
                new DataIntegrityViolationException(
                        "Duplicate supplier code"
                )
        );

        String result = controller.save(
                "SUP-001",
                "Test Supplier",
                "",
                "",
                admin(),
                model,
                redirect
        );

        assertEquals("supplier-form", result);

        verify(model).addAttribute("editing", false);
        verify(model).addAttribute("code", "SUP-001");
        verify(model).addAttribute("name", "Test Supplier");

        verify(model).addAttribute(
                eq("error"),
                contains("may already exist")
        );
    }

    @Test
    void updateHandlesDatabaseConstraintViolation() {

        when(suppliers.updateSupplier(
                1L,
                "SUP-002",
                "Updated Supplier",
                "",
                ""
        )).thenThrow(
                new DataIntegrityViolationException(
                        "Duplicate supplier code"
                )
        );

        String result = controller.update(
                1L,
                "SUP-002",
                "Updated Supplier",
                "",
                "",
                admin(),
                model,
                redirect
        );

        assertEquals("supplier-form", result);

        verify(model).addAttribute("editing", true);
        verify(model).addAttribute("supplierId", 1L);
        verify(model).addAttribute("code", "SUP-002");

        verify(model).addAttribute(
                eq("error"),
                contains("may already exist")
        );
    }
}
