package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ReportService {

    private final SaleRepository sales;
    private final CreditPaymentRepository payments;
    private final ExpenseRepository expenses;
    private final ProductRepository products;
    private final StockMovementRepository movements;

    public ReportService(
            SaleRepository sales,
            CreditPaymentRepository payments,
            ExpenseRepository expenses,
            ProductRepository products,
            StockMovementRepository movements) {

        this.sales = sales;
        this.payments = payments;
        this.expenses = expenses;
        this.products = products;
        this.movements = movements;
    }

    private static BigDecimal sum(
            Collection<BigDecimal> numbers) {

        return numbers.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isInventoryLoss(StockMovement movement) {

        if (movement.getType() != MovementType.STOCK_OUT) {
            return false;
        }

        MovementCategory category = movement.getCategory();

        // Transfers and sales are not inventory losses.
        if (category == MovementCategory.STOCK_TRANSFER
                || category == MovementCategory.SALE) {
            return false;
        }

        String reason = movement.getReason();

        // Compatibility with historical sale movements.
        if (reason != null && reason.startsWith("SALE #")) {
            return false;
        }

        // Explicit inventory-loss categories.
        if (category == MovementCategory.SPOILAGE
                || category == MovementCategory.DAMAGE
                || category == MovementCategory.MISSING_STOCK) {
            return true;
        }

        // Compatibility with existing approved physical audits.
        // Audit #2 was recorded as STOCK_ADJUSTMENT.
        // Only the known physical-audit reason pattern qualifies.
        return category == MovementCategory.STOCK_ADJUSTMENT
                && reason != null
                && reason.matches(
                        "Physical audit #[0-9]+ approved adjustment");
    }

    private BigDecimal lossValue(StockMovement movement) {

        // Preserve the amount recorded at transaction time.
        if (movement.getInventoryLossValue() != null) {
            return movement.getInventoryLossValue();
        }

        // Historical movements without a stored value:
        // estimate using the current product cost.
        // This is NOT a historical cost snapshot.
        BigDecimal currentCost = movement.getProduct().getCostPerKg();

        if (currentCost == null) {
            return BigDecimal.ZERO.setScale(2);
        }

        return movement.getWeightKg()
                .multiply(currentCost)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> daily(LocalDate date) {

        var start = date.atStartOfDay();
        var end = date.plusDays(1).atStartOfDay();

        var daySales =
                sales.findBySoldAtGreaterThanEqualAndSoldAtLessThan(
                        start, end);

        var dayPayments =
                payments.findByPaidAtGreaterThanEqualAndPaidAtLessThan(
                        start, end);

        var dayExpenses =
                expenses.findByIncurredAtGreaterThanEqualAndIncurredAtLessThanOrderByIncurredAtDesc(
                        start, end);

        var dayMovements =
                movements.findAllByOrderByRecordedAtDescIdDesc()
                        .stream()
                        .filter(x ->
                                !x.getRecordedAt().isBefore(start)
                                && x.getRecordedAt().isBefore(end))
                        .toList();

        BigDecimal revenue = sum(
                daySales.stream()
                        .map(Sale::getTotal)
                        .toList());

        BigDecimal cost = sum(
                daySales.stream()
                        .map(Sale::getCostAtSale)
                        .toList());

        BigDecimal cashSales = sum(
                daySales.stream()
                        .filter(s ->
                                s.getPaymentMethod()
                                        == PaymentMethod.CASH)
                        .map(Sale::getTotal)
                        .toList());

        BigDecimal gcashSales = sum(
                daySales.stream()
                        .filter(s ->
                                s.getPaymentMethod()
                                        == PaymentMethod.GCASH)
                        .map(Sale::getTotal)
                        .toList());

        BigDecimal creditSales = sum(
                daySales.stream()
                        .filter(s ->
                                s.getPaymentMethod()
                                        == PaymentMethod.CREDIT)
                        .map(Sale::getTotal)
                        .toList());

        BigDecimal cashRepay = sum(
                dayPayments.stream()
                        .filter(x ->
                                x.getMethod()
                                        == PaymentMethod.CASH)
                        .map(CreditPayment::getAmount)
                        .toList());

        BigDecimal gcashRepay = sum(
                dayPayments.stream()
                        .filter(x ->
                                x.getMethod()
                                        == PaymentMethod.GCASH)
                        .map(CreditPayment::getAmount)
                        .toList());

        BigDecimal expenseTotal = sum(
                dayExpenses.stream()
                        .map(Expense::getAmount)
                        .toList());

        var lossMovements = dayMovements.stream()
                .filter(this::isInventoryLoss)
                .toList();

        BigDecimal lossKg = sum(
                lossMovements.stream()
                        .map(StockMovement::getWeightKg)
                        .toList());

        BigDecimal estimatedLossCost = sum(
                lossMovements.stream()
                        .map(this::lossValue)
                        .toList());

        BigDecimal gross = revenue.subtract(cost);

        BigDecimal collected =
                cashSales.add(cashRepay)
                        .add(gcashSales)
                        .add(gcashRepay);

        BigDecimal estimatedResult =
                gross.subtract(expenseTotal)
                        .subtract(estimatedLossCost);

        BigDecimal stockCostValue = sum(
                products.findAll()
                        .stream()
                        .map(Product::getStockCostValue)
                        .toList());

        // ========================================
        // CHART 1: REVENUE TREND
        // Last 6 calendar months, ending with the
        // month of the selected report date.
        // ========================================

        YearMonth lastMonth = YearMonth.from(date);
        YearMonth firstMonth = lastMonth.minusMonths(5);

        var trendSales =
                sales.findBySoldAtGreaterThanEqualAndSoldAtLessThan(
                        firstMonth.atDay(1).atStartOfDay(),
                        lastMonth.plusMonths(1).atDay(1).atStartOfDay());

        Map<YearMonth, BigDecimal> monthly = new LinkedHashMap<>();

        for (int i = 0; i < 6; i++) {
            monthly.put(firstMonth.plusMonths(i), BigDecimal.ZERO);
        }

        for (Sale s : trendSales) {
            monthly.merge(
                    YearMonth.from(s.getSoldAt()),
                    s.getTotal(),
                    BigDecimal::add);
        }

        List<String> trendLabels = new ArrayList<>();
        List<BigDecimal> trendValues = new ArrayList<>();

        monthly.forEach((month, total) -> {
            trendLabels.add(
                    month.getMonth().getDisplayName(
                            TextStyle.SHORT, Locale.ENGLISH));
            trendValues.add(total);
        });

        // ========================================
        // CHART 2: SALES BY CATEGORY
        // Last 7 days, ending on the selected date.
        // ========================================

        var weekSales =
                sales.findBySoldAtGreaterThanEqualAndSoldAtLessThan(
                        date.minusDays(6).atStartOfDay(), end);

        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();

        for (Sale s : weekSales) {

            String category = s.getProduct().getCategory();

            if (category == null || category.isBlank()) {
                category = "Other";
            }

            byCategory.merge(category, s.getTotal(), BigDecimal::add);
        }

        BigDecimal categoryTotal = sum(byCategory.values());

        List<Map<String, Object>> categorySales =
                byCategory.entrySet()
                        .stream()
                        .sorted(Map.Entry
                                .<String, BigDecimal>comparingByValue()
                                .reversed())
                        .map(entry -> {

                            Map<String, Object> row =
                                    new LinkedHashMap<>();

                            row.put("name", entry.getKey());
                            row.put("total", entry.getValue());
                            row.put("percent",
                                    categoryTotal.signum() == 0
                                            ? BigDecimal.ZERO
                                            : entry.getValue()
                                                    .multiply(BigDecimal.valueOf(100))
                                                    .divide(categoryTotal,
                                                            1,
                                                            RoundingMode.HALF_UP));

                            return row;
                        })
                        .toList();

        Map<String, Object> m = new LinkedHashMap<>();

        m.put("date", date);

        m.put("revenue", revenue);
        m.put("cost", cost);
        m.put("gross", gross);

        m.put("cashSales", cashSales);
        m.put("gcashSales", gcashSales);
        m.put("creditSales", creditSales);

        m.put("cashCollected", cashSales.add(cashRepay));
        m.put("gcashCollected", gcashSales.add(gcashRepay));
        m.put("collected", collected);

        m.put("expenses", expenseTotal);

        m.put("lossKg", lossKg);
        m.put("estimatedLossCost", estimatedLossCost);

        m.put("estimatedResult", estimatedResult);
        m.put("stockCostValue", stockCostValue);

        m.put("daySales", daySales);
        m.put("dayExpenses", dayExpenses);

        // Chart data
        m.put("trendLabels", trendLabels);
        m.put("trendValues", trendValues);
        m.put("categorySales", categorySales);
        m.put("categoryTotal", categoryTotal);

        return m;
    }

    @Transactional
    public void addExpense(
            String category,
            BigDecimal amount,
            String description) {

        if (category == null
                || category.isBlank()
                || amount == null
                || amount.signum() <= 0
                || amount.scale() > 2
                || description == null
                || description.isBlank()) {

            throw new IllegalArgumentException(
                    "Enter category, positive amount and description");
        }

        expenses.save(
                new Expense(
                        category.trim(),
                        amount,
                        description.trim()));
    }
}