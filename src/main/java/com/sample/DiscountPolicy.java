package com.sample;

public class DiscountPolicy {

    private static final int DISCOUNT_PERCENT = 5;

    public int applyDiscount(int amount) {
        return amount - amount * DISCOUNT_PERCENT / 100;
    }
}
