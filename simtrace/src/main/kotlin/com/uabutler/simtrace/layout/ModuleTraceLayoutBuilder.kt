package com.uabutler.simtrace.layout

import com.uabutler.netlistir.netlist.InputWireVectorGroup
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputWireVectorGroup
import com.uabutler.netlistir.netlist.PassThroughNode
import com.uabutler.netlistir.netlist.PredefinedFunctionNode

object ModuleTraceLayoutBuilder {
    private const val ANONYMOUS_PREFIX = "anonymous_"
    private fun Node.isAnonymous() = name().startsWith(ANONYMOUS_PREFIX)

    fun build(module: Module): ModuleTraceLayout {
        val signals = mutableListOf<TracedSignal>()
        val children = mutableListOf<ChildScope>()
        val perNodeTypeCounter = mutableMapOf<String, Int>() // reset per Module, per call to build()

        // InputNode: always traced (real name guaranteed), from its own outputWireVectorGroups —
        // an InputNode is "outputs only", its wires source a value into the module body.
        for (node in module.getInputNodes()) {
            signals += outputSignalsFor(node.name(), node.outputWireVectorGroups)
        }

        // OutputNode: always traced as an ALIAS under its own port name, from its inputWireVectorGroups
        // — it has no OutputWire of its own to be picked up by the "trace every OutputWire" rule below.
        for (node in module.getOutputNodes()) {
            signals += inputSignalsFor(node.name(), node.inputWireVectorGroups)
        }

        for (node in module.getBodyNodes()) {
            when (node) {
                is PassThroughNode -> if (!node.isAnonymous()) {
                    signals += outputSignalsFor(node.name(), node.outputWireVectorGroups)
                }
                // anonymous PassThroughNode: skipped entirely, no fallback name, no counter use.

                is PredefinedFunctionNode -> {
                    val baseName = localNameFor(node, perNodeTypeCounter)
                    signals += outputSignalsFor(baseName, node.outputWireVectorGroups)
                }

                is ModuleInvocationNode -> {
                    val baseName = localNameFor(node, perNodeTypeCounter)
                    signals += outputSignalsFor(baseName, node.outputWireVectorGroups)
                    // Always recurse, even if this invocation has zero output ports — nested traced
                    // signals still need a scope to live under.
                    children += ChildScope(scopeName = baseName, node = node)
                }

                else -> {}
            }
        }

        return ModuleTraceLayout(module, signals, children)
    }

    private fun localNameFor(node: Node, counter: MutableMap<String, Int>): String {
        if (!node.isAnonymous()) return node.name()
        val type = node.nodeType()
        val n = counter.getOrDefault(type, 0)
        counter[type] = n + 1
        return "${type}_$n"
    }

    private fun signalName(base: String, groupCount: Int, groupId: String, fieldPath: List<String>): String {
        var name = base
        if (groupCount > 1) name += "_$groupId"
        if (fieldPath.isNotEmpty()) name += "_" + fieldPath.joinToString("_")
        return name
    }

    private fun outputSignalsFor(baseName: String, groups: List<OutputWireVectorGroup>): List<TracedSignal> =
        groups.flatMap { group ->
            group.wireVectors.map { wv ->
                TracedSignal(signalName(baseName, groups.size, group.identifier, wv.identifier), wv.wires)
            }
        }

    private fun inputSignalsFor(baseName: String, groups: List<InputWireVectorGroup>): List<TracedSignal> =
        groups.flatMap { group ->
            group.wireVectors.map { wv ->
                TracedSignal(signalName(baseName, groups.size, group.identifier, wv.identifier), wv.wires)
            }
        }
}
