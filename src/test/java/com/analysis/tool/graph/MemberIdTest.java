package com.analysis.tool.graph;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H3: identity of a declared member. Arity is part of the identity, so two
 * overloads that differ only in parameter count are distinct members; a
 * constructor uses a sentinel name and can never equal a method.
 *
 * M1: declared parameter types are part of the identity too, so two overloads
 * that share a name AND an arity but differ in parameter type (f(int) vs
 * f(String)) are distinct. When the types are unknown the identity degrades to
 * the previous (class, name, arity, kind) behaviour, which only over-selects.
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

    @Test
    void withArityPreservesParameterTypes() {
        MemberId id = MemberId.forMethod("Calc", "f", 1, List.of("int"));
        MemberId unknown = id.withArity(MemberId.UNKNOWN_ARITY);

        assertEquals(List.of("int"), unknown.getParameterTypes());
        assertEquals(1, id.getParameterTypes().size());
    }

    @Test
    void sameArityOverloadsWithDifferentParameterTypesAreDistinct() {
        MemberId intOverload = MemberId.forMethod("Calc", "f", 1, List.of("int"));
        MemberId stringOverload = MemberId.forMethod("Calc", "f", 1, List.of("String"));

        assertNotEquals(intOverload, stringOverload);
        assertEquals(intOverload, MemberId.forMethod("Calc", "f", 1, List.of("int")));
        assertTrue(intOverload.hasParameterTypes());
    }

    @Test
    void unknownParameterTypesDegradeToArityOnlyIdentity() {
        MemberId withTypes = MemberId.forMethod("Calc", "f", 1, List.of("int"));
        MemberId withoutTypes = MemberId.forMethod("Calc", "f", 1);

        assertEquals(withoutTypes, withTypes.withoutParameterTypes());
        assertNotEquals(withTypes, withoutTypes);
        assertFalse(withoutTypes.hasParameterTypes());
    }

    @Test
    void possiblyCompatibleOnlyRejectsProvablyDisjointCategories() {
        // Provably disjoint argument/parameter pairs are rejected...
        assertFalse(MemberId.possiblyCompatible("int", "String"));
        assertFalse(MemberId.possiblyCompatible("String", "int"));
        assertFalse(MemberId.possiblyCompatible("boolean", "int"));

        // ...while matching, reference and unknown types stay compatible.
        assertTrue(MemberId.possiblyCompatible("int", "int"));
        assertTrue(MemberId.possiblyCompatible("String", "String"));
        assertTrue(MemberId.possiblyCompatible("int", MemberId.UNKNOWN_TYPE));
        assertTrue(MemberId.possiblyCompatible(MemberId.UNKNOWN_TYPE, "int"));
        assertTrue(MemberId.possiblyCompatible("Object", "String"));
        assertTrue(MemberId.possiblyCompatible("Comparable", "int"));
    }

    @Test
    void simpleTypeNameStripsGenericsArraysAndPackage() {
        assertEquals("List", MemberId.simpleTypeName("java.util.List<String>"));
        assertEquals("int", MemberId.simpleTypeName("int[]"));
        assertEquals("String", MemberId.simpleTypeName("String..."));
    }
}
