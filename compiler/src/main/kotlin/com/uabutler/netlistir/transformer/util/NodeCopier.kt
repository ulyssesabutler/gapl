package com.uabutler.netlistir.transformer.util

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

object NodeCopier {

    data class WirePair<T: Wire>(
        val current: T,
        val inlining: T,
    )

    data class WirePairs(
        val input: List<WirePair<InputWire>>,
        val output: List<WirePair<OutputWire>>,
    ) {
        operator fun plus(other: WirePairs): WirePairs {
            return WirePairs(input + other.input, output + other.output)
        }
    }

    data class CreatedNode<T: Node>(
        val node: T,
        val wirePairs: WirePairs,
    )

    private fun copiedIdentifier(invocationIdentifier: String, nodeName: String) = "COPY$$invocationIdentifier$$nodeName"

    private fun createWirePairs(current: Node, inlining: Node): WirePairs {
        val inputWirePairs = current.inputWires().zip(inlining.inputWires()).map { (currentWire, inliningWire) ->
            WirePair(current = currentWire, inlining = inliningWire)
        }
        val outputWirePairs = current.outputWires().zip(inlining.outputWires()).map { (currentWire, inliningWire) ->
            WirePair(current = currentWire, inlining = inliningWire)
        }

        return WirePairs(
            input = inputWirePairs,
            output = outputWirePairs,
        )
    }

    fun copyInputNode(inputNode: InputNode, invocationIdentifier: String, newParent: Module): CreatedNode<InputNode> {
        val node = InputNode(
            identifier = copiedIdentifier(invocationIdentifier, inputNode.name()),
            parentModule = newParent,
            outputWireVectorGroupsBuilder = { parentNode ->
                inputNode.outputWireVectorGroups.map {
                    OutputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                }
            },
        ).also { newParent.addInputNode(it) }

        val wirePairs = createWirePairs(node, inputNode)

        return CreatedNode(node, wirePairs)
    }

    fun copyOutputNode(outputNode: OutputNode, invocationIdentifier: String, newParent: Module): CreatedNode<OutputNode> {
        val node = OutputNode(
            identifier = copiedIdentifier(invocationIdentifier, outputNode.name()),
            parentModule = newParent,
            inputWireVectorGroupsBuilder = { parentNode ->
                outputNode.inputWireVectorGroups.map {
                    InputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                }
            },
        ).also { newParent.addOutputNode(it) }

        val wirePairs = createWirePairs(node, outputNode)

        return CreatedNode(node, wirePairs)
    }

    fun copyInputNodeToPassThroughNode(inputNode: InputNode, invocationIdentifier: String, newParent: Module): CreatedNode<PassThroughNode> {
        val node = PassThroughNode(
            identifier = copiedIdentifier(invocationIdentifier, inputNode.name()),
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
        ).also { newParent.addBodyNode(it) }

        val wirePairs = createWirePairs(node, inputNode)

        return CreatedNode(node, wirePairs)
    }

    fun copyOutputNodeToPassThroughNode(outputNode: OutputNode, invocationIdentifier: String, newParent: Module): CreatedNode<PassThroughNode> {
        val node = PassThroughNode(
            identifier = copiedIdentifier(invocationIdentifier, outputNode.name()),
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
        ).also { newParent.addBodyNode(it) }

        val wirePairs = createWirePairs(node, outputNode)

        return CreatedNode(node, wirePairs)
    }


    fun copyBodyNode(bodyNode: BodyNode, invocationIdentifier: String, newParent: Module): CreatedNode<BodyNode> {
        return when (bodyNode) {
            is ModuleInvocationNode -> throw Exception("This is a bug in the compiler. Module invocation nodes should have been inlined")
            is PassThroughNode -> {
                val node = PassThroughNode(
                    identifier = copiedIdentifier(invocationIdentifier, bodyNode.name()),
                    parentModule = newParent,
                    inputWireVectorGroupsBuilder = { parentNode ->
                        bodyNode.inputWireVectorGroups.map {
                            InputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                        }
                    },
                    outputWireVectorGroupsBuilder = { parentNode ->
                        bodyNode.outputWireVectorGroups.map {
                            OutputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                        }
                    }
                ).also { newParent.addBodyNode(it) }

                val wirePairs = createWirePairs(node, bodyNode)

                CreatedNode(node, wirePairs)
            }
            is PredefinedFunctionNode -> {
                val node = PredefinedFunctionNode(
                    identifier = copiedIdentifier(invocationIdentifier, bodyNode.name()),
                    parentModule = newParent,
                    inputWireVectorGroupsBuilder = { parentNode ->
                        bodyNode.inputWireVectorGroups.map {
                            InputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                        }
                    },
                    outputWireVectorGroupsBuilder = { parentNode ->
                        bodyNode.outputWireVectorGroups.map {
                            OutputWireVectorGroup(it.identifier, parentNode, it.gaplStructure)
                        }
                    },
                    predefinedFunction = bodyNode.predefinedFunction,
                ).also { newParent.addBodyNode(it) }

                val wirePairs = createWirePairs(node, bodyNode)

                CreatedNode(node, wirePairs)
            }
        }
    }

}