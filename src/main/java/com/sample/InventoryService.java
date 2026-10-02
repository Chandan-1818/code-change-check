package com.sample;

public class InventoryService {

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    public void restock(String item, int quantity) {
        repository.setStock(item, quantity);
    }

    public int available(String item) {
        return repository.getStock(item);
    }
}
