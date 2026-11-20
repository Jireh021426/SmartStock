
package com.smartstock.service;

import com.smartstock.model.Supplier;
import com.smartstock.repository.SupplierRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository suppliers;

    public SupplierService(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @Transactional(readOnly = true)
    public List<Supplier> allSuppliers() {
        return suppliers.findAllByOrderByNameAscIdAsc();
    }

    @Transactional(readOnly = true)
    public List<Supplier> activeSuppliers() {
        return suppliers.findByActiveTrueOrderByNameAscIdAsc();
    }

    @Transactional(readOnly = true)
    public Supplier findSupplier(Long id) {
        return suppliers.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Supplier not found"));
    }

    @Transactional
    public Supplier createSupplier(
            String code,
            String name,
            String contactNumber,
            String address) {

        Supplier supplier = new Supplier(
                code,
                name,
                contactNumber,
                address
        );

        if (suppliers.existsByCodeIgnoreCase(
                supplier.getCode())) {

            throw new IllegalArgumentException(
                    "Supplier code already exists");
        }

        return suppliers.save(supplier);
    }

    @Transactional
    public Supplier updateSupplier(
            Long id,
            String code,
            String name,
            String contactNumber,
            String address) {

        Supplier supplier = findSupplier(id);

        Supplier existing = suppliers
                .findByCodeIgnoreCase(code == null ? "" : code.trim())
                .orElse(null);

        if (existing != null
                && !existing.getId().equals(id)) {

            throw new IllegalArgumentException(
                    "Supplier code already exists");
        }

        supplier.update(
                code,
                name,
                contactNumber,
                address
        );

        return supplier;
    }

    @Transactional
    public void deactivateSupplier(Long id) {
        Supplier supplier = findSupplier(id);
        supplier.deactivate();
    }

    @Transactional
    public void activateSupplier(Long id) {
        Supplier supplier = findSupplier(id);
        supplier.activate();
    }
}
