
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
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

    private static String normalizedQuery(String query) {
        if (query == null) return "";
        String trimmed = query.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }

    private static String returnUrl(int page, int size, String query) {
        return "redirect:/employees?page=" + Math.max(0, page)
                + "&size=" + allowedPageSize(size)
                + "&q=" + URLEncoder.encode(normalizedQuery(query), StandardCharsets.UTF_8);
    }

    @GetMapping
    public String employees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String q,
            Model model) {

        int pageSize = allowedPageSize(size);
        String searchQuery = normalizedQuery(q);
        int safePage = Math.max(0, page);

        Sort sort = Sort.by(Sort.Order.asc("fullName"), Sort.Order.asc("id"));
        Pageable pageable = PageRequest.of(safePage, pageSize, sort);
        Page<Employee> employeePage = employeeService.searchEmployees(searchQuery, pageable);

        // Invalid large page numbers return the last existing page.
        if (employeePage.getTotalPages() > 0
                && safePage >= employeePage.getTotalPages()) {
            safePage = employeePage.getTotalPages() - 1;
            employeePage = employeeService.searchEmployees(searchQuery,
                    PageRequest.of(safePage, pageSize, sort));
        }

        int totalPages = employeePage.getTotalPages();
        long totalEmployees = employeePage.getTotalElements();
        int windowStart = Math.max(0,
                Math.min(safePage - 3, totalPages - 7));
        int windowEnd = Math.min(totalPages, windowStart + 7);

        model.addAttribute("employees", employeePage.getContent());
        model.addAttribute("searchQuery", searchQuery);
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

    // Admin-only export: every matching record, not just the visible 10-row page.
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportEmployees(
            @RequestParam(defaultValue = "") String q) {
        String query = normalizedQuery(q);
        Sort sort = Sort.by(Sort.Order.asc("fullName"), Sort.Order.asc("id"));
        StringBuilder csv = new StringBuilder("\uFEFF")
                .append("ID,Full Name,Username,Role,Assigned Location,Status\r\n");
        int index = 0;
        Page<Employee> page;
        do {
            page = employeeService.searchEmployees(query, PageRequest.of(index++, 250, sort));
            for (Employee employee : page.getContent()) {
                csv.append(employee.getId()).append(',')
                   .append(csvCell(employee.getFullName())).append(',')
                   .append(csvCell(employee.getUsername())).append(',')
                   .append(csvCell(employee.getRole().name())).append(',')
                   .append(csvCell(employee.getAssignedLocation() == null
                           ? "Not Assigned" : employee.getAssignedLocation().getName())).append(',')
                   .append(csvCell(employee.isActive() ? "ACTIVE" : "INACTIVE"))
                   .append("\r\n");
            }
        } while (page.hasNext());
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"smartstock-employees.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(bytes.length)
                .body(bytes);
    }

    // CSV injection protection for untrusted display names/locations in spreadsheets.
    private static String csvCell(String value) {
        String safe = value == null ? "" : value;
        String stripped = safe.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
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
            @RequestParam(defaultValue = "") String q,
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

        return returnUrl(page, size, q);
    }

    @PostMapping("/activate")
    public String activate(
            @RequestParam Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String q,
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

        return returnUrl(page, size, q);
    }
}
