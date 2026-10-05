package com.analysis.tool.experiment;

import com.analysis.tool.experiment.FaultInjectionRunner.Fault;

import java.util.Arrays;
import java.util.List;

/**
 * Faults for the grown sample project at V16 (7059f4c): one single-line fault
 * per production method present at that revision. Calculator.compute is not
 * included because it was removed in V16. Each find-text occurs exactly once
 * in its file at V16 (the runner enforces this).
 */
public final class GrownSampleFaults {

    private GrownSampleFaults() {
    }

    public static List<Fault> faults() {
        String calc = "src/main/java/com/sample/Calculator.java";
        String mult = "src/main/java/com/sample/Multiplier.java";
        String orderRepo = "src/main/java/com/sample/OrderRepository.java";
        String orderService = "src/main/java/com/sample/OrderService.java";
        String tax = "src/main/java/com/sample/TaxCalculator.java";
        String discount = "src/main/java/com/sample/DiscountPolicy.java";
        String pricing = "src/main/java/com/sample/PricingService.java";
        String invRepo = "src/main/java/com/sample/InventoryRepository.java";
        String invService = "src/main/java/com/sample/InventoryService.java";
        String checkout = "src/main/java/com/sample/CheckoutService.java";
        return Arrays.asList(
                new Fault("Calculator.add", "a + b -> a - b", calc,
                        "return a + b;", "return a - b;"),
                new Fault("Calculator.subtract", "a - b -> a + b", calc,
                        "int result = a - b;", "int result = a + b;"),
                new Fault("Calculator.negate", "-a -> a", calc,
                        "return -a;", "return a;"),
                new Fault("Multiplier.multiply", "a * b -> a + b", mult,
                        "int product = a * b;", "int product = a + b;"),
                new Fault("OrderRepository.save", "stores total + 1", orderRepo,
                        "orderTotals.put(orderId, storedTotal);", "orderTotals.put(orderId, storedTotal + 1);"),
                new Fault("OrderRepository.findTotal", "returns total + 1", orderRepo,
                        "return total == null ? 0 : total;", "return total == null ? 0 : total + 1;"),
                new Fault("OrderService.placeOrder", "saves total + 1", orderService,
                        "repository.save(orderId, total);", "repository.save(orderId, total + 1);"),
                new Fault("OrderService.getOrderTotal", "returns total + 1", orderService,
                        "return repository.findTotal(orderId);", "return repository.findTotal(orderId) + 1;"),
                new Fault("TaxCalculator.calculateTax", "result + 1", tax,
                        "return amount > 0 && tax == 0 ? 1 : tax;", "return amount > 0 && tax == 0 ? 1 : tax + 1;"),
                new Fault("DiscountPolicy.applyDiscount", "result + 1", discount,
                        "return amount - amount * DISCOUNT_PERCENT / 100;",
                        "return amount - amount * DISCOUNT_PERCENT / 100 + 1;"),
                new Fault("PricingService.finalPrice", "tax dropped", pricing,
                        "return discounted + taxCalculator.calculateTax(discounted);", "return discounted;"),
                new Fault("InventoryRepository.setStock", "stores quantity + 1", invRepo,
                        "stock.put(item, quantity);", "stock.put(item, quantity + 1);"),
                new Fault("InventoryRepository.getStock", "returns quantity + 1", invRepo,
                        "return quantity == null ? 0 : quantity;", "return quantity == null ? 0 : quantity + 1;"),
                new Fault("InventoryService.restock", "restocks quantity + 1", invService,
                        "repository.setStock(item, quantity);", "repository.setStock(item, quantity + 1);"),
                new Fault("InventoryService.available", "returns stock + 1", invService,
                        "return repository.getStock(item);", "return repository.getStock(item) + 1;"),
                new Fault("CheckoutService.checkout", "places order with price + 1", checkout,
                        "orderService.placeOrder(orderId, price);", "orderService.placeOrder(orderId, price + 1);"),
                new Fault("CheckoutService.quote", "result + 1", checkout,
                        "return pricingService.finalPrice(amount);", "return pricingService.finalPrice(amount) + 1;"));
    }
}
