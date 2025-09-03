package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.BodyNode
import com.uabutler.netlistir.netlist.InputNode
import com.uabutler.netlistir.netlist.InputWire
import com.uabutler.netlistir.netlist.InputWireVectorGroup
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.netlist.ModuleInvocationNode
import com.uabutler.netlistir.netlist.Node
import com.uabutler.netlistir.netlist.OutputNode
import com.uabutler.netlistir.netlist.OutputWire
import com.uabutler.netlistir.netlist.OutputWireVectorGroup
import com.uabutler.netlistir.netlist.PassThroughNode
import com.uabutler.netlistir.netlist.PredefinedFunctionNode
import com.uabutler.netlistir.netlist.Wire
import com.uabutler.netlistir.transformer.util.NodeCopier
import com.uabutler.netlistir.transformer.util.NodeCopier.copyBodyNode
import com.uabutler.netlistir.transformer.util.NodeCopier.copyInputNodeToPassThroughNode
import com.uabutler.netlistir.transformer.util.NodeCopier.copyOutputNodeToPassThroughNode

object Flattener: Transformer {
    private class Helper(val original: List<Module>) {
        val modules = original.associateBy { it.invocation }
        val flattenedModules = mutableMapOf<Module.Invocation, Module>()

        fun rootModules(): List<Module> {
            val couldBeRoot = original.associate { it.invocation to true }.toMutableMap()

            val invocations = original.flatMap { it.getBodyNodes() }
                .filterIsInstance<ModuleInvocationNode>()
                .map { it.invocation }
                .toSet()

            invocations.forEach { couldBeRoot[it] = false }

            return couldBeRoot.filterValues { it }.keys.map { modules[it]!! }
        }

        fun inlineModuleInvocation(node: ModuleInvocationNode) {
            val invocationIdentifier = node.name()
            val currentModule = node.parentModule
            val inliningModule = flattenModule(modules[node.invocation]!!)

            var wirePairs = NodeCopier.WirePairs(emptyList(), emptyList())

            // STEP 1a: Create the IO Nodes. We create a PassThrough node for each IO Node in the inlining module
            val inputNodes = inliningModule.getInputNodes().associate { inputNode ->
                val createdNode = copyInputNodeToPassThroughNode(inputNode, invocationIdentifier, currentModule)
                wirePairs += createdNode.wirePairs
                inputNode.name() to createdNode.node
            }

            val outputNodes = inliningModule.getOutputNodes().associate { outputNode ->
                val createdNode = copyOutputNodeToPassThroughNode(outputNode, invocationIdentifier, currentModule)
                wirePairs += createdNode.wirePairs
                outputNode.name() to createdNode.node
            }

            // STEP 1b: Disconnect the ModuleInvocationNode and connect the new PassThroughNodes for each IO Node
            //  Note: These will have no equivalent in the "wire pairs" list
            val inputGroupLookup = node.inputWireVectorGroups.associateBy { it.identifier }
            inputNodes.keys.forEach { variableName ->
                val currentGroup = inputGroupLookup[variableName]!!
                val newNodeGroup = inputNodes[variableName]!!.inputWireVectorGroups.first()

                currentGroup.wires().zip(newNodeGroup.wires()).forEach { (currentWire, newWire) ->
                    val currentWireSource = currentModule.getConnectionForInputWire(currentWire).source
                    currentModule.disconnect(currentWire)
                    currentModule.connect(newWire, currentWireSource)
                }
            }

            val outputGroupLookup = node.outputWireVectorGroups.associateBy { it.identifier }
            outputNodes.keys.forEach { variableName ->
                val currentGroup = outputGroupLookup[variableName]!!
                val newNodeGroup = outputNodes[variableName]!!.outputWireVectorGroups.first()

                currentGroup.wires().zip(newNodeGroup.wires()).forEach { (currentWire, newWire) ->
                    val currentWireConnections = currentModule.getConnectionsForOutputWire(currentWire)

                    currentWireConnections.forEach { connection ->
                        currentModule.disconnect(connection.sink)
                        currentModule.connect(connection.sink, newWire)
                    }
                }
            }

            // Step 2a: Create an identical body node in the current module for each node in the inlining module
            val bodyNodes = inliningModule.getBodyNodes().map {
                val createdNode = copyBodyNode(it, invocationIdentifier, currentModule)
                wirePairs += createdNode.wirePairs
                createdNode.node
            }

            val inliningInput = wirePairs.input.associate { it.current to it.inlining }
            val currentOutput = wirePairs.output.associate { it.inlining to it.current }

            // Step 2b: Connect each new node in the new module based on the connections in the inlining module
            (bodyNodes + outputNodes.values).forEach { node ->
                node.inputWires().forEach { inputWire ->
                    val correspondingOfCurrentInput = inliningInput[inputWire]!!
                    val sourceOfCorresponding = inliningModule.getConnectionForInputWire(correspondingOfCurrentInput).source
                    val correspondingOfSource = currentOutput[sourceOfCorresponding]!!
                    currentModule.connect(inputWire, correspondingOfSource)
                }
            }

            currentModule.removeNode(node)
        }

        fun flattenModule(module: Module): Module {
            if (module.invocation in flattenedModules) return module

            module.getBodyNodes()
                .filterIsInstance<ModuleInvocationNode>()
                .forEach { inlineModuleInvocation(it) }

            flattenedModules[module.invocation] = module

            return module
        }

        fun flatten(): List<Module> {
            return rootModules().onEach { flattenModule(it) }
        }
    }

    override fun transform(original: List<Module>): List<Module> {
        return Helper(original).flatten()
    }
}