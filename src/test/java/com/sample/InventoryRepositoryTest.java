package com.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class InventoryRepositoryTest {

    @Test
    public void testSetAndGetStock() {
        InventoryRepository repository = new InventoryRepository();
        repository.setStock("widget", 12);
        assertEquals(12, repository.getStock("widget"));
    }
}
