
package com.smartstock.controller;

import com.smartstock.model.EmployeeRole;
import com.smartstock.repository.StockLocationRepository;
import com.smartstock.service.EmployeeService;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/employees")
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final StockLocationRepository locationRepository;

    public EmployeeController(
            EmployeeService employeeService,
            StockLocationRepository locationRepository) {

        this.employeeService = employeeService;
        this.locationRepository = locationRepository;
    }

    @GetMapping
    public String employees(Model model) {

        model.addAttribute(
                "employees",
                employeeService.allEmployees()
        );

        model.addAttribute(
                "locations",
                locationRepository.findByActiveTrueOrderByNameAsc()
        );

        model.addAttribute(
                "roles",
                EmployeeRole.values()
        );

        return "employees";
    }

    @PostMapping("/create")
    public String create(
            @RequestParam String fullName,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam EmployeeRole role,
            @RequestParam(required = false) Long locationId,
            RedirectAttributes redirect) {

        try {
            employeeService.createEmployee(
                    fullName,
                    username,
                    password,
                    role,
                    locationId
            );

            redirect.addFlashAttribute(
                    "notice",
                    "Employee account created successfully."
            );

        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute(
                    "error",
                    ex.getMessage()
            );
        }

        return "redirect:/employees";
    }

    @PostMapping("/deactivate")
    public String deactivate(
            @RequestParam Long employeeId,
            Authentication authentication,
            RedirectAttributes redirect) {

        try {
            var employee = employeeService.findById(employeeId);

            if (employee.getUsername()
                    .equals(authentication.getName())) {
                throw new IllegalArgumentException(
                    "You cannot deactivate your own account."
                );
            }

            employeeService.deactivateEmployee(employeeId);

            redirect.addFlashAttribute(
                    "notice",
                    "Employee account deactivated."
            );

        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute(
                    "error",
                    ex.getMessage()
            );
        }

        return "redirect:/employees";
    }

    @PostMapping("/activate")
    public String activate(
            @RequestParam Long employeeId,
            RedirectAttributes redirect) {

        try {
            employeeService.activateEmployee(employeeId);

            redirect.addFlashAttribute(
                    "notice",
                    "Employee account activated."
            );

        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute(
                    "error",
                    ex.getMessage()
            );
        }

        return "redirect:/employees";
    }
}
