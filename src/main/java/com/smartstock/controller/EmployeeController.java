
package com.smartstock.controller;

import com.smartstock.model.Employee;
import com.smartstock.model.EmployeeRole;
import com.smartstock.repository.StockLocationRepository;
import com.smartstock.service.EmployeeService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.stream.IntStream;

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

    private static final List<Integer> PAGE_SIZES = List.of(5, 10, 20, 50);

    private static int allowedPageSize(int requestedSize) {
        return PAGE_SIZES.contains(requestedSize) ? requestedSize : 10;
    }

    @GetMapping
    public String employees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        int pageSize = allowedPageSize(size);
        int safePage = Math.max(0, page);

        Sort sort = Sort.by(Sort.Order.asc("fullName"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(safePage, pageSize, sort);
        Page<Employee> employeePage = employeeService.pagedEmployees(pageable);

        // Invalid large page numbers return the last existing page.
        if (employeePage.getTotalPages() > 0
                && safePage >= employeePage.getTotalPages()) {
            safePage = employeePage.getTotalPages() - 1;
            employeePage = employeeService.pagedEmployees(
                    PageRequest.of(safePage, pageSize, sort));
        }

        int totalPages = employeePage.getTotalPages();
        long totalEmployees = employeePage.getTotalElements();
        int windowStart = Math.max(0,
                Math.min(safePage - 3, totalPages - 7));
        int windowEnd = Math.min(totalPages, windowStart + 7);

        model.addAttribute("employees", employeePage.getContent());
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalEmployees", totalEmployees);
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("pageSizes", PAGE_SIZES);
        model.addAttribute("pageNumbers",
                IntStream.range(windowStart, windowEnd).boxed().toList());
        model.addAttribute("showingStart",
                totalEmployees == 0 ? 0 : (long) safePage * pageSize + 1);
        model.addAttribute("showingEnd",
                Math.min(((long) safePage + 1) * pageSize, totalEmployees));

        model.addAttribute("locations",
                locationRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("roles", EmployeeRole.values());

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
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

        return "redirect:/employees?page=" + Math.max(0, page)
                + "&size=" + allowedPageSize(size);
    }

    @PostMapping("/activate")
    public String activate(
            @RequestParam Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
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

        return "redirect:/employees?page=" + Math.max(0, page)
                + "&size=" + allowedPageSize(size);
    }
}
