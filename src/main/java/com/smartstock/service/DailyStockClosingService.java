package com.smartstock.service;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class DailyStockClosingService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Manila");
    private static final BigDecimal ZERO_KG = new BigDecimal("0.000");
    private static final BigDecimal ZERO_PESOS = new BigDecimal("0.00");

    private final DailyStockClosingRepository closings;
    private final ProductRepository products;
    private final StockLocationRepository locations;
    private final LocationInventoryRepository inventories;
    private final StockMovementRepository movements;
    private final SaleRepository sales;

    public DailyStockClosingService(DailyStockClosingRepository closings,
                                    ProductRepository products,
                                    StockLocationRepository locations,
                                    LocationInventoryRepository inventories,
                                    StockMovementRepository movements,
                                    SaleRepository sales) {
        this.closings = closings;
        this.products = products;
        this.locations = locations;
        this.inventories = inventories;
        this.movements = movements;
        this.sales = sales;
    }

    public record SalesTotals(BigDecimal kg, BigDecimal revenue,
                              BigDecimal cash, BigDecimal gcash, BigDecimal credit) { }

    public record DailySummary(LocalDate date, BigDecimal recordedKg,
                               BigDecimal recordedRevenue, BigDecimal cashSales,
                               BigDecimal gcashSales, BigDecimal creditSales,
                               BigDecimal potentialUnrecordedKg,
                               BigDecimal potentialRevenue, List<DailyStockClosing> closings) { }

    private void requireRole(Authentication auth, String... allowed) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalArgumentException("Login required");
        }
        boolean authorized = Arrays.stream(allowed).anyMatch(role ->
                auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role)));
        if (!authorized) throw new IllegalArgumentException("You do not have permission for daily closings");
    }

    private boolean isCompanyStall(StockLocation location) {
        return location != null && location.isActive() &&
                "STALL".equalsIgnoreCase(location.getLocationType()) &&
                !"LEGACY".equalsIgnoreCase(location.getCode());
    }

    private void validateDate(LocalDate date) {
        if (date == null || date.isAfter(LocalDate.now(ZONE))) {
            throw new IllegalArgumentException("Choose a valid current or past business date");
        }
    }

    /** Cashier-recorded sales at company stalls; independent deliveries are excluded. */
    private List<Sale> salesForDate(LocalDate date) {
        var current = new ArrayList<>(sales.findByBusinessDate(date));
        current.addAll(sales.findByBusinessDateIsNullAndSoldAtGreaterThanEqualAndSoldAtLessThan(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay()));
        return current.stream()
                .filter(s -> s.getCashier() != null && s.getDeliveryReference() == null &&
                        s.getLocation() != null &&
                        "STALL".equalsIgnoreCase(s.getLocation().getLocationType()))
                .toList();
    }

    private SalesTotals totalsFor(List<Sale> entries, Long locationId, Long productId) {
        BigDecimal kg = ZERO_KG;
        BigDecimal revenue = ZERO_PESOS;
        BigDecimal cash = ZERO_PESOS;
        BigDecimal gcash = ZERO_PESOS;
        BigDecimal credit = ZERO_PESOS;
        for (Sale sale : entries) {
            if ((locationId == null || sale.getLocation().getId().equals(locationId)) &&
                (productId == null || sale.getProduct().getId().equals(productId))) {
                kg = kg.add(sale.getWeightKg());
                revenue = revenue.add(sale.getTotal());
                if (sale.getPaymentMethod() == PaymentMethod.CASH) {
                    cash = cash.add(sale.getTotal());
                } else if (sale.getPaymentMethod() == PaymentMethod.GCASH) {
                    gcash = gcash.add(sale.getTotal());
                } else if (sale.getPaymentMethod() == PaymentMethod.CREDIT) {
                    credit = credit.add(sale.getTotal());
                }
            }
        }
        return new SalesTotals(kg.setScale(3), revenue.setScale(2),
                cash.setScale(2), gcash.setScale(2), credit.setScale(2));
    }

    @Transactional(readOnly = true)
    public DailySummary summary(LocalDate date) {
        validateDate(date);
        List<DailyStockClosing> history = closings.findByBusinessDateOrderByIdDesc(date);
        SalesTotals booked = totalsFor(salesForDate(date), null, null);
        BigDecimal estimatedKg = ZERO_KG;
        BigDecimal estimatedRevenue = ZERO_PESOS;
        Set<String> seen = new HashSet<>();
        // Only the newest non-rejected closing per product and stall contributes
        // to estimates. Old rejected counts must never be double counted.
        for (DailyStockClosing closing : history) {
            String key = closing.getLocation().getId() + ":" + closing.getProduct().getId();
            if (seen.add(key) && closing.getStatus() != DailyStockClosing.Status.REJECTED
                    && closing.getBusinessDate().equals(LocalDate.now(ZONE))) {
                estimatedKg = estimatedKg.add(closing.getPotentialUnrecordedKg());
                estimatedRevenue = estimatedRevenue.add(closing.getPotentialUnrecordedRevenue());
            }
        }
        return new DailySummary(date, booked.kg(), booked.revenue(),
                booked.cash(), booked.gcash(), booked.credit(),
                estimatedKg, estimatedRevenue, history);
    }

    @Transactional
    public Long submit(Authentication auth, DailyClosingForm form) {
        requireRole(auth, "ADMIN", "MANAGER", "AUDITOR");
        if (form == null || form.getLocationId() == null || form.getProductId() == null ||
                form.getCountedKg() == null || form.getCountedKg().signum() < 0 ||
                form.getCountedKg().scale() > 3 || form.getNote() == null ||
                form.getNote().isBlank() || form.getNote().trim().length() > 1000) {
            throw new IllegalArgumentException("Provide a valid physical count and explanation");
        }
        validateDate(form.getBusinessDate());
        // A count entered today cannot prove the book position on a prior date.
        // Past-dated submissions are therefore disallowed until historical
        // inventory reconstruction and date-aware transfers are implemented.
        if (!form.getBusinessDate().equals(LocalDate.now(ZONE))) {
            throw new IllegalArgumentException(
                    "Historical closing counts are not supported yet. " +
                    "Choose today's date; backdating would compare old counts against today's inventory.");
        }

        // Product row lock serializes a count with sales, transfers and other
        // inventory updates, and with concurrent duplicate closing submissions.
        Product product = products.findLockedById(form.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        StockLocation location = locations.findById(form.getLocationId())
                .orElseThrow(() -> new IllegalArgumentException("Location not found"));
        if (!isCompanyStall(location)) {
            throw new IllegalArgumentException("Daily closing is only for active company stalls");
        }
        boolean existing = closings.findByBusinessDateAndLocationIdAndProductIdOrderByIdDesc(
                        form.getBusinessDate(), location.getId(), product.getId()).stream()
                .anyMatch(c -> c.getStatus() != DailyStockClosing.Status.REJECTED);
        if (existing) {
            throw new IllegalStateException(
                    "This stall/product already has an open or approved closing for the date. " +
                    "Reject the pending count before submitting a corrected one.");
        }

        BigDecimal bookKg = inventories.findLockedByLocationAndProduct(
                        location.getId(), product.getId())
                .map(LocationInventory::getStockKg).orElse(ZERO_KG);
        SalesTotals recorded = totalsFor(salesForDate(form.getBusinessDate()),
                location.getId(), product.getId());
        BigDecimal price = product.getPrice();
        if (price == null || price.signum() < 0) {
            throw new IllegalStateException("Product selling price is invalid");
        }

        DailyStockClosing closing = new DailyStockClosing(
                location, product, form.getBusinessDate(), bookKg,
                form.getCountedKg(), recorded.kg(), recorded.revenue(), price,
                auth.getName(), form.getNote());
        return closings.saveAndFlush(closing).getId();
    }

    @Transactional
    public void review(Authentication auth, Long id, boolean approve, String note) {
        requireRole(auth, "ADMIN", "MANAGER");
        if (id == null) throw new IllegalArgumentException("Closing ID is required");
        DailyStockClosing original = closings.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Closing count not found"));
        Product product = products.findLockedById(original.getProduct().getId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        DailyStockClosing closing = closings.findById(id).orElseThrow();
        if (closing.getStatus() != DailyStockClosing.Status.PENDING) {
            throw new IllegalStateException("Closing has already been reviewed");
        }
        if (approve) {
            if (!closing.getBusinessDate().equals(LocalDate.now(ZONE))) {
                throw new IllegalStateException("A historical count cannot be approved against current stock");
            }
            BigDecimal current = inventories.findLockedByLocationAndProduct(
                            closing.getLocation().getId(), product.getId())
                    .map(LocationInventory::getStockKg).orElse(ZERO_KG);
            if (current.compareTo(closing.getBookKg()) != 0 ||
                    movements.existsByProductIdAndLocationIdAndRecordedAtAfter(
                            product.getId(), closing.getLocation().getId(), closing.getCountedAt())) {
                throw new IllegalStateException("Inventory changed after counting. Reject and recount.");
            }
            SalesTotals now = totalsFor(salesForDate(closing.getBusinessDate()),
                    closing.getLocation().getId(), product.getId());
            if (now.kg().compareTo(closing.getRecordedSalesKg()) != 0 ||
                    now.revenue().compareTo(closing.getRecordedRevenue()) != 0) {
                throw new IllegalStateException("Recorded sales changed after counting. Reject and recount.");
            }
        }
        closing.review(auth.getName(), approve, note);
        // No stock adjustment or sale is created: confirmed on-hand stock is
        // already the next day's opening book stock unless new activity occurs.
    }
}
