package com.sample;

public class TaxCalculator {

    private static final int TAX_PERCENT = 10;

    public int calculateTax(int amount) {
        Multiplier multiplier = new Multiplier();
        int tax = multiplier.multiply(amount, TAX_PERCENT) / 100;
        return amount > 0 && tax == 0 ? 1 : tax;
    }
}
