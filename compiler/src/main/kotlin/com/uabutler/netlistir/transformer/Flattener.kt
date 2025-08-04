package com.uabutler.netlistir.transformer

import com.uabutler.netlistir.netlist.BodyNode
import com.uabutler.netlistir.netlist.IONode
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

object Flattener: Transformer {
    private class Helper(val original: List<Module>) {
        val modules = original.associateBy { it.invocation }
        val flattenedModules = mutableMapOf<Module.Invocation, Module>()

        data class WirePair<T: Wire>(
            val current: T,
            val inlining: T,
        )

        val inputWirePairs: MutableList<WirePair<InputWire>> = mutableListOf()
        val outputWirePairs: MutableList<WirePair<OutputWire>> = mutableListOf()

        fun inlinedIdentifier(invocationIdentifier: String, nodeIdentifier: String) = "$invocationIdentifier$$nodeIdentifier"

        fun rootModules(): List<Module> {
            val couldBeRoot = original.associate { it.invocation to true }.toMutableMap()

            val invocations = original.flatMap { it.getBodyNodes() }
                .filterIsInstance<ModuleInvocationNode>()
                .map { it.invocation }
                .toSet()

            invocations.forEach { couldBeRoot[it] = false }

            return couldBeRoot.filterValues { it }.keys.map { modules[it]!! }
        }

        fun addWirePairs(current: Node, inlining: Node) {
            current.inputWires().zip(inlining.inputWires()).forEach { (currentWire, inliningWire) ->
                inputWirePairs.add(WirePair(current = currentWire, inlining = inliningWire))
            }

            current.outputWires().zip(inlining.outputWires()).forEach { (currentWire, inliningWire) ->
                outputWirePairs.add(WirePair(current = currentWire, inlining = inliningWire))
            }
        }

        fun copyInputNodeToPassThroughNode(inputNode: InputNode, invocationIdentifier: String, newParent: Module): PassThroughNode {
            return PassThroughNode(
                identifier = inlinedIdentifier(invocationIdentifier, inputNode.identifier),
                parentModule = newParent,
                inputWireVectorGroupsBuilder = { parentNode ->
                    inputNode.outputWireVectorGroups.map {
                        InputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                    }
                },
                outputWireVectorGroupsBuilder = { parentNode ->
                    inputNode.outputWireVectorGroups.map {
                        OutputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                    }
                },
            ).also { addWirePairs(it, inputNode) }
        }

        fun copyOutputNodeToPassThroughNode(outputNode: OutputNode, invocationIdentifier: String, newParent: Module): PassThroughNode {
            return PassThroughNode(
                identifier = inlinedIdentifier(invocationIdentifier, outputNode.identifier),
                parentModule = newParent,
                inputWireVectorGroupsBuilder = { parentNode ->
                    outputNode.inputWireVectorGroups.map {
                        InputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                    }
                },
                outputWireVectorGroupsBuilder = { parentNode ->
                    outputNode.inputWireVectorGroups.map {
                        OutputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                    }
                },
            ).also { addWirePairs(it, outputNode) }
        }


        fun copyBodyNode(node: BodyNode, invocationIdentifier: String, newParent: Module): BodyNode {
            return when (node) {
                is ModuleInvocationNode -> throw Exception("This is a bug in the compiler. Module invocation nodes should have been inlined")
                is PassThroughNode -> PassThroughNode(
                    identifier = inlinedIdentifier(invocationIdentifier, node.identifier),
                    parentModule = newParent,
                    inputWireVectorGroupsBuilder = { parentNode ->
                        node.inputWireVectorGroups.map {
                            InputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                        }
                    },
                    outputWireVectorGroupsBuilder = { parentNode ->
                        node.outputWireVectorGroups.map {
                            OutputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                        }
                    }
                ).also { addWirePairs(it, node) }
                is PredefinedFunctionNode -> PredefinedFunctionNode(
                    identifier = inlinedIdentifier(invocationIdentifier, node.identifier),
                    parentModule = newParent,
                    inputWireVectorGroupsBuilder = { parentNode ->
                        node.inputWireVectorGroups.map {
                            InputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                        }
                    },
                    outputWireVectorGroupsBuilder = { parentNode ->
                        node.outputWireVectorGroups.map {
                            OutputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                        }
                    },
                    predefinedFunction = node.predefinedFunction,
                ).also { addWirePairs(it, node) }
            }
        }

        fun inlineModuleInvocation(node: ModuleInvocationNode) {
            val invocationIdentifier = node.identifier
            val currentModule = node.parentModule
            val inliningModule = flattenModule(modules[node.invocation]!!)

            // STEP 1a: Create the IO Nodes. We create a PassThrough node for each IO Node in the inlining module
            val inputNodes = inliningModule.getInputNodes().associate { inputNode ->
                inputNode.identifier to copyInputNodeToPassThroughNode(inputNode, invocationIdentifier, currentModule)
            }

            val outputNodes = inliningModule.getOutputNodes().associate { outputNode ->
                outputNode.identifier to copyOutputNodeToPassThroughNode(outputNode, invocationIdentifier, currentModule)
            }

            // STEP 1b: Disconnect the ModuleInvocationNode and connect the new PassThroughNodes for each IO Node
            //  Note: These will have no equivalent in the "wire pairs" list
            inputNodes.keys.forEach { variableName ->
                val currentGroup = node.inputWireVectorGroups.first { it.identifier == variableName }
                val newNodeGroup = inputNodes[variableName]!!.inputWireVectorGroups.first()

                currentGroup.wires().zip(newNodeGroup.wires()).forEach { (currentWire, newWire) ->
                    val currentWireSource = currentModule.getConnectionForInputWire(currentWire).outputWire
                    currentModule.disconnect(currentWire)
                    currentModule.connect(newWire, currentWireSource)
                }
            }

            outputNodes.keys.forEach { variableName ->
                val currentGroup = node.outputWireVectorGroups.first { it.identifier == variableName }
                val newNodeGroup = outputNodes[variableName]!!.outputWireVectorGroups.first()

                currentGroup.wires().zip(newNodeGroup.wires()).forEach { (currentWire, newWire) ->
                    val currentWireConnections = currentModule.getConnectionsForOutputWire(currentWire)

                    currentWireConnections.forEach { connection ->
                        currentModule.disconnect(connection.inputWire)
                        currentModule.connect(connection.inputWire, newWire)
                    }
                }
            }

            // Step 2a: Create an identical body node in the current module for each node in the inlining module
            val bodyNodes = inliningModule.getBodyNodes().map {
                copyBodyNode(it, invocationIdentifier, currentModule)
            }

            // Step 2b: Connect each new node in the new module based on the connections in the inlining module
            bodyNodes.forEach { node ->
                node.inputWires().forEach { inputWire ->
                    val correspondingOfCurrentInput = inputWirePairs.first { it.current == inputWire }.inlining
                    val sourceOfCorresponding = inliningModule.getConnectionForInputWire(correspondingOfCurrentInput).outputWire
                    val correspondingOfSource = outputWirePairs.first { it.inlining == sourceOfCorresponding }.current
                    currentModule.connect(inputWire, correspondingOfSource)
                }
            }
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