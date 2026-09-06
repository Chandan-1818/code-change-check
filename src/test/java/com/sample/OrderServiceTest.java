package com.sample;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OrderServiceTest {

    @Test
    public void testCalculateTotal() {
        OrderService service = new OrderService();
        int result = service.calculateTotal(10, 3);
        assertEquals(30, result);
    }
}