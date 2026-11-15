
package com.smartstock.service;

import com.smartstock.model.LocationInventory;
import com.smartstock.model.StockLocation;
import com.smartstock.model.StockLocationForm;

import com.smartstock.repository.LocationInventoryRepository;
import com.smartstock.repository.StockLocationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class StockLocationService {

    private static final String LEGACY_CODE = "LEGACY";

    private final StockLocationRepository locations;
    private final LocationInventoryRepository inventories;

    public StockLocationService(
            StockLocationRepository locations,
            LocationInventoryRepository inventories) {

        this.locations = locations;
        this.inventories = inventories;
    }

    @Transactional(readOnly = true)
    public List<StockLocation> findAll() {
        return locations.findAll();
    }

    @Transactional(readOnly = true)
    public StockLocation findById(Long id) {
        return locations.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Stock location not found"));
    }

    @Transactional
    public void create(StockLocationForm form) {

        validate(form);

        String code = normalizeCode(form.getCode());

        if (LEGACY_CODE.equals(code)) {
            throw new IllegalArgumentException(
                    "LEGACY is a reserved system location code");
        }

        if (locations.existsByCode(code)) {
            throw new IllegalArgumentException(
                    "Location code already exists");
        }

        StockLocation location = new StockLocation(
                code,
                form.getName().trim(),
                form.getLocationType()
        );

        locations.save(location);
    }

    @Transactional
    public void update(Long id, StockLocationForm form) {

        validate(form);

        StockLocation location = findById(id);

        if (LEGACY_CODE.equals(location.getCode())) {
            throw new IllegalArgumentException(
                    "The LEGACY location cannot be edited");
        }

        String code = normalizeCode(form.getCode());

        if (LEGACY_CODE.equals(code)) {
            throw new IllegalArgumentException(
                    "LEGACY is a reserved system location code");
        }

        locations.findByCode(code).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new IllegalArgumentException(
                        "Location code already exists");
            }
        });

        location.update(
                code,
                form.getName().trim(),
                form.getLocationType()
        );
    }

    @Transactional
    public void deactivate(Long id) {

        StockLocation location = findById(id);

        if (LEGACY_CODE.equals(location.getCode())) {
            throw new IllegalArgumentException(
                    "The LEGACY location cannot be deactivated");
        }

        List<LocationInventory> balances =
                inventories.findByLocationId(id);

        BigDecimal total = balances.stream()
                .map(LocationInventory::getStockKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.signum() != 0) {
            throw new IllegalArgumentException(
                    "Cannot deactivate a location holding stock. " +
                    "Transfer or reconcile its inventory first.");
        }

        location.deactivate();
    }

    @Transactional
    public void activate(Long id) {
        StockLocation location = findById(id);
        location.activate();
    }

    @Transactional(readOnly = true)
    public StockLocationForm toForm(Long id) {

        StockLocation location = findById(id);

        StockLocationForm form = new StockLocationForm();
        form.setCode(location.getCode());
        form.setName(location.getName());
        form.setLocationType(location.getLocationType());

        return form;
    }

    private void validate(StockLocationForm form) {

        if (form == null ||
                form.getCode() == null ||
                !form.getCode().trim().matches("[A-Za-z0-9_-]{1,30}") ||
                form.getName() == null ||
                form.getName().isBlank() ||
                form.getName().trim().length() > 100 ||
                form.getLocationType() == null ||
                (!form.getLocationType().equals("STALL") &&
                 !form.getLocationType().equals("STORAGE"))) {

            throw new IllegalArgumentException(
                    "Enter a valid location code, name and type");
        }
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
