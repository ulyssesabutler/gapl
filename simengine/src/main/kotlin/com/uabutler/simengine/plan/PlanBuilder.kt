package com.uabutler.simengine.plan

import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.util.isRegister
import com.uabutler.util.graph.UnweightedGraph

object PlanBuilder {

    fun build(module: Module): ModulePlan {
        module.validateModule() // fail fast on unconnected wires with a clear message

        val registerNodes = module.getBodyNodes()
            .filterIsInstance<PredefinedFunctionNode>()
            .filter { it.predefinedFunction.isRegister }
        val registerNodeSet = registerNodes.toSet() // reference equality (Node has no equals override)

        // Registers are excluded from the ordering graph entirely: a register's output reflects
        // last tick's latched state and never gates this tick's combinational settle order, and its
        // input ("next") is only ever consumed by ModuleInstance.latchRegisters(), not settle().
        val orderedNodes = module.getBodyNodes().filterNot { it in registerNodeSet }
        val graphNodes: Map<Node, UnweightedGraph.Node<Node>> = orderedNodes.associateWith { UnweightedGraph.Node(it) }

        val edges = orderedNodes.flatMap { node ->
            node.inputWires().mapNotNull { inputWire ->
                val sourceNode = module.getConnectionForInputWire(inputWire).source
                    .parentWireVector.parentGroup.parentNode
                // No edge if the source isn't itself in the ordering graph (an InputNode's value is
                // available from tick 0, and a register's "current" output is always already-latched)
                val sourceGraphNode = graphNodes[sourceNode] ?: return@mapNotNull null
                UnweightedGraph.Edge(source = sourceGraphNode, sink = graphNodes.getValue(node), value = Unit)
            }
        }

        val evaluationOrder = UnweightedGraph(graphNodes.values, edges).topologicalSort().map { it.value }

        // Every OutputWire in the module (including register outputs, excluded above only from the
        // ordering graph) gets a dense storage slot; every InputWire resolves lazily to its driver's slot.
        val allOutputWires: List<OutputWire> = module.getNodes().flatMap { it.outputWires() }
        val outputWireIndex = allOutputWires.withIndex().associate { (i, w) -> w to i }

        val inputWireSource: Map<InputWire, Int> = module.getNodes()
            .flatMap { it.inputWires() }
            .associateWith { inputWire -> outputWireIndex.getValue(module.getConnectionForInputWire(inputWire).source) }

        return ModulePlan(
            module = module,
            outputWireIndex = outputWireIndex,
            outputWireCount = allOutputWires.size,
            inputWireSource = inputWireSource,
            evaluationOrder = evaluationOrder,
            registerNodes = registerNodes,
        )
    }
}
