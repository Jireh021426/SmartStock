package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

/**
 * Reconstructs historical evidence independently of live stock and accounting.
 * NO Product, LocationInventory, Sale, Customer or CreditPayment writes occur.
 */
@Service
public class HistoricalReconciliationService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final HistoricalDayRepository days;
    private final HistoricalDebtRepository debts;
    private final HistoricalStockRepository stocks;
    private final StockLocationRepository locations;

    public record DaySummary(HistoricalDay day, List<HistoricalDebt> debtRows,
                             List<HistoricalStock> stockRows, BigDecimal closingDebt,
                             BigDecimal previousDebt, BigDecimal estimatedSales,
                             BigDecimal listedRetailValue, BigDecimal retailVariance) { }

    public HistoricalReconciliationService(HistoricalDayRepository days, HistoricalDebtRepository debts,
                                          HistoricalStockRepository stocks, StockLocationRepository locations) {
        this.days = days;
        this.debts = debts;
        this.stocks = stocks;
        this.locations = locations;
    }

    private void requireRole(Authentication auth, String... roles) {
        if (auth == null || !auth.isAuthenticated() ||
            Arrays.stream(roles).noneMatch(role -> auth.getAuthorities().stream()
                    .anyMatch(a -> ("ROLE_" + role).equals(a.getAuthority())))) {
            throw new IllegalArgumentException("You do not have permission for historical reconciliation");
        }
    }

    @Transactional(readOnly = true)
    public List<HistoricalDay> list(Authentication auth) {
        requireRole(auth, "ADMIN", "MANAGER", "AUDITOR");
        return days.findAllByOrderByBusinessDateDescIdDesc();
    }

    @Transactional(readOnly = true)
    public DaySummary details(Authentication auth, Long id) {
        requireRole(auth, "ADMIN", "MANAGER", "AUDITOR");
        return summarize(id);
    }

    private DaySummary summarize(Long id) {
        HistoricalDay day = days.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Historical day not found"));
        List<HistoricalDebt> debtRows = debts.findByDayIdOrderByCustomerNameAsc(day.getId());
        List<HistoricalStock> stockRows = stocks.findByDayIdOrderByProductNameAsc(day.getId());
        BigDecimal closingDebt = debtRows.stream().map(HistoricalDebt::getClosingBalance)
                .reduce(ZERO, BigDecimal::add).setScale(2);
        BigDecimal prior = null;
        var previous = days.findByLocationIdAndBusinessDate(
                day.getLocation().getId(), day.getBusinessDate().minusDays(1));
        if (previous.isPresent() && previous.get().isDebtsComplete()) {
            prior = debts.findByDayIdOrderByCustomerNameAsc(previous.get().getId()).stream()
                    .map(HistoricalDebt::getClosingBalance).reduce(ZERO, BigDecimal::add).setScale(2);
        }
        BigDecimal estimated = day.isDebtsComplete() && prior != null
                ? day.getCollections().add(closingDebt).subtract(prior).setScale(2) : null;
        BigDecimal retail = stockRows.stream().map(HistoricalStock::getEstimatedRetailValue)
                .reduce(ZERO, BigDecimal::add).setScale(2);
        BigDecimal gap = day.isStockComplete() && estimated != null ? retail.subtract(estimated) : null;
        return new DaySummary(day, debtRows, stockRows, closingDebt, prior, estimated, retail, gap);
    }

    @Transactional
    public Long create(Authentication auth, HistoricalDayForm form) {
        requireRole(auth, "ADMIN", "MANAGER");
        if (form == null || form.getLocationId() == null)
            throw new IllegalArgumentException("Select a company stall");
        StockLocation location = locations.findById(form.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("Location not found"));
        if (days.findByLocationIdAndBusinessDate(location.getId(), form.getBusinessDate()).isPresent())
            throw new IllegalStateException("Historical record already exists for this stall and date");
        HistoricalDay day = new HistoricalDay(location, form.getBusinessDate(), form.getCollections(),
                form.getSourceFile(), form.getNotes(), auth.getName());
        return days.saveAndFlush(day).getId();
    }

    @Transactional
    public void updateSummary(Authentication auth, Long id, HistoricalSummaryEditForm form) {
        requireRole(auth, "ADMIN", "MANAGER");
        HistoricalDay day = days.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Historical day not found"));
        if (form == null) throw new IllegalArgumentException("Enter corrected summary details");
        day.reviseSummary(form.getCollections(), form.getSourceFile(), form.getNotes(), auth.getName());
    }

    @Transactional
    public void addDebt(Authentication auth, Long dayId, HistoricalDebtForm form) {
        requireRole(auth, "ADMIN", "MANAGER");
        HistoricalDay day = days.findLockedById(dayId)
                .orElseThrow(() -> new IllegalArgumentException("Historical day not found"));
        day.requireDraft();
        if (day.isDebtsComplete()) throw new IllegalStateException("Reopen the debt list before editing");
        if (form == null || form.getCustomerName() == null || form.getCustomerName().isBlank())
            throw new IllegalArgumentException("Customer name is required");
        String key = form.getCustomerName().trim().toUpperCase(Locale.ROOT);
        if (debts.existsByDayIdAndCustomerKey(dayId, key))
            throw new IllegalArgumentException("Customer already has a balance for this day. Remove the old row before correcting it.");
        debts.saveAndFlush(new HistoricalDebt(day, form.getCustomerName(), form.getClosingBalance()));
    }

    @Transactional
    public void addStock(Authentication auth, Long dayId, HistoricalStockForm form) {
        requireRole(auth, "ADMIN", "MANAGER");
        HistoricalDay day = days.findLockedById(dayId)
                .orElseThrow(() -> new IllegalArgumentException("Historical day not found"));
        day.requireDraft();
        if (day.isStockComplete()) throw new IllegalStateException("Reopen the stock list before editing");
        if (form == null || form.getProductName() == null || form.getProductName().isBlank() || form.getUnit() == null)
            throw new IllegalArgumentException("Product and unit are required");
        String key = form.getProductName().trim().toUpperCase(Locale.ROOT) + ":" + form.getUnit();
        if (stocks.existsByDayIdAndProductKey(dayId, key))
            throw new IllegalArgumentException("Product already exists for this day. Remove its previous row first.");
        stocks.saveAndFlush(new HistoricalStock(day, form.getProductName(), form.getUnit(), form.getAvailableQty(),
                form.getClosingQty(), form.getKnownLossQty(), form.getSellingPrice()));
    }

    @Transactional
    public void removeDebt(Authentication auth, Long dayId, Long rowId) {
        requireRole(auth, "ADMIN", "MANAGER");
        HistoricalDay day = days.findLockedById(dayId).orElseThrow();
        day.requireDraft();
        if (day.isDebtsComplete()) throw new IllegalStateException("Reopen the debt list before editing");
        HistoricalDebt row = debts.findById(rowId).orElseThrow(() -> new IllegalArgumentException("Debt row not found"));
        if (!row.getDay().getId().equals(dayId)) throw new IllegalArgumentException("Wrong historical day");
        debts.delete(row);
    }

    @Transactional
    public void removeStock(Authentication auth, Long dayId, Long rowId) {
        requireRole(auth, "ADMIN", "MANAGER");
        HistoricalDay day = days.findLockedById(dayId).orElseThrow();
        day.requireDraft();
        if (day.isStockComplete()) throw new IllegalStateException("Reopen the stock list before editing");
        HistoricalStock row = stocks.findById(rowId).orElseThrow(() -> new IllegalArgumentException("Stock row not found"));
        if (!row.getDay().getId().equals(dayId)) throw new IllegalArgumentException("Wrong historical day");
        stocks.delete(row);
    }

    @Transactional
    public void setComplete(Authentication auth, Long id, String section, boolean complete) {
        requireRole(auth, "ADMIN", "MANAGER");
        HistoricalDay day = days.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Historical day not found"));
        if ("DEBTS".equals(section)) day.setDebtsComplete(complete);
        else if ("STOCK".equals(section)) day.setStockComplete(complete);
        else throw new IllegalArgumentException("Choose debts or stock");
    }

    @Transactional
    public void review(Authentication auth, Long id, String note) {
        requireRole(auth, "ADMIN", "MANAGER");
        HistoricalDay day = days.findLockedById(id)
                .orElseThrow(() -> new IllegalArgumentException("Historical day not found"));
        DaySummary summary = summarize(id);
        // A baseline day can be reviewed without its previous date; do not fabricate sales.
        if (summary.estimatedSales() != null && summary.estimatedSales().signum() < 0)
            throw new IllegalStateException("Negative estimated sales. Investigate collections/debt before review.");
        if (summary.stockRows().isEmpty())
            throw new IllegalStateException("Enter stock rows before review");
        day.review(auth.getName(), note);
    }
}
