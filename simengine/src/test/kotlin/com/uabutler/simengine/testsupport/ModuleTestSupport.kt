package com.uabutler.simengine.testsupport

import com.uabutler.netlistir.netlist.InputNode
import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.InputWireVectorGroup
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.MutableModule
import com.uabutler.netlistir.netlist.OutputNode
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.netlist.OutputWireVectorGroup
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.util.PredefinedFunction

/** Registers a single-vector input port of the given bit width on [module] and returns its node. */
fun MutableModule.inputPort(name: String, size: Int): InputNode {
    val node = InputNode(identifier = name, parentModule = this) { n ->
        listOf(OutputWireVectorGroup(identifier = "value", parentNode = n, structure = PredefinedFunction.wireVector(size)))
    }
    addInputNode(node)
    return node
}

/** Registers a single-vector output port of the given bit width on [module] and returns its node. */
fun MutableModule.outputPort(name: String, size: Int): OutputNode {
    val node = OutputNode(identifier = name, parentModule = this) { n ->
        listOf(InputWireVectorGroup(identifier = "value", parentNode = n, structure = PredefinedFunction.wireVector(size)))
    }
    addOutputNode(node)
    return node
}

/** Registers a [PredefinedFunctionNode] wrapping [fn] on [module] and returns it. */
fun MutableModule.predefinedFunctionNode(name: String, fn: PredefinedFunction): PredefinedFunctionNode {
    val node = PredefinedFunctionNode(
        identifier = name,
        parentModule = this,
        inputWireVectorGroupsBuilder = { n -> fn.inputs.map { it.toInputWireVectorGroup(n) } },
        outputWireVectorGroupsBuilder = { n -> fn.outputs.map { it.toOutputWireVectorGroup(n) } },
        predefinedFunction = fn,
    )
    addBodyNode(node)
    return node
}

fun MutableModule.wire(source: OutputWire, sink: InputWire) = connect(sink, source)

/** Connects each corresponding pair of bits — [sources] and [sinks] must be the same length. */
fun MutableModule.wireAll(sources: List<OutputWire>, sinks: List<InputWire>) =
    sources.zip(sinks).forEach { (source, sink) -> wire(source, sink) }

/**
 * Registers a [ModuleInvocationNode] on [module] calling [callee], with one wire-vector group per
 * callee port, keyed by the callee's own port names (matching how a real invocation is wired) and
 * sized to match each callee port's actual bit width.
 */
fun MutableModule.moduleInvocationNode(name: String, callee: Module): ModuleInvocationNode {
    val node = ModuleInvocationNode(
        identifier = name,
        parentModule = this,
        inputWireVectorGroupsBuilder = { n ->
            callee.getInputNodes().map { calleeInput ->
                InputWireVectorGroup(
                    identifier = calleeInput.name(),
                    parentNode = n,
                    structure = PredefinedFunction.wireVector(calleeInput.outputWires().size),
                )
            }
        },
        outputWireVectorGroupsBuilder = { n ->
            callee.getOutputNodes().map { calleeOutput ->
                OutputWireVectorGroup(
                    identifier = calleeOutput.name(),
                    parentNode = n,
                    structure = PredefinedFunction.wireVector(calleeOutput.inputWires().size),
                )
            }
        },
        invocation = callee.invocation,
    )
    addBodyNode(node)
    return node
}
