package com.analysis.tool.metrics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StaticCoverageTest {

    private static final String PROD =
            "package demo;\n"
          + "public class Calc {\n"
          + "    public int add(int a, int b) { return a + b; }\n"
          + "    public int twice(int a) { return add(a, a); }\n"
          + "    public int lonely(int a) { return -a; }\n"
          + "}\n";

    private static final String TEST =
            "package demo;\n"
          + "import org.junit.jupiter.api.Test;\n"
          + "public class CalcTest {\n"
          + "    @Test public void testTwice() { new Calc().twice(2); }\n"
          + "}\n";

    @Test
    public void directAndTransitiveCallsCountAndUncalledMethodsDoNot() {
        StaticCoverage.Result r = new StaticCoverage().compute(List.of(PROD), List.of(TEST));

        assertEquals(Set.of("Calc.add", "Calc.twice", "Calc.lonely"), r.getProduction());
        assertEquals(Set.of("Calc.add", "Calc.twice"), r.getCovered());
        assertEquals(Set.of("Calc.lonely"), r.getUncovered());
        assertEquals(200.0 / 3, r.percent(), 0.001);
    }

    @Test
    public void noTestsMeansNothingIsCovered() {
        StaticCoverage.Result r = new StaticCoverage().compute(List.of(PROD), List.of());

        assertTrue(r.getCovered().isEmpty());
        assertEquals(0.0, r.percent(), 0.0);
    }

    @Test
    public void noProductionMethodsGivesNaN() {
        assertTrue(Double.isNaN(new StaticCoverage()
                .compute(List.of("package demo; public class Empty { }"), List.of()).percent()));
    }
}
