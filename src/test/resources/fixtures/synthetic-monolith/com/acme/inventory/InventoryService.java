package com.acme.inventory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    @Autowired
    private InventoryRepository inventoryRepository;

    public void checkStock(Long id) {
        inventoryRepository.findById(id);
    }
}
