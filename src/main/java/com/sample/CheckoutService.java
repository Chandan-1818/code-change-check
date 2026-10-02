package com.sample;

public class CheckoutService {

    private final PricingService pricingService;
    private final OrderService orderService;
    private final InventoryService inventoryService;

    public CheckoutService(PricingService pricingService, OrderService orderService,
                           InventoryService inventoryService) {
        this.pricingService = pricingService;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
    }

    public int checkout(int orderId, String item, int amount) {
        if (inventoryService.available(item) <= 0) {
            return -1;
        }
        int price = pricingService.finalPrice(amount);
        orderService.placeOrder(orderId, price);
        return price;
    }
}
