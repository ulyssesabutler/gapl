package com.uabutler.simengine.testsupport

import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.util.PredefinedFunction
import com.uabutler.simengine.eval.bitsToUnsignedBigInteger
import com.uabutler.simengine.eval.unsignedBigIntegerToBits
import java.math.BigInteger

/** bit 0 = LSB, matching every port's flattened wire order. */
fun bits(value: Int, size: Int): List<Boolean> = unsignedBigIntegerToBits(BigInteger.valueOf(value.toLong()), size)

fun List<Boolean>.toIntValue(): Int = bitsToUnsignedBigInteger(this).toInt()

fun testModule(): MutableModule = MutableModule(Module.Invocation("test", emptyList(), emptyList()))

fun standaloneNode(fn: PredefinedFunction, module: MutableModule = testModule()): PredefinedFunctionNode =
    PredefinedFunctionNode(
        identifier = "node",
        parentModule = module,
        inputWireVectorGroupsBuilder = { n -> fn.inputs.map { it.toInputWireVectorGroup(n) } },
        outputWireVectorGroupsBuilder = { n -> fn.outputs.map { it.toOutputWireVectorGroup(n) } },
        predefinedFunction = fn,
    )

/**
 * Builds a `read` callback for [standaloneNode] from named-port bit lists (bit 0 = LSB, matching
 * every port's flattened wire order).
 */
fun readerFor(node: PredefinedFunctionNode, values: Map<String, List<Boolean>>): (InputWire) -> Boolean {
    val wireToValue = node.inputWireVectorGroups.flatMap { group ->
        val portValues = values.getValue(group.identifier)
        group.wires().mapIndexed { i, wire -> wire to portValues[i] }
    }.toMap()
    return { wireToValue.getValue(it) }
}
