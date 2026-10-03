package com.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class TaxCalculatorTest {

    @Test
    public void testCalculateTax() {
        TaxCalculator taxCalculator = new TaxCalculator();
        assertEquals(20, taxCalculator.calculateTax(200));
    }

    @Test
    public void testCalculateTaxOnZeroAmount() {
        TaxCalculator taxCalculator = new TaxCalculator();
        assertEquals(0, taxCalculator.calculateTax(0));
    }

    @Test
    public void testCalculateTaxHasMinimumOfOne() {
        TaxCalculator taxCalculator = new TaxCalculator();
        assertEquals(1, taxCalculator.calculateTax(5));
    }
}
