package com.uabutler.netlistir.transformer.retiming

import com.uabutler.netlistir.builder.util.VectorInterfaceStructure
import com.uabutler.netlistir.builder.util.WireInterfaceStructure
import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.transformer.retiming.graph.WeightedModule
import com.uabutler.netlistir.util.RegisterFunction
import com.uabutler.util.AnonymousIdentifierGenerator

object ModuleFromWeightedModule {

    fun removeConnections(module: Module) {
        module.getConnections().forEach { module.disconnect(it.sink) }
    }

    fun removeRegisters(module: Module) {
        module.getNodes()
            .filterIsInstance<PredefinedFunctionNode>()
            .filter { it.predefinedFunction is RegisterFunction }
            .forEach { module.removeNode(it) }
    }

    fun addVectorConnection(module: Module, source: List<OutputWire>, sink: List<InputWire>, weight: Int) {
        if (weight > 0) {
            val registerFunction = RegisterFunction(
                storageStructure = VectorInterfaceStructure(
                    vectoredInterface = WireInterfaceStructure,
                    size = source.size,
                )
            )

            val registerNode = PredefinedFunctionNode(
                identifier = AnonymousIdentifierGenerator.genIdentifier(), // TODO: Use a better identifier
                parentModule = module,
                inputWireVectorGroupsBuilder = { node ->
                    registerFunction.inputs.map { it.toInputWireVectorGroup(node) }
                },
                outputWireVectorGroupsBuilder = { node ->
                    registerFunction.outputs.map { it.toOutputWireVectorGroup(node) }
                },
                predefinedFunction = registerFunction,
            )

            module.addBodyNode(registerNode)

            sink.zip(registerNode.outputWires()).forEach { (sinkWire, registerWire) ->
                module.connect(sinkWire, registerWire)
            }

            addVectorConnection(module, source, registerNode.inputWires(), weight - 1)
        }
    }

    fun addWeightedGroupConnection(module: Module, groupConnection: WeightedModule.InputGroupConnection, weight: Int) {
        addVectorConnection(
            module = module,
            source = groupConnection.connections.map { it.source },
            sink = groupConnection.connections.map { it.sink },
            weight = weight,
        )
    }

    fun addWeightedConnection(module: Module, weightedConnection: WeightedModule.WeightedConnection) {
        weightedConnection.connectionGroups.forEach { addWeightedGroupConnection(module, it, weightedConnection.weight) }
    }

    fun convert(weightedModule: WeightedModule): Module {
        // Re-condense
        val weightedModule = WeightedModuleCondenser.condenseWeightedModule(weightedModule)

        // Start with a clean slate
        removeConnections(weightedModule.module)
        removeRegisters(weightedModule.module)

        // Re-Add all connections
        weightedModule.connections.forEach { connection ->
            addWeightedConnection(weightedModule.module, connection)
        }

        return weightedModule.module
    }

}