package com.smartstock;

import com.smartstock.model.*;
import com.smartstock.repository.*;
import com.smartstock.service.HistoricalReconciliationService;
import com.smartstock.service.LegacyInventoryMigration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
    "spring.datasource.url=jdbc:h2:mem:smartstock_test;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "smartstock.migration.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class HistoricalReconciliationTest {
    @Autowired HistoricalReconciliationService service;
    @Autowired StockLocationRepository locations;
    @Autowired HistoricalDebtRepository debts;
    @Autowired HistoricalStockRepository stock;
    @Autowired HistoricalDayRepository days;
    @Autowired ProductRepository products;
    @Autowired SaleRepository sales;
    @Autowired MockMvc mvc;
    @MockBean LegacyInventoryMigration migration;
    @MockBean(name = "temporaryAdminService") UserDetailsService temporaryAdminService;
    private Long locationId;
    private Long day15;
    private Long day16;

    private Authentication account(String name, String role) {
        return new UsernamePasswordAuthenticationToken(name, "unused",
            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    private HistoricalDayForm form(int date, String collections) {
        HistoricalDayForm f = new HistoricalDayForm();
        f.setLocationId(locationId);
        f.setBusinessDate(LocalDate.of(2026, 11, date));
        f.setCollections(new BigDecimal(collections));
        f.setSourceFile("November " + date + ", 2026.xlsx");
        f.setNotes("Original spreadsheet, not posted to live ledgers");
        return f;
    }
    private void debt(Long dayId, String name, String amount) {
        HistoricalDebtForm f = new HistoricalDebtForm();
        f.setCustomerName(name); f.setClosingBalance(new BigDecimal(amount));
        service.addDebt(account("encoder", "ADMIN"), dayId, f);
    }
    private void completeDebts(Long dayId) {
        service.setComplete(account("encoder","ADMIN"), dayId, "DEBTS", true);
    }
    private void stock(Long id, String name, String available, String closing, String price) {
        HistoricalStockForm f = new HistoricalStockForm();
        f.setProductName(name); f.setUnit("KG");
        f.setAvailableQty(new BigDecimal(available));
        f.setClosingQty(new BigDecimal(closing));
        f.setKnownLossQty(BigDecimal.ZERO); f.setSellingPrice(new BigDecimal(price));
        service.addStock(account("encoder","ADMIN"), id, f);
    }
    @BeforeEach
    void setup() {
        String code = "H" + UUID.randomUUID().toString().substring(0, 12);
        var loc = locations.save(new StockLocation(code, "Historical Aljun", "STALL"));
        locationId = loc.getId();
        day15 = service.create(account("encoder", "ADMIN"), form(15,"80650.00"));
        day16 = service.create(account("encoder", "ADMIN"), form(16,"79564.00"));
    }
    @Test
    void originalNovemberDebtListsYieldAccurateSalesEstimate() {
        String[] names = {"DEPED", "PASIL", "CORDOVA", "RAMOS", "NERISA", "LISA"};
        String[] d15 = {"30000.00","12780.00","12771.00","2270.00","2497.00","1354.00"};
        String[] d16 = {"30000.00","12780.00","11286.00","3080.00","2197.00","1643.00"};
        for (int i=0; i<names.length; i++) { debt(day15,names[i],d15[i]); debt(day16,names[i],d16[i]); }
        completeDebts(day15); completeDebts(day16);
        var baseline = service.details(account("auditor","AUDITOR"), day15);
        var current = service.details(account("auditor","AUDITOR"), day16);
        assertEquals(0, baseline.closingDebt().compareTo(new BigDecimal("61672.00")));
        assertNull(baseline.estimatedSales(), "November 15 is a baseline without November 14 debtor balances");
        assertEquals(0, current.previousDebt().compareTo(new BigDecimal("61672.00")));
        assertEquals(0, current.closingDebt().compareTo(new BigDecimal("60986.00")));
        assertEquals(0, current.estimatedSales().compareTo(new BigDecimal("78878.00")));
    }
    @Test
    void historicalPriceAndStockAreNotReplacedByCurrentCatalogValues() {
        stock(day16, "Whole chicken", "219.850", "0.000", "175.00");
        var result = service.details(account("auditor","AUDITOR"), day16);
        assertEquals(0, result.stockRows().get(0).getImpliedSoldQty().compareTo(new BigDecimal("219.850")));
        assertEquals(0, result.listedRetailValue().compareTo(new BigDecimal("38473.75")));
        assertNull(result.retailVariance(), "Partial stock list cannot be compared to all-product sales");
    }
    @Test
    void missingPreviousDebtSnapshotNeverFabricatesSales() {
        debt(day16, "DEPED", "30000");
        completeDebts(day16);
        assertNull(service.details(account("manager","MANAGER"), day16).estimatedSales());
    }
    @Test
    void repeatedNamesAndDaysAreRejected() {
        debt(day15,"DEPED","30000");
        assertThrows(IllegalArgumentException.class, () -> debt(day15,"deped","30000"));
        assertThrows(IllegalStateException.class, () ->
            service.create(account("encoder","ADMIN"), form(15,"80650.00")));
    }
    @Test
    void completedDebtListMustBeReopenedBeforeEditing() {
        debt(day15,"DEPED","30000");
        completeDebts(day15);
        assertThrows(IllegalStateException.class, () -> debt(day15,"LISA","1354"));
        service.setComplete(account("encoder","ADMIN"), day15, "DEBTS", false);
        debt(day15,"LISA","1354");
    }
    @Test
    void invalidStockCannotBeRecordedAsSold() {
        assertThrows(IllegalArgumentException.class, () -> stock(day16,"Chicken","5.000","6.000","175.00"));
        HistoricalStockForm f = new HistoricalStockForm();
        f.setProductName("Chicken"); f.setUnit("KG");
        f.setAvailableQty(new BigDecimal("10.000"));
        f.setClosingQty(new BigDecimal("4.000"));
        f.setKnownLossQty(new BigDecimal("7.000"));
        f.setSellingPrice(new BigDecimal("175.00"));
        assertThrows(IllegalArgumentException.class, () ->
            service.addStock(account("encoder","ADMIN"),day16,f));
    }
    @Test
    void independentReviewerLocksEvidenceWithoutPostingSalesOrStock() {
        long salesBefore = sales.count();
        long productsBefore = products.count();
        debt(day16,"DEPED","30000.00");
        completeDebts(day16);
        stock(day16,"Whole chicken","219.850","0.000","175.00");
        service.setComplete(account("encoder","ADMIN"),day16,"STOCK",true);
        assertThrows(IllegalArgumentException.class, () ->
            service.review(account("encoder","ADMIN"),day16,"Self approval"));
        service.review(account("manager","MANAGER"),day16,"Source sheet checked; this is evidence only");
        assertEquals(HistoricalDay.Status.REVIEWED,days.findById(day16).orElseThrow().getStatus());
        assertThrows(IllegalStateException.class, () -> debt(day16,"LISA","10.00"));
        assertEquals(salesBefore, sales.count(), "Historical evidence must never create live sales");
        assertEquals(productsBefore, products.count(), "Historical evidence must never create live products or stock");
    }
    @Test
    void locationMustBeCompanyStallAndDateMustBeHistorical() {
        StockLocation storage = locations.save(new StockLocation(
            "HST" + UUID.randomUUID().toString().substring(0, 10), "Main Storage", "STORAGE"));
        HistoricalDayForm invalid = form(14, "0");
        invalid.setLocationId(storage.getId());
        assertThrows(IllegalArgumentException.class, () ->
            service.create(account("encoder", "ADMIN"), invalid));
        invalid.setLocationId(locationId);
        invalid.setBusinessDate(LocalDate.now(java.time.ZoneId.of("Asia/Manila")));
        assertThrows(IllegalArgumentException.class, () ->
            service.create(account("encoder", "ADMIN"), invalid));
    }
    @Test
    void completeStockListCannotBeChangedUnlessReopened() {
        stock(day16, "Whole chicken", "219.850", "0.000", "175.00");
        service.setComplete(account("encoder","ADMIN"),day16,"STOCK",true);
        assertThrows(IllegalStateException.class, () ->
            stock(day16, "Liver", "10.000", "5.000", "190.00"));
        service.setComplete(account("encoder","ADMIN"),day16,"STOCK",false);
        stock(day16, "Liver", "10.000", "5.000", "190.00");
    }
    @Test
    void draftCollectionsCanBeCorrectedAndCorrectionsAreAudited() {
        HistoricalSummaryEditForm f = new HistoricalSummaryEditForm();
        f.setCollections(new BigDecimal("79564.00"));
        f.setSourceFile("November 16, 2026.xlsx");
        f.setNotes("Reviewed the cash notebook");
        service.updateSummary(account("managerEdit","MANAGER"),day16,f);
        var current = days.findById(day16).orElseThrow();
        assertEquals(0,current.getCollections().compareTo(new BigDecimal("79564.00")));
        assertEquals("managerEdit",current.getLastEditedBy());
        assertNotNull(current.getLastEditedAt());
        stock(day16,"Whole chicken","219.850","0.000","175.00");
        service.setComplete(account("encoder","ADMIN"),day16,"STOCK",true);
        service.setComplete(account("encoder","ADMIN"),day16,"DEBTS",true);
        service.review(account("differentManager","MANAGER"),day16,"Checked source evidence");
        assertThrows(IllegalStateException.class,()->service.updateSummary(account("managerEdit","MANAGER"),day16,f));
    }
    @Test
    void cashierIsForbiddenAndAuditorCanReadOnly() throws Exception {
        mvc.perform(get("/historical-reconciliation").with(user("cashier").roles("CASHIER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/historical-reconciliation").with(user("auditor").roles("AUDITOR")))
            .andExpect(status().isOk());
        mvc.perform(get("/historical-reconciliation/" + day16).with(user("auditor").roles("AUDITOR")))
            .andExpect(status().isOk());
    }
}
