package com.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class OrderServiceTest {

    @Test
    public void testPlaceOrder() {
        OrderService service = new OrderService(new OrderRepository());
        service.placeOrder(7, 500);
        assertEquals(500, service.getOrderTotal(7));
    }
}
