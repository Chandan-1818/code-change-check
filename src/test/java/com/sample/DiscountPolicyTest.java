package com.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class DiscountPolicyTest {

    @Test
    public void testApplyDiscount() {
        DiscountPolicy discountPolicy = new DiscountPolicy();
        assertEquals(190, discountPolicy.applyDiscount(200));
    }

    @Test
    public void testApplyDiscountOnZeroAmount() {
        DiscountPolicy discountPolicy = new DiscountPolicy();
        assertEquals(0, discountPolicy.applyDiscount(0));
    }
}
