package com.uabutler.simengine.eval

import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.util.AdditionFunction
import com.uabutler.netlistir.util.BitwiseAndFunction
import com.uabutler.netlistir.util.BitwiseNotFunction
import com.uabutler.netlistir.util.BitwiseOrFunction
import com.uabutler.netlistir.util.BitwiseXorFunction
import com.uabutler.netlistir.util.DemuxFunction
import com.uabutler.netlistir.util.EqualsFunction
import com.uabutler.netlistir.util.GreaterThanFunction
import com.uabutler.netlistir.util.LeftShiftFunction
import com.uabutler.netlistir.util.LessThanFunction
import com.uabutler.netlistir.util.LiteralFunction
import com.uabutler.netlistir.util.LogicalAndFunction
import com.uabutler.netlistir.util.LogicalNotFunction
import com.uabutler.netlistir.util.LogicalOrFunction
import com.uabutler.netlistir.util.MultiplicationFunction
import com.uabutler.netlistir.util.MuxFunction
import com.uabutler.netlistir.util.PriorityFunction
import com.uabutler.netlistir.util.RightShiftFunction
import com.uabutler.netlistir.util.SubtractionFunction
import com.uabutler.simengine.testsupport.bits
import com.uabutler.simengine.testsupport.readerFor
import com.uabutler.simengine.testsupport.standaloneNode
import com.uabutler.simengine.testsupport.toIntValue
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PredefinedFunctionEvaluatorTest {

    // Directed bit-ordering check: 3 + 1 = 4 only comes out right if bit 0 is genuinely the LSB
    // on both inputs and the result, per BitUtils' documented convention.
    @Test
    fun `bit ordering is LSB-first end to end through eval`() {
        val node = standaloneNode(AdditionFunction(size = 4))
        val read = readerFor(node, mapOf("lhs" to bits(3, 4), "rhs" to bits(1, 4)))
        assertEquals(4, PredefinedFunctionEvaluator.evaluate(node, read).toIntValue())
    }

    @Test
    fun `addition truncates to size bits`() {
        val node = standaloneNode(AdditionFunction(size = 4))
        val read = readerFor(node, mapOf("lhs" to bits(15, 4), "rhs" to bits(2, 4)))
        assertEquals(1, PredefinedFunctionEvaluator.evaluate(node, read).toIntValue()) // 17 mod 16 = 1
    }

    @Test
    fun `subtraction wraps around on underflow`() {
        val node = standaloneNode(SubtractionFunction(size = 4))
        val read = readerFor(node, mapOf("lhs" to bits(0, 4), "rhs" to bits(1, 4)))
        assertEquals(15, PredefinedFunctionEvaluator.evaluate(node, read).toIntValue())
    }

    @Test
    fun `multiplication`() {
        val node = standaloneNode(MultiplicationFunction(size = 8))
        val read = readerFor(node, mapOf("lhs" to bits(6, 8), "rhs" to bits(7, 8)))
        assertEquals(42, PredefinedFunctionEvaluator.evaluate(node, read).toIntValue())
    }

    @Test
    fun `left and right shift`() {
        val leftNode = standaloneNode(LeftShiftFunction(size = 8))
        val leftRead = readerFor(leftNode, mapOf("lhs" to bits(1, 8), "rhs" to bits(3, 8)))
        assertEquals(8, PredefinedFunctionEvaluator.evaluate(leftNode, leftRead).toIntValue())

        val rightNode = standaloneNode(RightShiftFunction(size = 8))
        val rightRead = readerFor(rightNode, mapOf("lhs" to bits(8, 8), "rhs" to bits(3, 8)))
        assertEquals(1, PredefinedFunctionEvaluator.evaluate(rightNode, rightRead).toIntValue())
    }

    @Test
    fun `comparisons`() {
        val eqNode = standaloneNode(EqualsFunction(size = 4))
        assertTrue(PredefinedFunctionEvaluator.evaluate(eqNode, readerFor(eqNode, mapOf("lhs" to bits(3, 4), "rhs" to bits(3, 4))))[0])
        assertFalse(PredefinedFunctionEvaluator.evaluate(eqNode, readerFor(eqNode, mapOf("lhs" to bits(3, 4), "rhs" to bits(4, 4))))[0])

        val ltNode = standaloneNode(LessThanFunction(size = 4))
        assertTrue(PredefinedFunctionEvaluator.evaluate(ltNode, readerFor(ltNode, mapOf("lhs" to bits(2, 4), "rhs" to bits(3, 4))))[0])

        val gtNode = standaloneNode(GreaterThanFunction(size = 4))
        assertFalse(PredefinedFunctionEvaluator.evaluate(gtNode, readerFor(gtNode, mapOf("lhs" to bits(2, 4), "rhs" to bits(3, 4))))[0])
    }

    @Test
    fun `boolean and bitwise ops`() {
        val andNode = standaloneNode(LogicalAndFunction)
        assertTrue(PredefinedFunctionEvaluator.evaluate(andNode, readerFor(andNode, mapOf("lhs" to listOf(true), "rhs" to listOf(true))))[0])

        val orNode = standaloneNode(LogicalOrFunction)
        assertTrue(PredefinedFunctionEvaluator.evaluate(orNode, readerFor(orNode, mapOf("lhs" to listOf(false), "rhs" to listOf(true))))[0])

        val notNode = standaloneNode(LogicalNotFunction)
        assertFalse(PredefinedFunctionEvaluator.evaluate(notNode, readerFor(notNode, mapOf("input" to listOf(true))))[0])

        val bitwiseAndNode = standaloneNode(BitwiseAndFunction(size = 4))
        assertEquals(0b0100, PredefinedFunctionEvaluator.evaluate(bitwiseAndNode, readerFor(bitwiseAndNode, mapOf("lhs" to bits(0b0110, 4), "rhs" to bits(0b1100, 4)))).toIntValue())

        val bitwiseOrNode = standaloneNode(BitwiseOrFunction(size = 4))
        assertEquals(0b1110, PredefinedFunctionEvaluator.evaluate(bitwiseOrNode, readerFor(bitwiseOrNode, mapOf("lhs" to bits(0b0110, 4), "rhs" to bits(0b1100, 4)))).toIntValue())

        val bitwiseXorNode = standaloneNode(BitwiseXorFunction(size = 4))
        assertEquals(0b1010, PredefinedFunctionEvaluator.evaluate(bitwiseXorNode, readerFor(bitwiseXorNode, mapOf("lhs" to bits(0b0110, 4), "rhs" to bits(0b1100, 4)))).toIntValue())

        val bitwiseNotNode = standaloneNode(BitwiseNotFunction(size = 4))
        assertEquals(0b0001, PredefinedFunctionEvaluator.evaluate(bitwiseNotNode, readerFor(bitwiseNotNode, mapOf("input" to bits(0b1110, 4)))).toIntValue())
    }

    @Test
    fun `literal ignores read and returns its constant value`() {
        val node = standaloneNode(LiteralFunction(size = 8, value = BigInteger.valueOf(42)))
        assertEquals(42, PredefinedFunctionEvaluator.evaluate(node) { error("literal has no inputs") }.toIntValue())
    }

    @Test
    fun `mux selects the input at the selector index`() {
        val node = standaloneNode(MuxFunction(outputStructure = WireInterfaceStructure, inputCount = 4, selectorSize = 2))
        val read = readerFor(node, mapOf("selector" to bits(2, 2), "inputs" to listOf(false, true, true, false)))
        assertTrue(PredefinedFunctionEvaluator.evaluate(node, read)[0])
    }

    @Test
    fun `mux throws on an out-of-range selector`() {
        val node = standaloneNode(MuxFunction(outputStructure = WireInterfaceStructure, inputCount = 3, selectorSize = 2))
        val read = readerFor(node, mapOf("selector" to bits(3, 2), "inputs" to listOf(false, false, false)))
        assertFailsWith<IllegalStateException> { PredefinedFunctionEvaluator.evaluate(node, read) }
    }

    @Test
    fun `demux routes to the selected output and zero-fills the rest`() {
        val node = standaloneNode(DemuxFunction(inputStructure = WireInterfaceStructure, outputCount = 3, selectorSize = 2))
        val read = readerFor(node, mapOf("selector" to bits(1, 2), "input" to listOf(true)))
        assertEquals(listOf(false, true, false), PredefinedFunctionEvaluator.evaluate(node, read))
    }

    @Test
    fun `priority picks the first matching conditional, else the default`() {
        val node = standaloneNode(PriorityFunction(conditionalCount = 3, inputStructure = WireInterfaceStructure))
        val matchRead = readerFor(
            node,
            mapOf(
                "conditionals" to listOf(false, true, false) + listOf(false, true, true), // condition[3], value[3]
                "default" to listOf(false),
            ),
        )
        assertTrue(PredefinedFunctionEvaluator.evaluate(node, matchRead)[0])

        val noMatchRead = readerFor(
            node,
            mapOf(
                "conditionals" to listOf(false, false, false) + listOf(true, true, true),
                "default" to listOf(true),
            ),
        )
        assertTrue(PredefinedFunctionEvaluator.evaluate(node, noMatchRead)[0])
    }
}
