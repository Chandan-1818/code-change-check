package com.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class PricingServiceTest {

    @Test
    public void testFinalPrice() {
        PricingService pricingService = new PricingService(new TaxCalculator(), new DiscountPolicy());
        assertEquals(209, pricingService.finalPrice(200));
    }

    @Test
    public void testFinalPriceOnZeroAmount() {
        PricingService pricingService = new PricingService(new TaxCalculator(), new DiscountPolicy());
        assertEquals(0, pricingService.finalPrice(0));
    }
}
