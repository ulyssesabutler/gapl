package com.uabutler.netlistir.transformer.retiming

import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.InputWireVectorGroup
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.transformer.retiming.delay.PropagationDelay
import com.uabutler.netlistir.transformer.retiming.graph.WeightedModule
import com.uabutler.netlistir.util.RegisterFunction

object WeightedModuleFromModule {

    fun convert(module: Module, delay: PropagationDelay): WeightedModule {
        return module
            .let { buildSimpleWeightedModule(it, delay) }
            .let { WeightedModuleCondenser.condenseWeightedModule(it) }
    }

    private data class WeightedSource(
        val source: OutputWire,
        val weight: Int,
    )

    private fun getNonRegisterSource(module: Module, inputWire: InputWire, registerSourceConnections: Map<OutputWire, InputWire>, currentWeight: Int = 0): WeightedSource {
        val source = module.getConnectionForInputWire(inputWire).source
        val sourceNode = source.parentWireVector.parentGroup.parentNode

        if (sourceNode is PredefinedFunctionNode && sourceNode.predefinedFunction !is RegisterFunction) {
            return WeightedSource(source, currentWeight)
        }

        val sourceRegisterInput = registerSourceConnections[source]!!

        return getNonRegisterSource(module, sourceRegisterInput, registerSourceConnections, currentWeight + 1)
    }

    private fun buildSimpleWeightedModule(module: Module, delay: PropagationDelay): WeightedModule {
        val registerSourceConnections = module.getNodes()
            .filterIsInstance<PredefinedFunctionNode>()
            .filter { it.predefinedFunction is RegisterFunction }
            .flatMap { it.outputWires().zip(it.inputWires()) }
            .associate { it }

        val nonRegisterNodes = module.getNodes()
            .filter { it !is PredefinedFunctionNode || it.predefinedFunction !is RegisterFunction }
            .map { WeightedModule.WeightedNode(it, delay.forNode(it)) }
            .associateBy { it.node }

        val weightedConnections = nonRegisterNodes.keys
            .flatMap { node -> node.inputWires() }
            .map {
                val source = getNonRegisterSource(module, it, registerSourceConnections)
                WeightedModule.WeightedConnection(
                    source = nonRegisterNodes[it.parentWireVector.parentGroup.parentNode]!!,
                    sink = nonRegisterNodes[source.source.parentWireVector.parentGroup.parentNode]!!,
                    weight = source.weight,
                    connectionGroups = listOf(
                        WeightedModule.InputGroupConnection(
                            group = it.parentWireVector.parentGroup as InputWireVectorGroup,
                            connections = listOf(
                                Module.Connection(
                                    source = source.source,
                                    sink = it,
                                )
                            )
                        )
                    )
                )
            }

        return WeightedModule(
            module = module,
            nodes = nonRegisterNodes.values.toList(),
            connections = weightedConnections,
        )
    }

}