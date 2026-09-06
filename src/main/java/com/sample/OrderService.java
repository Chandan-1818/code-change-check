package com.sample;

public class OrderService {

    private final Calculator calculator = new Calculator();

    public int calculateTotal(int price, int quantity) {
        int total = 0;
        for (int i = 0; i < quantity; i++) {
            total = calculator.add(total, price);
        }
        return total;
    }
}