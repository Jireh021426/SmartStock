
package com.smartstock.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import org.springframework.ui.Model;

@ControllerAdvice(annotations = Controller.class)
public class RoleViewAdvice {

    @ModelAttribute
    public void addRoleFlags(
            Model model,
            Authentication authentication) {

        boolean admin = hasRole(authentication, "ADMIN");
        boolean manager = hasRole(authentication, "MANAGER");
        boolean cashier = hasRole(authentication, "CASHIER");

        model.addAttribute("isAdmin", admin);
        model.addAttribute("isManager", manager);
        model.addAttribute("isCashier", cashier);

        model.addAttribute(
                "canRecordExpense",
                admin || manager);
    }

    private boolean hasRole(
            Authentication authentication,
            String role) {

        if (authentication == null
                || !authentication.isAuthenticated()) {
            return false;
        }

        String required = "ROLE_" + role;

        for (GrantedAuthority authority :
                authentication.getAuthorities()) {

            if (required.equals(authority.getAuthority())) {
                return true;
            }
        }

        return false;
    }
}
