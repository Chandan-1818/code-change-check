package com.analysis.tool.experiment;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Safety check on the grown sample project at the pinned base revision
 * V16 = 7059f4c (never a HEAD-relative ref): for every injected fault, every
 * test that fails in the full suite must be among the tests the tool selected.
 * Calculator.negate and CheckoutService.quote have no test, so no test can
 * fail for them; that is a coverage gap in the sample, not a selection error.
 */
public class FaultInjectionGrownSampleTest {

    private static final String SAMPLE_PROJECT_PATH =
            "D:/PROJECT/SAMPLE-PROJECT/sample-project";
    private static final String VERSION_16_COMMIT = "7059f4c";

    @Test
    public void shouldDefineSeventeenFaultsWithUniqueMethodNames() {
        List<FaultInjectionRunner.Fault> faults = GrownSampleFaults.faults();
        Set<String> names = new HashSet<>();
        for (FaultInjectionRunner.Fault fault : faults) {
            names.add(fault.getMethod());
        }
        assertEquals(17, faults.size());
        assertEquals(17, names.size(), "Fault method names must be unique");
    }

    @Test
    public void shouldSelectEveryFailingTestForEachInjectedFault() throws Exception {
        Map<String, Set<String>> expected = new HashMap<>();
        expected.put("Calculator.add", Set.of("testAdd"));
        expected.put("Calculator.subtract", Set.of("testSubtract"));
        expected.put("Calculator.negate", Collections.emptySet());
        expected.put("Multiplier.multiply",
                Set.of("testMultiply", "testCalculateTax", "testFinalPrice", "testCheckout"));
        expected.put("OrderRepository.save",
                Set.of("testSaveAndFindTotal", "testPlaceOrder", "testCheckout"));
        expected.put("OrderRepository.findTotal",
                Set.of("testSaveAndFindTotal", "testPlaceOrder", "testCheckout"));
        expected.put("OrderService.placeOrder", Set.of("testPlaceOrder", "testCheckout"));
        expected.put("OrderService.getOrderTotal",
                Set.of("testPlaceOrder", "testCheckout", "testCheckoutOutOfStock"));
        expected.put("TaxCalculator.calculateTax",
                Set.of("testCalculateTax", "testCalculateTaxOnZeroAmount",
                        "testFinalPrice", "testFinalPriceOnZeroAmount", "testCheckout"));
        expected.put("DiscountPolicy.applyDiscount",
                Set.of("testApplyDiscount", "testApplyDiscountOnZeroAmount",
                        "testFinalPrice", "testFinalPriceOnZeroAmount", "testCheckout"));
        expected.put("PricingService.finalPrice", Set.of("testFinalPrice", "testCheckout"));
        expected.put("InventoryRepository.setStock", Set.of("testSetAndGetStock", "testRestockAndAvailable"));
        expected.put("InventoryRepository.getStock", Set.of("testSetAndGetStock", "testRestockAndAvailable"));
        expected.put("InventoryService.restock", Set.of("testRestockAndAvailable"));
        expected.put("InventoryService.available", Set.of("testRestockAndAvailable", "testCheckoutOutOfStock"));
        expected.put("CheckoutService.checkout", Set.of("testCheckout"));
        expected.put("CheckoutService.quote", Collections.emptySet());

        List<FaultInjectionRunner.FaultResult> results = new FaultInjectionRunner().run(
                SAMPLE_PROJECT_PATH, VERSION_16_COMMIT, GrownSampleFaults.faults());

        String table = FaultInjectionRunner.toMarkdown(results);
        assertEquals(17, results.size(), "Actual table:\n" + table);

        for (FaultInjectionRunner.FaultResult result : results) {
            String method = result.getFault().getMethod();
            Set<String> expectedFailures = expected.get(method);
            assertNotNull(expectedFailures, "No expectation for " + method);

            assertNotEquals(FaultInjectionRunner.Outcome.MISSED, result.getOutcome(),
                    method + " left a failing test unselected. Actual table:\n" + table);
            assertTrue(result.getFailingTests().containsAll(expectedFailures),
                    method + " should fail at least " + expectedFailures + ". Actual table:\n" + table);
            assertEquals(expectedFailures.isEmpty()
                            ? FaultInjectionRunner.Outcome.NOT_DETECTED_BY_SUITE
                            : FaultInjectionRunner.Outcome.CAUGHT,
                    result.getOutcome(), method + ". Actual table:\n" + table);
        }

        assertTrue(FaultInjectionRunner.summary(results).contains("missed: 0"),
                "Actual table:\n" + table);
    }
}
