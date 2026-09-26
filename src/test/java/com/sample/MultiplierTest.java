package com.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class MultiplierTest {

    @Test
    public void testMultiply() {
        Multiplier multiplier = new Multiplier();
        assertEquals(6, multiplier.multiply(2, 3));
    }
}
