package com.analysis.tool.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * H3: identity of a declared member. Arity is part of the identity, so two
 * overloads that differ only in parameter count are distinct members; a
 * constructor uses a sentinel name and can never equal a method.
 */
class MemberIdTest {

    @Test
    void methodIdentityIncludesClassArityAndKind() {
        MemberId a = MemberId.forMethod("Calc", "f", 1);
        MemberId b = MemberId.forMethod("Calc", "f", 1);
        MemberId otherArity = MemberId.forMethod("Calc", "f", 2);
        MemberId otherClass = MemberId.forMethod("Other", "f", 1);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, otherArity);
        assertNotEquals(a, otherClass);
    }

    @Test
    void constructorUsesSentinelNameKindAndNeverEqualsAMethod() {
        MemberId ctor = MemberId.forConstructor("Calc", 1);

        assertEquals(MemberId.CONSTRUCTOR_NAME, ctor.getMemberName());
        assertEquals(MemberKind.CONSTRUCTOR, ctor.getKind());
        assertNotEquals(ctor, MemberId.forMethod("Calc", MemberId.CONSTRUCTOR_NAME, 1));
    }

    @Test
    void withArityReturnsACopyThatOnlyChangesArity() {
        MemberId id = MemberId.forMethod("Calc", "f", 2);
        MemberId unknown = id.withArity(MemberId.UNKNOWN_ARITY);

        assertEquals(MemberId.UNKNOWN_ARITY, unknown.getArity());
        assertEquals("Calc", unknown.getClassName());
        assertEquals("f", unknown.getMemberName());
        assertEquals(MemberKind.METHOD, unknown.getKind());
        assertNotEquals(id, unknown);
    }
}
