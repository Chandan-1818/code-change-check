package com.sample;

public class Calculator {

    public int add(int a, int b) {
        return a + b;
    }

    public int subtract(int a, int b) {
        int result = a - b;
        return result;
    }

    public int compute(int a, int b) {
        Multiplier multiplier = new Multiplier();
        return multiplier.multiply(a, b);
    }

    public int negate(int a) {
        return -a;
    }
}
