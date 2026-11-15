package com.smartstock.security;

import com.smartstock.model.Employee;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public final class EmployeePrincipal extends User {

    private static final long serialVersionUID = 1L;

    private final Long employeeId;
    private final String credentialFingerprint;

    public EmployeePrincipal(Employee employee) {
        super(
                employee.getUsername(),
                employee.getPasswordHash(),
                employee.isActive(),
                true,
                true,
                true,
                AuthorityUtils.createAuthorityList(
                        "ROLE_" + employee.getRole().name()
                )
        );

        this.employeeId = employee.getId();

        this.credentialFingerprint =
                CredentialFingerprint.fromPasswordHash(
                        employee.getPasswordHash()
                );
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getCredentialFingerprint() {
        return credentialFingerprint;
    }
}
