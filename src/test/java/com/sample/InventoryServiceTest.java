package com.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class InventoryServiceTest {

    @Test
    public void testRestockAndAvailable() {
        InventoryService service = new InventoryService(new InventoryRepository());
        service.restock("gadget", 7);
        assertEquals(7, service.available("gadget"));
    }
}
