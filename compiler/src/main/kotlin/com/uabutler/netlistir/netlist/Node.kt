package com.uabutler.netlistir.netlist

import com.uabutler.netlistir.util.ObjectUtils
import com.uabutler.netlistir.util.PredefinedFunction

sealed class Node(
    private var identifier: String,
    val parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>
) {
    fun name() = identifier
    fun rename(newName: String) { identifier = newName }

    val inputWireVectorGroups: List<InputWireVectorGroup> = inputWireVectorGroupsBuilder(this)
    val outputWireVectorGroups: List<OutputWireVectorGroup> = outputWireVectorGroupsBuilder(this)

    fun inputWires(): List<InputWire> = inputWireVectorGroups.flatMap { it.wires() }
    fun outputWires(): List<OutputWire> = outputWireVectorGroups.flatMap { it.wires() }

    fun inputWireVectors() = inputWireVectorGroups.flatMap { it.wireVectors }
    fun outputWireVectors() = outputWireVectorGroups.flatMap { it.wireVectors }

    override fun toString(): String {
        return ObjectUtils.toStringBuilder(
            obj = this,
            normalProps = mapOf(
                "identifier" to identifier,
                "inputWireVectorGroups" to inputWireVectorGroups,
                "outputWireVectorGroups" to outputWireVectorGroups
            ),
            identityProps = mapOf(
                "parentModule" to parentModule
            )
        )
    }
}

sealed class IONode(
    identifier: String,
    parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>
) : Node(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)

class InputNode(
    identifier: String,
    parentModule: Module,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>
) : IONode(identifier, parentModule, { emptyList() }, outputWireVectorGroupsBuilder)

class OutputNode(
    identifier: String,
    parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
) : IONode(identifier, parentModule, inputWireVectorGroupsBuilder, { emptyList() })

sealed class BodyNode(
    identifier: String,
    parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>
) : Node(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)

class ModuleInvocationNode(
    identifier: String,
    parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>,
    val invocation: Module.Invocation,
) : BodyNode(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)

class PredefinedFunctionNode(
    identifier: String,
    parentModule: Module,
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>,
    val predefinedFunction: PredefinedFunction
) : BodyNode(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)

class PassThroughNode(
    identifier: String,
    parentModule: Module,
    // TODO: We need some way to represent the interface that will be the same on both sides
    //   For now, the user is just going to pinky promise they're the same
    inputWireVectorGroupsBuilder: (Node) -> List<InputWireVectorGroup>,
    outputWireVectorGroupsBuilder: (Node) -> List<OutputWireVectorGroup>,
): BodyNode(identifier, parentModule, inputWireVectorGroupsBuilder, outputWireVectorGroupsBuilder)