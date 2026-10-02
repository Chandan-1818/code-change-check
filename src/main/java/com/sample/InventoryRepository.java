package com.sample;

import java.util.HashMap;
import java.util.Map;

public class InventoryRepository {

    private final Map<String, Integer> stock = new HashMap<>();

    public void setStock(String item, int quantity) {
        stock.put(item, quantity);
    }

    public int getStock(String item) {
        Integer quantity = stock.get(item);
        return quantity == null ? 0 : quantity;
    }
}
