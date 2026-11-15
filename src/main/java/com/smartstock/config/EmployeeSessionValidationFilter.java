package com.smartstock.config;

import com.smartstock.model.Employee;
import com.smartstock.repository.EmployeeRepository;
import com.smartstock.security.EmployeePrincipal;
import com.smartstock.security.CredentialFingerprint;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class EmployeeSessionValidationFilter extends OncePerRequestFilter {

    private final EmployeeRepository employeeRepository;

    public EmployeeSessionValidationFilter(
            EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                        instanceof EmployeePrincipal principal)) {

            filterChain.doFilter(request, response);
            return;
        }

        Optional<Employee> employeeOptional =
                employeeRepository.findById(principal.getEmployeeId());

        boolean valid = false;

        if (employeeOptional.isPresent()) {
            Employee employee = employeeOptional.get();

            String expectedAuthority =
                    "ROLE_" + employee.getRole().name();

            valid = employee.isActive()
                    && employee.getUsername()
                            .equals(principal.getUsername())
                    && principal.getCredentialFingerprint().equals(
                            CredentialFingerprint.fromPasswordHash(
                                    employee.getPasswordHash()
                            )
                    )
                    && authentication.getAuthorities().size() == 1
                    && authentication.getAuthorities().stream()
                            .anyMatch(authority ->
                                    expectedAuthority.equals(
                                            authority.getAuthority()
                                    )
                            );
        }

        if (!valid) {
            invalidateSession(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void invalidateSession(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        SecurityContextHolder.clearContext();

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        response.sendRedirect(
                request.getContextPath() + "/login?sessionExpired"
        );
    }
}
