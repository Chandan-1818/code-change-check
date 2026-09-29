package com.sample;

public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public void placeOrder(int orderId, int total) {
        repository.save(orderId, total);
    }

    public int getOrderTotal(int orderId) {
        return repository.findTotal(orderId);
    }
}
