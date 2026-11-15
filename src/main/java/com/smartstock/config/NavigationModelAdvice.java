
package com.smartstock.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class NavigationModelAdvice {

    @ModelAttribute("nav")
    public NavigationPermissions navigationPermissions() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return new NavigationPermissions(authentication);
    }

    public static class NavigationPermissions {

        private final boolean admin;
        private final boolean manager;
        private final boolean cashier;
        private final boolean sender;
        private final boolean receiver;
        private final boolean auditor;

        public NavigationPermissions(Authentication authentication) {

            this.admin = hasRole(authentication, "ADMIN");
            this.manager = hasRole(authentication, "MANAGER");
            this.cashier = hasRole(authentication, "CASHIER");
            this.sender = hasRole(authentication, "SENDER");
            this.receiver = hasRole(authentication, "RECEIVER");
            this.auditor = hasRole(authentication, "AUDITOR");
        }

        private static boolean hasRole(
                Authentication authentication,
                String role) {

            if (authentication == null ||
                    !authentication.isAuthenticated()) {
                return false;
            }

            return authentication.getAuthorities().stream()
                    .anyMatch(authority ->
                            ("ROLE_" + role).equals(
                                    authority.getAuthority()));
        }

        public boolean isAdmin() {
            return admin;
        }

        public boolean isManager() {
            return manager;
        }

        public boolean isCashier() {
            return cashier;
        }

        public boolean isSender() {
            return sender;
        }

        public boolean isReceiver() {
            return receiver;
        }

        public boolean isAuditor() {
            return auditor;
        }

        public boolean isCanViewLocations() {
            return admin || manager || auditor;
        }

        public boolean isCanViewTransfers() {
            return admin || manager || sender || receiver || auditor;
        }

        public boolean isCanViewAudits() {
            return admin || manager || auditor;
        }

        public boolean isCanViewRemittances() {
            return admin || manager || cashier;
        }
    }
}
