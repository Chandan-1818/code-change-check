package com.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class OrderRepositoryTest {

    @Test
    public void testSaveAndFindTotal() {
        OrderRepository repository = new OrderRepository();
        repository.save(1, 250);
        assertEquals(250, repository.findTotal(1));
    }
}
