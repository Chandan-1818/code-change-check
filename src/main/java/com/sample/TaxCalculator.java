package com.sample;

public class TaxCalculator {

    private static final int TAX_PERCENT = 10;

    public int calculateTax(int amount) {
        Multiplier multiplier = new Multiplier();
        return multiplier.multiply(amount, TAX_PERCENT) / 100;
    }
}
