package com.sample;

import java.util.HashMap;
import java.util.Map;

public class OrderRepository {

    private final Map<Integer, Integer> orderTotals = new HashMap<>();

    public void save(int orderId, int total) {
        orderTotals.put(orderId, total);
    }

    public int findTotal(int orderId) {
        Integer total = orderTotals.get(orderId);
        return total == null ? 0 : total;
    }
}
