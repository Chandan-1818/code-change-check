package com.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class CheckoutServiceTest {

    private CheckoutService newCheckoutService(OrderService orderService, InventoryService inventoryService) {
        PricingService pricingService = new PricingService(new TaxCalculator(), new DiscountPolicy());
        return new CheckoutService(pricingService, orderService, inventoryService);
    }

    @Test
    public void testCheckout() {
        OrderService orderService = new OrderService(new OrderRepository());
        InventoryService inventoryService = new InventoryService(new InventoryRepository());
        inventoryService.restock("widget", 3);
        CheckoutService checkoutService = newCheckoutService(orderService, inventoryService);

        assertEquals(209, checkoutService.checkout(1, "widget", 200));
        assertEquals(209, orderService.getOrderTotal(1));
    }

    @Test
    public void testCheckoutOutOfStock() {
        OrderService orderService = new OrderService(new OrderRepository());
        InventoryService inventoryService = new InventoryService(new InventoryRepository());
        CheckoutService checkoutService = newCheckoutService(orderService, inventoryService);

        assertEquals(-1, checkoutService.checkout(2, "gadget", 200));
        assertEquals(0, orderService.getOrderTotal(2));
    }
}
