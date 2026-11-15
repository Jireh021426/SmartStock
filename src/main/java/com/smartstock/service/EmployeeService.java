
package com.smartstock.service;

import com.smartstock.model.Employee;
import com.smartstock.model.EmployeeRole;
import com.smartstock.model.StockLocation;

import com.smartstock.repository.EmployeeRepository;
import com.smartstock.repository.StockLocationRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final StockLocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            StockLocationRepository locationRepository,
            PasswordEncoder passwordEncoder) {

        this.employeeRepository = employeeRepository;
        this.locationRepository = locationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<Employee> allEmployees() {
        return employeeRepository.findAllWithAssignedLocation();
    }

    @Transactional(readOnly = true)
    public Employee findById(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Employee not found."));
    }

    @Transactional
    public Employee createEmployee(
            String fullName,
            String username,
            String rawPassword,
            EmployeeRole role,
            Long locationId) {

        if (fullName == null ||
                fullName.isBlank() ||
                fullName.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Enter a valid employee name.");
        }

        if (username == null ||
                !username.trim().matches(
                        "[A-Za-z0-9._-]{3,50}")) {
            throw new IllegalArgumentException(
                    "Username must contain 3 to 50 valid characters.");
        }

        String normalizedUsername =
                username.trim().toLowerCase(Locale.ROOT);

        if (normalizedUsername.equals("admin")) {
            throw new IllegalArgumentException(
                    "The username admin is reserved.");
        }

        if (employeeRepository.existsByUsername(
                normalizedUsername)) {
            throw new IllegalArgumentException(
                    "Username already exists.");
        }

        if (rawPassword == null ||
                rawPassword.length() < 12 ||
                rawPassword.length() > 72) {
            throw new IllegalArgumentException(
                    "Password must contain 12 to 72 characters.");
        }

        if (role == null) {
            throw new IllegalArgumentException(
                    "Employee role is required.");
        }

        StockLocation assignedLocation = null;

        if (locationId != null) {
            assignedLocation = locationRepository
                    .findById(locationId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Assigned location not found."));

            if (!assignedLocation.isActive()) {
                throw new IllegalArgumentException(
                        "Cannot assign an inactive location.");
            }
        }

        if ((role == EmployeeRole.SENDER ||
                role == EmployeeRole.RECEIVER ||
                role == EmployeeRole.CASHIER) &&
                assignedLocation == null) {
            throw new IllegalArgumentException(
                    "Sender, Receiver and Cashier accounts require an assigned location.");
        }

        if (role == EmployeeRole.CASHIER &&
                (!"STALL".equalsIgnoreCase(assignedLocation.getLocationType()) ||
                 "LEGACY".equalsIgnoreCase(assignedLocation.getCode()))) {
            throw new IllegalArgumentException(
                    "Cashiers must be assigned to an active STALL location.");
        }

        Employee employee = new Employee(
                fullName.trim(),
                normalizedUsername,
                passwordEncoder.encode(rawPassword),
                role,
                assignedLocation
        );

        return employeeRepository.save(employee);
    }

    @Transactional
    public void deactivateEmployee(Long id) {

        Employee employee = findById(id);

        if (employee.getRole() == EmployeeRole.ADMIN &&
                employee.isActive()) {

            long activeAdmins = employeeRepository
                    .findByRole(EmployeeRole.ADMIN)
                    .stream()
                    .filter(Employee::isActive)
                    .count();

            if (activeAdmins <= 1) {
                throw new IllegalArgumentException(
                        "Cannot deactivate the last active administrator.");
            }
        }

        employee.deactivate();
    }

    @Transactional
    public void activateEmployee(Long id) {
        Employee employee = findById(id);
        employee.activate();
    }

    @Transactional
    public void changePassword(
            Long employeeId,
            String newPassword) {

        if (newPassword == null ||
                newPassword.length() < 12 ||
                newPassword.length() > 72) {
            throw new IllegalArgumentException(
                    "Password must contain 12 to 72 characters.");
        }

        Employee employee = findById(employeeId);

        employee.changePassword(
                passwordEncoder.encode(newPassword)
        );
    }
}
