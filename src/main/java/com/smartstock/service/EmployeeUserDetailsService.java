package com.smartstock.service;

import com.smartstock.model.Employee;
import com.smartstock.repository.EmployeeRepository;
import com.smartstock.security.EmployeePrincipal;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class EmployeeUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;

    public EmployeeUserDetailsService(
            EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException(
                    "Invalid username or password."
            );
        }

        String normalizedUsername = username.trim()
                .toLowerCase(Locale.ROOT);

        Employee employee = employeeRepository
                .findByUsername(normalizedUsername)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Invalid username or password."
                        )
                );

        return new EmployeePrincipal(employee);
    }
}
