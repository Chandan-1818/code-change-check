package com.sample;

public class PricingService {

    private final TaxCalculator taxCalculator;
    private final DiscountPolicy discountPolicy;

    public PricingService(TaxCalculator taxCalculator, DiscountPolicy discountPolicy) {
        this.taxCalculator = taxCalculator;
        this.discountPolicy = discountPolicy;
    }

    public int finalPrice(int amount) {
        int discounted = discountPolicy.applyDiscount(amount);
        return discounted + taxCalculator.calculateTax(discounted);
    }
}
