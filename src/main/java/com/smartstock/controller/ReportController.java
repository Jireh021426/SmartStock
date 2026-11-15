package com.smartstock.controller;
import com.smartstock.service.ReportService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.time.LocalDate;
@Controller public class ReportController {
 private final ReportService reports;
 public ReportController(ReportService reports){this.reports=reports;}
 @GetMapping({"/","/dashboard"}) public String dashboard(@RequestParam(required=false) LocalDate date,Model model){model.addAllAttributes(reports.daily(date==null?LocalDate.now():date));return "dashboard";}
 @PostMapping("/expenses") public String addExpense(@RequestParam String category,@RequestParam BigDecimal amount,@RequestParam String description,RedirectAttributes flash){try{reports.addExpense(category,amount,description);flash.addFlashAttribute("notice","Expense recorded");}catch(IllegalArgumentException e){flash.addFlashAttribute("error",e.getMessage());}return "redirect:/dashboard";}
}